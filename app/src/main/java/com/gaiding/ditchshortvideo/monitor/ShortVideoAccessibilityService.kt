package com.gaiding.ditchshortvideo.monitor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.gaiding.ditchshortvideo.data.DayUtils
import com.gaiding.ditchshortvideo.data.MonitoredApps
import com.gaiding.ditchshortvideo.data.PlanEngine
import com.gaiding.ditchshortvideo.data.Prefs
import com.gaiding.ditchshortvideo.data.UsageStore
import com.gaiding.ditchshortvideo.notify.ReminderNotifier

/**
 * The only component that counts time.
 *
 * It is deliberately strict: a page must be explicitly recognised as a short
 * video feed for two consecutive seconds. Any app switch, screen-off, lock or
 * missing accessibility tree pauses the count immediately.
 */
class ShortVideoAccessibilityService : AccessibilityService() {
    private lateinit var prefs: Prefs
    private lateinit var usage: UsageStore
    private val handler = Handler(Looper.getMainLooper())

    private var foregroundPackage: String? = null
    private var foregroundActivity: String? = null
    private var detection: Detection? = null
    private var positiveStreak = 0
    private var sessionSeconds = 0L
    private var currentDate = DayUtils.today()
    private var lastSnapshotAt = 0L
    private var pageRetryUntil = 0L
    private var lastReminderAttempt = 0L
    private var countdownKey: String? = null
    private var countdownInactiveSince = 0L
    private var lastActivePackage: String? = null
    private var graceStartedAt = 0L
    private var wasCounting = false
    private var lastPage: PageSnapshot? = null
    private var lastLoggedActive = false
    private var overlayPackage: String? = null
    private var overlaySince = 0L
    private var connected = false
    private var ticker: Runnable? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                positiveStreak = 0
                resetGrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        usage = UsageStore(this)
        currentDate = DayUtils.today()
        PlanEngine.applyToday(prefs, usage)
        ReminderNotifier.cancelCountdown(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }

        connected = true
        prefs.guardArmed = true
        Guardian.ensureChannels(this)
        Guardian.schedule(this)
        KeepAliveService.start(this)
        lastReminderAttempt = -60_000L
        MonitorLog.add("无障碍服务已连接")
        ReminderNotifier.cancelServiceStopped(this)
        registerScreenReceiver()
        MonitorRuntime.update(
            MonitorRuntime.State(
                connected = true,
                reason = "等待打开监控 App",
                updatedAtElapsed = SystemClock.elapsedRealtime()
            )
        )

        ticker?.let { handler.removeCallbacks(it) }
        ticker = object : Runnable {
            override fun run() {
                tick()
                handler.postDelayed(this, TICK_MS)
            }
        }
        handler.post(ticker!!)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!connected || event == null) return
        val pkg = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val cls = event.className?.toString()
                val eventNow = SystemClock.elapsedRealtime()
                if (pkg != foregroundPackage && isSystemOverlay(pkg, cls)) {
                    // ColorOS emits transient SystemUI/IME window events while
                    // the feed is still visible. Switching foreground on those
                    // events resets the grace window and causes visible
                    // stop/start flicker. Hold the current app for up to
                    // PAGE_GRACE_MS; the tick promotes the overlay if it stays
                    // on screen longer than that.
                    if (overlayPackage != pkg) {
                        overlayPackage = pkg
                        overlaySince = eventNow
                        MonitorLog.add(
                            "系统覆盖窗口=${MonitoredApps.labelFor(pkg) ?: pkg}，暂不切换"
                        )
                    }
                    return
                }
                if (pkg == foregroundPackage && overlaySince != 0L) {
                    overlayPackage = null
                    overlaySince = 0L
                    MonitorLog.add("系统覆盖窗口结束，继续识别")
                }
                if (pkg != foregroundPackage) {
                    overlayPackage = null
                    overlaySince = 0L
                    resetGrace()
                    foregroundPackage = pkg
                    foregroundActivity = cls?.takeIf { looksLikeActivity(it, pkg) }
                    detection = null
                    positiveStreak = 0
                    sessionSeconds = 0L
                    pageRetryUntil = SystemClock.elapsedRealtime() + 8_000L
                    MonitorLog.add(
                        "切到 ${MonitoredApps.labelFor(pkg) ?: pkg}；页面类=${cls?.substringAfterLast('.') ?: "空"}"
                    )
                } else if (looksLikeActivity(cls, pkg) &&
                    !cls.isNullOrBlank() && cls != foregroundActivity
                ) {
                    // A real Activity changed. Overlay windows and dialogs often
                    // report FrameLayout/View classes; those must not erase the
                    // Activity that is still underneath (this used to stop the
                    // timer as soon as a comment sheet or feed swipe appeared).
                    foregroundActivity = cls
                    detection = null
                    positiveStreak = 0
                    sessionSeconds = 0L
                    pageRetryUntil = SystemClock.elapsedRealtime() + 8_000L
                    MonitorLog.add("Activity 变化=${cls.substringAfterLast('.')}")
                } else if (!cls.isNullOrBlank() &&
                    !looksLikeActivity(cls, pkg) &&
                    ShortVideoDetector.shouldClearActivityOnOverlay(pkg) &&
                    foregroundActivity != null
                ) {
                    // Bilibili/Xiaohongshu can open a full-screen video before
                    // a real Activity name is reported. Do not keep a stale
                    // MainActivity: clear it and let the structural detector
                    // decide from story_title/video evidence instead.
                    foregroundActivity = null
                    detection = null
                    positiveStreak = 0
                    pageRetryUntil = SystemClock.elapsedRealtime() + 8_000L
                    MonitorLog.add("页面类非 Activity，按结构识别兜底")
                }

                if (prefs.isMonitored(pkg)) {
                    captureAndEvaluate(pkg, foregroundActivity)
                } else {
                    detection = null
                    positiveStreak = 0
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                if (pkg == foregroundPackage && prefs.isMonitored(pkg)) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastSnapshotAt >= SNAPSHOT_INTERVAL_MS) {
                        captureAndEvaluate(pkg, foregroundActivity)
                    }
                }
            }
        }
    }

    private fun captureAndEvaluate(pkg: String, activity: String?) {
        lastSnapshotAt = SystemClock.elapsedRealtime()
        val root = runCatching { rootInActiveWindow }.getOrNull()
        if (root == null) {
            if (detection?.isShortVideo == true) {
                MonitorLog.add("页面读取失败：无障碍树为空")
            }
            detection = Detection(false, "暂时读不到页面内容", 0)
            positiveStreak = 0
            return
        }

        val rootPackage = root.packageName?.toString()
        if (!rootPackage.isNullOrBlank() && rootPackage != pkg) {
            if (detection?.isShortVideo == true) {
                MonitorLog.add("页面读取失败：根节点属于 $rootPackage")
            }
            detection = Detection(false, "前台页面已经切换", 0)
            positiveStreak = 0
            return
        }

        val page = SnapshotBuilder.capture(root, pkg, activity)
        lastPage = page
        val result = ShortVideoDetector.evaluate(page)
        val changed = detection?.isShortVideo != result.isShortVideo
        detection = result
        if (changed) {
            MonitorLog.add(
                "识别=${if (result.isShortVideo) "计时" else "暂停"}：${result.reason}"
            )
        }
        if (!result.isShortVideo) positiveStreak = 0
    }

    /**
     * Window-state events also fire for dialogs, bottom sheets and menu windows.
     * Their className is usually FrameLayout/View, not the Activity underneath.
     * Treating those as a page change used to pause the timer while a comment
     * sheet was open or while a feed was being swiped.
     */
    private fun looksLikeActivity(cls: String?, pkg: String): Boolean {
        if (cls.isNullOrBlank()) return false
        val simple = cls.substringAfterLast('.')
        if (simple.endsWith("Activity") ||
            simple.endsWith("UI") ||
            simple.endsWith("Ui")
        ) {
            return true
        }
        if (!cls.startsWith(pkg)) return false
        val viewWords = listOf("View", "Layout", "Dialog", "Popup", "Window", "Fragment", "Adapter")
        return viewWords.none { simple.contains(it, ignoreCase = true) }
    }

    private fun syncCountdown(totalSeconds: Long) {
        if (!prefs.notificationsEnabled || !ReminderNotifier.canPost(this)) {
            hideCountdown()
            return
        }
        val limitMinutes = prefs.dailyLimitMinutes.toLong()
        val remainingSeconds = (limitMinutes * 60L - totalSeconds).coerceAtLeast(0L)
        val remainingMinutes = (remainingSeconds + 59L) / 60L
        val key = "$remainingMinutes:${remainingSeconds == 0L}"
        if (key == countdownKey) return

        if (ReminderNotifier.showCountdown(this, totalSeconds, limitMinutes)) {
            countdownKey = key
        }
    }

    private fun isSystemOverlay(pkg: String, cls: String?): Boolean {
        if (pkg == "com.android.systemui") return true
        if (cls?.contains("SoftInputWindow", ignoreCase = true) == true) return true
        val lower = pkg.lowercase()
        return lower.contains("inputmethod") || lower.contains("input_oppo")
    }

    private fun hideCountdown() {
        if (countdownKey == null) return
        ReminderNotifier.cancelCountdown(this)
        countdownKey = null
        countdownInactiveSince = 0L
    }

    private fun graceSecondsLeft(now: Long, graceMs: Long): Long {
        if (graceStartedAt == 0L) return 0L
        val elapsed = (now - graceStartedAt).coerceAtLeast(0L)
        return ((graceMs - elapsed + 999L) / 1000L).coerceAtLeast(1L)
    }

    private fun resetGrace() {
        lastActivePackage = null
        graceStartedAt = 0L
        wasCounting = false
        overlayPackage = null
        overlaySince = 0L
    }

    private fun tick() {
        if (!connected) return
        val now = SystemClock.elapsedRealtime()
        val today = DayUtils.today()
        if (today != currentDate) {
            currentDate = today
            sessionSeconds = 0L
            positiveStreak = 0
            lastReminderAttempt = -60_000L
            PlanEngine.applyToday(prefs, usage)
            countdownKey = null
            resetGrace()
        }

        // A real shade/IME may stay on screen. Keep the current app's grace
        // alive for a short overlay, then promote the overlay to foreground so
        // the timer does not keep running while the feed is covered.
        if (overlaySince != 0L && now - overlaySince >= PAGE_GRACE_MS) {
            val overlay = overlayPackage
            overlayPackage = null
            overlaySince = 0L
            if (overlay != null) {
                foregroundPackage = overlay
                foregroundActivity = null
                detection = null
                positiveStreak = 0
                resetGrace()
                MonitorLog.add("系统覆盖窗口超过 ${PAGE_GRACE_MS / 1000} 秒，暂停计时")
            }
        }

        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        val interactive = power.isInteractive
        val locked = keyguard.isKeyguardLocked
        val pkg = foregroundPackage
        val monitored = prefs.isMonitored(pkg)

        // A cold page often emits its first window event before the
        // accessibility tree is readable. Retry for a few seconds instead of
        // waiting for the user to open a comment sheet.
        if (interactive && !locked && monitored &&
            now < pageRetryUntil &&
            detection?.isShortVideo != true &&
            now - lastSnapshotAt >= 1_000L
        ) {
            pkg?.let { captureAndEvaluate(it, foregroundActivity) }
        }

        val currentDetection = detection
        val eligible = interactive &&
            !locked &&
            monitored &&
            currentDetection?.isShortVideo == true

        if (!eligible) {
            positiveStreak = 0
        } else {
            positiveStreak++
        }
        val strictlyActive = eligible && positiveStreak >= REQUIRED_STREAK
        val sameApp = pkg != null && pkg == lastActivePackage

        if (!interactive || locked || pkg == null || !monitored || !sameApp) {
            resetGrace()
        }

        var active = strictlyActive
        var countingByGrace = false
        val commentPage = lastPage?.let(ShortVideoDetector::isCommentLike) == true
        val graceMs = if (commentPage) COMMENT_GRACE_MS else PAGE_GRACE_MS
        if (strictlyActive) {
            lastActivePackage = pkg
            wasCounting = true
            graceStartedAt = 0L
        } else if (wasCounting && sameApp && interactive && !locked && monitored) {
            if (graceStartedAt == 0L) graceStartedAt = now
            if (now - graceStartedAt < graceMs) {
                active = true
                countingByGrace = true
            } else {
                wasCounting = false
                graceStartedAt = 0L
            }
        }

        if (active) {
            usage.addSeconds(today, 1L)
            sessionSeconds++
        }

        val total = usage.todaySeconds()
        if (active) {
            countdownInactiveSince = 0L
            syncCountdown(total)
            maybeRemind(total, today, now)
        } else {
            // Keep the countdown briefly visible when the notification shade is
            // pulled down. Opening the shade makes SystemUI the foreground app;
            // cancelling on the same tick would make the countdown vanish before
            // the user can read it.
            if (countdownKey != null) {
                if (countdownInactiveSince == 0L) countdownInactiveSince = now
                if (now - countdownInactiveSince >= COUNTDOWN_LINGER_MS) {
                    hideCountdown()
                }
            } else {
                countdownInactiveSince = 0L
            }
        }

        val reason = when {
            !interactive -> "屏幕已关闭，计时暂停"
            locked -> "手机已锁屏，计时暂停"
            pkg == null -> "等待前台应用"
            !monitored -> "等待打开短视频 App"
            countingByGrace ->
                if (commentPage) {
                    "评论区宽限计时（剩余 ${graceSecondsLeft(now, graceMs)} 秒）"
                } else {
                    "页面切换宽限计时（剩余 ${graceSecondsLeft(now, graceMs)} 秒）"
                }
            currentDetection == null || !currentDetection.isShortVideo ->
                currentDetection?.reason ?: "正在观察页面"
            positiveStreak < REQUIRED_STREAK ->
                "正在确认短视频页面（$positiveStreak/$REQUIRED_STREAK）"
            else -> "识别到短视频页面"
        }

        if (active != lastLoggedActive) {
            if (active || lastLoggedActive) {
                MonitorLog.add(if (active) "开始计时" else "暂停计时：$reason")
            }
            lastLoggedActive = active
        }

        MonitorRuntime.update(
            MonitorRuntime.State(
                connected = true,
                active = active,
                packageName = pkg,
                activityName = foregroundActivity,
                appLabel = MonitoredApps.labelFor(pkg),
                reason = reason,
                detection = currentDetection?.let { "${it.reason} · ${it.confidence}%" }.orEmpty(),
                screenInteractive = interactive,
                locked = locked,
                sessionSeconds = sessionSeconds,
                positiveStreak = positiveStreak,
                updatedAtElapsed = now
            )
        )
    }

    private fun maybeRemind(totalSeconds: Long, today: java.time.LocalDate, now: Long) {
        if (!prefs.notificationsEnabled) return
        if (prefs.lastReminderDate == today.toString()) return
        if (now - lastReminderAttempt < REMINDER_RETRY_MS) return

        val limitMinutes = prefs.dailyLimitMinutes.toLong()
        if (totalSeconds < limitMinutes * 60L) return

        lastReminderAttempt = now
        val posted = ReminderNotifier.sendLimitReminder(
            context = this,
            prefs = prefs,
            usedSeconds = totalSeconds,
            limitMinutes = limitMinutes
        )
        if (posted) {
            prefs.lastReminderDate = today.toString()
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }

    override fun onInterrupt() {
        val wasConnected = connected
        MonitorLog.add("无障碍服务被系统中断")
        connected = false
        stopTicker()
        resetGrace()
        hideCountdown()
        ReminderNotifier.cancelCountdown(this)
        MonitorRuntime.update(
            MonitorRuntime.State(
                connected = false,
                active = false,
                reason = "无障碍服务被系统中断",
                updatedAtElapsed = SystemClock.elapsedRealtime()
            )
        )
        if (wasConnected) {
            ReminderNotifier.showServiceStopped(this)
        }
    }

    override fun onDestroy() {
        val wasConnected = connected
        MonitorLog.add("无障碍服务已销毁")
        connected = false
        stopTicker()
        resetGrace()
        hideCountdown()
        ReminderNotifier.cancelCountdown(this)
        unregisterScreenReceiver()
        MonitorRuntime.update(
            MonitorRuntime.State(
                connected = false,
                active = false,
                reason = "无障碍服务未连接",
                updatedAtElapsed = SystemClock.elapsedRealtime()
            )
        )
        if (wasConnected) {
            ReminderNotifier.showServiceStopped(this)
        }
        super.onDestroy()
    }

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    private var screenReceiverRegistered = false

    companion object {
        private const val TICK_MS = 1_000L
        private const val SNAPSHOT_INTERVAL_MS = 450L
        private const val REQUIRED_STREAK = 2
        private const val REMINDER_RETRY_MS = 60_000L
        private const val COMMENT_GRACE_MS = 30_000L
        private const val PAGE_GRACE_MS = 3_000L
        private const val COUNTDOWN_LINGER_MS = 3_000L
    }
}

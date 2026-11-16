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
    private var lastReminderAttempt = 0L
    private var connected = false
    private var ticker: Runnable? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                positiveStreak = 0
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        usage = UsageStore(this)
        currentDate = DayUtils.today()
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
        lastReminderAttempt = -60_000L
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
                if (pkg != foregroundPackage) {
                    foregroundPackage = pkg
                    foregroundActivity = cls
                    detection = null
                    positiveStreak = 0
                    sessionSeconds = 0L
                } else if (!cls.isNullOrBlank() && cls != foregroundActivity) {
                    foregroundActivity = cls
                    detection = null
                    positiveStreak = 0
                    sessionSeconds = 0L
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
            detection = Detection(false, "暂时读不到页面内容", 0)
            positiveStreak = 0
            return
        }

        val rootPackage = root.packageName?.toString()
        if (!rootPackage.isNullOrBlank() && rootPackage != pkg) {
            detection = Detection(false, "前台页面已经切换", 0)
            positiveStreak = 0
            return
        }

        val page = SnapshotBuilder.capture(root, pkg, activity)
        val result = ShortVideoDetector.evaluate(page)
        detection = result
        if (!result.isShortVideo) positiveStreak = 0
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
        }

        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        val interactive = power.isInteractive
        val locked = keyguard.isKeyguardLocked
        val pkg = foregroundPackage
        val monitored = prefs.isMonitored(pkg)
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
        val active = eligible && positiveStreak >= REQUIRED_STREAK

        if (active) {
            usage.addSeconds(today, 1L)
            sessionSeconds++
        }

        val total = usage.todaySeconds()
        if (active) {
            maybeRemind(total, today, now)
        }

        val reason = when {
            !interactive -> "屏幕已关闭，计时暂停"
            locked -> "手机已锁屏，计时暂停"
            pkg == null -> "等待前台应用"
            !monitored -> "等待打开短视频 App"
            currentDetection == null || !currentDetection.isShortVideo ->
                currentDetection?.reason ?: "正在观察页面"
            positiveStreak < REQUIRED_STREAK ->
                "正在确认短视频页面（$positiveStreak/$REQUIRED_STREAK）"
            else -> "识别到短视频页面"
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
        connected = false
        stopTicker()
        MonitorRuntime.update(
            MonitorRuntime.State(
                connected = false,
                active = false,
                reason = "无障碍服务被系统中断",
                updatedAtElapsed = SystemClock.elapsedRealtime()
            )
        )
    }

    override fun onDestroy() {
        connected = false
        stopTicker()
        unregisterScreenReceiver()
        MonitorRuntime.update(
            MonitorRuntime.State(
                connected = false,
                active = false,
                reason = "无障碍服务未连接",
                updatedAtElapsed = SystemClock.elapsedRealtime()
            )
        )
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
    }
}

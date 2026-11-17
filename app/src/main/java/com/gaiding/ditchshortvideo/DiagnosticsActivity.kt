package com.gaiding.ditchshortvideo

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.gaiding.ditchshortvideo.data.Prefs
import com.gaiding.ditchshortvideo.monitor.DetectorSelfTest
import com.gaiding.ditchshortvideo.monitor.MonitorRuntime
import com.gaiding.ditchshortvideo.notify.ReminderNotifier
import com.gaiding.ditchshortvideo.notify.XiaomiIsland
import com.gaiding.ditchshortvideo.system.SystemStatus
import com.gaiding.ditchshortvideo.ui.Ui
import java.util.Locale

class DiagnosticsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var statusPill: TextView
    private lateinit var runtimeText: TextView
    private lateinit var permissionContainer: LinearLayout
    private lateinit var selfTestResult: TextView
    private lateinit var islandInfo: TextView
    private var lastReport: DetectorSelfTest.Report? = null
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            refreshRuntime()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        refreshRuntime()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    private fun buildScreen(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Ui.colorOf(this@DiagnosticsActivity, R.color.background))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = Ui.column(this).apply {
            setPadding(Ui.dp(this@DiagnosticsActivity, 20), Ui.dp(this@DiagnosticsActivity, 12),
                Ui.dp(this@DiagnosticsActivity, 20), Ui.dp(this@DiagnosticsActivity, 36))
        }
        scroll.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(scroll, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        Ui.edgeToEdge(this, content)

        content.addView(buildHeader())
        content.addView(buildRuntimeCard(), topMargin(16))
        content.addView(buildPermissionCard(), topMargin(14))
        content.addView(buildSelfTestCard(), topMargin(14))
        content.addView(buildIslandCard(), topMargin(14))
        content.addView(buildDeviceCard(), topMargin(14))
        content.addView(
            Ui.text(this, "诊断信息只用于排查识别问题，不会自动上传。", 10f, Ui.colorOf(this, R.color.ink_faint))
                .apply { gravity = Gravity.CENTER_HORIZONTAL },
            topMargin(18)
        )
        return root
    }

    private fun buildHeader(): View {
        val row = Ui.row(this)
        val back = Ui.text(this, "←", 23f, Ui.colorOf(this, R.color.ink)).apply {
            gravity = Gravity.CENTER
            setPadding(Ui.dp(this@DiagnosticsActivity, 6), Ui.dp(this@DiagnosticsActivity, 2),
                Ui.dp(this@DiagnosticsActivity, 10), Ui.dp(this@DiagnosticsActivity, 4))
            contentDescription = "返回"
            setOnClickListener { finish() }
        }
        row.addView(back)
        row.addView(
            Ui.text(this, "诊断", 24f, Ui.colorOf(this, R.color.ink), bold = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        return row
    }

    private fun buildRuntimeCard(): View {
        val card = Ui.card(this)
        val top = Ui.row(this)
        val titleBox = Ui.column(this)
        titleBox.addView(Ui.text(this, "运行状态", 18f, Ui.colorOf(this, R.color.ink), bold = true))
        titleBox.addView(
            Ui.text(this, "每秒刷新", 11f, Ui.colorOf(this, R.color.ink_soft)),
            topMargin(3)
        )
        top.addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        statusPill = Ui.pill(this, "未连接", R.drawable.bg_soft_pill, Ui.colorOf(this, R.color.ink_soft))
        top.addView(statusPill)
        card.addView(top)

        runtimeText = Ui.text(this, "", 12f, Ui.colorOf(this, R.color.ink_soft))
        runtimeText.setLineSpacing(Ui.dp(this, 3).toFloat(), 1.2f)
        card.addView(runtimeText, topMargin(12))
        return card
    }

    private fun buildPermissionCard(): View {
        val card = Ui.card(this)
        card.addView(Ui.sectionHeader(this, "权限", "缺少任意一项都可能让计时失效"))
        permissionContainer = Ui.column(this)
        card.addView(permissionContainer, topMargin(6))
        return card
    }

    private fun buildSelfTestCard(): View {
        val card = Ui.card(this)
        card.addView(
            Ui.sectionHeader(
                this,
                "页面规则自检",
                "纯逻辑用例，不需要安装抖音等 App"
            )
        )
        val run = Ui.button(this, "运行 ${DetectorSelfTest.caseCount} 个用例", primary = true)
        run.setOnClickListener { runSelfTest() }
        card.addView(run, topMargin(12))
        selfTestResult = Ui.text(this, "还没运行。改版后如果识别不到，可以先跑一遍。", 11f, Ui.colorOf(this, R.color.ink_soft))
        selfTestResult.setLineSpacing(Ui.dp(this, 2).toFloat(), 1.25f)
        card.addView(selfTestResult, topMargin(10))
        return card
    }

    private fun buildIslandCard(): View {
        val card = Ui.card(this)
        card.addView(
            Ui.sectionHeader(
                this,
                "小米超级岛",
                "系统允许就上岛，否则普通通知兜底"
            )
        )
        islandInfo = Ui.text(this, XiaomiIsland.describe(this), 11f, Ui.colorOf(this, R.color.ink_soft))
        islandInfo.setLineSpacing(Ui.dp(this, 2).toFloat(), 1.2f)
        card.addView(islandInfo, topMargin(10))

        card.addView(Ui.divider(this), topMargin(10))

        val switchRow = Ui.row(this).apply {
            setPadding(0, Ui.dp(this@DiagnosticsActivity, 10), 0, 0)
        }
        val textBox = Ui.column(this)
        textBox.addView(
            Ui.text(this, "实验：附加小米焦点通知参数", 13f, Ui.colorOf(this, R.color.ink), bold = true)
        )
        textBox.addView(
            Ui.text(this, "侧载应用通常拿不到官方权限，系统可能整包忽略；普通通知仍会显示。", 10f, Ui.colorOf(this, R.color.ink_soft)),
            topMargin(3)
        )
        switchRow.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val toggle = Switch(this).apply {
            isChecked = prefs.focusParamExperimental
            contentDescription = "实验性小米焦点通知参数"
            styleSwitch(this)
            setOnCheckedChangeListener { _, checked ->
                prefs.focusParamExperimental = checked
                Ui.toast(
                    this@DiagnosticsActivity,
                    if (checked) "已开启，仅影响测试提醒" else "已关闭实验参数"
                )
            }
        }
        switchRow.addView(toggle)
        card.addView(switchRow)

        val test = Ui.button(this, "发送测试提醒", primary = false)
        test.setOnClickListener {
            if (!ReminderNotifier.sendTest(this, prefs)) {
                Ui.toast(this, "发不出去：请先允许通知")
                SystemStatus.openNotificationSettings(this)
            } else {
                Ui.toast(this, "测试提醒已发送")
            }
        }
        card.addView(test, topMargin(12))
        return card
    }

    private fun buildDeviceCard(): View {
        val card = Ui.card(this)
        card.addView(Ui.sectionHeader(this, "设备与版本", "复制给开发者，方便定位规则问题"))
        val info = Ui.text(
            this,
            "v${BuildConfig.VERSION_NAME}\n" +
                "${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）\n" +
                "包名 ${packageName}",
            11f,
            Ui.colorOf(this, R.color.ink_soft)
        )
        info.setLineSpacing(Ui.dp(this, 2).toFloat(), 1.2f)
        card.addView(info, topMargin(10))
        val copy = Ui.ghostButton(this, "复制诊断信息")
        copy.setOnClickListener { copyDiagnostics() }
        card.addView(copy, topMargin(6))
        return card
    }

    private fun refreshRuntime() {
        val state = MonitorRuntime.state
        val connected = state.connected
        val active = connected && state.active
        statusPill.text = when {
            !connected -> "未连接"
            active -> "计时中"
            else -> "已暂停"
        }
        statusPill.setTextColor(
            Ui.colorOf(
                this,
                when {
                    !connected -> R.color.danger
                    active -> R.color.accent_deep
                    else -> R.color.warning
                }
            )
        )
        statusPill.background = GradientDrawable().apply {
            cornerRadius = Ui.dp(this@DiagnosticsActivity, 99).toFloat()
            setColor(
                Ui.colorOf(
                    this@DiagnosticsActivity,
                    when {
                        !connected -> R.color.danger_soft
                        active -> R.color.accent_soft
                        else -> R.color.warning_soft
                    }
                )
            )
        }

        val updatedAgo = if (state.updatedAtElapsed == 0L) {
            "从未更新"
        } else {
            val seconds = ((SystemClock.elapsedRealtime() - state.updatedAtElapsed) / 1000L).coerceAtLeast(0L)
            "${seconds} 秒前"
        }
        val app = state.appLabel ?: "—"
        val pkg = state.packageName ?: "—"
        val activity = state.activityName ?: "—"
        runtimeText.text = buildString {
            appendLine("服务：${if (connected) "已连接" else "未连接"}")
            appendLine("计时：${if (active) "正在计时" else "已暂停"}")
            appendLine("前台：$app（$pkg）")
            appendLine("Activity：$activity")
            appendLine("原因：${state.reason}")
            appendLine("识别：${state.detection.ifBlank { "—" }}")
            appendLine("连续确认：${state.positiveStreak}/2")
            appendLine("本次计时：${state.sessionSeconds} 秒")
            appendLine("屏幕：${if (state.screenInteractive) "亮" else "灭"} · 锁屏：${if (state.locked) "是" else "否"}")
            append("更新：$updatedAgo")
        }
    }

    private fun refreshPermissions() {
        if (!::permissionContainer.isInitialized) return
        permissionContainer.removeAllViews()
        permissionRow("页面识别", SystemStatus.accessibilityEnabled(this)) {
            SystemStatus.openAccessibilitySettings(this)
        }
        permissionRow("通知", SystemStatus.notificationsAllowed(this)) {
            SystemStatus.openNotificationSettings(this)
        }
        permissionRow("后台运行", SystemStatus.batteryUnrestricted(this)) {
            SystemStatus.openBatterySettings(this)
        }
    }

    private fun permissionRow(title: String, ok: Boolean, action: () -> Unit) {
        val row = Ui.row(this).apply {
            setPadding(0, Ui.dp(this@DiagnosticsActivity, 8), 0, Ui.dp(this@DiagnosticsActivity, 8))
        }
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Ui.colorOf(this@DiagnosticsActivity, if (ok) R.color.accent else R.color.warning))
            }
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@DiagnosticsActivity, 7), Ui.dp(this@DiagnosticsActivity, 7))
        }
        row.addView(dot)
        val label = Ui.text(this, title, 13f, Ui.colorOf(this, R.color.ink), bold = true)
        val labelParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        labelParams.leftMargin = Ui.dp(this, 9)
        row.addView(label, labelParams)
        val actionText = Ui.text(
            this,
            if (ok) "已开启" else "去开启",
            12f,
            Ui.colorOf(this, if (ok) R.color.accent else R.color.accent_deep),
            bold = true
        ).apply {
            if (!ok) {
                isClickable = true
                setOnClickListener { action() }
            }
        }
        row.addView(actionText)
        permissionContainer.addView(row)
    }

    private fun runSelfTest() {
        val report = DetectorSelfTest.run()
        lastReport = report
        selfTestResult.text = if (report.ok) {
            "✓ 通过 ${report.passed}/${report.total}\n所有纯逻辑用例都符合预期。"
        } else {
            buildString {
                appendLine("通过 ${report.passed}/${report.total}，有 ${report.failures.size} 个不符合：")
                report.failures.forEach { failure ->
                    appendLine("✗ ${failure.name}")
                    appendLine("  预期 ${if (failure.expected) "计时" else "不计时"}，实际 ${if (failure.actual) "计时" else "不计时"} · ${failure.reason}")
                }
            }.trim()
        }
        selfTestResult.setTextColor(
            Ui.colorOf(this, if (report.ok) R.color.accent_deep else R.color.danger)
        )
    }

    private fun copyDiagnostics() {
        val state = MonitorRuntime.state
        val report = lastReport
        val text = buildString {
            appendLine("扔掉短视频 v${BuildConfig.VERSION_NAME} 诊断")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("无障碍：${SystemStatus.accessibilityEnabled(this@DiagnosticsActivity)}")
            appendLine("通知：${SystemStatus.notificationsAllowed(this@DiagnosticsActivity)}")
            appendLine("省电白名单：${SystemStatus.batteryUnrestricted(this@DiagnosticsActivity)}")
            appendLine("服务连接：${state.connected}")
            appendLine("计时中：${state.active}")
            appendLine("前台：${state.packageName ?: "—"}")
            appendLine("Activity：${state.activityName ?: "—"}")
            appendLine("原因：${state.reason}")
            appendLine("识别：${state.detection}")
            appendLine("连续确认：${state.positiveStreak}/2")
            if (report != null) {
                appendLine("规则自检：${report.passed}/${report.total}${if (report.ok) " 通过" else " 有失败"}")
                report.failures.forEach { appendLine("失败：${it.name} · ${it.reason}") }
            } else {
                appendLine("规则自检：未运行")
            }
            appendLine("超级岛：")
            append(XiaomiIsland.describe(this@DiagnosticsActivity))
        }
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("ditch-short-video diagnostics", text))
        Ui.toast(this, "诊断信息已复制")
    }

    private fun styleSwitch(sw: Switch) {
        sw.thumbTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Ui.colorOf(this, R.color.accent), Ui.colorOf(this, R.color.white))
        )
        sw.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Ui.colorOf(this, R.color.accent_soft), Ui.colorOf(this, R.color.divider))
        )
    }

    private fun topMargin(dp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = Ui.dp(this, dp) }
}

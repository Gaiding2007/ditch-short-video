package com.gaiding.ditchshortvideo

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.gaiding.ditchshortvideo.data.DayUtils
import com.gaiding.ditchshortvideo.data.MonitoredApps
import com.gaiding.ditchshortvideo.data.Prefs
import com.gaiding.ditchshortvideo.data.UsageStore
import com.gaiding.ditchshortvideo.monitor.MonitorRuntime
import com.gaiding.ditchshortvideo.system.SystemStatus
import com.gaiding.ditchshortvideo.ui.HeatmapView
import com.gaiding.ditchshortvideo.ui.ProgressRingView
import com.gaiding.ditchshortvideo.ui.Ui
import java.time.LocalDate
import kotlin.math.max

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var usage: UsageStore
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var ring: ProgressRingView
    private lateinit var limitValue: TextView
    private lateinit var timeSummary: TextView
    private lateinit var statusPill: TextView
    private lateinit var statusRow: LinearLayout
    private lateinit var statusDot: View
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var preparationContainer: LinearLayout
    private lateinit var appsContainer: LinearLayout
    private lateinit var heatmap: HeatmapView
    private lateinit var heatScroll: HorizontalScrollView
    private lateinit var heatSummary: TextView

    private var preparationSignature = ""
    private var lastHeatRefresh = 0L
    private var heatScrolled = false

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        usage = UsageStore(this)
        setContentView(buildScreen())
        window.decorView.post { maybeShowOnboarding() }
    }

    override fun onResume() {
        super.onResume()
        refresh(forcePreparation = true, forceHeatmap = true)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh(forcePreparation = true)
    }

    private fun buildScreen(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Ui.colorOf(this@MainActivity, R.color.background))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = Ui.column(this).apply {
            setPadding(Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 12),
                Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 36))
        }
        scroll.addView(
            content,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        root.addView(
            scroll,
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        Ui.edgeToEdge(this, content)

        content.addView(buildHeader())
        content.addView(buildHero(), topMargin(12))
        content.addView(buildStatusStrip(), topMargin(10))
        content.addView(buildPreparationCard(), topMargin(14))
        content.addView(buildAppsCard(), topMargin(14))
        content.addView(buildHeatmapCard(), topMargin(14))
        content.addView(buildFooter(), topMargin(22))
        return root
    }

    private fun buildHeader(): View {
        val row = Ui.row(this)
        val brand = Ui.column(this)
        brand.addView(Ui.text(this, "扔掉短视频", 25f, Ui.colorOf(this, R.color.ink), bold = true))
        val tagline = Ui.text(this, "把时间，拿回来", 12f, Ui.colorOf(this, R.color.ink_soft))
        brand.addView(tagline, topMargin(4))
        row.addView(
            brand,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val settings = Ui.pill(
            this,
            "设置",
            R.drawable.bg_soft_pill,
            Ui.colorOf(this, R.color.ink)
        ).apply {
            setPadding(Ui.dp(this@MainActivity, 14), Ui.dp(this@MainActivity, 8),
                Ui.dp(this@MainActivity, 14), Ui.dp(this@MainActivity, 8))
            contentDescription = "打开设置"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
        row.addView(settings)
        return row
    }

    private fun buildHero(): View {
        val card = Ui.card(this, 20)
        val top = Ui.row(this)
        val todayBox = Ui.column(this)
        todayBox.addView(Ui.text(this, "今天已刷", 12f, Ui.colorOf(this, R.color.ink_soft)))
        todayBox.addView(
            Ui.text(this, "短视频", 21f, Ui.colorOf(this, R.color.ink), bold = true),
            topMargin(2)
        )
        top.addView(
            todayBox,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        statusPill = Ui.pill(
            this,
            "已暂停",
            R.drawable.bg_soft_pill,
            Ui.colorOf(this, R.color.ink_soft)
        )
        top.addView(statusPill)
        card.addView(top)

        ring = ProgressRingView(this)
        card.addView(ring, topMargin(2))

        timeSummary = Ui.text(this, "", 13f, Ui.colorOf(this, R.color.ink_soft))
        timeSummary.gravity = Gravity.CENTER_HORIZONTAL
        card.addView(timeSummary, topMargin(0))

        card.addView(Ui.divider(this), topMargin(16))

        val limitRow = Ui.row(this)
        val limitBox = Ui.column(this)
        limitBox.addView(Ui.text(this, "每日上限", 12f, Ui.colorOf(this, R.color.ink_soft)))
        limitValue = Ui.text(this, "30 分钟", 17f, Ui.colorOf(this, R.color.ink), bold = true)
        limitBox.addView(limitValue, topMargin(3))
        limitRow.addView(
            limitBox,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val adjust = Ui.ghostButton(this, "调整额度")
        adjust.setOnClickListener { showLimitDialog() }
        limitRow.addView(adjust)
        card.addView(limitRow, topMargin(10))
        return card
    }

    private fun buildStatusStrip(): View {
        statusRow = Ui.row(this).apply {
            setPadding(Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 10),
                Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 10))
        }
        statusDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Ui.colorOf(this@MainActivity, R.color.ink_faint))
            }
        }
        statusRow.addView(statusDot, LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8)))
        val textBox = Ui.column(this)
        statusTitle = Ui.text(this, "正在读取状态", 13f, Ui.colorOf(this, R.color.ink), bold = true)
        statusDetail = Ui.text(this, "打开无障碍后，这里会显示计时状态", 11f, Ui.colorOf(this, R.color.ink_soft))
        textBox.addView(statusTitle)
        textBox.addView(statusDetail, topMargin(3))
        val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        params.leftMargin = Ui.dp(this, 10)
        statusRow.addView(textBox, params)
        return statusRow
    }

    private fun buildPreparationCard(): View {
        val card = Ui.card(this)
        card.addView(
            Ui.sectionHeader(
                this,
                "开始前，只差这几步",
                "三件事都完成后，才会开始计时"
            )
        )
        preparationContainer = Ui.column(this)
        card.addView(preparationContainer, topMargin(12))
        return card
    }

    private fun buildAppsCard(): View {
        val card = Ui.card(this)
        card.addView(
            Ui.sectionHeader(
                this,
                "监控哪些 App",
                "只统计识别到的短视频页面，其他页面不计时"
            )
        )
        appsContainer = Ui.column(this)
        card.addView(appsContainer, topMargin(4))

        val monitorApps = MonitoredApps.all
        monitorApps.forEachIndexed { index, app ->
            if (index > 0) cardAppsDivider(appsContainer)
            val row = Ui.row(this).apply {
                setPadding(0, Ui.dp(this@MainActivity, 9), 0, Ui.dp(this@MainActivity, 9))
            }
            row.addView(Ui.avatar(this, app.letter, app.fallbackColor))
            val textBox = Ui.column(this)
            textBox.addView(Ui.text(this, app.label, 15f, Ui.colorOf(this, R.color.ink), bold = true))
            textBox.addView(
                Ui.text(this, app.note, 11f, Ui.colorOf(this, R.color.ink_soft)),
                topMargin(2)
            )
            val textParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            textParams.leftMargin = Ui.dp(this, 12)
            row.addView(textBox, textParams)

            val toggle = Switch(this).apply {
                isChecked = prefs.monitoredPackages.contains(app.packageName)
                contentDescription = "监控${app.label}"
                thumbTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(Ui.colorOf(this@MainActivity, R.color.accent), Ui.colorOf(this@MainActivity, R.color.white))
                )
                trackTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(Ui.colorOf(this@MainActivity, R.color.accent_soft), Ui.colorOf(this@MainActivity, R.color.divider))
                )
                setOnCheckedChangeListener { button, checked ->
                    val next = prefs.monitoredPackages.toMutableSet()
                    if (checked) next.add(app.packageName) else next.remove(app.packageName)
                    if (next.isEmpty()) {
                        button.isChecked = true
                        Ui.toast(this@MainActivity, "至少保留一个 App，否则不会计时")
                    } else {
                        prefs.setMonitoredPackages(next)
                    }
                }
            }
            val switchParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            switchParams.leftMargin = Ui.dp(this, 8)
            row.addView(toggle, switchParams)
            appsContainer.addView(row)
        }
        return card
    }

    private fun cardAppsDivider(parent: LinearLayout) {
        val divider = View(this).apply {
            setBackgroundColor(Ui.colorOf(this@MainActivity, R.color.divider))
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            max(1, Ui.dp(this, 1))
        )
        params.leftMargin = Ui.dp(this, 52)
        parent.addView(divider, params)
    }

    private fun buildHeatmapCard(): View {
        val card = Ui.card(this)
        card.addView(
            Ui.sectionHeader(
                this,
                "今年刷了多少",
                "颜色越深，刷得越久。左右滑动看全年。"
            )
        )
        heatSummary = Ui.text(this, "", 12f, Ui.colorOf(this, R.color.ink_soft))
        card.addView(heatSummary, topMargin(8))

        heatScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        heatmap = HeatmapView(this)
        heatScroll.addView(
            heatmap,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        card.addView(heatScroll, topMargin(8))

        val legend = Ui.row(this)
        legend.gravity = Gravity.END
        legend.addView(Ui.text(this, "少", 10f, Ui.colorOf(this, R.color.ink_faint)))
        listOf(R.color.heat_0, R.color.heat_1, R.color.heat_2, R.color.heat_3, R.color.heat_4)
            .forEach { color ->
                val swatch = Ui.legendSwatch(this, Ui.colorOf(this, color))
                val params = LinearLayout.LayoutParams(swatch.layoutParams)
                params.leftMargin = Ui.dp(this, 4)
                legend.addView(swatch, params)
            }
        val more = Ui.text(this, "多", 10f, Ui.colorOf(this, R.color.ink_faint))
        val moreParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        moreParams.leftMargin = Ui.dp(this, 4)
        legend.addView(more, moreParams)
        card.addView(legend, topMargin(6))
        return card
    }

    private fun buildFooter(): View {
        val box = Ui.column(this)
        box.addView(
            Ui.text(this, "数据只保存在这台手机，没有账号，也不会上传。", 11f, Ui.colorOf(this, R.color.ink_faint))
                .apply { gravity = Gravity.CENTER_HORIZONTAL }
        )
        box.addView(
            Ui.text(this, "v0.0.1", 10f, Ui.colorOf(this, R.color.ink_faint))
                .apply { gravity = Gravity.CENTER_HORIZONTAL },
            topMargin(5)
        )
        return box
    }

    private fun refresh(forcePreparation: Boolean = false, forceHeatmap: Boolean = false) {
        refreshData()
        refreshStatus()
        refreshPreparation(forcePreparation)
        refreshHeatmap(forceHeatmap)
    }

    private fun refreshData() {
        val total = usage.todaySeconds()
        val limit = prefs.dailyLimitMinutes
        ring.setProgress(total, limit)
        limitValue.text = "$limit 分钟"
        val remaining = limit * 60L - total
        timeSummary.text = if (remaining <= 0L) {
            "今天已刷 ${DayUtils.humanDuration(total)} · ${DayUtils.overText(-remaining)}"
        } else {
            "今天已刷 ${DayUtils.humanDuration(total)} · ${DayUtils.remainingText(remaining)}"
        }
    }

    private fun refreshStatus() {
        val state = MonitorRuntime.state
        val connected = state.connected
        val active = connected && state.active
        val statusText: String
        val statusColor: Int
        val background: Int
        when {
            !connected -> {
                statusText = "未连接"
                statusColor = Ui.colorOf(this, R.color.danger)
                background = R.color.danger_soft
            }
            active -> {
                statusText = "计时中"
                statusColor = Ui.colorOf(this, R.color.accent_deep)
                background = R.color.accent_soft
            }
            else -> {
                statusText = "已暂停"
                statusColor = Ui.colorOf(this, R.color.warning)
                background = R.color.warning_soft
            }
        }
        statusPill.text = statusText
        statusPill.setTextColor(statusColor)
        statusPill.background = rounded(background, 99)
        statusRow.background = rounded(if (active) R.color.accent_softer else R.color.surface_soft, 14)
        (statusDot.background as? GradientDrawable)?.setColor(statusColor)

        statusTitle.text = when {
            !connected -> "页面识别未连接"
            active -> "正在计时"
            state.locked -> "锁屏了，计时暂停"
            !state.screenInteractive -> "熄屏了，计时暂停"
            else -> "计时已暂停"
        }
        statusDetail.text = state.reason
        statusRow.contentDescription = "${statusTitle.text}，${state.reason}"
    }

    private fun refreshPreparation(force: Boolean) {
        val accessibility = SystemStatus.accessibilityEnabled(this)
        val notifications = SystemStatus.notificationsAllowed(this)
        val battery = SystemStatus.batteryUnrestricted(this)
        val signature = "$accessibility|$notifications|$battery"
        if (!force && signature == preparationSignature) return
        preparationSignature = signature

        preparationContainer.removeAllViews()
        addPreparationStep(
            index = 1,
            title = "开启页面识别",
            description = "无障碍权限。只在本机读取页面结构，用来判断是不是短视频。",
            done = accessibility,
            actionLabel = "去开启",
            action = { SystemStatus.openAccessibilitySettings(this) }
        )
        addPreparationStep(
            index = 2,
            title = "允许通知",
            description = "到额度时提醒一次；不允许的话，提醒无法送达。",
            done = notifications,
            actionLabel = "去开启",
            action = { SystemStatus.openNotificationSettings(this) }
        )
        addPreparationStep(
            index = 3,
            title = "允许后台运行",
            description = "小米手机尤其需要，避免计时服务被系统清理。",
            done = battery,
            actionLabel = "去设置",
            action = { SystemStatus.openBatterySettings(this) }
        )
        if (accessibility && notifications && battery) {
            val done = Ui.text(
                this,
                "都准备好了。打开任意一个监控 App 的短视频页面试试。",
                11f,
                Ui.colorOf(this, R.color.accent_deep)
            )
            preparationContainer.addView(done, topMargin(10))
        }
    }

    private fun addPreparationStep(
        index: Int,
        title: String,
        description: String,
        done: Boolean,
        actionLabel: String,
        action: () -> Unit
    ) {
        val row = Ui.row(this).apply {
            setPadding(0, Ui.dp(this@MainActivity, 8), 0, Ui.dp(this@MainActivity, 8))
        }
        val badge = Ui.text(
            this,
            if (done) "✓" else index.toString(),
            11f,
            if (done) Ui.colorOf(this, R.color.accent_deep) else Ui.colorOf(this, R.color.ink_soft),
            bold = true
        ).apply {
            gravity = Gravity.CENTER
            background = rounded(if (done) R.color.accent_soft else R.color.surface_soft, 99)
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 26), Ui.dp(this@MainActivity, 26))
        }
        row.addView(badge)

        val textBox = Ui.column(this)
        textBox.addView(
            Ui.text(this, title, 14f, Ui.colorOf(this, R.color.ink), bold = true)
        )
        textBox.addView(
            Ui.text(this, description, 11f, Ui.colorOf(this, R.color.ink_soft)),
            topMargin(3)
        )
        val textParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        textParams.leftMargin = Ui.dp(this, 10)
        row.addView(textBox, textParams)

        val button = Ui.ghostButton(this, if (done) "已开启" else actionLabel).apply {
            setTextColor(Ui.colorOf(this@MainActivity, if (done) R.color.accent else R.color.accent_deep))
            isClickable = !done
            isFocusable = !done
            if (!done) setOnClickListener { action() }
        }
        row.addView(button)
        preparationContainer.addView(row)
    }

    private fun refreshHeatmap(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastHeatRefresh < 15_000L) return
        lastHeatRefresh = now

        val year = DayUtils.today().year
        val map = usage.recentMap(400).filterKeys { it.year == year }
        val total = map.values.sum()
        val activeDays = map.values.count { it > 0L }
        heatmap.submit(map, prefs.dailyLimitMinutes)
        heatSummary.text = if (total == 0L) {
            "今年还没有记录，从今天开始。"
        } else {
            "今年累计 ${DayUtils.humanDuration(total)} · $activeDays 天有记录"
        }
        if (!heatScrolled) {
            heatScrolled = true
            heatScroll.post {
                heatScroll.scrollTo(max(0, heatmap.scrollXForToday(heatScroll.width)), 0)
            }
        }
    }

    private fun showLimitDialog() {
        val box = Ui.column(this).apply {
            setPadding(Ui.dp(this@MainActivity, 22), Ui.dp(this@MainActivity, 4),
                Ui.dp(this@MainActivity, 22), 0)
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.dailyLimitMinutes.toString())
            setSelection(text.length)
            setSelectAllOnFocus(true)
            background = getDrawable(R.drawable.bg_input)
            setPadding(Ui.dp(this@MainActivity, 14), Ui.dp(this@MainActivity, 10),
                Ui.dp(this@MainActivity, 14), Ui.dp(this@MainActivity, 10))
            setTextColor(Ui.colorOf(this@MainActivity, R.color.ink))
            hint = "1 – 720"
            contentDescription = "每日最多观看分钟数"
        }
        box.addView(input, topMargin(8))

        val presets = Ui.row(this)
        listOf(15, 30, 45, 60, 90, 120).forEach { value ->
            val chip = Ui.text(this, value.toString(), 12f, Ui.colorOf(this, R.color.ink_soft), bold = true).apply {
                gravity = Gravity.CENTER
                background = getDrawable(R.drawable.bg_soft_pill)
                setPadding(Ui.dp(this@MainActivity, 9), Ui.dp(this@MainActivity, 6),
                    Ui.dp(this@MainActivity, 9), Ui.dp(this@MainActivity, 6))
                isClickable = true
                setOnClickListener {
                    input.setText(value.toString())
                    input.setSelection(input.text.length)
                }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (value != 15) params.leftMargin = Ui.dp(this, 5)
            presets.addView(chip, params)
        }
        box.addView(presets, topMargin(12))
        box.addView(
            Ui.text(this, "到点只提醒一次，不会强制关掉 App。", 11f, Ui.colorOf(this, R.color.ink_soft)),
            topMargin(12)
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("每天最多看多久")
            .setView(box)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().toIntOrNull()
                if (value == null || value !in Prefs.MIN_LIMIT..Prefs.MAX_LIMIT) {
                    input.error = "请输入 1 – 720 之间的整数"
                    return@setOnClickListener
                }
                prefs.dailyLimitMinutes = value
                refresh(forcePreparation = false, forceHeatmap = true)
                dialog.dismiss()
            }
        }
        dialog.setOnDismissListener { Ui.hideKeyboard(this, input) }
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    private fun maybeShowOnboarding() {
        if (prefs.onboardingSeen) return
        AlertDialog.Builder(this)
            .setTitle("先花 10 秒了解")
            .setMessage(
                "只有开启页面识别、允许通知、允许后台运行，计时才会可靠。\n\n" +
                    "• 只有识别到短视频页面才计时；聊天、搜索、个人页都不算。\n" +
                    "• 离开页面、切 App、锁屏或熄屏，会立刻暂停。\n" +
                    "• 到额度只提醒一次，不会锁机或关掉 App。\n\n" +
                    "所有数据只留在本机，这个 App 没有联网权限。"
            )
            .setPositiveButton("开始设置") { _, _ ->
                prefs.onboardingSeen = true
                requestNotificationPermissionIfNeeded()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && !SystemStatus.notificationsAllowed(this)) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun topMargin(dp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = Ui.dp(this, dp) }

    private fun rounded(colorRes: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = Ui.dp(this@MainActivity, radiusDp).toFloat()
            setColor(Ui.colorOf(this@MainActivity, colorRes))
        }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 100
    }
}

package com.gaiding.ditchshortvideo

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.gaiding.ditchshortvideo.data.BackupManager
import com.gaiding.ditchshortvideo.data.DayUtils
import com.gaiding.ditchshortvideo.data.PlanEngine
import com.gaiding.ditchshortvideo.data.Prefs
import com.gaiding.ditchshortvideo.data.UsageStore
import com.gaiding.ditchshortvideo.share.ChartShare
import com.gaiding.ditchshortvideo.ui.DailyCurveView
import com.gaiding.ditchshortvideo.ui.DailyPoint
import com.gaiding.ditchshortvideo.ui.HeatmapView
import com.gaiding.ditchshortvideo.ui.Ui
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

class DataActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var usage: UsageStore

    private lateinit var curve: DailyCurveView
    private lateinit var curveScroll: HorizontalScrollView
    private lateinit var curveSummary: TextView
    private lateinit var planBody: LinearLayout
    private lateinit var dailyList: LinearLayout
    private lateinit var heatmap: HeatmapView
    private lateinit var heatScroll: HorizontalScrollView
    private lateinit var heatSummary: TextView
    private lateinit var weekSummary: TextView

    private var selectedGoal = 30
    private var chartPreview: ChartShare.RenderedChart? = null
    private var curveScrolled = false
    private var heatScrolled = false
    private var pendingImport: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        usage = UsageStore(this)
        selectedGoal = prefs.planGoalMinutes
        setContentView(buildScreen())
        window.decorView.post { PlanEngine.applyToday(prefs, usage); refreshAll() }
    }

    override fun onResume() {
        super.onResume()
        PlanEngine.applyToday(prefs, usage)
        selectedGoal = prefs.planGoalMinutes
        refreshAll()
    }

    private fun buildScreen(): View {
        val root = android.widget.FrameLayout(this).apply {
            setBackgroundColor(Ui.colorOf(this@DataActivity, R.color.background))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = Ui.column(this).apply {
            setPadding(
                Ui.dp(this@DataActivity, 20),
                Ui.dp(this@DataActivity, 12),
                Ui.dp(this@DataActivity, 20),
                Ui.dp(this@DataActivity, 36)
            )
        }
        scroll.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(scroll, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        Ui.edgeToEdge(this, content)

        content.addView(buildHeader())
        content.addView(buildWeekCard(), topMargin(14))
        content.addView(buildCurveCard(), topMargin(14))
        content.addView(buildPlanCard(), topMargin(14))
        content.addView(buildBackupCard(), topMargin(14))
        content.addView(buildDailyListCard(), topMargin(14))
        content.addView(buildHeatmapCard(), topMargin(14))
        content.addView(buildFooter(), topMargin(22))
        return root
    }

    private fun buildHeader(): View {
        val row = Ui.row(this)
        val back = Ui.text(this, "←", 24f, Ui.colorOf(this, R.color.ink), bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(Ui.dp(this@DataActivity, 6), Ui.dp(this@DataActivity, 2),
                Ui.dp(this@DataActivity, 14), Ui.dp(this@DataActivity, 2))
            contentDescription = "返回"
            setOnClickListener { finish() }
        }
        row.addView(back)
        val box = Ui.column(this)
        box.addView(Ui.text(this, "数据", 25f, Ui.colorOf(this, R.color.ink), bold = true))
        box.addView(
            Ui.text(this, "每天看了多久，一眼看清", 12f, Ui.colorOf(this, R.color.ink_soft)),
            topMargin(4)
        )
        row.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val share = Ui.pill(
            this,
            "分享图",
            R.drawable.bg_accent_pill,
            Ui.colorOf(this, R.color.accent_deep)
        ).apply {
            contentDescription = "分享每日曲线图片"
            setOnClickListener { showSharePreview() }
        }
        row.addView(share)
        return row
    }

    private fun showSharePreview() {
        val chart = ChartShare.create(this, prefs, usage) ?: return
        chartPreview?.bitmap?.recycle()
        chartPreview = chart

        val image = ImageView(this).apply {
            setImageBitmap(chart.bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Ui.colorOf(this@DataActivity, R.color.surface))
            setPadding(
                Ui.dp(this@DataActivity, 12), Ui.dp(this@DataActivity, 12),
                Ui.dp(this@DataActivity, 12), Ui.dp(this@DataActivity, 12)
            )
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("分享图预览")
            .setView(image)
            .setNegativeButton("取消", null)
            .setPositiveButton("分享", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                ChartShare.share(this, chart.uri)
                dialog.dismiss()
            }
        }
        dialog.setOnDismissListener {
            chart.bitmap.recycle()
            if (chartPreview === chart) chartPreview = null
        }
        dialog.show()
    }

    private fun buildWeekCard(): View {
        val card = Ui.card(this)
        card.addView(Ui.text(this, "最近 7 天", 12f, Ui.colorOf(this, R.color.ink_soft)))
        weekSummary = Ui.text(this, "还没有记录", 25f, Ui.colorOf(this, R.color.ink), bold = true)
        card.addView(weekSummary, topMargin(4))
        return card
    }

    private fun buildCurveCard(): View {
        val card = Ui.card(this)
        card.addView(
            Ui.sectionHeader(
                this,
                "每日曲线",
                "过去 30 天，标出每天的具体时长；轻触曲线上的点查看日期与时长，启用计划后会多一条未来曲线。"
            )
        )
        curveSummary = Ui.text(this, "", 12f, Ui.colorOf(this, R.color.ink_soft))
        card.addView(curveSummary, topMargin(8))

        curveScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        curve = DailyCurveView(this)
        curveScroll.addView(
            curve,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        card.addView(curveScroll, topMargin(4))

        val legend = Ui.row(this)
        legend.gravity = Gravity.END
        legend.addView(legendLine("实际", R.color.accent))
        val gap = View(this)
        legend.addView(gap, LinearLayout.LayoutParams(Ui.dp(this, 14), 1))
        legend.addView(legendLine("计划", R.color.warning))
        card.addView(legend, topMargin(2))
        return card
    }

    private fun legendLine(label: String, colorRes: Int): View {
        val row = Ui.row(this)
        val swatch = Ui.legendSwatch(this, Ui.colorOf(this, colorRes))
        row.addView(swatch)
        val text = Ui.text(this, label, 10f, Ui.colorOf(this, R.color.ink_faint))
        text.setPadding(Ui.dp(this, 4), 0, 0, 0)
        row.addView(text)
        return row
    }

    private fun buildPlanCard(): View {
        val card = Ui.card(this)
        card.addView(
            Ui.sectionHeader(
                this,
                "控制计划",
                "先记录 3 天以上；计划会从最近的使用水平慢慢滑向你的目标，并带一点正常波动。"
            )
        )
        planBody = Ui.column(this)
        card.addView(planBody, topMargin(10))
        return card
    }

    private fun buildBackupCard(): View {
        val card = Ui.card(this)
        card.addView(
            Ui.sectionHeader(
                this,
                "备份与导出",
                "导出一个 JSON 文件，包含设置和每日记录；导入时会覆盖当前数据。"
            )
        )
        val actions = Ui.row(this)
        val export = Ui.button(this, "导出备份", primary = false).apply {
            setOnClickListener { pickExport() }
        }
        actions.addView(export, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val import = Ui.button(this, "导入备份", primary = false).apply {
            setOnClickListener { pickImport() }
        }
        val importParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        importParams.leftMargin = Ui.dp(this, 8)
        actions.addView(import, importParams)
        card.addView(actions, topMargin(12))
        card.addView(
            Ui.text(this, "文件由你选择保存位置，不会上传；建议同时保留一份到电脑。", 10f, Ui.colorOf(this, R.color.ink_faint)),
            topMargin(8)
        )
        return card
    }

    private fun pickExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, BackupManager.suggestedName())
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    private fun pickImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/json", "text/plain", "application/octet-stream")
            )
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_EXPORT -> {
                val ok = BackupManager.export(this, prefs, usage, uri)
                Ui.toast(this, if (ok) "备份已导出" else "导出失败，请换个位置再试")
            }
            REQUEST_IMPORT -> {
                pendingImport = uri
                AlertDialog.Builder(this)
                    .setTitle("导入备份？")
                    .setMessage("会覆盖当前设置和所有每日记录。导入后可在数据页检查。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("导入") { _, _ ->
                        val source = pendingImport
                        pendingImport = null
                        if (source == null) return@setPositiveButton
                        try {
                            val result = BackupManager.import(this, prefs, usage, source)
                            selectedGoal = prefs.planGoalMinutes
                            PlanEngine.applyToday(prefs, usage)
                            refreshAll()
                            Ui.toast(this, "已导入 ${result.days} 天记录")
                        } catch (e: Exception) {
                            Ui.toast(this, e.message ?: "导入失败")
                        }
                    }
                    .show()
            }
        }
    }

    private fun buildDailyListCard(): View {
        val card = Ui.card(this)
        card.addView(
            Ui.sectionHeader(
                this,
                "每日明细",
                "最近 30 天，具体到分钟。"
            )
        )
        dailyList = Ui.column(this)
        card.addView(dailyList, topMargin(6))
        return card
    }

    private fun buildHeatmapCard(): View {
        val card = Ui.card(this)
        card.addView(
            Ui.sectionHeader(
                this,
                "年度热力图",
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
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = Ui.dp(this, 4)
        legend.addView(more, params)
        card.addView(legend, topMargin(6))
        return card
    }

    private fun buildFooter(): View {
        val box = Ui.column(this)
        box.addView(
            Ui.text(this, "所有记录只在这台手机，没有账号，也不会上传。", 11f, Ui.colorOf(this, R.color.ink_faint))
                .apply { gravity = Gravity.CENTER_HORIZONTAL }
        )
        box.addView(
            Ui.text(this, "v${BuildConfig.VERSION_NAME}", 10f, Ui.colorOf(this, R.color.ink_faint))
                .apply { gravity = Gravity.CENTER_HORIZONTAL },
            topMargin(5)
        )
        return box
    }

    private fun refreshAll() {
        refreshSummary()
        refreshCurve()
        renderPlanBody()
        refreshDailyList()
        refreshHeatmap()
    }

    private fun refreshSummary() {
        val week = usage.recentDays(7)
        val total = week.sumOf { it.seconds }
        val days = week.count { it.seconds > 0L }
        val average = if (days == 0) 0L else total / days
        weekSummary.text = if (total == 0L) {
            "还没有记录，今天开始算"
        } else {
            "${DayUtils.humanDuration(total)} · $days 天有记录 · 日均 ${DayUtils.humanDuration(average)}"
        }
        weekSummary.textSize = 20f
        weekSummary.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private fun refreshCurve() {
        val today = DayUtils.today()
        val state = PlanEngine.state(prefs, usage)
        val start = state.startDate
        val past = usage.recentDays(30).map { day ->
            val planned = if (state.enabled && start != null && !day.date.isBefore(start)) {
                PlanEngine.limitFor(day.date, state.goalMinutes, state.baselineMinutes, start)
            } else {
                null
            }
            DailyPoint(day.date, day.seconds, planned)
        }.toMutableList()

        if (state.enabled && start != null) {
            for (offset in 1..PlanEngine.HORIZON_DAYS) {
                val date = today.plusDays(offset.toLong())
                past.add(
                    DailyPoint(
                        date,
                        null,
                        PlanEngine.limitFor(date, state.goalMinutes, state.baselineMinutes, start)
                    )
                )
            }
        }
        curve.submit(past)
        curveSummary.text = if (state.enabled) {
            "目标 ${state.goalMinutes} 分钟 · 计划第 ${state.dayIndex + 1}/${PlanEngine.HORIZON_DAYS} 天 · 今天自动额度 ${state.todayLimit} 分钟"
        } else {
            "还没有启用计划；先按自己的额度记录，攒够 3 天再生成。"
        }
        if (!curveScrolled) {
            curveScrolled = true
            curveScroll.post {
                curveScroll.scrollTo(max(0, curve.scrollXForToday(curveScroll.width)), 0)
            }
        }
    }

    private fun renderPlanBody() {
        planBody.removeAllViews()
        val state = PlanEngine.state(prefs, usage)
        if (selectedGoal !in PlanEngine.goalOptions) selectedGoal = 30

        val goalLabel = Ui.text(this, "希望控制到", 12f, Ui.colorOf(this, R.color.ink_soft))
        planBody.addView(goalLabel)
        val goals = Ui.row(this)
        goals.gravity = Gravity.START
        PlanEngine.goalOptions.forEachIndexed { index, value ->
            val selected = value == selectedGoal
            val chip = Ui.text(
                this,
                "$value 分",
                10f,
                Ui.colorOf(this, if (selected) R.color.accent_deep else R.color.ink_soft),
                bold = true
            ).apply {
                gravity = Gravity.CENTER
                setBackgroundResource(if (selected) R.drawable.bg_accent_pill else R.drawable.bg_soft_pill)
                setPadding(Ui.dp(this@DataActivity, 4), Ui.dp(this@DataActivity, 7),
                    Ui.dp(this@DataActivity, 4), Ui.dp(this@DataActivity, 7))
                isClickable = true
                setOnClickListener {
                    selectedGoal = value
                    renderPlanBody()
                }
            }
            val params = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            if (index > 0) params.leftMargin = Ui.dp(this, 3)
            goals.addView(chip, params)
        }
        planBody.addView(goals, topMargin(8))

        val dataDays = state.dataDays
        val hint = Ui.text(
            this,
            when {
                state.enabled -> "今天额度 ${state.todayLimit} 分钟。计划只调整额度，不锁机；手动改额度会关闭计划。"
                dataDays < PlanEngine.MIN_DATA_DAYS ->
                    "已记录 $dataDays 天，满 ${PlanEngine.MIN_DATA_DAYS} 天后可以生成。先照常使用即可。"
                else -> "已有 $dataDays 天记录，可以生成计划了。前几天先按现在的水平，之后慢慢下降。"
            },
            11f,
            Ui.colorOf(this, if (state.enabled) R.color.accent_deep else R.color.ink_soft)
        )
        planBody.addView(hint, topMargin(8))

        if (state.enabled) {
            val line = Ui.row(this)
            val stats = Ui.text(
                this,
                "起点 ${state.baselineMinutes} 分钟 · 目标 ${state.goalMinutes} 分钟\n第 ${state.dayIndex + 1} 天 / ${PlanEngine.HORIZON_DAYS} 天",
                12f,
                Ui.colorOf(this, R.color.ink)
            )
            line.addView(stats, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            planBody.addView(line, topMargin(10))

            val actions = Ui.row(this)
            val update = Ui.ghostButton(this, "按新目标更新")
            update.setOnClickListener { generatePlan(force = true) }
            actions.addView(update)
            val off = Ui.ghostButton(this, "关闭自动计划").apply {
                setTextColor(Ui.colorOf(this@DataActivity, R.color.danger))
                setOnClickListener {
                    prefs.clearPlan()
                    Ui.toast(this@DataActivity, "已关闭自动计划，当前额度保留")
                    refreshAll()
                }
            }
            val offParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            offParams.leftMargin = Ui.dp(this, 6)
            actions.addView(off, offParams)
            planBody.addView(actions, topMargin(6))
        } else {
            val generate = Ui.button(this, "生成计划并启用", primary = true).apply {
                isEnabled = dataDays >= PlanEngine.MIN_DATA_DAYS
                alpha = if (isEnabled) 1f else 0.45f
                setOnClickListener { generatePlan(force = false) }
            }
            planBody.addView(generate, topMargin(12))
        }
    }

    private fun generatePlan(force: Boolean) {
        val ok = PlanEngine.enable(prefs, usage, selectedGoal)
        if (!ok) {
            Ui.toast(this, "再记录 ${PlanEngine.MIN_DATA_DAYS - PlanEngine.recordedDays(usage)} 天就可以生成计划")
            renderPlanBody()
            return
        }
        Ui.toast(
            this,
            if (force) "计划已更新，今天额度 ${prefs.dailyLimitMinutes} 分钟"
            else "计划已启用，今天额度 ${prefs.dailyLimitMinutes} 分钟"
        )
        refreshAll()
    }

    private fun refreshDailyList() {
        dailyList.removeAllViews()
        val today = DayUtils.today()
        val days = usage.recentDays(30).reversed()
        val state = PlanEngine.state(prefs, usage)
        val start = state.startDate
        var added = 0
        days.forEach { day ->
            val planned = if (state.enabled && start != null && !day.date.isBefore(start)) {
                PlanEngine.limitFor(day.date, state.goalMinutes, state.baselineMinutes, start)
            } else {
                null
            }
            if (day.seconds <= 0L && planned == null) return@forEach
            if (added > 0) dailyList.addView(Ui.divider(this), topMargin(3))
            added++

            val row = Ui.row(this).apply {
                setPadding(0, Ui.dp(this@DataActivity, 8), 0, Ui.dp(this@DataActivity, 8))
            }
            val dateText = Ui.text(
                this,
                if (day.date == today) "今天" else formattedDay(day.date),
                12f,
                Ui.colorOf(this, R.color.ink),
                bold = day.date == today
            )
            row.addView(dateText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val right = Ui.column(this)
            right.gravity = Gravity.END
            right.addView(
                Ui.text(
                    this,
                    DayUtils.humanDuration(day.seconds),
                    13f,
                    Ui.colorOf(this, if (day.seconds > 0) R.color.ink else R.color.ink_faint),
                    bold = true
                )
            )
            if (planned != null) {
                right.addView(
                    Ui.text(this, "计划 $planned 分钟", 10f, Ui.colorOf(this, R.color.warning)),
                    topMargin(2)
                )
            }
            row.addView(right)
            dailyList.addView(row)
        }
        if (added == 0) {
            dailyList.addView(
                Ui.text(this, "还没有记录。打开一个监控 App 的短视频页面试试。", 11f, Ui.colorOf(this, R.color.ink_soft)),
                topMargin(8)
            )
        }
    }

    private fun refreshHeatmap() {
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

    private fun formattedDay(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA))

    private fun topMargin(dp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = Ui.dp(this, dp) }

    private companion object {
        const val REQUEST_EXPORT = 1001
        const val REQUEST_IMPORT = 1002
    }
}

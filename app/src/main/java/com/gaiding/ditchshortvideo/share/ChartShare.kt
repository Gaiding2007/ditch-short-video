package com.gaiding.ditchshortvideo.share

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.gaiding.ditchshortvideo.BuildConfig
import com.gaiding.ditchshortvideo.data.DayUtils
import com.gaiding.ditchshortvideo.data.PlanEngine
import com.gaiding.ditchshortvideo.data.Prefs
import com.gaiding.ditchshortvideo.data.UsageStore
import com.gaiding.ditchshortvideo.ui.DailyCurveView
import com.gaiding.ditchshortvideo.ui.DailyPoint
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Renders the daily curve to a PNG and hands it to the system share sheet. */
object ChartShare {
    private const val WIDTH = 1440
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

    /** The bitmap is kept for the preview dialog; the file behind [uri] is what gets shared. */
    data class RenderedChart(val bitmap: Bitmap, val uri: Uri)

    fun create(activity: Activity, prefs: Prefs, usage: UsageStore): RenderedChart? {
        return try {
            render(activity, prefs, usage)
        } catch (_: Exception) {
            android.widget.Toast.makeText(activity, "生成分享图失败", android.widget.Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun share(activity: Activity, uri: Uri) {
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "我的短视频记录 · 扔掉短视频")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(send, "分享每日曲线"))
        } catch (_: Exception) {
            android.widget.Toast.makeText(activity, "没有可用的分享应用", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun render(activity: Activity, prefs: Prefs, usage: UsageStore): RenderedChart {
        val points = curvePoints(prefs, usage)
        val density = activity.resources.displayMetrics.density
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(30f, density), dp(28f, density), dp(30f, density), dp(24f, density))
        }

        root.addView(text(activity, "我的短视频记录", 27f, Color.rgb(29, 33, 31), true))
        root.addView(
            text(
                activity,
                "最近 30 天 · 每天具体看了多久",
                13f,
                Color.rgb(112, 118, 114),
                false
            ),
            marginTop(dp(6f, density))
        )
        root.addView(
            text(
                activity,
                "导出日期 ${DayUtils.today().format(dateFormat)}",
                11f,
                Color.rgb(150, 155, 151),
                false
            ),
            marginTop(dp(3f, density))
        )

        val chart = DailyCurveView(activity).apply {
            setFitToWidth(true)
            submit(points)
        }
        root.addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.topMargin = dp(18f, density)
        })

        val legend = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
        }
        legend.addView(text(activity, "● 实际", 11f, Color.rgb(29, 154, 97), true))
        legend.addView(
            text(activity, "● 计划", 11f, Color.rgb(184, 125, 42), true),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.leftMargin = dp(14f, density) }
        )
        root.addView(legend, marginTop(dp(2f, density)))

        root.addView(
            text(
                activity,
                "扔掉短视频 · v${BuildConfig.VERSION_NAME} · 数据只在这台手机",
                10f,
                Color.rgb(150, 155, 151),
                false
            ),
            marginTop(dp(8f, density))
        )

        val widthSpec = View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        root.measure(widthSpec, heightSpec)
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        val bitmap = Bitmap.createBitmap(root.measuredWidth, root.measuredHeight, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File(activity.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, ShareProvider.CHART_NAME)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return RenderedChart(bitmap, ShareProvider.uri(activity))
    }

    private fun curvePoints(prefs: Prefs, usage: UsageStore): List<DailyPoint> {
        val today = DayUtils.today()
        val state = PlanEngine.state(prefs, usage)
        val start = state.startDate
        val points = usage.recentDays(30).map { day ->
            val planned = if (state.enabled && start != null && !day.date.isBefore(start)) {
                PlanEngine.limitFor(day.date, state.goalMinutes, state.baselineMinutes, start)
            } else {
                null
            }
            DailyPoint(day.date, day.seconds, planned)
        }.toMutableList()

        if (state.enabled && start != null) {
            for (offset in 1..PlanEngine.HORIZON_DAYS) {
                val date: LocalDate = today.plusDays(offset.toLong())
                points.add(
                    DailyPoint(
                        date,
                        null,
                        PlanEngine.limitFor(
                            date,
                            state.goalMinutes,
                            state.baselineMinutes,
                            start
                        )
                    )
                )
            }
        }
        return points
    }

    private fun text(
        activity: Activity,
        value: String,
        size: Float,
        color: Int,
        bold: Boolean
    ): TextView = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
        typeface = Typeface.create(
            "sans-serif",
            if (bold) Typeface.BOLD else Typeface.NORMAL
        )
    }

    private fun marginTop(px: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = px }

    private fun dp(value: Float, density: Float): Int = (value * density).toInt()
}

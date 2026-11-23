package com.gaiding.ditchshortvideo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.gaiding.ditchshortvideo.data.DayUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

data class DailyPoint(
    val date: LocalDate,
    /** null marks a future date with no actual usage yet. */
    val seconds: Long?,
    val plannedMinutes: Int?
)

/**
 * Plain Canvas chart: actual minutes (solid green) and the auto-plan limit
 * (dashed amber). It is intentionally lightweight and works on API 26.
 */
class DailyCurveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var points: List<DailyPoint> = emptyList()

    private val density = resources.displayMetrics.density
    private val leftPad = dp(42f)
    private val rightPad = dp(14f)
    private val topPad = dp(20f)
    private val bottomPad = dp(32f)
    private val columnWidth = dp(25f)
    private val chartHeight = dp(224f)

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1F1E2A24
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
    }
    private val todayLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x331D9A61
        strokeWidth = dp(1.2f)
        style = Paint.Style.STROKE
    }
    private val actualPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1D9A61.toInt()
        strokeWidth = dp(2.4f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val planPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB5761F.toInt()
        strokeWidth = dp(1.8f)
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(4f)), 0f)
        strokeCap = Paint.Cap.ROUND
    }
    private val actualDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1D9A61.toInt()
        style = Paint.Style.FILL
    }
    private val planDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB5761F.toInt()
        style = Paint.Style.FILL
    }
    private val axisTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF78847D.toInt()
        textSize = dp(9.5f)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF78847D.toInt()
        textSize = dp(12f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val xFormatter = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)

    fun submit(points: List<DailyPoint>) {
        this.points = points
        contentDescription = if (points.isEmpty()) {
            "每日时长曲线，暂无记录"
        } else {
            "最近 ${points.size} 天的每日时长曲线，共 ${points.count { (it.seconds ?: 0L) > 0L }} 天有记录"
        }
        requestLayout()
        invalidate()
    }

    fun scrollXForToday(viewportWidth: Int): Int {
        val today = DayUtils.today()
        val index = points.indexOfFirst { it.date == today }
        if (index < 0) return 0
        val center = leftPad + index * columnWidth + columnWidth / 2f
        return max(0, (center - viewportWidth / 3f).toInt())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = leftPad + rightPad + max(1, points.size) * columnWidth
        val width = resolveSize(desiredWidth.toInt(), widthMeasureSpec)
        val height = resolveSize(chartHeight.toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val chartBottom = height - bottomPad
        if (points.isEmpty() || chartBottom <= topPad) {
            canvas.drawText("还没有足够的记录", width / 2f, height / 2f, emptyPaint)
            return
        }

        val maxValue = points.maxOf { point ->
            max(
                (point.seconds ?: 0L) / 60f,
                (point.plannedMinutes ?: 0).toFloat()
            )
        }
        val step = niceStep(maxValue)
        val yMax = max(step, ceil(maxValue / step) * step)
        val chartHeightPx = chartBottom - topPad
        fun yFor(value: Float): Float = chartBottom - (value / yMax) * chartHeightPx
        fun xFor(index: Int): Float = leftPad + index * columnWidth + columnWidth / 2f

        for (i in 0..4) {
            val value = yMax * i / 4f
            val y = yFor(value)
            canvas.drawLine(leftPad, y, width - rightPad, y, gridPaint)
            canvas.drawText(
                if (value >= 10f) value.toInt().toString() else String.format(Locale.CHINA, "%.0f", value),
                dp(4f),
                y + dp(3f),
                axisTextPaint
            )
        }

        val todayIndex = points.indexOfFirst { it.date == DayUtils.today() }
        if (todayIndex >= 0) {
            val x = xFor(todayIndex)
            canvas.drawLine(x, topPad, x, chartBottom, todayLinePaint)
        }

        val planPath = Path()
        var planStarted = false
        points.forEachIndexed { index, point ->
            val planned = point.plannedMinutes ?: return@forEachIndexed
            val x = xFor(index)
            val y = yFor(planned.toFloat())
            if (!planStarted) {
                planPath.moveTo(x, y)
                planStarted = true
            } else {
                planPath.lineTo(x, y)
            }
        }
        if (planStarted) canvas.drawPath(planPath, planPaint)

        val actualPath = Path()
        var actualStarted = false
        points.forEachIndexed { index, point ->
            val seconds = point.seconds ?: return@forEachIndexed
            val x = xFor(index)
            val y = yFor(seconds / 60f)
            if (!actualStarted) {
                actualPath.moveTo(x, y)
                actualStarted = true
            } else {
                actualPath.lineTo(x, y)
            }
        }
        if (actualStarted) canvas.drawPath(actualPath, actualPaint)

        points.forEachIndexed { index, point ->
            val x = xFor(index)
            point.plannedMinutes?.let { planned ->
                canvas.drawCircle(x, yFor(planned.toFloat()), dp(2.2f), planDotPaint)
            }
            point.seconds?.let { seconds ->
                canvas.drawCircle(x, yFor(seconds / 60f), dp(2.8f), actualDotPaint)
            }
        }

        // Date labels every five days, plus today when possible.
        pointDateLabels().forEach { index ->
            val date = points[index].date
            val text = date.format(xFormatter)
            val x = xFor(index)
            val paintWidth = axisTextPaint.measureText(text)
            val left = (x - paintWidth / 2f).coerceIn(dp(2f), width - paintWidth - dp(2f))
            canvas.drawText(text, left, height - dp(8f), axisTextPaint)
        }
    }

    private fun pointDateLabels(): List<Int> {
        if (points.isEmpty()) return emptyList()
        val labels = mutableSetOf<Int>()
        labels.add(0)
        labels.add(points.lastIndex)
        points.indices.filter { it % 5 == 0 }.forEach { labels.add(it) }
        val today = points.indexOfFirst { it.date == DayUtils.today() }
        if (today >= 0) labels.add(today)
        return labels.filter { it in points.indices }.sorted()
    }

    private fun niceStep(maxValue: Float): Float = when {
        maxValue <= 10f -> 2f
        maxValue <= 30f -> 5f
        maxValue <= 60f -> 10f
        maxValue <= 120f -> 20f
        maxValue <= 300f -> 50f
        else -> 100f
    }

    private fun dp(value: Float): Float = value * density
}

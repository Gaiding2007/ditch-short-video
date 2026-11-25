package com.gaiding.ditchshortvideo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.gaiding.ditchshortvideo.data.DayUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DailyPoint(
    val date: LocalDate,
    /** null marks a future date with no actual usage yet. */
    val seconds: Long?,
    val plannedMinutes: Int?
)

/**
 * Plain Canvas chart: actual minutes (solid green) and the auto-plan limit
 * (dashed amber). It is intentionally lightweight and works on API 26.
 *
 * The curve uses monotone cubic interpolation, so it is smooth without
 * overshooting below zero. Touch a point to show a small floating label with
 * the exact date and duration.
 */
class DailyCurveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var points: List<DailyPoint> = emptyList()
    private var selectedIndex = -1
    private var downX = 0f
    private var moved = false

    private val density = resources.displayMetrics.density
    private val leftPad = dp(42f)
    private val rightPad = dp(14f)
    private val topPad = dp(22f)
    private val bottomPad = dp(32f)
    private var columnWidth = dp(25f)
    private val chartHeight = dp(224f)
    private var fitToWidth = false

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
    private val selectedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x331E2A24
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(3f)), 0f)
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
    private val pointLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF126B44.toInt()
        textSize = dp(8.5f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF78847D.toInt()
        textSize = dp(12f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xF21E2A24.toInt()
        style = Paint.Style.FILL
    }
    private val selectionCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val tooltipTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = dp(11f)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val tooltipBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE6EDE8.toInt()
        textSize = dp(10.5f)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val tooltipPlanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8B15E.toInt()
        textSize = dp(10.5f)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val xFormatter = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)
    private val tooltipDateFormatter = DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA)

    private val hideTooltip = Runnable {
        selectedIndex = -1
        invalidate()
    }

    init {
        isClickable = true
    }

    fun submit(points: List<DailyPoint>) {
        this.points = points
        if (selectedIndex !in points.indices) selectedIndex = -1
        contentDescription = if (points.isEmpty()) {
            "每日时长曲线，暂无记录"
        } else {
            "最近 ${points.size} 天的每日时长曲线，共 ${points.count { (it.seconds ?: 0L) > 0L }} 天有记录；点击曲线上的点查看日期和时长"
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

    /** Fit all points into the measured width; used by the share-image renderer. */
    fun setFitToWidth(enabled: Boolean) {
        fitToWidth = enabled
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = leftPad + rightPad + max(1, points.size) * columnWidth
        val width = resolveSize(desiredWidth.toInt(), widthMeasureSpec)
        if (fitToWidth) {
            val available = (width - leftPad - rightPad).coerceAtLeast(dp(1f))
            columnWidth = (available / max(1, points.size)).coerceAtLeast(dp(3f))
        }
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

        val actualPoints = points.mapIndexedNotNull { index, point ->
            point.seconds?.let { seconds ->
                Point(index, xFor(index), yFor(seconds / 60f), seconds)
            }
        }
        val planPoints = points.mapIndexedNotNull { index, point ->
            point.plannedMinutes?.let { planned ->
                Point(index, xFor(index), yFor(planned.toFloat()), planned.toLong())
            }
        }

        if (planPoints.size > 1) {
            canvas.drawPath(smoothPath(planPoints), planPaint)
        }

        if (actualPoints.size > 1) {
            canvas.drawPath(smoothPath(actualPoints), actualPaint)
        }

        points.forEachIndexed { index, point ->
            val x = xFor(index)
            point.plannedMinutes?.let { planned ->
                canvas.drawCircle(x, yFor(planned.toFloat()), dp(2.2f), planDotPaint)
            }
            point.seconds?.let { seconds ->
                canvas.drawCircle(x, yFor(seconds / 60f), dp(2.8f), actualDotPaint)
            }
        }

        drawPointLabels(canvas, actualPoints)
        drawDateLabels(canvas, ::xFor)

        if (selectedIndex in points.indices) {
            drawSelection(canvas, selectedIndex, xFor(selectedIndex), chartBottom, ::yFor)
        }
    }

    private fun drawPointLabels(canvas: Canvas, points: List<Point>) {
        if (points.isEmpty()) return
        points.forEachIndexed { index, point ->
            val isImportant = index == 0 || index == points.lastIndex ||
                points[index].index == selectedIndex ||
                points[index].index == this.points.indexOfFirst { it.date == DayUtils.today() }
            if (!isImportant && points.size > 40 && index % 2 != 0) return@forEachIndexed
            val minutes = (point.value / 60f).roundToInt()
            if (minutes <= 0) return@forEachIndexed
            val text = minutes.toString()
            val textWidth = pointLabelPaint.measureText(text)
            val x = point.x.coerceIn(leftPad + textWidth / 2f, width - rightPad - textWidth / 2f)
            val above = point.y - dp(7f)
            val baseline = if (above - dp(9f) < topPad) point.y + dp(16f) else above
            canvas.drawText(text, x, baseline, pointLabelPaint)
        }
    }

    private fun drawDateLabels(canvas: Canvas, xFor: (Int) -> Float) {
        var lastRight = -Float.MAX_VALUE
        pointDateLabels().forEach { index ->
            val date = points[index].date
            val text = date.format(xFormatter)
            val textWidth = axisTextPaint.measureText(text)
            val left = (xFor(index) - textWidth / 2f)
                .coerceIn(dp(2f), width - textWidth - dp(2f))
            if (left < lastRight + dp(4f)) return@forEach
            canvas.drawText(text, left, height - dp(8f), axisTextPaint)
            lastRight = left + textWidth
        }
    }

    private fun drawSelection(
        canvas: Canvas,
        index: Int,
        x: Float,
        chartBottom: Float,
        yFor: (Float) -> Float
    ) {
        val point = points[index]
        val y = when {
            point.seconds != null -> yFor(point.seconds / 60f)
            point.plannedMinutes != null -> yFor(point.plannedMinutes.toFloat())
            else -> chartBottom
        }
        canvas.drawLine(x, topPad, x, chartBottom, selectedLinePaint)
        val dotPaint = if (point.seconds != null) actualDotPaint else planDotPaint
        canvas.drawCircle(x, y, dp(4.8f), dotPaint)
        canvas.drawCircle(x, y, dp(2.2f), selectionCenterPaint)

        val title = point.date.format(tooltipDateFormatter)
        val actualText = if (point.seconds == null) {
            "实际 无记录"
        } else {
            "实际 ${DayUtils.humanDuration(point.seconds)}"
        }
        val planText = point.plannedMinutes?.let { "计划 $it 分钟" }

        val padding = dp(9f)
        val lineHeight = dp(17f)
        val titleWidth = tooltipTitlePaint.measureText(title)
        val actualWidth = tooltipBodyPaint.measureText(actualText)
        val planWidth = planText?.let { tooltipPlanPaint.measureText(it) } ?: 0f
        val contentWidth = max(titleWidth, max(actualWidth, planWidth))
        val boxWidth = contentWidth + padding * 2f
        val boxHeight = padding * 2f + lineHeight * (if (planText == null) 2 else 3)

        var left = (x - boxWidth / 2f).coerceIn(dp(3f), width - boxWidth - dp(3f))
        var top = y - dp(13f) - boxHeight
        if (top < topPad) {
            // Near the top of the chart, put the label beside the point instead
            // of below it, so it does not cover the curve.
            left = x - boxWidth - dp(12f)
            top = y - boxHeight / 2f
            if (left < dp(3f)) {
                left = x + dp(12f)
                if (left + boxWidth > width - dp(3f)) {
                    left = width - boxWidth - dp(3f)
                }
            }
        }
        if (top + boxHeight > chartBottom) top = chartBottom - boxHeight
        top = top.coerceAtLeast(topPad)
        val rect = RectF(left, top, left + boxWidth, top + boxHeight)
        canvas.drawRoundRect(rect, dp(8f), dp(8f), tooltipBgPaint)

        val textX = left + padding
        var baseline = top + padding + dp(10f)
        canvas.drawText(title, textX, baseline, tooltipTitlePaint)
        baseline += lineHeight
        canvas.drawText(actualText, textX, baseline, tooltipBodyPaint)
        if (planText != null) {
            baseline += lineHeight
            canvas.drawText(planText, textX, baseline, tooltipPlanPaint)
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

    private fun smoothPath(points: List<Point>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points[0].x, points[0].y)
        if (points.size == 1) return path

        val n = points.size
        val secants = FloatArray(n - 1)
        for (i in 0 until n - 1) {
            val dx = points[i + 1].x - points[i].x
            secants[i] = if (abs(dx) < 0.001f) 0f else (points[i + 1].y - points[i].y) / dx
        }
        val tangents = FloatArray(n)
        tangents[0] = secants[0]
        tangents[n - 1] = secants[n - 2]
        for (i in 1 until n - 1) {
            tangents[i] = if (secants[i - 1] * secants[i] <= 0f) {
                0f
            } else {
                val limit = 3f * min(abs(secants[i - 1]), abs(secants[i]))
                ((secants[i - 1] + secants[i]) / 2f).coerceIn(-limit, limit)
            }
        }

        for (i in 0 until n - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val dx = (p2.x - p1.x) / 3f
            path.cubicTo(
                p1.x + dx,
                p1.y + tangents[i] * dx,
                p2.x - dx,
                p2.y - tangents[i + 1] * dx,
                p2.x,
                p2.y
            )
        }
        return path
    }

    private fun niceStep(maxValue: Float): Float = when {
        maxValue <= 10f -> 2f
        maxValue <= 30f -> 5f
        maxValue <= 60f -> 10f
        maxValue <= 120f -> 20f
        maxValue <= 300f -> 50f
        else -> 100f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (points.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                moved = false
                selectAt(event.x)
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > dp(10f)) {
                    moved = true
                    parent?.requestDisallowInterceptTouchEvent(false)
                    removeCallbacks(hideTooltip)
                    selectedIndex = -1
                    invalidate()
                    return false
                }
                selectAt(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    selectAt(event.x)
                    scheduleHide()
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                scheduleHide()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun selectAt(x: Float) {
        if (points.isEmpty()) return
        val relative = (x - leftPad - columnWidth / 2f) / columnWidth
        val index = relative.roundToInt().coerceIn(0, points.lastIndex)
        val targetX = leftPad + index * columnWidth + columnWidth / 2f
        if (abs(x - targetX) > columnWidth * 0.75f) return
        if (selectedIndex != index) {
            selectedIndex = index
            invalidate()
        }
    }

    private fun scheduleHide() {
        removeCallbacks(hideTooltip)
        postDelayed(hideTooltip, 3500L)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideTooltip)
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * density

    private data class Point(
        val index: Int,
        val x: Float,
        val y: Float,
        val value: Long
    )
}

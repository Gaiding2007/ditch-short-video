package com.gaiding.ditchshortvideo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.gaiding.ditchshortvideo.R
import com.gaiding.ditchshortvideo.data.DayUtils
import java.time.LocalDate
import kotlin.math.max

/**
 * A GitHub-contribution-style year heatmap. Cells are laid out in week columns
 * and can be wider than the screen; callers put this view in a horizontal
 * scroll view.
 */
class HeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var data: Map<LocalDate, Long> = emptyMap()
    private var limitSeconds = 30L * 60L

    private val density = resources.displayMetrics.density
    private val scaled = resources.displayMetrics.scaledDensity
    private val cell = 12f * density
    private val gap = 3f * density
    private val leftGutter = 24f * density
    private val topGutter = 18f * density
    private val rightGutter = 2f * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * density
        color = context.getColor(R.color.accent_deep)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f * scaled
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        color = context.getColor(R.color.ink_faint)
    }
    private val cellRect = RectF()

    fun submit(daySeconds: Map<LocalDate, Long>, limitMinutes: Int) {
        data = daySeconds
        limitSeconds = max(60L, limitMinutes.toLong() * 60L)
        contentDescription = "今年每日刷短视频时长热力图，颜色越深代表时间越长"
        requestLayout()
        invalidate()
    }

    /** Horizontal scroll offset that puts today's week near the middle. */
    fun scrollXForToday(viewportWidth: Int): Int {
        if (viewportWidth <= 0) return 0
        val today = DayUtils.today()
        val grid = gridFor(today)
        val column = ((today.toEpochDay() - grid.start.toEpochDay()) / 7L).toInt()
        val cellCenter = leftGutter + column * (cell + gap) + cell / 2f
        return (cellCenter - viewportWidth / 2f).toInt().coerceAtLeast(0)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val grid = gridFor(DayUtils.today())
        val desiredWidth = (leftGutter + grid.columns * (cell + gap) + rightGutter).toInt()
        val desiredHeight = (topGutter + 7 * (cell + gap)).toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val today = DayUtils.today()
        val grid = gridFor(today)
        drawWeekdayLabels(canvas)
        drawMonthLabels(canvas, grid)

        var date = grid.start
        var column = 0
        while (!date.isAfter(grid.end)) {
            val row = date.dayOfWeek.value - 1
            if (date.year == today.year) {
                val x = leftGutter + column * (cell + gap)
                val y = topGutter + row * (cell + gap)
                cellRect.set(x, y, x + cell, y + cell)

                if (date.isAfter(today)) {
                    fillPaint.color = context.getColor(R.color.heat_0)
                    fillPaint.alpha = 70
                } else {
                    fillPaint.color = colorFor(data[date] ?: 0L)
                    fillPaint.alpha = 255
                }
                val radius = 3f * density
                canvas.drawRoundRect(cellRect, radius, radius, fillPaint)

                if (date == today) {
                    val outline = RectF(cellRect)
                    outline.inset(1f * density, 1f * density)
                    canvas.drawRoundRect(outline, radius, radius, strokePaint)
                }
            }

            date = date.plusDays(1)
            if (row == 6) column++
        }
    }

    private fun drawWeekdayLabels(canvas: Canvas) {
        val lines = listOf(0 to "一", 2 to "三", 4 to "五", 6 to "日")
        lines.forEach { (row, label) ->
            val y = topGutter + row * (cell + gap) + cell - 2f * density
            val x = leftGutter - 6f * density - textPaint.measureText(label)
            canvas.drawText(label, x, y, textPaint)
        }
    }

    private fun drawMonthLabels(canvas: Canvas, grid: Grid) {
        var lastX = -1000f
        var lastMonth = -1
        var date = grid.start
        var column = 0
        while (!date.isAfter(grid.end)) {
            if (date.year == DayUtils.today().year &&
                date.dayOfMonth == 1 &&
                date.monthValue != lastMonth
            ) {
                val x = leftGutter + column * (cell + gap)
                if (x - lastX >= 26f * density) {
                    canvas.drawText("${date.monthValue}月", x, 10f * density, textPaint)
                    lastX = x
                    lastMonth = date.monthValue
                }
            }
            date = date.plusDays(1)
            if (date.minusDays(1).dayOfWeek.value == 7) column++
        }
    }

    private fun colorFor(seconds: Long): Int {
        if (seconds <= 0L) return context.getColor(R.color.heat_0)
        val ratio = seconds.toFloat() / limitSeconds.toFloat()
        return when {
            ratio < 0.25f -> context.getColor(R.color.heat_1)
            ratio < 0.5f -> context.getColor(R.color.heat_2)
            ratio < 1f -> context.getColor(R.color.heat_3)
            else -> context.getColor(R.color.heat_4)
        }
    }

    private fun gridFor(today: LocalDate): Grid {
        val yearStart = LocalDate.of(today.year, 1, 1)
        val start = yearStart.minusDays((yearStart.dayOfWeek.value - 1).toLong())
        val yearEnd = LocalDate.of(today.year, 12, 31)
        val end = yearEnd.plusDays((7 - yearEnd.dayOfWeek.value).toLong())
        val columns = ((end.toEpochDay() - start.toEpochDay() + 1L) / 7L).toInt()
        return Grid(start, end, columns)
    }

    private data class Grid(val start: LocalDate, val end: LocalDate, val columns: Int)
}

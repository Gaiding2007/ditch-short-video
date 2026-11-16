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
import kotlin.math.min

class ProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var usedSeconds = 0L
    private var limitMinutes = 30
    private val density = resources.displayMetrics.density
    private val scaled = resources.displayMetrics.scaledDensity

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.ink)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.ink_soft)
        textSize = 13f * scaled
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    fun setProgress(usedSeconds: Long, limitMinutes: Int) {
        this.usedSeconds = usedSeconds.coerceAtLeast(0L)
        this.limitMinutes = limitMinutes.coerceAtLeast(1)
        contentDescription = "今天已刷短视频 ${DayUtils.humanDuration(this.usedSeconds)}，" +
            "每日上限 $limitMinutes 分钟"
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (240f * density).toInt()
        val desiredHeight = (218f * density).toInt()
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val stroke = 12f * density
        val radius = min(width, height) / 2f - stroke - 4f * density

        rect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        trackPaint.color = context.getColor(R.color.divider)
        canvas.drawArc(rect, -90f, 360f, false, trackPaint)

        val limitSeconds = limitMinutes * 60f
        val ratio = (usedSeconds / limitSeconds).coerceIn(0f, 1f)
        ringPaint.color = when {
            usedSeconds >= limitSeconds -> context.getColor(R.color.danger)
            ratio >= 0.8f -> context.getColor(R.color.warning)
            else -> context.getColor(R.color.accent)
        }
        if (ratio > 0f) {
            canvas.drawArc(rect, -90f, ratio * 360f, false, ringPaint)
        }

        val number = DayUtils.bigMinutes(usedSeconds)
        numberPaint.textSize = (if (number.length >= 4) 44f else 58f) * scaled
        val numberMetrics = numberPaint.fontMetrics
        val numberBaseline = centerY - (numberMetrics.ascent + numberMetrics.descent) / 2f - 8f * density
        canvas.drawText(number, centerX, numberBaseline, numberPaint)

        val labelMetrics = labelPaint.fontMetrics
        val labelBaseline = centerY + 42f * density - (labelMetrics.ascent + labelMetrics.descent) / 2f
        canvas.drawText("分钟", centerX, labelBaseline, labelPaint)
    }
}

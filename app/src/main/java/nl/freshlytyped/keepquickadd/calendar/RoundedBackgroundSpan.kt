package nl.freshlytyped.keepquickadd.calendar

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan

/**
 * A span that draws a rounded rectangle background behind text with padding.
 * Provides better visual feedback than a plain BackgroundColorSpan.
 */
class RoundedBackgroundSpan(
    private val bgColor: Int,
    private val textColor: Int,
    private val cornerRadius: Float = 8f,
    private val padding: Float = 3f
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        // Let the paint measure the text width
        return paint.measureText(text, start, end).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        // Calculate the text width
        val textWidth = paint.measureText(text, start, end)

        // Draw rounded rectangle background with padding
        val rect = RectF(
            x - padding,
            top.toFloat(),
            x + textWidth + padding,
            bottom.toFloat()
        )

        val originalColor = paint.color
        paint.color = bgColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        // Draw the text
        paint.color = textColor
        canvas.drawText(text, start, end, x, y.toFloat(), paint)

        // Restore original color
        paint.color = originalColor
    }
}

package com.reelingsoft.todaysfish.widget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface


/**
 * Create a bordered text object with the specified interior and exterior colors, text size and
 * alignment.
 *
 * @param interiorColor the interior text color
 * @param exteriorColor the exterior text color
 * @param textSize text size in pixels
 */
class BorderedText(interiorColor: Int, exteriorColor: Int, textSize: Float) {

    private val interiorPaint = Paint()
    private val exteriorPaint = Paint()

    constructor(textSize: Float): this(Color.WHITE, Color.BLACK, textSize)

    init {
        with (interiorPaint) {
            setTextSize(textSize)
            color = interiorColor
            style = Paint.Style.FILL
            isAntiAlias = false
            alpha = 255
        }

        with (exteriorPaint) {
            setTextSize(textSize)
            color = exteriorColor
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = textSize / 8
            isAntiAlias = false
            alpha = 255
        }
    }


    fun setTypeface(typeface: Typeface) {
        interiorPaint.typeface = typeface
        exteriorPaint.typeface = typeface
    }


    fun drawText(canvas: Canvas, posX: Float, posY: Float, text: String) {
        canvas.drawText(text, posX, posY, exteriorPaint)
        canvas.drawText(text, posX, posY, interiorPaint)
    }

    fun drawText(canvas: Canvas, posX: Float, posY: Float, text: String, bgPaint: Paint) {
        val width = exteriorPaint.measureText(text)
        val tsize = exteriorPaint.textSize

        val paint = Paint(bgPaint)
        paint.style = Paint.Style.FILL
        paint.alpha = 160
        canvas.drawRect(posX, (posY + tsize.toInt()), (posX + width.toInt()), posY, paint)
        canvas.drawText(text, posX, (posY + tsize), interiorPaint)
    }


    fun setAlpha(alpha: Int) {
        interiorPaint.alpha = alpha
        exteriorPaint.alpha = alpha
    }


    fun setTextAlign(align: Paint.Align) {
        interiorPaint.textAlign = align
        exteriorPaint.textAlign = align
    }

}
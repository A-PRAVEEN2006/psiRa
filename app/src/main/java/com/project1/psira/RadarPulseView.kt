package com.project1.psira

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

class RadarPulseView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val circles = mutableListOf<Circle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#B388FF")
    }

    private var isActive = false

    private inner class Circle(var radius: Float, var alpha: Int)

    fun start() {
        isActive = true
        circles.clear()
        invalidate()
    }

    fun stop() {
        isActive = false
        circles.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isActive) return

        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = Math.min(centerX, centerY)

        // Add new circle periodically
        if (System.currentTimeMillis() % 1000 < 50 && (circles.isEmpty() || circles.last().radius > maxRadius / 4)) {
            circles.add(Circle(0f, 255))
        }

        val iterator = circles.iterator()
        while (iterator.hasNext()) {
            val circle = iterator.next()
            circle.radius += 5f
            circle.alpha = (255 * (1 - circle.radius / maxRadius)).toInt()

            if (circle.radius > maxRadius) {
                iterator.remove()
            } else {
                paint.alpha = Math.max(0, circle.alpha)
                canvas.drawCircle(centerX, centerY, circle.radius, paint)
            }
        }

        if (isActive) {
            postInvalidateOnAnimation()
        }
    }
}

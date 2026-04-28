package com.project1.psira

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Calendar
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class AnalogClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var isRealTime = true

    // Current displayed time
    private var currentHour = 0f
    private var currentMinute = 0f
    private var currentSecond = 0f

    // Dimensions
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    // Interaction state
    private var activeHand: HandType? = null

    enum class HandType { HOUR, MINUTE }

    var onClockUnlockListener: ((String) -> Unit)? = null

    private val ticker = object : Runnable {
        override fun run() {
            if (isRealTime) {
                val calendar = Calendar.getInstance()
                currentSecond = calendar.get(Calendar.SECOND).toFloat()
                currentMinute = calendar.get(Calendar.MINUTE).toFloat() + currentSecond / 60f
                currentHour = calendar.get(Calendar.HOUR).toFloat() + currentMinute / 60f
                invalidate()
                postDelayed(this, 1000)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isRealTime = true
        post(ticker)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(ticker)
    }

    fun resetToRealTime() {
        isRealTime = true
        activeHand = null
        post(ticker)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = min(centerX, centerY) * 0.9f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Face - Clean White with subtle shadow
        paint.color = Color.parseColor("#F5F5F7")
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, radius, paint)
        
        // Face Border
        paint.color = Color.parseColor("#D1D1D6")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawCircle(centerX, centerY, radius, paint)
        
        // Draw Ticks & Numbers
        paint.textSize = radius * 0.16f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = false
        paint.style = Paint.Style.FILL

        for (i in 0..59) {
            val isHour = i % 5 == 0
            val tickLength = if (isHour) radius * 0.1f else radius * 0.05f
            paint.strokeWidth = if (isHour) 5f else 2f
            paint.color = if (isHour) Color.parseColor("#3A3A3C") else Color.parseColor("#C7C7CC")
            
            val angle = Math.PI * i / 30 - Math.PI / 2
            val startX = centerX + cos(angle).toFloat() * (radius * 0.92f - tickLength)
            val startY = centerY + sin(angle).toFloat() * (radius * 0.92f - tickLength)
            val stopX = centerX + cos(angle).toFloat() * (radius * 0.92f)
            val stopY = centerY + sin(angle).toFloat() * (radius * 0.92f)
            canvas.drawLine(startX, startY, stopX, stopY, paint)
            
            // Draw Numbers for Hours (Premium Typography feel)
            if (isHour) {
                val num = if (i == 0) 12 else i / 5
                val numOffset = radius * 0.65f
                val numX = centerX + cos(angle).toFloat() * numOffset
                val numY = centerY + sin(angle).toFloat() * numOffset - (paint.descent() + paint.ascent()) / 2
                paint.color = Color.parseColor("#1C1C1E")
                canvas.drawText(num.toString(), numX, numY, paint)
            }
        }

        // Draw Hands
        val hourAngle = Math.PI * currentHour / 6 - Math.PI / 2
        val minAngle = Math.PI * currentMinute / 30 - Math.PI / 2
        val secAngle = Math.PI * currentSecond / 30 - Math.PI / 2

        // Hour Hand
        paint.color = Color.parseColor("#1C1C1E")
        paint.strokeWidth = 20f
        paint.strokeCap = Paint.Cap.ROUND
        val hX = centerX + cos(hourAngle).toFloat() * (radius * 0.5f)
        val hY = centerY + sin(hourAngle).toFloat() * (radius * 0.5f)
        canvas.drawLine(centerX, centerY, hX, hY, paint)

        // Minute Hand
        paint.strokeWidth = 12f
        val mX = centerX + cos(minAngle).toFloat() * (radius * 0.75f)
        val mY = centerY + sin(minAngle).toFloat() * (radius * 0.75f)
        canvas.drawLine(centerX, centerY, mX, mY, paint)

        // Second Hand (Tactical Orange/Red)
        val accentColor = Color.parseColor("#FF3B30")
        if (isRealTime) {
            paint.color = accentColor
            paint.strokeWidth = 4f
            val sX = centerX + cos(secAngle).toFloat() * (radius * 0.85f)
            val sY = centerY + sin(secAngle).toFloat() * (radius * 0.85f)
            val counterX = centerX - cos(secAngle).toFloat() * (radius * 0.15f)
            val counterY = centerY - sin(secAngle).toFloat() * (radius * 0.15f)
            canvas.drawLine(counterX, counterY, sX, sY, paint)
            
            // Second hand pivot circle
            paint.style = Paint.Style.FILL
            canvas.drawCircle(centerX, centerY, 8f, paint)
        } else {
            // Pivot hub
            paint.color = Color.parseColor("#1C1C1E")
            paint.style = Paint.Style.FILL
            canvas.drawCircle(centerX, centerY, 10f, paint)
        }

        // Hub cap
        paint.color = Color.parseColor("#FFFFFF")
        canvas.drawCircle(centerX, centerY, 4f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val dx = x - centerX
        val dy = y - centerY
        val distance = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val triggerZone = radius * 0.35f
                if (distance < triggerZone) {
                    activeHand = null
                    return true
                }

                if (isRealTime) {
                    isRealTime = false
                    removeCallbacks(ticker)
                }

                activeHand = if (distance > radius * 0.5f) HandType.MINUTE else HandType.HOUR
                updateTimeFromTouch(dx, dy)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeHand != null) {
                    updateTimeFromTouch(dx, dy)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val triggerZone = radius * 0.35f
                if (activeHand == null && distance < triggerZone) {
                    triggerUnlock()
                }
                activeHand = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateTimeFromTouch(dx: Float, dy: Float) {
        if (activeHand == null) return
        var angle = (atan2(dy.toDouble(), dx.toDouble()) + Math.PI / 2) % (2 * Math.PI)
        if (angle < 0) angle += 2 * Math.PI

        if (activeHand == HandType.MINUTE) {
            val exactMinute = (angle / (2 * Math.PI) * 60).toFloat()
            currentMinute = Math.round(exactMinute).toFloat() // Snap to sharp minutes
        } else if (activeHand == HandType.HOUR) {
            val exactHour = (angle / (2 * Math.PI) * 12).toFloat()
            currentHour = Math.round(exactHour).toFloat() // Snap to sharp hours
        }
        invalidate()
    }

    private fun triggerUnlock() {
        val h = currentHour.toInt() % 12
        val hrDisplay = if (h == 0) 12 else h
        val m = currentMinute.toInt() % 60
        val timeString = String.format(Locale.US, "%02d:%02d", hrDisplay, m)
        onClockUnlockListener?.invoke(timeString)
    }
}

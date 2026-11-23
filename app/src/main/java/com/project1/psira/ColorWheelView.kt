package com.project1.psira

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ColorWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val colorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val satPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.WHITE
    }
    private val thumbFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    private var currentHue = 180f
    private var currentSat = 1f
    var currentBrightness = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            notifyColor()
            invalidate()
        }

    private var selectedX = 0f
    private var selectedY = 0f

    var onColorChangedListener: ((color: Int, hex: String) -> Unit)? = null

    init {
        // Pre-configure shaders when size is known
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = (Math.min(w, h) / 2f) - 16f

        val colors = intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN,
            Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
        )
        val sweepGradient = SweepGradient(centerX, centerY, colors, null)
        colorPaint.shader = sweepGradient

        val radialGradient = RadialGradient(
            centerX, centerY, radius,
            Color.WHITE, Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        satPaint.shader = radialGradient

        updateThumbPosition()
    }

    private fun updateThumbPosition() {
        val rad = Math.toRadians(currentHue.toDouble())
        val dist = currentSat * radius
        selectedX = (centerX + dist * Math.cos(rad)).toFloat()
        selectedY = (centerY + dist * Math.sin(rad)).toFloat()
    }

    fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        currentHue = hsv[0]
        currentSat = hsv[1]
        currentBrightness = hsv[2]
        updateThumbPosition()
        invalidate()
    }

    fun getSelectedColor(): Int {
        return Color.HSVToColor(floatArrayOf(currentHue, currentSat, currentBrightness))
    }

    private fun notifyColor() {
        val color = getSelectedColor()
        val hex = String.format("#%06X", (0xFFFFFF and color))
        onColorChangedListener?.invoke(color, hex)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius <= 0) return

        // Draw color wheel
        canvas.drawCircle(centerX, centerY, radius, colorPaint)
        canvas.drawCircle(centerX, centerY, radius, satPaint)

        // Dim if brightness < 1
        if (currentBrightness < 1f) {
            val dimPaint = Paint().apply {
                color = Color.BLACK
                alpha = ((1f - currentBrightness) * 255).toInt()
            }
            canvas.drawCircle(centerX, centerY, radius, dimPaint)
        }

        // Draw thumb
        thumbFillPaint.color = getSelectedColor()
        canvas.drawCircle(selectedX, selectedY, 18f, thumbFillPaint)
        canvas.drawCircle(selectedX, selectedY, 18f, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - centerX
                val dy = event.y - centerY
                val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

                currentSat = (dist / radius).coerceIn(0f, 1f)
                var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (angle < 0) angle += 360f
                currentHue = angle

                updateThumbPosition()
                notifyColor()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

package com.project1.psira

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

class SpaceGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Star(var x: Float, var y: Float, val speed: Float, val size: Float)
    data class Laser(var x: Float, var y: Float)
    data class Asteroid(var x: Float, var y: Float, val radius: Float, val speed: Float)

    private val stars = mutableListOf<Star>()
    private val lasers = mutableListOf<Laser>()
    private val asteroids = mutableListOf<Asteroid>()

    private var playerX = 150f
    private val playerWidth = 36f
    private val playerHeight = 28f
    private var score = 0
    var isGameOver = false
        private set

    var onScoreChanged: ((Int) -> Unit)? = null
    var onGameOver: (() -> Unit)? = null

    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00E5FF") }
    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#76FF03")
        strokeWidth = 6f
    }
    private val asteroidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5722")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    private val handler = Handler(Looper.getMainLooper())
    private val gameLoop = object : Runnable {
        override fun run() {
            if (!isGameOver) {
                updateGame()
                invalidate()
                handler.postDelayed(this, 30)
            }
        }
    }

    init {
        resetGame()
    }

    fun start() {
        handler.removeCallbacks(gameLoop)
        if (isGameOver) resetGame()
        handler.post(gameLoop)
    }

    fun pause() {
        handler.removeCallbacks(gameLoop)
    }

    fun resetGame() {
        lasers.clear()
        asteroids.clear()
        isGameOver = false
        score = 0
        onScoreChanged?.invoke(score)
        invalidate()
    }

    fun moveLeft() {
        if (isGameOver) { resetGame(); start(); return }
        playerX = (playerX - 25f).coerceAtLeast(playerWidth)
        invalidate()
    }

    fun moveRight() {
        if (isGameOver) { resetGame(); start(); return }
        playerX = (playerX + 25f).coerceAtMost(width - playerWidth)
        invalidate()
    }

    fun fire() {
        if (isGameOver) { resetGame(); start(); return }
        lasers.add(Laser(playerX, height - playerHeight - 15f))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        playerX = w / 2f
        stars.clear()
        for (i in 0 until 40) {
            stars.add(Star(Random.nextFloat() * w, Random.nextFloat() * h, Random.nextFloat() * 2f + 1f, Random.nextFloat() * 2f + 1f))
        }
    }

    private fun updateGame() {
        val h = height.toFloat()
        val w = width.toFloat()
        if (w <= 0 || h <= 0) return

        // Update stars
        for (s in stars) {
            s.y += s.speed
            if (s.y > h) {
                s.y = 0f
                s.x = Random.nextFloat() * w
            }
        }

        // Spawn asteroids
        if (Random.nextFloat() < 0.05f && asteroids.size < 6) {
            asteroids.add(
                Asteroid(
                    x = Random.nextFloat() * (w - 40f) + 20f,
                    y = -20f,
                    radius = Random.nextFloat() * 10f + 12f,
                    speed = Random.nextFloat() * 3f + 3f
                )
            )
        }

        // Move asteroids
        val astIter = asteroids.iterator()
        while (astIter.hasNext()) {
            val ast = astIter.next()
            ast.y += ast.speed

            // Check collision with player
            val playerY = h - playerHeight - 10f
            if (Math.abs(ast.x - playerX) < (ast.radius + playerWidth / 2) &&
                Math.abs(ast.y - playerY) < (ast.radius + playerHeight / 2)) {
                isGameOver = true
                onGameOver?.invoke()
                return
            }

            if (ast.y > h + 30f) {
                astIter.remove()
            }
        }

        // Move lasers
        val laserIter = lasers.iterator()
        while (laserIter.hasNext()) {
            val laser = laserIter.next()
            laser.y -= 16f
            var hit = false

            val astIter2 = asteroids.iterator()
            while (astIter2.hasNext()) {
                val ast = astIter2.next()
                if (Math.abs(laser.x - ast.x) < ast.radius + 6f && Math.abs(laser.y - ast.y) < ast.radius + 6f) {
                    astIter2.remove()
                    hit = true
                    score += 25
                    onScoreChanged?.invoke(score)
                    break
                }
            }

            if (hit || laser.y < -20f) {
                laserIter.remove()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw stars
        for (s in stars) {
            canvas.drawCircle(s.x, s.y, s.size, starPaint)
        }

        val h = height.toFloat()
        val playerY = h - playerHeight - 10f

        // Draw player ship (triangle)
        val shipPath = Path().apply {
            moveTo(playerX, playerY - playerHeight / 2)
            lineTo(playerX - playerWidth / 2, playerY + playerHeight / 2)
            lineTo(playerX + playerWidth / 2, playerY + playerHeight / 2)
            close()
        }
        canvas.drawPath(shipPath, playerPaint)

        // Draw lasers
        for (l in lasers) {
            canvas.drawLine(l.x, l.y, l.x, l.y - 12f, laserPaint)
        }

        // Draw asteroids
        for (a in asteroids) {
            canvas.drawCircle(a.x, a.y, a.radius, asteroidPaint)
        }

        if (isGameOver) {
            textPaint.color = Color.parseColor("#FF5252")
            canvas.drawText("SHIP DESTROYED", width / 2f, height / 2f, textPaint)
            textPaint.textSize = 20f
            textPaint.color = Color.WHITE
            canvas.drawText("Tap or Fire to Restart", width / 2f, height / 2f + 40f, textPaint)
            textPaint.textSize = 32f
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (isGameOver) {
                resetGame()
                start()
            } else {
                if (event.y < height - 120f) {
                    fire()
                } else {
                    if (event.x < width / 2f) moveLeft() else moveRight()
                }
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pause()
    }
}

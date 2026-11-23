package com.project1.psira

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

class SnakeGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Direction { UP, DOWN, LEFT, RIGHT }

    private val gridSize = 16
    private var cellSize = 0f

    private val snake = mutableListOf<Point>()
    private var direction = Direction.RIGHT
    private var nextDirection = Direction.RIGHT
    private var food = Point(8, 8)
    private var score = 0
    var isGameOver = false
        private set

    var onScoreChanged: ((Int) -> Unit)? = null
    var onGameOver: (() -> Unit)? = null

    private val snakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
    }
    private val snakeHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00B0FF")
        style = Paint.Style.FILL
    }
    private val foodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744")
        style = Paint.Style.FILL
    }
    private val gridBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F2937")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isGameOver) {
                updateGame()
                invalidate()
                handler.postDelayed(this, 140)
            }
        }
    }

    private var startX = 0f
    private var startY = 0f

    init {
        resetGame()
    }

    fun start() {
        handler.removeCallbacks(tickRunnable)
        if (isGameOver) resetGame()
        handler.post(tickRunnable)
    }

    fun pause() {
        handler.removeCallbacks(tickRunnable)
    }

    fun resetGame() {
        snake.clear()
        snake.add(Point(5, 7))
        snake.add(Point(4, 7))
        snake.add(Point(3, 7))
        direction = Direction.RIGHT
        nextDirection = Direction.RIGHT
        isGameOver = false
        score = 0
        onScoreChanged?.invoke(score)
        spawnFood()
        invalidate()
    }

    fun changeDirection(newDir: Direction) {
        if (isGameOver) {
            resetGame()
            start()
            return
        }
        if (direction == Direction.UP && newDir != Direction.DOWN) nextDirection = newDir
        else if (direction == Direction.DOWN && newDir != Direction.UP) nextDirection = newDir
        else if (direction == Direction.LEFT && newDir != Direction.RIGHT) nextDirection = newDir
        else if (direction == Direction.RIGHT && newDir != Direction.LEFT) nextDirection = newDir
    }

    private fun spawnFood() {
        var p: Point
        do {
            p = Point(Random.nextInt(gridSize), Random.nextInt(gridSize))
        } while (snake.contains(p))
        food = p
    }

    private fun updateGame() {
        direction = nextDirection
        val head = Point(snake.first())
        when (direction) {
            Direction.UP -> head.y -= 1
            Direction.DOWN -> head.y += 1
            Direction.LEFT -> head.x -= 1
            Direction.RIGHT -> head.x += 1
        }

        // Wall collision wrap-around or death (let's do wall collision death for challenge)
        if (head.x < 0 || head.x >= gridSize || head.y < 0 || head.y >= gridSize) {
            isGameOver = true
            onGameOver?.invoke()
            return
        }

        // Self collision
        if (snake.contains(head)) {
            isGameOver = true
            onGameOver?.invoke()
            return
        }

        snake.add(0, head)
        if (head == food) {
            score += 10
            onScoreChanged?.invoke(score)
            spawnFood()
        } else {
            snake.removeAt(snake.size - 1)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellSize = (w.coerceAtMost(h) / gridSize).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, cellSize * gridSize, cellSize * gridSize, gridBorderPaint)

        // Draw food
        val foodRect = RectF(
            food.x * cellSize + 4f,
            food.y * cellSize + 4f,
            (food.x + 1) * cellSize - 4f,
            (food.y + 1) * cellSize - 4f
        )
        canvas.drawRoundRect(foodRect, 8f, 8f, foodPaint)

        // Draw snake
        for (i in snake.indices) {
            val part = snake[i]
            val rect = RectF(
                part.x * cellSize + 2f,
                part.y * cellSize + 2f,
                (part.x + 1) * cellSize - 2f,
                (part.y + 1) * cellSize - 2f
            )
            val paint = if (i == 0) snakeHeadPaint else snakePaint
            canvas.drawRoundRect(rect, 6f, 6f, paint)
        }

        if (isGameOver) {
            val overPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 36f
                textAlign = Paint.Align.CENTER
                setShadowLayer(8f, 0f, 0f, Color.RED)
            }
            canvas.drawText("GAME OVER", width / 2f, height / 2f, overPaint)
            overPaint.textSize = 20f
            overPaint.clearShadowLayer()
            canvas.drawText("Tap or D-Pad to restart", width / 2f, height / 2f + 40f, overPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - startX
                val dy = event.y - startY
                if (Math.abs(dx) > 40 || Math.abs(dy) > 40) {
                    if (Math.abs(dx) > Math.abs(dy)) {
                        if (dx > 0) changeDirection(Direction.RIGHT) else changeDirection(Direction.LEFT)
                    } else {
                        if (dy > 0) changeDirection(Direction.DOWN) else changeDirection(Direction.UP)
                    }
                } else if (isGameOver) {
                    resetGame()
                    start()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pause()
    }
}

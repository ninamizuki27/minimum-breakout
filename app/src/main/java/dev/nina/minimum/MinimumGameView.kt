package dev.nina.minimum

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class MinimumGameView(context: Context) : View(context) {
    private data class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pixelPaint = Paint().apply { isAntiAlias = false }
    private val balls = ArrayList<Ball>()
    private lateinit var cells: BooleanArray
    private var cols = 0
    private var rows = 0
    private var cell = 3f
    private var top = 0f
    private var fieldHeight = 0f
    private var alive = 0
    private var initial = 1
    private var destroyed = 0
    private var nextSpawn = 18
    private var level = 1
    private var paddleX = 0f
    private var paddleW = 0f
    private var paddleY = 0f
    private var lastNs = 0L
    private var started = false
    private var over = false
    private val rng = Random(System.nanoTime())

    init {
        setBackgroundColor(Color.BLACK)
        isHapticFeedbackEnabled = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        paddleW = max(42f, w * .115f)
        paddleX = w / 2f
        paddleY = h * .91f
        resetLevel()
    }

    private fun resetLevel() {
        if (width == 0) return
        balls.clear()
        cell = max(2f, width / 360f)
        cols = max(1, (width / cell).toInt())
        top = height * .105f
        fieldHeight = min(height * (.25f + level * .018f), height * .54f)
        rows = max(1, (fieldHeight / cell).toInt())
        cells = BooleanArray(cols * rows)
        val density = min(.98f, .62f + level * .035f)
        for (i in cells.indices) cells[i] = rng.nextFloat() < density
        alive = cells.count { it }
        initial = max(1, alive)
        destroyed = 0
        nextSpawn = max(7, 22 - level)
        val speed = min(width * .75f, width * (.40f + level * .025f))
        balls += Ball(width / 2f, paddleY - 18f, speed * .55f, -speed)
        started = false
        over = false
        lastNs = 0L
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0) return
        val now = System.nanoTime()
        if (lastNs == 0L) lastNs = now
        var dt = ((now - lastNs) / 1_000_000_000f).coerceIn(0f, .025f)
        lastNs = now
        if (started && !over) {
            var remaining = dt
            while (remaining > 0f) {
                val step = min(.006f, remaining)
                update(step)
                remaining -= step
            }
        }

        pixelPaint.color = Color.rgb(225, 225, 225)
        for (r in 0 until rows) for (c in 0 until cols) {
            if (cells[r * cols + c]) {
                val x = c * cell
                val y = top + r * cell
                canvas.drawRect(x, y, x + max(1f, cell - 1f), y + max(1f, cell - 1f), pixelPaint)
            }
        }

        pixelPaint.color = Color.WHITE
        val br = max(1.25f, cell * .52f)
        for (b in balls) canvas.drawCircle(b.x, b.y, br, pixelPaint)
        canvas.drawRect(paddleX - paddleW / 2, paddleY, paddleX + paddleW / 2, paddleY + max(2f, cell), pixelPaint)

        paint.color = Color.WHITE
        paint.textSize = max(20f, width * .031f)
        paint.isAntiAlias = true
        canvas.drawText(level.toString(), width * .045f, height * .055f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${balls.size}", width * .955f, height * .055f, paint)
        paint.textAlign = Paint.Align.CENTER
        if (!started) {
            paint.textSize = max(16f, width * .025f)
            canvas.drawText("tap", width / 2f, height * .78f, paint)
        } else if (over) {
            paint.textSize = max(16f, width * .025f)
            canvas.drawText("tap", width / 2f, height * .78f, paint)
        }
        paint.textAlign = Paint.Align.LEFT
        postInvalidateOnAnimation()
    }

    private fun update(dt: Float) {
        val radius = max(1.25f, cell * .52f)
        val speedCap = width * 1.15f
        val dead = ArrayList<Ball>()
        for (b in balls) {
            b.x += b.vx * dt
            b.y += b.vy * dt
            if (b.x < radius) { b.x = radius; b.vx = abs(b.vx) }
            if (b.x > width - radius) { b.x = width - radius; b.vx = -abs(b.vx) }
            if (b.y < radius) { b.y = radius; b.vy = abs(b.vy) }

            if (b.vy > 0 && b.y + radius >= paddleY && b.y - radius <= paddleY + 12f && abs(b.x - paddleX) <= paddleW / 2 + radius) {
                b.y = paddleY - radius
                val hit = ((b.x - paddleX) / (paddleW / 2)).coerceIn(-1f, 1f)
                val speed = min(speedCap, sqrt(b.vx * b.vx + b.vy * b.vy) * 1.015f)
                b.vx = speed * hit * .82f
                b.vy = -sqrt(max(1f, speed * speed - b.vx * b.vx))
            }
            collidePixel(b, radius)
            if (b.y > height + 20f) dead += b
        }
        balls.removeAll(dead.toSet())
        if (balls.isEmpty()) over = true
        if (alive <= 0) {
            level++
            resetLevel()
            started = true
        }
    }

    private fun collidePixel(b: Ball, radius: Float) {
        if (b.y + radius < top || b.y - radius > top + rows * cell) return
        val c0 = ((b.x - radius) / cell).toInt().coerceIn(0, cols - 1)
        val c1 = ((b.x + radius) / cell).toInt().coerceIn(0, cols - 1)
        val r0 = ((b.y - radius - top) / cell).toInt().coerceIn(0, rows - 1)
        val r1 = ((b.y + radius - top) / cell).toInt().coerceIn(0, rows - 1)
        for (r in r0..r1) for (c in c0..c1) {
            val i = r * cols + c
            if (!cells[i]) continue
            cells[i] = false
            alive--
            destroyed++
            val cx = (c + .5f) * cell
            val cy = top + (r + .5f) * cell
            if (abs(b.x - cx) > abs(b.y - cy)) b.vx = -b.vx else b.vy = -b.vy
            if (destroyed >= nextSpawn) {
                destroyed = 0
                nextSpawn = max(3, (nextSpawn * .96f).toInt())
                if (balls.size < 650) {
                    val jitter = (rng.nextFloat() - .5f) * .42f
                    val speed = sqrt(b.vx * b.vx + b.vy * b.vy)
                    val nvx = (b.vx + speed * jitter).coerceIn(-speed * .9f, speed * .9f)
                    val nvy = if (rng.nextBoolean()) -sqrt(max(1f, speed * speed - nvx * nvx)) else sqrt(max(1f, speed * speed - nvx * nvx))
                    balls += Ball(b.x, b.y, nvx, nvy)
                }
            }
            return
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (over) { resetLevel(); started = true }
                else started = true
                paddleX = event.x.coerceIn(paddleW / 2, width - paddleW / 2)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                paddleX = event.x.coerceIn(paddleW / 2, width - paddleW / 2)
                return true
            }
        }
        return true
    }
}

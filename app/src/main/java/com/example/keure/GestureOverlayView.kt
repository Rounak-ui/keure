package com.example.keure

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.hypot

class GestureOverlayView(context: Context) : View(context) {

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
    }

    // ---- Hold (cursor-move) glass-glow effect ----
    private var holdActive = false
    private var holdX = 0f
    private var holdY = 0f
    private data class Ring(val startTime: Long)
    private val rings = mutableListOf<Ring>()
    private val ringDurationMs = 900L
    private val ringSpawnIntervalMs = 350L
    private var lastRingSpawn = 0L

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
    }

    fun startHold(x: Float, y: Float) {
        holdActive = true
        holdX = x; holdY = y
        rings.clear()
        lastRingSpawn = 0L
        postInvalidateOnAnimation()
    }

    fun updateHold(x: Float, y: Float) {
        holdX = x; holdY = y
    }

    fun stopHold() {
        holdActive = false
        rings.clear()
        invalidate()
    }

    // ---- Swipe streak effect ----
    private data class Streak(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val startTime: Long)
    private val streaks = mutableListOf<Streak>()
    private val streakDurationMs = 300L

    private val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun triggerSwipe(x: Float, y: Float, dx: Float, dy: Float) {
        val now = System.currentTimeMillis()
        val norm = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
        val ux = dx / norm
        val uy = dy / norm
        val len = 160f
        val ex = x + ux * len
        val ey = y + uy * len
        streaks.clear()
        streaks.add(Streak(x, y, ex, ey, now))
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()

        if (holdActive && now - lastRingSpawn > ringSpawnIntervalMs) {
            val progress = ringDurationMs.toFloat()
            val radius = 40f + progress * 120f
            ringPaint.alpha = ((1f - progress) * 90).toInt().coerceIn(0, 255)
            canvas.drawCircle(holdX, holdY, radius, ringPaint)
        }

        val ringIt = rings.iterator()
        while (ringIt.hasNext()) {
            val ring = ringIt.next()
            val elapsed = now - ring.startTime
            if (elapsed > ringDurationMs) { ringIt.remove(); continue }
            val progress = elapsed / ringDurationMs.toFloat()
            val radius = 12f + progress * 75f
            ringPaint.alpha = ((1f - progress) * 130).toInt().coerceIn(0, 255)
            ringPaint.strokeWidth = 6f * (1f - progress * 0.5f)
            canvas.drawCircle(holdX, holdY, radius, ringPaint)
        }

        val streakIt = streaks.iterator()
        while (streakIt.hasNext()) {
            val s = streakIt.next()
            val elapsed = now - s.startTime
            if (elapsed < 0) continue
            if (elapsed > streakDurationMs) { streakIt.remove(); continue }
            val progress = elapsed / streakDurationMs.toFloat()
            streakPaint.alpha = ((1f - progress) * 210).toInt().coerceIn(0, 255)
            streakPaint.strokeWidth = 3f
            canvas.drawLine(s.x1, s.y1, s.x2, s.y2, streakPaint)
        }

        if (holdActive || rings.isNotEmpty() || streaks.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }
}
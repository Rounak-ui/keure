package com.example.keure

import kotlin.math.abs

class GestureEngine(
    private val onSwipeLeft: () -> Unit,
    private val onSwipeRight: () -> Unit,
    private val onSwipeUp: () -> Unit,
    private val onSwipeDown: () -> Unit
) {
    private val swipeMinDistanceFraction = 0.20f
    private val swipeMaxVerticalDrift = 150f
    private val swipeMaxDuration = 700L

    private val swipeVerticalMinDistance = 100f
    private val swipeVerticalMaxHorizontalDrift = 60f

    enum class DetectedGesture { NONE, LEFT, RIGHT, UP, DOWN }

    private var pendingGesture = DetectedGesture.NONE

    /**
     * Evaluates the current drag vector and returns which gesture it matches,
     * if any. The caller decides whether to act on it (this lets panel mode
     * ignore LEFT/RIGHT/UP and only react to DOWN, for example).
     */
    fun detectGesture(dx: Float, dy: Float, elapsedMs: Long, keyboardWidth: Int): DetectedGesture {
        if (elapsedMs >= swipeMaxDuration) {
            pendingGesture = DetectedGesture.NONE
            return pendingGesture
        }

        val isMostlyVertical = abs(dy) > abs(dx)

        if (isMostlyVertical) {
            val isStraightEnough = abs(dx) < swipeVerticalMaxHorizontalDrift
            if (isStraightEnough) {
                if (dy < -swipeVerticalMinDistance) {
                    pendingGesture = DetectedGesture.UP
                    return pendingGesture
                }
                if (dy > swipeVerticalMinDistance) {
                    pendingGesture = DetectedGesture.DOWN
                    return pendingGesture
                }
            }
        } else {
            val minDistancePx = keyboardWidth * swipeMinDistanceFraction
            val isHorizontalEnough = abs(dy) < swipeMaxVerticalDrift
            val isFarEnough = abs(dx) > minDistancePx
            if (isHorizontalEnough && isFarEnough) {
                pendingGesture = if (dx < 0) DetectedGesture.LEFT else DetectedGesture.RIGHT
                return pendingGesture
            }
        }

        pendingGesture = DetectedGesture.NONE
        return pendingGesture
    }

    fun fireGesture() {
        when (pendingGesture) {
            DetectedGesture.LEFT -> onSwipeLeft()
            DetectedGesture.RIGHT -> onSwipeRight()
            DetectedGesture.UP -> onSwipeUp()
            DetectedGesture.DOWN -> onSwipeDown()
            DetectedGesture.NONE -> {}
        }
        pendingGesture = DetectedGesture.NONE
    }
}
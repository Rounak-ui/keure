package com.example.keure

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import kotlin.math.abs

class GestureAwareKeyboardLayout(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop.toFloat() }

    var gestureEngine: GestureEngine? = null
    var cursorController: CursorController? = null
    var inputConnectionProvider: (() -> InputConnection?)? = null
    var panelModeActive = false

    private var gestureFired = false
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var trackingPointerId = -1

    private val holdThresholdMs = 350L
    private val holdHandler = Handler(Looper.getMainLooper())
    private var holdRunnable: Runnable? = null
    private var cursorModeActive = false

    private val excludedFromCursorHold = mutableListOf<View>()

    fun addCursorHoldExclusion(view: View) {
        excludedFromCursorHold.add(view)
    }

    private fun isTouchOnExcludedView(touchX: Float, touchY: Float): Boolean {
        val parentLoc = IntArray(2)
        this.getLocationOnScreen(parentLoc)

        for (view in excludedFromCursorHold) {
            if (view.visibility != View.VISIBLE) continue
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            val left = loc[0] - parentLoc[0]
            val top = loc[1] - parentLoc[1]
            val right = left + view.width
            val bottom = top + view.height
            if (touchX >= left && touchX <= right && touchY >= top && touchY <= bottom) {
                return true
            }
        }
        return false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                trackingPointerId = ev.getPointerId(0)
                downX = ev.x
                downY = ev.y
                downTime = System.currentTimeMillis()
                gestureFired = false
                cursorModeActive = false

                val touchOnExcluded = isTouchOnExcludedView(downX, downY)

                if (!panelModeActive && !touchOnExcluded) {
                    val isLeftHalf = downX < width / 2f
                    holdRunnable = Runnable {
                        cursorModeActive = true
                        cursorController?.start(inputConnectionProvider?.invoke(), isLeftHalf)
                    }
                    holdHandler.postDelayed(holdRunnable!!, holdThresholdMs)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!panelModeActive) {
                    trackingPointerId = -1
                    cancelHold()
                    cursorController?.stop()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (!panelModeActive && cursorModeActive) return true

                if (trackingPointerId == -1) return false
                val pointerIndex = ev.findPointerIndex(trackingPointerId)
                if (pointerIndex == -1) return false
                if (ev.pointerCount > 1) {
                    trackingPointerId = -1
                    cancelHold()
                    return false
                }

                val dx = ev.getX(pointerIndex) - downX
                val dy = ev.getY(pointerIndex) - downY
                val elapsed = System.currentTimeMillis() - downTime

                if (!panelModeActive && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    cancelHold()
                }

                val detected = gestureEngine?.detectGesture(dx, dy, elapsed, width)
                    ?: GestureEngine.DetectedGesture.NONE

                if (panelModeActive) {
                    if (detected == GestureEngine.DetectedGesture.DOWN) {
                        gestureFired = true
                        return true
                    }
                    return false
                } else {
                    if (detected != GestureEngine.DetectedGesture.NONE &&
                        detected != GestureEngine.DetectedGesture.DOWN
                    ) {
                        gestureFired = true
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                trackingPointerId = -1
                cancelHold()
                if (!panelModeActive && cursorModeActive) {
                    cursorController?.stop()
                    cursorModeActive = false
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelHold()
                if (!panelModeActive && cursorModeActive) {
                    cursorController?.stop()
                    cursorModeActive = false
                }
            }
        }

        if (gestureFired) {
            gestureEngine?.fireGesture()
            gestureFired = false
        }
        return true
    }

    private fun cancelHold() {
        holdRunnable?.let { holdHandler.removeCallbacks(it) }
        holdRunnable = null
    }
}
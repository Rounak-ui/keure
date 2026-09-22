package com.example.keure

import android.os.Handler
import android.os.Looper
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

class CursorController(
    private val repeatIntervalMs: Long = 50L
) {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var moveLeft = true

    fun start(inputConnection: InputConnection?, moveLeft: Boolean) {
        stop()
        this.moveLeft = moveLeft

        runnable = object : Runnable {
            override fun run() {
                step(inputConnection)
                handler.postDelayed(this, repeatIntervalMs)
            }
        }
        handler.post(runnable!!)
    }

    fun stop() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    private fun step(inputConnection: InputConnection?) {
        val ic = inputConnection ?: return
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        val cursorPos = extracted.selectionStart
        val textLength = extracted.text?.length ?: 0

        if (moveLeft) {
            if (cursorPos > 0) {
                ic.setSelection(cursorPos - 1, cursorPos - 1)
            }
        } else {
            if (cursorPos < textLength) {
                ic.setSelection(cursorPos + 1, cursorPos + 1)
            }
        }
    }
}
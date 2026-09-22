package com.example.keure

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

sealed class EditOperation {
    data class Insert(
        var text: String,
        val position: Int
    ) : EditOperation()

    data class Delete(
        var text: String,       // the text that was deleted, needed to restore it
        val position: Int       // where it was, so we know where to re-insert it
    ) : EditOperation()
}

private enum class CharClass { LETTER_OR_DIGIT, SPACE, OTHER }

class UndoManager(private val historyLimit: Int = 50) {

    private val history = ArrayDeque<EditOperation>()
    private var lastUndoTime = 0L
    private val undoCooldownMs = 300L

    private fun classify(c: Char): CharClass = when {
        c.isLetterOrDigit() -> CharClass.LETTER_OR_DIGIT
        c == ' ' -> CharClass.SPACE
        else -> CharClass.OTHER
    }

    fun recordInsert(char: String, position: Int) {
        if (char.length != 1) {
            pushNewOp(EditOperation.Insert(char, position))
            return
        }

        val newCharClass = classify(char[0])
        val lastOp = history.lastOrNull() as? EditOperation.Insert

        val isContiguous = lastOp != null && (lastOp.position + lastOp.text.length == position)
        val sameClass = lastOp != null && lastOp.text.isNotEmpty() &&
                classify(lastOp.text.last()) == newCharClass

        if (lastOp != null && isContiguous && sameClass) {
            lastOp.text += char
        } else {
            pushNewOp(EditOperation.Insert(char, position))
        }
    }

    /**
     * Records a deleted chunk (either one backspaced character, or an
     * entire deleted selection). Consecutive single-char backspaces at
     * contiguous positions get grouped into one block, same spirit as
     * recordInsert's grouping.
     */
    fun recordDelete(deletedText: String, positionAfterDelete: Int) {
        val lastOp = history.lastOrNull() as? EditOperation.Delete

        // Contiguous backspacing: each new deleted block sits immediately
        // BEFORE the start of the previous deleted block.
        val isContiguous = lastOp != null && (positionAfterDelete == lastOp.position)

        if (lastOp != null && isContiguous) {
            lastOp.text = deletedText + lastOp.text
        } else {
            pushNewOp(EditOperation.Delete(deletedText, positionAfterDelete))
        }
    }

    private fun pushNewOp(op: EditOperation) {
        if (history.size >= historyLimit) {
            history.removeFirst()
        }
        history.addLast(op)
    }

    fun undo(inputConnection: InputConnection?) {
        if (inputConnection == null) return

        val now = System.currentTimeMillis()
        if (now - lastUndoTime < undoCooldownMs) return

        val lastOp = history.removeLastOrNull() ?: return

        when (lastOp) {
            is EditOperation.Insert -> undoInsert(inputConnection, lastOp, now)
            is EditOperation.Delete -> undoDelete(inputConnection, lastOp, now)
        }
    }

    private fun undoInsert(ic: InputConnection, op: EditOperation.Insert, now: Long) {
        val expectedText = op.text
        val startPos = op.position
        val endPos = startPos + expectedText.length

        ic.beginBatchEdit()
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        val fullText = extracted?.text?.toString() ?: ""

        val safeToUndo = fullText.length >= endPos &&
                fullText.substring(startPos, endPos) == expectedText

        if (safeToUndo) {
            ic.setSelection(endPos, endPos)
            ic.deleteSurroundingText(expectedText.length, 0)
            lastUndoTime = now
        } else {
            history.addLast(op) // couldn't verify - put it back, don't lose it
        }
        ic.endBatchEdit()
    }

    private fun undoDelete(ic: InputConnection, op: EditOperation.Delete, now: Long) {
        ic.beginBatchEdit()
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        val currentCursor = extracted?.selectionStart ?: -1

        // Safety check: cursor should currently be exactly where the delete left it
        val safeToUndo = currentCursor == op.position

        if (safeToUndo) {
            ic.setSelection(op.position, op.position)
            ic.commitText(op.text, 1)
            lastUndoTime = now
        } else {
            history.addLast(op)
        }
        ic.endBatchEdit()
    }

    fun clear() {
        history.clear()
    }
}
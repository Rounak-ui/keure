package com.example.keure

import android.annotation.SuppressLint
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.app.AlertDialog
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.viewpager2.widget.ViewPager2

class KeureKeyboardService : InputMethodService() {

    private var isGlowActive = false
    private var isShiftActive = false
    private val undoManager = UndoManager()
    private val cursorController = CursorController()
    private var currentFieldId: Int = -1
    private var enterButtonRef: ImageButton? = null
    private var currentEnterAction: Int = EditorInfo.IME_ACTION_NONE

    private var isEmojiPanelActive = false

    private val greekAndCurrencyKeyIds = listOf(
        R.id.key_sym_alpha, R.id.key_sym_beta, R.id.key_sym_gamma, R.id.key_sym_theta,
        R.id.key_sym_lambda, R.id.key_sym_sigma, R.id.key_sym_omega,
        R.id.key_sym_rupee
    )
    private val letterKeyIds = listOf(
        R.id.key_q, R.id.key_w, R.id.key_e, R.id.key_r, R.id.key_t,
        R.id.key_y, R.id.key_u, R.id.key_i, R.id.key_o, R.id.key_p,
        R.id.key_a, R.id.key_s, R.id.key_d, R.id.key_f, R.id.key_g,
        R.id.key_h, R.id.key_j, R.id.key_k, R.id.key_l,
        R.id.key_z, R.id.key_x, R.id.key_c, R.id.key_v, R.id.key_b,
        R.id.key_n, R.id.key_m
    )

    private val numberAndPunctuationKeyIds = listOf(
        R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4, R.id.key_5,
        R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9, R.id.key_0,
        R.id.key_comma, R.id.key_period
    )

    private val symbolKeyIds = listOf(
        R.id.key_sym_at, R.id.key_sym_hash, R.id.key_sym_dollar, R.id.key_sym_underscore,
        R.id.key_sym_amp, R.id.key_sym_dash, R.id.key_sym_plus, R.id.key_sym_lparen,
        R.id.key_sym_rparen, R.id.key_sym_slash, R.id.key_sym_star, R.id.key_sym_quote,
        R.id.key_sym_apos, R.id.key_sym_colon, R.id.key_sym_semicolon, R.id.key_sym_exclaim,
        R.id.key_sym_question
    )
    private enum class KeyboardLayer { LETTERS, SYMBOLS_1, SYMBOLS_2 }
    private var currentLayer = KeyboardLayer.LETTERS
    private var cachedLettersHeight = 0

    private val symbolPage2KeyIds = listOf(
        R.id.key_sym2_tilde, R.id.key_sym2_backtick, R.id.key_sym2_pipe, R.id.key_sym2_dot,
        R.id.key_sym2_sqrt, R.id.key_sym2_pi, R.id.key_sym2_divide, R.id.key_sym2_multiply,
        R.id.key_sym2_section, R.id.key_sym2_triangle,
        R.id.key_sym2_pound, R.id.key_sym2_cent, R.id.key_sym2_euro, R.id.key_sym2_yen,
        R.id.key_sym2_caret, R.id.key_sym2_degree, R.id.key_sym2_equals, R.id.key_sym2_lbrace,
        R.id.key_sym2_rbrace, R.id.key_sym2_backslash,
        R.id.key_sym2_percent, R.id.key_sym2_copyright, R.id.key_sym2_registered,
        R.id.key_sym2_tm, R.id.key_sym2_check, R.id.key_sym2_lbracket, R.id.key_sym2_rbracket
    )

    // Backspace hold-to-repeat state
    private val backspaceHandler = Handler(Looper.getMainLooper())
    private var backspaceRunnable: Runnable? = null
    private val backspaceInitialDelayMs = 400L
    private val backspaceRepeatIntervalMs = 60L

    // Spacebar hold-to-home state
    private val spacebarHomeHandler = Handler(Looper.getMainLooper())
    private var spacebarHomeRunnable: Runnable? = null
    private val spacebarHoldThresholdMs = 350L
    private var spacebarHoldTriggered = false

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateInputView(): View {
        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as GestureAwareKeyboardLayout

        // Letter keys
        for (id in letterKeyIds) {
            val button = keyboardView.findViewById<Button>(id)
            button.setOnClickListener {
                val char = if (isShiftActive) {
                    button.text.toString().uppercase()
                } else {
                    button.text.toString().lowercase()
                }
                typeAndRecord(char)
                if (isShiftActive) {
                    isShiftActive = false
                    updateShiftVisual(keyboardView)
                }
            }
        }

        // Number + comma/period keys
        for (id in numberAndPunctuationKeyIds) {
            val button = keyboardView.findViewById<Button>(id)
            button.setOnClickListener { typeAndRecord(button.text.toString()) }
        }

        // Symbol panel keys
        for (id in symbolKeyIds) {
            val button = keyboardView.findViewById<Button>(id)
            button.setOnClickListener { typeAndRecord(button.text.toString()) }
        }
        for (id in greekAndCurrencyKeyIds) {
            val button = keyboardView.findViewById<Button>(id)
            button.setOnClickListener { typeAndRecord(button.text.toString()) }
        }
        for (id in symbolPage2KeyIds) {
            val button = keyboardView.findViewById<Button>(id)
            button.setOnClickListener { typeAndRecord(button.text.toString()) }
        }

        // Emoji/GIF/Sticker pager (single setup block - no duplicates)
        val emojiPager = keyboardView.findViewById<ViewPager2>(R.id.emoji_pager)
        val emojiPageLabel = keyboardView.findViewById<TextView>(R.id.emoji_page_label)

        val dot1 = keyboardView.findViewById<View>(R.id.dot1)
        val dot2 = keyboardView.findViewById<View>(R.id.dot2)
        val dot3 = keyboardView.findViewById<View>(R.id.dot3)

        fun setCurrentPage(page: Int) {
            dot1.setBackgroundResource(if (page == 0) R.drawable.dot_active else R.drawable.dot_inactive)
            dot2.setBackgroundResource(if (page == 1) R.drawable.dot_active else R.drawable.dot_inactive)
            dot3.setBackgroundResource(if (page == 2) R.drawable.dot_active else R.drawable.dot_inactive)
        }

        emojiPager.adapter = KeyboardPagerAdapter(
            EmojiData.loadCategories(this),
            onEmojiClick = { emoji -> typeAndRecord(emoji) },
            onGifClick = { gif -> insertGif(gif, keyboardView) }
        )

        emojiPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                emojiPageLabel.text = when (position) {
                    0 -> "Emoji"
                    1 -> "GIFs"
                    else -> "Stickers"
                }
                setCurrentPage(position)
            }
        })

        setCurrentPage(0) // initial state when panel first opens

        keyboardView.findViewById<Button>(R.id.key_emoji_back).setOnClickListener {
            closeEmojiPanel(keyboardView)
        }

        keyboardView.findViewById<ImageButton>(R.id.key_emoji_close).setOnClickListener {
            closeEmojiPanel(keyboardView)
        }

        setupBackspaceButton(keyboardView.findViewById(R.id.key_emoji_backspace))
        setupBackspaceButton(keyboardView.findViewById(R.id.key_sym2_backspace))

        val sym2ToPage1Button = keyboardView.findViewById<Button>(R.id.key_sym2_toPage1)
        sym2ToPage1Button.setOnClickListener {
            currentLayer = KeyboardLayer.SYMBOLS_1
            applyLayerVisibility(keyboardView)
        }

        setupBackspaceButton(keyboardView.findViewById(R.id.key_sym_backspace))

        val spaceButton = keyboardView.findViewById<Button>(R.id.key_space)
        setupSpacebarHoldToHome(spaceButton, keyboardView)
        keyboardView.addCursorHoldExclusion(spaceButton)

        val shiftButton = keyboardView.findViewById<Button>(R.id.key_shift)
        shiftButton.setOnClickListener {
            isShiftActive = !isShiftActive
            updateShiftVisual(keyboardView)
        }

        // IMPORTANT: backspace uses setOnTouchListener ONLY (tap + hold-to-repeat).
        // Do not also add setOnClickListener for backspace - that would double-delete.
        val backspaceButton = keyboardView.findViewById<Button>(R.id.key_backspace)
        setupBackspaceButton(backspaceButton)
        keyboardView.addCursorHoldExclusion(backspaceButton)

        val enterButton = keyboardView.findViewById<ImageButton>(R.id.key_enter)
        enterButtonRef = enterButton
        enterButton.setOnClickListener { performEnterAction() }

        // ?123 / ABC toggle button
        val symbolsToggleButton = keyboardView.findViewById<Button>(R.id.key_symbols)
        symbolsToggleButton.setOnClickListener {
            when (currentLayer) {
                KeyboardLayer.LETTERS -> cycleLayerForward(keyboardView)
                KeyboardLayer.SYMBOLS_1 -> { currentLayer = KeyboardLayer.LETTERS; applyLayerVisibility(keyboardView) }
                KeyboardLayer.SYMBOLS_2 -> { currentLayer = KeyboardLayer.SYMBOLS_1; applyLayerVisibility(keyboardView) }
            }
        }

        val symPage2Button = keyboardView.findViewById<Button>(R.id.key_sym_page2)
        symPage2Button.setOnClickListener {
            currentLayer = KeyboardLayer.SYMBOLS_2
            applyLayerVisibility(keyboardView)
        }

        val emojiButton = keyboardView.findViewById<ImageButton>(R.id.key_emoji)
        emojiButton.setOnClickListener {
            when (currentLayer) {
                KeyboardLayer.LETTERS -> { openEmojiPanel(keyboardView) }
                KeyboardLayer.SYMBOLS_1 -> cycleLayerForward(keyboardView)
                KeyboardLayer.SYMBOLS_2 -> { currentLayer = KeyboardLayer.LETTERS; applyLayerVisibility(keyboardView) }
            }
        }

        val brightnessButton = keyboardView.findViewById<Button>(R.id.key_sym_brightness)
        brightnessButton.setOnClickListener { toggleGlow(keyboardView) }

        val muteButton = keyboardView.findViewById<Button>(R.id.key_sym_mute)
        muteButton.setOnClickListener { toggleMute() }

        // Gesture engine: left = undo, right = cycle layers, up = open pager, down = close pager
        keyboardView.gestureEngine = GestureEngine(
            onSwipeLeft = { cycleLayerBackward(keyboardView) },
            onSwipeRight = { cycleLayerForward(keyboardView) },
            onSwipeUp = { openEmojiPanel(keyboardView) },
            onSwipeDown = { closeEmojiPanel(keyboardView) },
            onDiagonalUndo = { undoManager.undo(currentInputConnection) }
        )

        keyboardView.cursorController = cursorController
        keyboardView.inputConnectionProvider = { currentInputConnection }

        val overlay = GestureOverlayView(this)
        keyboardView.gestureOverlay = overlay

        val frame = android.widget.FrameLayout(this)
        frame.addView(
            keyboardView,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        val overlayParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        frame.addView(overlay, overlayParams)
        overlay.bringToFront()

        keyboardView.viewTreeObserver.addOnGlobalLayoutListener {
            val h = keyboardView.height
            if (h > 0 && overlay.layoutParams.height != h) {
                overlay.layoutParams = overlay.layoutParams.apply { height = h }
                overlay.requestLayout()
            }
        }

        applyLayerVisibility(keyboardView)
        updateSuggestionBar(keyboardView)
        return frame
    }

    private fun cycleLayerForward(keyboardView: GestureAwareKeyboardLayout) {
        currentLayer = when (currentLayer) {
            KeyboardLayer.LETTERS -> {
                val lettersContainer = keyboardView.findViewById<LinearLayout>(R.id.letters_container)
                if (lettersContainer.height > 0) cachedLettersHeight = lettersContainer.height
                KeyboardLayer.SYMBOLS_1
            }
            KeyboardLayer.SYMBOLS_1 -> KeyboardLayer.SYMBOLS_2
            KeyboardLayer.SYMBOLS_2 -> KeyboardLayer.LETTERS
        }
        applyLayerVisibility(keyboardView)
    }

    private fun cycleLayerBackward(keyboardView: GestureAwareKeyboardLayout) {
        currentLayer = when (currentLayer) {
            KeyboardLayer.LETTERS -> {
                val lettersContainer = keyboardView.findViewById<LinearLayout>(R.id.letters_container)
                if (lettersContainer.height > 0) cachedLettersHeight = lettersContainer.height
                KeyboardLayer.SYMBOLS_2
            }
            KeyboardLayer.SYMBOLS_2 -> KeyboardLayer.SYMBOLS_1
            KeyboardLayer.SYMBOLS_1 -> KeyboardLayer.LETTERS
        }
        applyLayerVisibility(keyboardView)
    }

    private fun applyLayerVisibility(keyboardView: GestureAwareKeyboardLayout) {
        val lettersContainer = keyboardView.findViewById<LinearLayout>(R.id.letters_container)
        val symbolsContainer = keyboardView.findViewById<LinearLayout>(R.id.symbols_container)
        val symbols2Container = keyboardView.findViewById<LinearLayout>(R.id.symbols_page2_container)

        lettersContainer.visibility = if (currentLayer == KeyboardLayer.LETTERS) View.VISIBLE else View.GONE
        symbolsContainer.visibility = if (currentLayer == KeyboardLayer.SYMBOLS_1) View.VISIBLE else View.GONE
        symbols2Container.visibility = if (currentLayer == KeyboardLayer.SYMBOLS_2) View.VISIBLE else View.GONE

        if (cachedLettersHeight > 0) {
            if (currentLayer == KeyboardLayer.SYMBOLS_1) {
                symbolsContainer.layoutParams = symbolsContainer.layoutParams.apply { height = cachedLettersHeight }
            }
            if (currentLayer == KeyboardLayer.SYMBOLS_2) {
                symbols2Container.layoutParams = symbols2Container.layoutParams.apply { height = cachedLettersHeight }
            }
        }

        val symbolsToggleButton = keyboardView.findViewById<Button>(R.id.key_symbols)
        val emojiButton = keyboardView.findViewById<ImageButton>(R.id.key_emoji)
        val commaButton = keyboardView.findViewById<Button>(R.id.key_comma)
        val periodButton = keyboardView.findViewById<Button>(R.id.key_period)

        when (currentLayer) {
            KeyboardLayer.LETTERS -> {
                symbolsToggleButton.text = "123"
                emojiButton.setImageResource(R.drawable.emoji)
                commaButton.text = ","
                periodButton.text = "."
            }
            KeyboardLayer.SYMBOLS_1 -> {
                symbolsToggleButton.text = "ABC"
                emojiButton.setImageResource(R.drawable.ic_abc)
                commaButton.text = ","
                periodButton.text = "."
            }
            KeyboardLayer.SYMBOLS_2 -> {
                symbolsToggleButton.text = "?123"
                emojiButton.setImageResource(R.drawable.ic_123)
                commaButton.text = "<"
                periodButton.text = ">"
            }
        }
    }

    private fun updateShiftVisual(keyboardView: GestureAwareKeyboardLayout) {
        val shiftButton = keyboardView.findViewById<Button>(R.id.key_shift)
        shiftButton.setBackgroundColor(
            if (isShiftActive) Color.LTGRAY else Color.DKGRAY
        )
        for (id in letterKeyIds) {
            val button = keyboardView.findViewById<Button>(id)
            button.text = if (isShiftActive) button.text.toString().uppercase() else button.text.toString().lowercase()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBackspaceButton(button: Button) {
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    deleteAndRecord()

                    backspaceRunnable = object : Runnable {
                        override fun run() {
                            deleteAndRecord()
                            backspaceHandler.postDelayed(this, backspaceRepeatIntervalMs)
                        }
                    }
                    backspaceHandler.postDelayed(backspaceRunnable!!, backspaceInitialDelayMs)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    backspaceRunnable?.let { backspaceHandler.removeCallbacks(it) }
                    backspaceRunnable = null
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSpacebarHoldToHome(button: Button, keyboardView: GestureAwareKeyboardLayout) {
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    spacebarHoldTriggered = false
                    spacebarHomeRunnable = Runnable {
                        spacebarHoldTriggered = true
                        goHome(keyboardView)
                    }
                    spacebarHomeHandler.postDelayed(spacebarHomeRunnable!!, spacebarHoldThresholdMs)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    spacebarHomeRunnable?.let { spacebarHomeHandler.removeCallbacks(it) }
                    if (!spacebarHoldTriggered) {
                        typeAndRecord(" ")
                    }
                    v.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    spacebarHomeRunnable?.let { spacebarHomeHandler.removeCallbacks(it) }
                    true
                }
                else -> false
            }
        }
    }

    private fun goHome(keyboardView: GestureAwareKeyboardLayout) {
        if (isEmojiPanelActive) return
        currentLayer = KeyboardLayer.LETTERS
        applyLayerVisibility(keyboardView)
    }

    private fun loadShortcuts(): MutableList<String> {
        val prefs = getSharedPreferences("keure_prefs", MODE_PRIVATE)
        val raw = prefs.getString("shortcut_words", "") ?: ""
        return if (raw.isEmpty()) mutableListOf() else raw.split("\u0001").toMutableList()
    }

    private fun saveShortcuts(list: List<String>) {
        val prefs = getSharedPreferences("keure_prefs", MODE_PRIVATE)
        prefs.edit().putString("shortcut_words", list.joinToString("\u0001")).apply()
    }

    private fun addShortcut(word: String) {
        val list = loadShortcuts()
        list.add(word)
        while (list.size > 3) {
            list.removeAt(0)
        }
        saveShortcuts(list)
    }

    private fun insertShortcut(word: String) {
        val ic = currentInputConnection ?: return
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        val cursorPos = extracted?.selectionStart ?: 0
        ic.commitText(word, 1)
        undoManager.recordInsert(word, cursorPos)
    }

    private fun updateSuggestionBar(keyboardView: GestureAwareKeyboardLayout) {
        val words = loadShortcuts()
        val slots = listOf(
            keyboardView.findViewById<TextView>(R.id.suggestion_1),
            keyboardView.findViewById<TextView>(R.id.suggestion_2),
            keyboardView.findViewById<TextView>(R.id.suggestion_3)
        )

        for (i in slots.indices) {
            val slot = slots[i]
            val word = words.getOrNull(i)
            if (word != null) {
                slot.text = word
                slot.setOnClickListener { insertShortcut(word) }
            } else {
                slot.text = "+"
                slot.setOnClickListener { showAddShortcutDialog(keyboardView) }
            }
        }
    }

    private fun showAddShortcutDialog(keyboardView: GestureAwareKeyboardLayout) {
        val editText = EditText(this)
        editText.hint = "Word or phrase"

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add quick word")
            .setView(editText)
            .setPositiveButton("Add") { _, _ ->
                val newWord = editText.text.toString().trim()
                if (newWord.isNotEmpty()) {
                    addShortcut(newWord)
                    updateSuggestionBar(keyboardView)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
        dialog.window?.attributes?.token = keyboardView.windowToken
        dialog.show()
    }

    private fun performEnterAction() {
        val ic = currentInputConnection ?: return

        if (currentEnterAction == EditorInfo.IME_ACTION_SEARCH) {
            ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
        } else {
            ic.commitText("\n", 1)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val newFieldId = info?.fieldId ?: -1
        if (newFieldId != currentFieldId) {
            undoManager.clear()
            currentFieldId = newFieldId
        }

        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        currentEnterAction = action

        if (action == EditorInfo.IME_ACTION_SEARCH) {
            enterButtonRef?.setImageResource(R.drawable.ic_search)
        } else {
            enterButtonRef?.setImageResource(R.drawable.keyboard_return)
        }
    }

    private fun typeAndRecord(char: String) {
        val ic = currentInputConnection ?: return
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        val cursorPos = extracted?.selectionStart ?: 0
        ic.commitText(char, 1)
        undoManager.recordInsert(char, cursorPos)
    }

    private fun deleteAndRecord() {
        val ic = currentInputConnection ?: return
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        val fullText = extracted.text?.toString() ?: return
        val selStart = extracted.selectionStart
        val selEnd = extracted.selectionEnd

        if (selStart != selEnd) {
            val from = minOf(selStart, selEnd)
            val to = maxOf(selStart, selEnd)
            if (from < 0 || to > fullText.length) return
            val deletedText = fullText.substring(from, to)

            ic.setSelection(to, to)
            ic.deleteSurroundingText(to - from, 0)
            undoManager.recordDelete(deletedText, from)
            return
        }

        val cursorPos = selStart
        if (cursorPos <= 0 || cursorPos > fullText.length) return

        val deletedChar = fullText[cursorPos - 1].toString()
        ic.deleteSurroundingText(1, 0)
        undoManager.recordDelete(deletedChar, cursorPos - 1)
    }

    private fun toggleGlow(keyboardView: GestureAwareKeyboardLayout) {
        isGlowActive = !isGlowActive

        val tintColor = if (isGlowActive) Color.parseColor("#7A7A7A") else Color.parseColor("#4A4A4A")
        val allTypingKeyIds = letterKeyIds + numberAndPunctuationKeyIds + symbolKeyIds +
                symbolPage2KeyIds + greekAndCurrencyKeyIds

        for (id in allTypingKeyIds) {
            val button = keyboardView.findViewById<Button>(id)
            button.background?.mutate()?.colorFilter =
                PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
        }

        keyboardView.setBackgroundColor(
            if (isGlowActive) Color.parseColor("#3A3A3A") else Color.parseColor("#212121")
        )
    }

    private fun toggleMute() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_TOGGLE_MUTE,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun openEmojiPanel(keyboardView: GestureAwareKeyboardLayout) {
        isEmojiPanelActive = true
        keyboardView.panelModeActive = true

        val normalHeight = keyboardView.height

        keyboardView.findViewById<View>(R.id.suggestion_bar).visibility = View.GONE
        keyboardView.findViewById<View>(R.id.number_row).visibility = View.GONE
        keyboardView.findViewById<View>(R.id.letters_container).visibility = View.GONE
        keyboardView.findViewById<View>(R.id.symbols_container).visibility = View.GONE
        keyboardView.findViewById<View>(R.id.symbols_page2_container).visibility = View.GONE
        keyboardView.findViewById<View>(R.id.bottom_row).visibility = View.GONE

        keyboardView.findViewById<View>(R.id.emoji_container).visibility = View.VISIBLE

        val emojiPager = keyboardView.findViewById<ViewPager2>(R.id.emoji_pager)
        if (normalHeight > 0) {
            emojiPager.layoutParams = emojiPager.layoutParams.apply {
                height = normalHeight
            }
        }
    }

    private fun closeEmojiPanel(keyboardView: GestureAwareKeyboardLayout) {
        isEmojiPanelActive = false
        keyboardView.panelModeActive = false

        keyboardView.findViewById<View>(R.id.emoji_container).visibility = View.GONE

        keyboardView.findViewById<View>(R.id.suggestion_bar).visibility = View.VISIBLE
        keyboardView.findViewById<View>(R.id.number_row).visibility = View.VISIBLE
        keyboardView.findViewById<View>(R.id.bottom_row).visibility = View.VISIBLE

        applyLayerVisibility(keyboardView)
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun insertGif(gif: GifResult, keyboardView: GestureAwareKeyboardLayout) {
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo

        val supportedMimeTypes = editorInfo?.contentMimeTypes
        val supportsGif = supportedMimeTypes?.any {
            android.content.ClipDescription.compareMimeTypes(it, "image/gif")
        } == true

        if (!supportsGif) {
            showUnsupportedDialog(keyboardView)
            return
        }

        // Download the GIF to local cache on a background thread, then insert on the main thread
        Thread {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(gif.insertUrl).build()
                val response = client.newCall(request).execute()
                val bytes = response.body?.bytes()

                if (bytes == null) {
                    mainHandlerForGif.post { showUnsupportedDialog(keyboardView) }
                    return@Thread
                }

                val gifDir = java.io.File(cacheDir, "gifs")
                if (!gifDir.exists()) gifDir.mkdirs()
                val file = java.io.File(gifDir, "shared_${System.currentTimeMillis()}.gif")
                file.writeBytes(bytes)

                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    this, "com.example.keure.fileprovider", file
                )

                mainHandlerForGif.post {
                    try {
                        val description = android.content.ClipDescription("GIF", arrayOf("image/gif"))
                        val inputContent = androidx.core.view.inputmethod.InputContentInfoCompat(
                            contentUri, description, null
                        )
                        val flags = androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                        val success = androidx.core.view.inputmethod.InputConnectionCompat.commitContent(
                            ic, editorInfo!!, inputContent, flags, null
                        )
                        if (!success) showUnsupportedDialog(keyboardView)
                    } catch (e: Exception) {
                        android.util.Log.e("KeureGif", "commitContent failed after download", e)
                        showUnsupportedDialog(keyboardView)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("KeureGif", "GIF download failed", e)
                mainHandlerForGif.post { showUnsupportedDialog(keyboardView) }
            }
        }.start()
    }

    private val mainHandlerForGif = Handler(Looper.getMainLooper())

    private fun showUnsupportedDialog(keyboardView: GestureAwareKeyboardLayout) {
        val dialog = AlertDialog.Builder(this)
            .setMessage("This app doesn't support GIFs or stickers from the keyboard.")
            .setPositiveButton("OK", null)
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
        dialog.window?.attributes?.token = keyboardView.windowToken
        dialog.show()
    }
}
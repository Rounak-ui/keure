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
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import android.text.InputType
import android.text.TextUtils
import android.view.inputmethod.InputConnection
import android.widget.Toast
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan

class KeureKeyboardService : InputMethodService() {

    // ---- Emoji / GIF / sticker search ----
    private var searchTitleView: TextView? = null
    private var searchActive = false
    private var lastSubmittedQuery = ""
    private val searchBuffer = StringBuilder()
    private var currentPagerPage = 0
    private var pagerAdapter: KeyboardPagerAdapter? = null
    private var emojiPanelHeight = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---- Suggestion bar / word learning ----
    private lateinit var predictor: WordPredictor
    private var suggestionsEnabled = true
    private val suggestHandler = Handler(Looper.getMainLooper())
    private var suggestRunnable: Runnable? = null

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

    // ---- Translator state ----
    private var keyboardViewRef: GestureAwareKeyboardLayout? = null
    private var translatorActive = false
    private val translatorBuffer = StringBuilder()
    private var translator: Translator? = null
    private var translatorReady = false
    private var translatorStatus: String? = null
    private var srcLang = TranslateLanguage.ENGLISH
    private var dstLang = TranslateLanguage.HINDI
    private val translateHandler = Handler(Looper.getMainLooper())
    private var translateRunnable: Runnable? = null
    private val modelManager by lazy { RemoteModelManager.getInstance() }

    private val translateLanguages = linkedMapOf(
        "English" to TranslateLanguage.ENGLISH,
        "Hindi" to TranslateLanguage.HINDI,
        "Spanish" to TranslateLanguage.SPANISH,
        "French" to TranslateLanguage.FRENCH,
        "German" to TranslateLanguage.GERMAN,
        "Arabic" to TranslateLanguage.ARABIC,
        "Bengali" to TranslateLanguage.BENGALI,
        "Tamil" to TranslateLanguage.TAMIL,
        "Telugu" to TranslateLanguage.TELUGU,
        "Marathi" to TranslateLanguage.MARATHI,
        "Gujarati" to TranslateLanguage.GUJARATI,
        "Urdu" to TranslateLanguage.URDU,
        "Japanese" to TranslateLanguage.JAPANESE,
        "Korean" to TranslateLanguage.KOREAN,
        "Chinese" to TranslateLanguage.CHINESE,
        "Russian" to TranslateLanguage.RUSSIAN,
        "Portuguese" to TranslateLanguage.PORTUGUESE
    )

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        predictor = WordPredictor(this)
        predictor.load()
    }

    override fun onDestroy() {
        predictor.saveNow()
        super.onDestroy()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        predictor.saveNow()
    }

    // Fires on typing, deleting, cursor moves, undo and field changes
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        scheduleSuggestions()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        if (translatorActive) {
            keyboardViewRef?.let { closeTranslator(it) }
        }
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

        suggestionsEnabled = computeSuggestionsEnabled(info)
        scheduleSuggestions()
    }

    // ------------------------------------------------------------------
    // View setup
    // ------------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateInputView(): View {
        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as GestureAwareKeyboardLayout

        // Put the emoji/search container above the number row (so search sits on top of the keys)
        val emojiContainerView = keyboardView.findViewById<View>(R.id.emoji_container)
        keyboardView.removeView(emojiContainerView)
        keyboardView.addView(
            emojiContainerView,
            keyboardView.indexOfChild(keyboardView.findViewById(R.id.number_row))
        )

        keyboardView.findViewById<ImageButton>(R.id.key_menu_grid).setOnClickListener {
            showAddShortcutDialog(keyboardView)
        }

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

        // Emoji/GIF/Sticker pager
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

        val adapter = KeyboardPagerAdapter(
            EmojiData.loadCategories(this),
            onEmojiClick = { emoji -> typeAndRecord(emoji) },
            onMediaClick = { item -> insertGif(item, keyboardView) }
        )
        pagerAdapter = adapter
        emojiPager.adapter = adapter
        currentPagerPage = 0

        // let long queries scroll inside the pill instead of pushing it wider
        emojiPageLabel.layoutParams = (emojiPageLabel.layoutParams as LinearLayout.LayoutParams).apply {
            width = 0
            weight = 1f
        }
        emojiPageLabel.isSingleLine = true
        emojiPageLabel.ellipsize = TextUtils.TruncateAt.START
        updateSearchLabel(keyboardView)

        // Tapping the pill starts search mode
        val searchPillView = keyboardView.findViewById<View>(R.id.search_pill)
        searchPillView.setOnClickListener {
            enterSearchMode(keyboardView)
        }

        // "Search KLIPY" title: shown INSTEAD of the pill while searching,
        // so there is only one search field on screen (the card's input row)
        val searchHeader = searchPillView.parent as LinearLayout
        searchTitleView = TextView(this).apply {
            text = "Search KLIPY"
            setTextColor(Color.WHITE)
            textSize = 20f
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(14) }
        }
        searchHeader.addView(searchTitleView, searchHeader.indexOfChild(searchPillView) + 1)

        emojiPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPagerPage = position
                setCurrentPage(position)
                if (position == 0 && searchActive) exitSearchMode(keyboardView, clear = true)
                pagerAdapter?.onPageShown(position)
                updateSearchLabel(keyboardView)
            }
        })

        setCurrentPage(0)

        keyboardView.findViewById<Button>(R.id.key_emoji_back).setOnClickListener {
            closeEmojiPanel(keyboardView)
        }

        keyboardView.findViewById<ImageButton>(R.id.key_emoji_close).setOnClickListener {
            if (searchActive) exitSearchMode(keyboardView) else closeEmojiPanel(keyboardView)
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
                KeyboardLayer.SYMBOLS_1 -> cycleLayerForward(keyboardView)
                KeyboardLayer.SYMBOLS_2 -> { currentLayer = KeyboardLayer.LETTERS; applyLayerVisibility(keyboardView) }
                KeyboardLayer.LETTERS -> {
                    if (searchActive) exitSearchMode(keyboardView) else openEmojiPanel(keyboardView)
                }
            }
        }

        val brightnessButton = keyboardView.findViewById<Button>(R.id.key_sym_brightness)
        brightnessButton.setOnClickListener { toggleGlow(keyboardView) }

        val muteButton = keyboardView.findViewById<Button>(R.id.key_sym_mute)
        muteButton.setOnClickListener { toggleMute() }

        // Translator bar wiring
        keyboardViewRef = keyboardView

        keyboardView.findViewById<ImageButton>(R.id.key_translate_back).setOnClickListener {
            closeTranslator(keyboardView)
        }
        keyboardView.findViewById<TextView>(R.id.translate_src).setOnClickListener {
            showLanguageMenu(keyboardView, it, isSource = true)
        }
        keyboardView.findViewById<TextView>(R.id.translate_dst).setOnClickListener {
            showLanguageMenu(keyboardView, it, isSource = false)
        }
        keyboardView.findViewById<View>(R.id.translate_swap).setOnClickListener {
            val tmp = srcLang
            srcLang = dstLang
            dstLang = tmp
            refreshTranslatorUi(keyboardView)
            prepareTranslator(keyboardView)
        }

        // Gestures: left = cycle back, right = cycle forward, up = emoji panel,
        // down = translator / close panel, diagonal = undo
        keyboardView.gestureEngine = GestureEngine(
            onSwipeLeft = { cycleLayerBackward(keyboardView) },
            onSwipeRight = { cycleLayerForward(keyboardView) },
            onSwipeUp = { openEmojiPanel(keyboardView) },
            onSwipeDown = {
                when {
                    searchActive -> exitSearchMode(keyboardView)
                    isEmojiPanelActive -> closeEmojiPanel(keyboardView)
                    translatorActive -> closeTranslator(keyboardView)
                    else -> openTranslator(keyboardView)
                }
            },
            onDiagonalUndo = {
                if (!translatorActive) {
                    undoManager.undo(currentInputConnection)
                    scheduleSuggestions()
                }
            }
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
        scheduleSuggestions()
        return frame
    }

    // ------------------------------------------------------------------
    // Layers
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // GIF / sticker search mode
    // ------------------------------------------------------------------

    private fun updateSearchLabel(kv: GestureAwareKeyboardLayout) {
        val label = kv.findViewById<TextView>(R.id.emoji_page_label)
        val query = searchBuffer.toString()
        when {
            query.isNotEmpty() -> {
                label.text = if (searchActive) "$query|" else query
                label.setTextColor(Color.WHITE)
            }
            else -> {
                label.text = if (currentPagerPage == 0) "Search emoji" else "Search KLIPY"
                label.setTextColor(Color.parseColor("#888888"))
            }
        }
    }

    private fun enterSearchMode(kv: GestureAwareKeyboardLayout) {
        if (searchActive || !isEmojiPanelActive) return
        if (currentPagerPage == 0) {
            Toast.makeText(this, "Emoji search isn't available yet", Toast.LENGTH_SHORT).show()
            return
        }
        searchActive = true
        searchBuffer.clear() // each search starts empty; recents sit right below

        kv.findViewById<View>(R.id.emoji_pager).visibility = View.GONE
        kv.findViewById<View>(R.id.emoji_bottom_row).visibility = View.GONE
        kv.findViewById<View>(R.id.search_suggestions).visibility = View.VISIBLE
        kv.findViewById<View>(R.id.search_pill).visibility = View.GONE   // hide the pill...
        searchTitleView?.visibility = View.VISIBLE                       // ...show the title instead
        kv.findViewById<View>(R.id.number_row).visibility = View.VISIBLE
        kv.findViewById<View>(R.id.bottom_row).visibility = View.VISIBLE
        applyLayerVisibility(kv) // shows the letters/symbols layer you were on

        enterButtonRef?.setImageResource(R.drawable.ic_search)
        updateSearchLabel(kv)
        refreshSearchSuggestions(kv)
    }

    /**
     * Leaves typing mode. By default it puts back the last submitted query (cancel).
     * clear = true resets the results to trending instead.
     */
    private fun exitSearchMode(kv: GestureAwareKeyboardLayout, clear: Boolean = false) {
        if (!searchActive) return
        searchActive = false

        kv.findViewById<View>(R.id.search_suggestions).visibility = View.GONE
        kv.findViewById<View>(R.id.search_pill).visibility = View.VISIBLE
        searchTitleView?.visibility = View.GONE
        kv.findViewById<View>(R.id.number_row).visibility = View.GONE
        kv.findViewById<View>(R.id.letters_container).visibility = View.GONE
        kv.findViewById<View>(R.id.symbols_container).visibility = View.GONE
        kv.findViewById<View>(R.id.symbols_page2_container).visibility = View.GONE
        kv.findViewById<View>(R.id.bottom_row).visibility = View.GONE
        kv.findViewById<View>(R.id.emoji_bottom_row).visibility = View.VISIBLE
        kv.findViewById<View>(R.id.emoji_pager).visibility = View.VISIBLE

        enterButtonRef?.setImageResource(
            if (currentEnterAction == EditorInfo.IME_ACTION_SEARCH) R.drawable.ic_search
            else R.drawable.keyboard_return
        )

        searchBuffer.clear()
        if (clear) {
            lastSubmittedQuery = ""
            pagerAdapter?.setQuery("", currentPagerPage)
        } else {
            searchBuffer.append(lastSubmittedQuery)
        }
        updateSearchLabel(kv)
    }

    private fun submitSearch(kv: GestureAwareKeyboardLayout) {
        val q = searchBuffer.toString().trim()
        if (q.isNotEmpty()) addRecentSearch(q)
        lastSubmittedQuery = q
        pagerAdapter?.setQuery(q, currentPagerPage)
        exitSearchMode(kv) // buffer becomes lastSubmittedQuery, so the bar shows what was searched
    }

    private fun loadRecentSearches(): MutableList<String> {
        val raw = getSharedPreferences("keure_prefs", MODE_PRIVATE)
            .getString("media_recent_searches", "") ?: ""
        return if (raw.isEmpty()) mutableListOf() else raw.split("\u0001").toMutableList()
    }

    private fun addRecentSearch(query: String) {
        val list = loadRecentSearches()
        list.removeAll { it.equals(query, ignoreCase = true) }
        list.add(0, query)
        while (list.size > 10) list.removeAt(list.size - 1)
        getSharedPreferences("keure_prefs", MODE_PRIVATE).edit()
            .putString("media_recent_searches", list.joinToString("\u0001")).apply()
    }

    private fun suggestionRow(
        iconRes: Int,
        label: String,
        textColor: Int,
        onClick: () -> Unit,
        onFill: (() -> Unit)? = null
    ): View {
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)   // row height - tweak here
            )
            setPadding(dp(14), 0, dp(4), 0)
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            setOnClickListener { onClick() }
        }

        row.addView(ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(Color.parseColor("#DDDDDD"))
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        })

        row.addView(TextView(this).apply {
            text = label
            setTextColor(textColor)
            textSize = 16f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(16) }
        })

        if (onFill != null) {
            row.addView(TextView(this).apply {
                text = "↙"
                setTextColor(Color.parseColor("#BBBBBB"))
                textSize = 20f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.MATCH_PARENT)
                isClickable = true
                setOnClickListener { onFill() }
            })
        }
        return row
    }

    private fun suggestionDivider(visible: Boolean = true): View = View(this).apply {
        setBackgroundColor(Color.parseColor("#555555"))
        visibility = if (visible) View.VISIBLE else View.INVISIBLE
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).apply { marginStart = dp(50) }
    }

    // keeps the card the same height when there are fewer than 3 suggestions
    private fun blankRow(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(40) // same as suggestionRow's height
        )
    }

    private fun searchInputRow(kv: GestureAwareKeyboardLayout, typed: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
            )
            setPadding(dp(14), 0, dp(4), 0)
            isClickable = true
            setOnClickListener { submitSearch(kv) }
        }

        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(Color.parseColor("#DDDDDD"))
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        })

        row.addView(TextView(this).apply {
            if (typed.isEmpty()) {
                text = "Search"
                setTextColor(Color.parseColor("#9AA0A6"))
            } else {
                val s = SpannableStringBuilder(typed)
                s.append("|")
                s.setSpan(
                    ForegroundColorSpan(Color.parseColor("#8AB4F8")),
                    s.length - 1, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                text = s
                setTextColor(Color.WHITE)
            }
            textSize = 16f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.START
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(16) }
        })

        row.addView(TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#DDDDDD"))
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.MATCH_PARENT)
            visibility = if (typed.isEmpty()) View.INVISIBLE else View.VISIBLE
            isClickable = true
            setOnClickListener {
                searchBuffer.clear()
                updateSearchLabel(kv)
                refreshSearchSuggestions(kv)
            }
        })
        return row
    }

    private fun refreshSearchSuggestions(kv: GestureAwareKeyboardLayout) {
        val card = kv.findViewById<LinearLayout>(R.id.search_suggestions)
        card.removeAllViews()

        val typed = searchBuffer.toString()
        val t = typed.trim()
        val recents = loadRecentSearches()

        // query -> true if it's from your history (clock), false if it's a completion (magnifier)
        val rows = LinkedHashMap<String, Boolean>()
        fun has(q: String) = rows.keys.any { it.equals(q, ignoreCase = true) }

        if (t.isEmpty()) {
            recents.take(3).forEach { rows[it] = true }
        } else {
            // 1) history that contains what you typed
            recents.filter { it.contains(t, ignoreCase = true) && !it.equals(t, ignoreCase = true) }
                .forEach { if (rows.size < 3) rows[it] = true }

            // 2) completions of the last word, from your own typing history
            val lastSpace = typed.lastIndexOf(' ')
            val head = typed.substring(0, lastSpace + 1)
            val prefix = typed.substring(lastSpace + 1)
            if (prefix.isNotEmpty()) {
                predictor.suggestForPrefix(prefix, 3).forEach { w ->
                    val full = head + w
                    if (rows.size < 3 && !has(full)) rows[full] = false
                }
            }

            // 3) fill the rest with your other recents so the card never shrinks
            for (r in recents) {
                if (rows.size >= 3) break
                if (!has(r) && !r.equals(t, ignoreCase = true)) rows[r] = true
            }
        }

        var shown = 0
        for ((q, isRecent) in rows) {
            card.addView(
                suggestionRow(
                    if (isRecent) android.R.drawable.ic_menu_recent_history else R.drawable.ic_search,
                    q, Color.WHITE,
                    onClick = {
                        searchBuffer.clear(); searchBuffer.append(q)
                        submitSearch(kv)
                    },
                    onFill = {
                        searchBuffer.clear(); searchBuffer.append(q)
                        updateSearchLabel(kv)
                        refreshSearchSuggestions(kv)
                    }
                )
            )
            card.addView(suggestionDivider(true))
            shown++
        }
        while (shown < 3) { // pad to exactly 3 rows
            card.addView(blankRow())
            card.addView(suggestionDivider(false))
            shown++
        }

        card.addView(searchInputRow(kv, typed))
    }

    // ------------------------------------------------------------------
    // Backspace / spacebar
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Quick words (wrench button)
    // ------------------------------------------------------------------

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
                    scheduleSuggestions()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
        dialog.window?.attributes?.token = keyboardView.windowToken
        dialog.show()
    }

    // ------------------------------------------------------------------
    // Suggestion bar
    // ------------------------------------------------------------------

    private fun isWordChar(c: Char) = c.isLetter() || c == '\''

    /** The word being typed right now (letters/apostrophes right before the cursor). */
    private fun trailingWord(before: CharSequence): String {
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        return before.substring(i, before.length)
    }

    /** The word before the current one, only if just spaces separate them. */
    private fun previousWord(before: CharSequence, prefixLen: Int): String? {
        var end = before.length - prefixLen
        while (end > 0 && before[end - 1].isWhitespace()) end--
        var start = end
        while (start > 0 && isWordChar(before[start - 1])) start--
        return if (start < end) before.substring(start, end).lowercase() else null
    }

    private fun atSentenceStart(before: CharSequence): Boolean {
        if (before.isNotEmpty() && before.last() == '\n') return true
        val t = before.trimEnd()
        return t.isEmpty() || t.last() in ".!?"
    }

    private fun computeSuggestionsEnabled(info: EditorInfo?): Boolean {
        val type = info?.inputType ?: return false
        if ((type and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        val v = type and InputType.TYPE_MASK_VARIATION
        return v != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
                v != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
                v != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD &&
                v != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
                v != InputType.TYPE_TEXT_VARIATION_URI
    }

    private fun learnCurrentWord(ic: InputConnection) {
        if (!suggestionsEnabled) return
        val before: CharSequence = ic.getTextBeforeCursor(60, 0) ?: return
        val word = trailingWord(before)
        if (word.length < 2) return
        val startIdx = before.length - word.length
        if (startIdx > 0 && before[startIdx - 1] in "@/#_") return // part of an email, link or tag
        predictor.learn(word, previousWord(before, word.length))
    }

    private fun scheduleSuggestions() {
        suggestRunnable?.let { suggestHandler.removeCallbacks(it) }
        val r = Runnable { refreshSuggestions() }
        suggestRunnable = r
        suggestHandler.postDelayed(r, 40) // let the editor finish applying our edit first
    }

    private fun refreshSuggestions() {
        val kv = keyboardViewRef ?: return
        if (translatorActive || !suggestionsEnabled) {
            renderSuggestions(kv, emptyList())
            return
        }
        val ic = currentInputConnection ?: return
        val before: CharSequence = ic.getTextBeforeCursor(60, 0) ?: ""
        val prefix = trailingWord(before)

        val results = if (prefix.isNotEmpty()) {
            predictor.suggestForPrefix(prefix, 3)
        } else {
            predictor.suggestNext(previousWord(before, 0), 3)
        }

        val capitalize = if (prefix.isNotEmpty()) prefix[0].isUpperCase() else atSentenceStart(before)
        val shown = results.map { w ->
            if (capitalize || w == "i" || w.startsWith("i'")) w.replaceFirstChar { it.uppercase() } else w
        }

        // On an empty field, your saved quick words come first
        val list = if (prefix.isEmpty() && before.isBlank()) {
            (loadShortcuts() + shown).distinct().take(3)
        } else shown

        renderSuggestions(kv, list)
    }

    private fun renderSuggestions(kv: GestureAwareKeyboardLayout, words: List<String>) {
        val slots = listOf(R.id.suggestion_1, R.id.suggestion_2, R.id.suggestion_3)
            .map { kv.findViewById<TextView>(it) }
        val slotForWord = listOf(1, 0, 2) // best suggestion goes in the centre slot

        slots.forEach {
            it.text = ""
            it.setOnClickListener(null)
            it.isClickable = false
            it.maxLines = 1
            it.ellipsize = TextUtils.TruncateAt.END
        }
        words.take(3).forEachIndexed { i, w ->
            val v = slots[slotForWord[i]]
            v.text = w
            v.setOnClickListener { acceptSuggestion(w) }
        }
    }

    private fun acceptSuggestion(word: String) {
        if (translatorActive) return
        val ic = currentInputConnection ?: return
        val before: CharSequence = ic.getTextBeforeCursor(60, 0) ?: ""
        val prefix = trailingWord(before)
        val cursor = ic.getExtractedText(ExtractedTextRequest(), 0)?.selectionStart
        val startPos = if (cursor != null) cursor - prefix.length else -1

        ic.beginBatchEdit()
        if (prefix.isNotEmpty()) {
            ic.deleteSurroundingText(prefix.length, 0)
            if (startPos >= 0) undoManager.recordDelete(prefix, startPos)
        }
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        if (startPos >= 0) undoManager.recordInsert("$word ", startPos)

        predictor.learn(word, previousWord(before, prefix.length))
        scheduleSuggestions()
    }

    // ------------------------------------------------------------------
    // Translator
    // ------------------------------------------------------------------

    private fun langName(code: String): String =
        translateLanguages.entries.firstOrNull { it.value == code }?.key ?: code

    private fun openTranslator(kv: GestureAwareKeyboardLayout) {
        if (translatorActive || isEmojiPanelActive) return
        translatorActive = true
        translatorBuffer.clear()
        kv.findViewById<View>(R.id.translator_bar).visibility = View.VISIBLE
        refreshTranslatorUi(kv)
        prepareTranslator(kv)
        scheduleSuggestions()
    }

    private fun closeTranslator(kv: GestureAwareKeyboardLayout) {
        translatorActive = false
        translateRunnable?.let { translateHandler.removeCallbacks(it) }
        currentInputConnection?.finishComposingText() // keeps the translated text in the editor
        translatorBuffer.clear()
        translator?.close()
        translator = null
        translatorReady = false
        translatorStatus = null
        kv.findViewById<View>(R.id.translator_bar).visibility = View.GONE
        scheduleSuggestions()
    }

    private fun refreshTranslatorUi(kv: GestureAwareKeyboardLayout) {
        kv.findViewById<TextView>(R.id.translate_src).text = langName(srcLang)
        kv.findViewById<TextView>(R.id.translate_dst).text = langName(dstLang)

        val input = kv.findViewById<TextView>(R.id.translate_input)
        if (translatorBuffer.isEmpty()) {
            input.text = translatorStatus ?: "Type here to translate"
            input.setTextColor(Color.parseColor("#AAAAAA"))
        } else {
            input.text = translatorBuffer.toString()
            input.setTextColor(Color.WHITE)
        }
    }

    private fun prepareTranslator(kv: GestureAwareKeyboardLayout) {
        translator?.close()
        translatorReady = false

        val t = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(srcLang)
                .setTargetLanguage(dstLang)
                .build()
        )
        translator = t

        var failed = false

        translatorStatus = "Downloading language data (first use, ~30 MB per language)…"
        refreshTranslatorUi(kv)
        android.util.Log.d("KeureTranslate", "Preparing $srcLang -> $dstLang")

        val slowNotice = Runnable {
            if (translator === t && !translatorReady && !failed) {
                translatorStatus = "Still downloading… large language packs can take a minute. Keep Wi-Fi on."
                refreshTranslatorUi(kv)
            }
        }
        translateHandler.postDelayed(slowNotice, 25000)

        fun fail(e: Exception) {
            failed = true
            translateHandler.removeCallbacks(slowNotice)
            android.util.Log.e("KeureTranslate", "Model download failed", e)
            if (translator === t) {
                translatorStatus = "Download failed: ${e.message ?: "unknown error"}"
                refreshTranslatorUi(kv)
            }
        }

        // Download each language through RemoteModelManager so the download keeps
        // going even if the translator object is closed or switched.
        val models = listOf(srcLang, dstLang).distinct()
            .map { TranslateRemoteModel.Builder(it).build() }
        var remaining = models.size

        models.forEach { model ->
            modelManager.download(model, DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    remaining--
                    if (remaining == 0 && !failed) {
                        t.downloadModelIfNeeded(DownloadConditions.Builder().build())
                            .addOnSuccessListener {
                                translateHandler.removeCallbacks(slowNotice)
                                android.util.Log.d("KeureTranslate", "Model ready")
                                if (translator === t) {
                                    translatorReady = true
                                    translatorStatus = null
                                    refreshTranslatorUi(kv)
                                    scheduleTranslate()
                                }
                            }
                            .addOnFailureListener { e -> fail(e) }
                    }
                }
                .addOnFailureListener { e -> fail(e) }
        }
    }

    private fun scheduleTranslate() {
        translateRunnable?.let { translateHandler.removeCallbacks(it) }
        val r = Runnable {
            val text = translatorBuffer.toString()
            val ic = currentInputConnection ?: return@Runnable

            if (text.isEmpty()) {
                ic.setComposingText("", 1)
                ic.finishComposingText()
                return@Runnable
            }

            val t = translator ?: return@Runnable
            if (!translatorReady) return@Runnable

            t.translate(text).addOnSuccessListener { result ->
                // ignore stale results if the user kept typing
                if (translatorActive && translatorBuffer.toString() == text) {
                    currentInputConnection?.setComposingText(result, 1)
                }
            }
        }
        translateRunnable = r
        translateHandler.postDelayed(r, 350)
    }

    private fun showLanguageMenu(kv: GestureAwareKeyboardLayout, anchor: View, isSource: Boolean) {
        val menu = android.widget.PopupMenu(anchor.context, anchor)
        translateLanguages.keys.forEachIndexed { i, name -> menu.menu.add(0, i, i, name) }
        menu.setOnMenuItemClickListener { item ->
            val picked = translateLanguages.values.elementAt(item.itemId)
            val old = if (isSource) srcLang else dstLang
            if (isSource) srcLang = picked else dstLang = picked
            // the two languages can't be identical - swap the other one back
            if (srcLang == dstLang) {
                if (isSource) dstLang = old else srcLang = old
            }
            refreshTranslatorUi(kv)
            prepareTranslator(kv)
            true
        }
        menu.show()
    }

    // ------------------------------------------------------------------
    // Typing / deleting / enter
    // ------------------------------------------------------------------

    private fun performEnterAction() {
        if (translatorActive) {
            keyboardViewRef?.let { closeTranslator(it) }
            return
        }
        if (searchActive) {
            keyboardViewRef?.let { submitSearch(it) }
            return
        }
        val ic = currentInputConnection ?: return
        learnCurrentWord(ic)

        if (currentEnterAction == EditorInfo.IME_ACTION_SEARCH) {
            ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
        } else {
            ic.commitText("\n", 1)
        }
    }

    private fun typeAndRecord(char: String) {
        if (translatorActive) {
            translatorBuffer.append(char)
            keyboardViewRef?.let { refreshTranslatorUi(it) }
            scheduleTranslate()
            return
        }
        if (searchActive) {
            searchBuffer.append(char)
            keyboardViewRef?.let {
                updateSearchLabel(it)
                refreshSearchSuggestions(it)
            }
            return
        }
        val ic = currentInputConnection ?: return

        val c = char.firstOrNull()
        if (c != null && !c.isLetter() && c != '\'') learnCurrentWord(ic) // space, punctuation, emoji...

        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        val cursorPos = extracted?.selectionStart ?: 0
        ic.commitText(char, 1)
        undoManager.recordInsert(char, cursorPos)
        scheduleSuggestions()
    }

    private fun deleteAndRecord() {
        // Translator box: backspace edits the buffer, not the editor
        if (translatorActive) {
            if (translatorBuffer.isNotEmpty()) {
                val n = if (translatorBuffer.length >= 2 &&
                    Character.isLowSurrogate(translatorBuffer.last())) 2 else 1
                translatorBuffer.setLength(translatorBuffer.length - n)
            }
            keyboardViewRef?.let { refreshTranslatorUi(it) }
            scheduleTranslate()
            return
        }

        // Search box: backspace edits the search text, not the editor
        if (searchActive) {
            if (searchBuffer.isNotEmpty()) {
                val n = if (searchBuffer.length >= 2 &&
                    Character.isLowSurrogate(searchBuffer.last())) 2 else 1
                searchBuffer.setLength(searchBuffer.length - n)
            }
            keyboardViewRef?.let {
                updateSearchLabel(it)
                refreshSearchSuggestions(it)
            }
            return
        }

        scheduleSuggestions()

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

    // ------------------------------------------------------------------
    // Glow / mute
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Emoji / GIF panel
    // ------------------------------------------------------------------

    private fun openEmojiPanel(keyboardView: GestureAwareKeyboardLayout) {
        if (translatorActive) closeTranslator(keyboardView)
        isEmojiPanelActive = true
        keyboardView.panelModeActive = true

        val normalHeight = keyboardView.height
        if (normalHeight > 0) emojiPanelHeight = normalHeight
        pagerAdapter?.onPageShown(currentPagerPage)

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
        if (searchActive) exitSearchMode(keyboardView)
        searchBuffer.clear()
        lastSubmittedQuery = ""
        pagerAdapter?.resetQuery()
        updateSearchLabel(keyboardView)

        isEmojiPanelActive = false
        keyboardView.panelModeActive = false

        keyboardView.findViewById<View>(R.id.emoji_container).visibility = View.GONE

        keyboardView.findViewById<View>(R.id.suggestion_bar).visibility = View.VISIBLE
        keyboardView.findViewById<View>(R.id.number_row).visibility = View.VISIBLE
        keyboardView.findViewById<View>(R.id.bottom_row).visibility = View.VISIBLE

        applyLayerVisibility(keyboardView)
        scheduleSuggestions()
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun insertGif(gif: GifResult, keyboardView: GestureAwareKeyboardLayout) {
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo ?: return
        val mime = gif.mimeType

        val supported = editorInfo.contentMimeTypes?.any {
            android.content.ClipDescription.compareMimeTypes(mime, it)
        } == true
        if (!supported) {
            showUnsupportedDialog(keyboardView)
            return
        }

        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/jpeg" -> "jpg"
            else -> "gif"
        }

        Thread {
            try {
                val dir = java.io.File(cacheDir, "gifs")
                if (!dir.exists()) dir.mkdirs()
                val file = java.io.File(dir, "media_${gif.insertUrl.hashCode()}.$ext")

                if (!file.exists()) {
                    val request = okhttp3.Request.Builder().url(gif.insertUrl).build()
                    val bytes = KlipyClient.client.newCall(request).execute().use { it.body?.bytes() }
                    if (bytes == null) {
                        mainHandlerForGif.post { showUnsupportedDialog(keyboardView) }
                        return@Thread
                    }
                    file.writeBytes(bytes)
                }

                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    this, "com.example.keure.fileprovider", file
                )

                mainHandlerForGif.post {
                    try {
                        val description = android.content.ClipDescription("Media", arrayOf(mime))
                        val inputContent = androidx.core.view.inputmethod.InputContentInfoCompat(
                            contentUri, description, null
                        )
                        val flags = androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                        val success = androidx.core.view.inputmethod.InputConnectionCompat.commitContent(
                            ic, editorInfo, inputContent, flags, null
                        )
                        if (success) RecentMedia.add(this, gif) else showUnsupportedDialog(keyboardView)
                    } catch (e: Exception) {
                        android.util.Log.e("KeureGif", "commitContent failed", e)
                        showUnsupportedDialog(keyboardView)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("KeureGif", "Download failed", e)
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
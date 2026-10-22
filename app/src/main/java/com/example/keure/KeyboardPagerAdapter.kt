package com.example.keure

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class KeyboardPagerAdapter(
    private val categories: List<EmojiCategory>,
    private val onEmojiClick: (String) -> Unit,
    private val onMediaClick: (GifResult) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private var gifPage: MediaPage? = null
    private var stickerPage: MediaPage? = null
    private var query = ""

    override fun getItemCount() = 3 // 0 = Emoji, 1 = GIFs, 2 = Stickers
    override fun getItemViewType(position: Int) = position

    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            0 -> PageViewHolder(buildEmojiPage(parent.context))
            1 -> {
                val page = MediaPage(parent.context, "gifs", onMediaClick)
                gifPage = page
                page.load(query)
                PageViewHolder(page.root)
            }
            else -> {
                val page = MediaPage(parent.context, "stickers", onMediaClick)
                stickerPage = page
                page.load(query)
                PageViewHolder(page.root)
            }
        }
    }

    /** New search text: reload the page the user is looking at right now. */
    fun setQuery(newQuery: String, visiblePage: Int) {
        query = newQuery
        when (visiblePage) {
            1 -> gifPage?.load(query)
            2 -> stickerPage?.load(query)
        }
    }

    /** Clear the stored query without reloading (pages catch up when shown). */
    fun resetQuery() {
        query = ""
    }

    /** Called when a page becomes visible; reloads it only if its results are out of date. */
    fun onPageShown(page: Int) {
        when (page) {
            1 -> gifPage?.loadIfNeeded(query)
            2 -> stickerPage?.loadIfNeeded(query)
        }
    }

    private fun buildEmojiPage(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val flatItems = mutableListOf<EmojiListItem>()
        val headerPositions = mutableMapOf<String, Int>()
        for (cat in categories) {
            headerPositions[cat.name] = flatItems.size
            flatItems.add(EmojiListItem.Header(cat.name))
            cat.emojis.forEach { flatItems.add(EmojiListItem.EmojiCell(it)) }
        }

        val spanCount = 6
        val layoutManager = GridLayoutManager(context, spanCount)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (flatItems[position] is EmojiListItem.Header) spanCount else 1
        }

        val recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            this.layoutManager = layoutManager
            adapter = FlatEmojiAdapter(flatItems) { emoji -> onEmojiClick(emoji) }
        }

        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(context, 40)
            )
        }

        for (cat in categories) {
            val tabButton = TextView(context).apply {
                text = cat.icon
                textSize = 18f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                )
                isClickable = true
                isFocusable = true
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, outValue, true
                )
                setBackgroundResource(outValue.resourceId)
                setOnClickListener {
                    val pos = headerPositions[cat.name] ?: 0
                    layoutManager.scrollToPositionWithOffset(pos, 0)
                }
            }
            tabRow.addView(tabButton)
        }

        root.addView(tabRow)
        root.addView(recyclerView)
        return root
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // Content is built in onCreateViewHolder
    }
}
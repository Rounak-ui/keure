package com.example.keure

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
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
    private val onGifClick: (GifResult) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemCount() = 3 // 0 = Emoji, 1 = GIFs, 2 = Stickers
    override fun getItemViewType(position: Int) = position

    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            0 -> PageViewHolder(buildEmojiPage(parent.context))
            1 -> PageViewHolder(buildGifPage(parent.context))
            else -> {
                val placeholder = TextView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    text = "Stickers coming soon"
                }
                PageViewHolder(placeholder)
            }
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

        // Flatten categories into header + emoji items, tracking header positions
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

        // Category tab row
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

    private fun buildGifPage(context: Context): View {
        val recyclerView = RecyclerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            layoutManager = GridLayoutManager(context, 3)
        }

        val mainHandler = Handler(Looper.getMainLooper())
        KlipyClient.fetchTrending { gifs ->
            mainHandler.post {
                recyclerView.adapter = GifGridAdapter(gifs) { gif -> onGifClick(gif) }
            }
        }

        return recyclerView
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // Content already built in onCreateViewHolder
    }
}
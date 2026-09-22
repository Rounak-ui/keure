package com.example.keure

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class EmojiListItem {
    data class Header(val title: String) : EmojiListItem()
    data class EmojiCell(val emoji: String) : EmojiListItem()
}

class FlatEmojiAdapter(
    private val items: List<EmojiListItem>,
    private val onEmojiClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_EMOJI = 1
    }

    class HeaderViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    class EmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    override fun getItemViewType(position: Int): Int =
        if (items[position] is EmojiListItem.Header) TYPE_HEADER else TYPE_EMOJI

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val textView = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(parent.context, 32)
                )
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(parent.context, 8), 0, 0, 0)
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 13f
            }
            HeaderViewHolder(textView)
        } else {
            val textView = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    dpToPx(parent.context, 64),
                    dpToPx(parent.context, 64)
                )
                gravity = Gravity.CENTER
                textSize = 30f
                isClickable = true
                isFocusable = true
                val outValue = android.util.TypedValue()
                parent.context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, outValue, true
                )
                setBackgroundResource(outValue.resourceId)
            }
            EmojiViewHolder(textView)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is EmojiListItem.Header -> (holder as HeaderViewHolder).textView.text = item.title
            is EmojiListItem.EmojiCell -> {
                (holder as EmojiViewHolder).textView.text = item.emoji
                holder.textView.setOnClickListener { onEmojiClick(item.emoji) }
            }
        }
    }

    override fun getItemCount() = items.size
}
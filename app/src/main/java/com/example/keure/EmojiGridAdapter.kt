package com.example.keure

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EmojiGridAdapter(
    private val emojis: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<EmojiGridAdapter.EmojiViewHolder>() {

    class EmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(parent.context, 48)
            )
            gravity = Gravity.CENTER
            textSize = 22f
            isClickable = true
            isFocusable = true
            val outValue = android.util.TypedValue()
            parent.context.theme.resolveAttribute(
                android.R.attr.selectableItemBackground, outValue, true
            )
            setBackgroundResource(outValue.resourceId)
        }
        return EmojiViewHolder(textView)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        val emoji = emojis[position]
        holder.textView.text = emoji
        holder.textView.setOnClickListener { onClick(emoji) }
    }

    override fun getItemCount() = emojis.size
}
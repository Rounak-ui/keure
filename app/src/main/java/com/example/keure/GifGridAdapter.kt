package com.example.keure

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class GifGridAdapter(
    gifs: List<GifResult>,
    private val scaleType: ImageView.ScaleType = ImageView.ScaleType.CENTER_CROP,
    private val onClick: (GifResult) -> Unit,
    private val onLongClick: ((GifResult) -> Unit)? = null
) : RecyclerView.Adapter<GifGridAdapter.GifViewHolder>() {

    private val items = gifs.toMutableList()

    class GifViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GifViewHolder {
        val pad = dpToPx(parent.context, 4)
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                dpToPx(parent.context, 110),
                dpToPx(parent.context, 110)
            )
            scaleType = this@GifGridAdapter.scaleType
            setPadding(pad, pad, pad, pad)
        }
        return GifViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: GifViewHolder, position: Int) {
        val gif = items[position]
        Glide.with(holder.imageView.context)
            .load(gif.previewUrl)
            .into(holder.imageView)

        holder.imageView.setOnClickListener { onClick(gif) }

        // views get recycled, so always set or clear the long-press listener
        if (onLongClick != null) {
            holder.imageView.setOnLongClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onLongClick.invoke(gif)
                true
            }
        } else {
            holder.imageView.setOnLongClickListener(null)
            holder.imageView.isLongClickable = false
        }
    }

    fun remove(gif: GifResult) {
        val index = items.indexOfFirst { it.insertUrl == gif.insertUrl }
        if (index >= 0) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    override fun getItemCount() = items.size
}
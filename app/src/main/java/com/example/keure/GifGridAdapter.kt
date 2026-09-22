package com.example.keure

import android.content.Context
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class GifGridAdapter(
    private val gifs: List<GifResult>,
    private val onClick: (GifResult) -> Unit
) : RecyclerView.Adapter<GifGridAdapter.GifViewHolder>() {

    class GifViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GifViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                dpToPx(parent.context, 110),
                dpToPx(parent.context, 110)
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        return GifViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: GifViewHolder, position: Int) {
        val gif = gifs[position]
        Glide.with(holder.imageView.context)
            .load(gif.previewUrl)
            .into(holder.imageView)
        holder.imageView.setOnClickListener { onClick(gif) }
    }

    override fun getItemCount() = gifs.size
}
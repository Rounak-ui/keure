package com.example.keure

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast

class MediaPage(
    private val context: Context,
    private val type: String, // "gifs" or "stickers"
    private val onClick: (GifResult) -> Unit
) {
    val root = FrameLayout(context)
    private val recyclerView = RecyclerView(context)
    private val status = TextView(context)
    private val handler = Handler(Looper.getMainLooper())
    private var requestId = 0
    private var loadedQuery: String? = null

    private val scaleType =
        if (type == "stickers") ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP

    init {
        root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        recyclerView.layoutManager = GridLayoutManager(context, 3)
        root.addView(
            recyclerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        status.gravity = Gravity.CENTER
        status.setTextColor(Color.parseColor("#AAAAAA"))
        status.textSize = 14f
        status.setPadding(24, 0, 24, 0)
        root.addView(
            status,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val powered = TextView(context).apply {
            text = "Powered by KLIPY"
            textSize = 10f
            setTextColor(Color.parseColor("#99FFFFFF"))
            setPadding(12, 4, 12, 4)
        }
        root.addView(
            powered,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            )
        )
    }

    /** Blank query = your recently used items (local, no API call). Otherwise a Klipy search. */
    fun load(query: String) {
        val q = query.trim()
        loadedQuery = q
        val id = ++requestId // any search still in flight is now out of date

        if (q.isEmpty()) {
            showRecents()
            return
        }

        status.text = "Loading…"
        KlipyClient.fetch(type, q) { results ->
            handler.post {
                if (id != requestId) return@post
                when {
                    results == null -> {
                        recyclerView.adapter = null
                        status.text = "Couldn't load. Check your internet."
                    }
                    results.isEmpty() -> {
                        recyclerView.adapter = null
                        status.text = "No results for \"$q\""
                    }
                    else -> {
                        status.text = ""
                        recyclerView.adapter = GifGridAdapter(results, scaleType, onClick)
                        recyclerView.scrollToPosition(0)
                    }
                }
            }
        }
    }

    private fun showRecents() {
        val recents = RecentMedia.load(context, type)
        if (recents.isEmpty()) {
            recyclerView.adapter = null
            val what = if (type == "stickers") "stickers" else "GIFs"
            status.text = "No recent $what yet\nTap the search bar to find some"
            return
        }

        status.text = ""
        var adapter: GifGridAdapter? = null
        adapter = GifGridAdapter(
            recents,
            scaleType,
            onClick = onClick,
            onLongClick = { item ->
                RecentMedia.remove(context, item)
                adapter?.remove(item)
                Toast.makeText(context, "Removed from recents", Toast.LENGTH_SHORT).show()
                if (adapter?.itemCount == 0) showRecents() // show the empty-state hint
            }
        )
        recyclerView.adapter = adapter
        recyclerView.scrollToPosition(0)
    }

    fun loadIfNeeded(query: String) {
        val q = query.trim()
        // recents are local, so always refresh them; searches only reload if the query changed
        if (q.isEmpty() || loadedQuery != q) load(q)
    }
}
package com.example.keure

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object RecentMedia {
    private const val PREFS = "keure_prefs"
    private const val MAX = 30

    private fun key(type: String) = "recent_media_$type"

    fun load(context: Context, type: String): List<GifResult> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(type), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GifResult(
                    o.getString("p"),
                    o.getString("f"),
                    o.getString("i"),
                    o.optString("m", "image/gif"),
                    type
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, type: String, list: List<GifResult>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("p", it.previewUrl)
                    .put("f", it.fullUrl)
                    .put("i", it.insertUrl)
                    .put("m", it.mimeType)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(type), arr.toString()).apply()
    }

    /** Puts [item] at the front of its list (no duplicates, capped at 30). */
    fun add(context: Context, item: GifResult) {
        val list = load(context, item.type).toMutableList()
        list.removeAll { it.insertUrl == item.insertUrl }
        list.add(0, item)
        while (list.size > MAX) list.removeAt(list.size - 1)
        save(context, item.type, list)
    }

    /** Removes [item] from its recents list. */
    fun remove(context: Context, item: GifResult) {
        val list = load(context, item.type).toMutableList()
        list.removeAll { it.insertUrl == item.insertUrl }
        save(context, item.type, list)
    }
}
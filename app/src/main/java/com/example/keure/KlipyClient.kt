package com.example.keure

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

data class GifResult(
    val previewUrl: String,
    val fullUrl: String,
    val insertUrl: String,
    val mimeType: String = "image/gif",
    val type: String = "gifs" // "gifs" or "stickers", so recents stay separate
)

object KlipyClient {
    private const val API_KEY = BuildConfig.KLIPY_API_KEY
    val client = OkHttpClient()

    /**
     * type = "gifs" or "stickers". Blank query = trending.
     * Callback gets null on a network/API error, an empty list for "no results".
     */
    fun fetch(type: String, query: String, onResult: (List<GifResult>?) -> Unit) {
        val base = "https://api.klipy.com/api/v1/$API_KEY/$type"
        val url = if (query.isBlank()) {
            "$base/trending?per_page=40"
        } else {
            "$base/search?q=${URLEncoder.encode(query.trim(), "UTF-8")}&per_page=40"
        }

        client.newCall(Request.Builder().url(url).build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                android.util.Log.e("KeureMedia", "Network failure ($type)", e)
                onResult(null)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    try {
                        val body = it.body?.string() ?: ""
                        if (!it.isSuccessful) {
                            android.util.Log.e("KeureMedia", "HTTP ${it.code} ($type): ${body.take(200)}")
                            onResult(null)
                            return
                        }
                        val results = parse(body, type)
                        android.util.Log.d("KeureMedia", "Parsed ${results.size} $type for '$query'")
                        onResult(results)
                    } catch (e: Exception) {
                        android.util.Log.e("KeureMedia", "Parse failure ($type)", e)
                        onResult(null)
                    }
                }
            }
        })
    }

    private val sizes = listOf("sm", "md", "xs", "hd")

    private fun pick(files: JSONObject, formats: List<String>): Pair<String, String>? {
        for (size in sizes) {
            val bucket = files.optJSONObject(size) ?: continue
            for (format in formats) {
                val url = bucket.optJSONObject(format)?.optString("url")
                if (!url.isNullOrEmpty()) return url to format
            }
        }
        return null
    }

    private fun mimeFor(format: String) = when (format) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "jpg" -> "image/jpeg"
        else -> "image/gif"
    }

    private fun parse(json: String, type: String): List<GifResult> {
        val items = JSONObject(json).optJSONObject("data")?.optJSONArray("data") ?: return emptyList()
        val sticker = type == "stickers"
        val previewFormats = if (sticker) listOf("webp", "png", "gif") else listOf("gif", "webp")
        val insertFormats = if (sticker) listOf("png", "webp", "gif") else listOf("gif")

        val list = mutableListOf<GifResult>()
        for (i in 0 until items.length()) {
            val files = items.getJSONObject(i).optJSONObject("file") ?: continue
            val preview = pick(files, previewFormats) ?: continue
            val insert = pick(files, insertFormats) ?: continue
            list.add(GifResult(preview.first, insert.first, insert.first, mimeFor(insert.second), type))
        }
        return list
    }
}
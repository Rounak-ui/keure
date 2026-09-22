package com.example.keure

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class GifResult(val previewUrl: String, val fullUrl: String, val insertUrl: String)

object KlipyClient {
    private const val API_KEY = BuildConfig.KLIPY_API_KEY
    val client = OkHttpClient()

    fun fetchTrending(onResult: (List<GifResult>) -> Unit) {
        android.util.Log.d("KeureGif", "API_KEY = '$API_KEY'")
        val url = "https://api.klipy.com/api/v1/$API_KEY/gifs/trending?limit=50"
        android.util.Log.d("KeureGif", "Requesting URL = $url")
        fetch(url, onResult)
    }
    fun search(query: String, onResult: (List<GifResult>) -> Unit) {
        val url = "https://api.klipy.com/api/v1/$API_KEY/gifs/search?q=$query&limit=50"
        fetch(url, onResult)
    }

    private fun fetch(url: String, onResult: (List<GifResult>) -> Unit) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                android.util.Log.e("KeureGif", "Network failure", e)
                onResult(emptyList())
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    val body = response.body?.string() ?: ""
                    android.util.Log.d("KeureGif", "Response code=${response.code}, body first 300 chars: ${body.take(300)}")
                    val results = parseGifs(body)
                    android.util.Log.d("KeureGif", "Parsed ${results.size} gifs")
                    onResult(results)
                } catch (e: Exception) {
                    android.util.Log.e("KeureGif", "Parse exception", e)
                    onResult(emptyList())
                }
            }
        })
    }

    private fun parseGifs(json: String): List<GifResult> {
        val list = mutableListOf<GifResult>()
        val root = JSONObject(json)

        val outerData = root.optJSONObject("data") ?: return list
        val items = outerData.optJSONArray("data") ?: return list

        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val files = item.optJSONObject("file") ?: continue

            val previewUrl = files.optJSONObject("sm")?.optJSONObject("gif")?.optString("url")
            val insertUrl = files.optJSONObject("sm")?.optJSONObject("gif")?.optString("url")
            val fullUrl = files.optJSONObject("md")?.optJSONObject("gif")?.optString("url")

            if (!previewUrl.isNullOrEmpty() && !fullUrl.isNullOrEmpty() && !insertUrl.isNullOrEmpty()) {
                list.add(GifResult(previewUrl, fullUrl, insertUrl))
            }
        }
        return list
    }
}
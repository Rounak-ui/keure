package com.example.keure

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class EmojiCategory(val name: String, val icon: String, val emojis: List<String>)

object EmojiData {
    private var cachedCategories: List<EmojiCategory>? = null

    fun loadCategories(context: Context): List<EmojiCategory> {
        cachedCategories?.let { return it }

        val categories = mutableListOf<EmojiCategory>()
        var currentName: String? = null
        var currentEmojis = mutableListOf<String>()

        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open("emojis.txt")))
            reader.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEachLine

                if (line.startsWith("## ")) {
                    if (currentName != null && currentEmojis.isNotEmpty()) {
                        categories.add(EmojiCategory(currentName!!, currentEmojis.first(), currentEmojis))
                    }
                    currentName = line.removePrefix("## ").trim()
                    currentEmojis = mutableListOf()
                } else {
                    currentEmojis.add(line)
                }
            }
            if (currentName != null && currentEmojis.isNotEmpty()) {
                categories.add(EmojiCategory(currentName!!, currentEmojis.first(), currentEmojis))
            }
            reader.close()
        } catch (e: Exception) {
            android.util.Log.e("KeureEmoji", "Failed to load emojis.txt", e)
            categories.add(EmojiCategory("Emoji", "😀", listOf("😀", "😂", "❤️", "👍")))
        }

        cachedCategories = categories
        return categories
    }
}
package com.example.keure

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import kotlin.math.max

class WordPredictor(private val context: Context) {

    private class Entry(val base: Double, var count: Int, var lastUsed: Long)

    private val words = HashMap<String, Entry>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()

    private val file = File(context.filesDir, "user_words.txt")
    private val saveHandler = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { saveNow() }
    private var dirty = false

    fun load() {
        // 1) bundled starter dictionary: earlier in the file = more common
        try {
            val tokens = context.assets.open("base_words.txt").bufferedReader().use { it.readText() }
                .split(Regex("\\s+")).filter { it.isNotEmpty() }
            val total = max(tokens.size, 1)
            tokens.forEachIndexed { rank, raw ->
                val w = raw.lowercase()
                if (isValid(w) && !words.containsKey(w)) {
                    words[w] = Entry(1.0 + 2.0 * (1.0 - rank.toDouble() / total), 0, 0L)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("KeureWords", "No base dictionary", e)
        }

        // 2) what the user has taught us
        try {
            if (file.exists()) {
                file.forEachLine { line ->
                    val p = line.split('\t')
                    if (p.size == 4 && p[0] == "W") {
                        val count = p[2].toIntOrNull() ?: return@forEachLine
                        val last = p[3].toLongOrNull() ?: 0L
                        val e = words.getOrPut(p[1]) { Entry(0.0, 0, 0L) }
                        e.count = count
                        e.lastUsed = last
                    } else if (p.size == 4 && p[0] == "B") {
                        val c = p[3].toIntOrNull() ?: return@forEachLine
                        bigrams.getOrPut(p[1]) { HashMap() }[p[2]] = c
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("KeureWords", "Couldn't read learned words", e)
        }
    }

    private fun isValid(w: String) = w.isNotEmpty() && w.all { it.isLetter() || it == '\'' }

    /** Called when a word is finished. [prev] is the word before it (lowercase), if any. */
    fun learn(word: String, prev: String?) {
        val w = word.trim('\'').lowercase()
        if (w.length < 2 || !isValid(w)) return
        val now = System.currentTimeMillis()
        val e = words.getOrPut(w) { Entry(0.0, 0, now) }
        e.count++
        e.lastUsed = now
        if (prev != null && isValid(prev)) {
            val m = bigrams.getOrPut(prev) { HashMap() }
            m[w] = (m[w] ?: 0) + 1
        }
        dirty = true
        saveHandler.removeCallbacks(saveRunnable)
        saveHandler.postDelayed(saveRunnable, 8000)
    }

    private fun score(e: Entry, now: Long): Double {
        val ageDays = (now - e.lastUsed) / 86_400_000.0
        val recency = if (e.count > 0) 2.0 / (1.0 + ageDays) else 0.0
        return e.base + e.count * 3.0 + recency
    }

    // starter words always qualify; new words must be typed at least twice
    private fun eligible(e: Entry) = e.base > 0.0 || e.count >= 2

    fun suggestForPrefix(prefix: String, limit: Int): List<String> {
        val p = prefix.lowercase()
        val now = System.currentTimeMillis()
        return words.entries.asSequence()
            .filter { it.key.length > p.length && it.key.startsWith(p) && eligible(it.value) }
            .sortedByDescending { score(it.value, now) }
            .take(limit)
            .map { it.key }
            .toList()
    }

    fun suggestNext(prev: String?, limit: Int): List<String> {
        val out = LinkedHashSet<String>()
        if (prev != null) {
            bigrams[prev]?.entries?.sortedByDescending { it.value }?.forEach {
                if (out.size < limit) out.add(it.key)
            }
        }
        if (out.size < limit) {
            val now = System.currentTimeMillis()
            val fill = words.entries.asSequence()
                .filter { eligible(it.value) && it.key != prev && it.key !in out }
                .sortedByDescending { score(it.value, now) }
                .take(limit - out.size)
                .map { it.key }
                .toList()
            out.addAll(fill)
        }
        return out.toList()
    }

    fun saveNow() {
        if (!dirty) return
        dirty = false
        saveHandler.removeCallbacks(saveRunnable)
        val sb = StringBuilder()
        for ((w, e) in words) {
            if (e.count > 0) sb.append("W\t").append(w).append('\t').append(e.count).append('\t').append(e.lastUsed).append('\n')
        }
        for ((prev, m) in bigrams) {
            for ((next, c) in m) sb.append("B\t").append(prev).append('\t').append(next).append('\t').append(c).append('\n')
        }
        val text = sb.toString()
        Thread {
            try { file.writeText(text) } catch (e: Exception) {
                android.util.Log.w("KeureWords", "Save failed", e)
            }
        }.start()
    }
}


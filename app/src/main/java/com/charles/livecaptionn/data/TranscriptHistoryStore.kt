package com.charles.livecaptionn.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists transcript history as a JSON array in internal storage.
 * Thread-safe via Mutex; keeps the most recent [MAX_ENTRIES] entries.
 */
class TranscriptHistoryStore(context: Context) {

    private val file = File(context.filesDir, "transcript_history.json")
    private val mutex = Mutex()

    suspend fun add(entry: TranscriptEntry) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val arr = readArray()
            arr.put(entry.toJson())
            // Trim oldest entries beyond limit
            while (arr.length() > MAX_ENTRIES) arr.remove(0)
            file.writeText(arr.toString())
        }
    }

    suspend fun getAll(): List<TranscriptEntry> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val arr = readArray()
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                TranscriptEntry(
                    timestamp = obj.optLong("timestamp", 0L),
                    originalText = obj.optString("originalText", ""),
                    translatedText = obj.optString("translatedText", ""),
                    sourceLanguage = obj.optString("sourceLanguage", ""),
                    targetLanguage = obj.optString("targetLanguage", "")
                )
            }.reversed() // newest first
        }
    }

    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) { file.delete() }
    }

    private fun readArray(): JSONArray {
        return try {
            if (file.exists()) JSONArray(file.readText()) else JSONArray()
        } catch (_: Throwable) {
            JSONArray()
        }
    }

    private fun TranscriptEntry.toJson() = JSONObject().apply {
        put("timestamp", timestamp)
        put("originalText", originalText)
        put("translatedText", translatedText)
        put("sourceLanguage", sourceLanguage)
        put("targetLanguage", targetLanguage)
    }

    companion object {
        private const val MAX_ENTRIES = 500
    }
}

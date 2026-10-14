package com.charles.livecaptionn.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Immutable, self-contained data suitable for sharing or writing to a file. */
data class TranscriptExport(val entries: List<TranscriptEntry>) {
    fun asText(): String {
        // Readable local time instead of raw epoch milliseconds.
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return entries.asReversed().joinToString("\n\n") {
            "[${format.format(Date(it.timestamp))}] ${it.sourceLanguage} -> ${it.targetLanguage}\n${it.originalText}\n${it.translatedText}"
        }
    }

    fun asJson(): String = JSONObject().put("entries", JSONArray().apply {
        entries.forEach { put(JSONObject().apply {
            put("id", it.id); put("timestamp", it.timestamp); put("originalText", it.originalText)
            put("translatedText", it.translatedText); put("sourceLanguage", it.sourceLanguage); put("targetLanguage", it.targetLanguage)
        }) }
    }).toString()
}

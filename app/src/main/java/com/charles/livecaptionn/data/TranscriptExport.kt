package com.charles.livecaptionn.data

import org.json.JSONArray
import org.json.JSONObject

/** Immutable, self-contained data suitable for sharing or writing to a file. */
data class TranscriptExport(val entries: List<TranscriptEntry>) {
    fun asText(): String = entries.asReversed().joinToString("\n") {
        "[${it.timestamp}] ${it.sourceLanguage} -> ${it.targetLanguage}\n${it.originalText}\n${it.translatedText}"
    }

    fun asJson(): String = JSONObject().put("entries", JSONArray().apply {
        entries.forEach { put(JSONObject().apply {
            put("id", it.id); put("timestamp", it.timestamp); put("originalText", it.originalText)
            put("translatedText", it.translatedText); put("sourceLanguage", it.sourceLanguage); put("targetLanguage", it.targetLanguage)
        }) }
    }).toString()
}

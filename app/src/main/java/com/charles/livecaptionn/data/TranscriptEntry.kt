package com.charles.livecaptionn.data

data class TranscriptEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val id: String = newTranscriptId()
)

fun newTranscriptId(): String = java.util.UUID.randomUUID().toString()

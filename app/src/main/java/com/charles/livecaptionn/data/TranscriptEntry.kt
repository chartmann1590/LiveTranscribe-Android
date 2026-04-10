package com.charles.livecaptionn.data

data class TranscriptEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String
)

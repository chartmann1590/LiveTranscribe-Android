package com.charles.livecaptionn.overlay

import com.charles.livecaptionn.speech.RecognitionStatus

data class OverlayUiState(
    val originalText: String = "",
    val translatedText: String = "",
    val status: RecognitionStatus = RecognitionStatus.IDLE,
    val textSizeSp: Float = 20f,
    val opacity: Float = 0.65f,
    val showOriginal: Boolean = true,
    val minimized: Boolean = false
)

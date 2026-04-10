package com.charles.livecaptionn.service

import com.charles.livecaptionn.speech.RecognitionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CaptionRuntimeState(
    val originalText: String = "",
    val translatedText: String = "",
    val status: RecognitionStatus = RecognitionStatus.IDLE,
    val running: Boolean = false,
    val paused: Boolean = false,
    val lastError: String? = null
)

class CaptionRuntimeStore {
    private val mutable = MutableStateFlow(CaptionRuntimeState())
    val state: StateFlow<CaptionRuntimeState> = mutable.asStateFlow()

    fun update(transform: (CaptionRuntimeState) -> CaptionRuntimeState) {
        mutable.update(transform)
    }
}

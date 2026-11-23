package com.charles.livecaptionn.translation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TranslationStatus(
    val downloading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

private val IDLE_TRANSLATION_STATUS_FLOW: StateFlow<TranslationStatus> =
    MutableStateFlow(TranslationStatus()).asStateFlow()

interface TranslationRepository {
    val statusFlow: StateFlow<TranslationStatus>
        get() = IDLE_TRANSLATION_STATUS_FLOW

    suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String

    /**
     * Eagerly download / warm up any offline assets required to translate
     * from [sourceCode] to [targetCode]. Safe to call repeatedly. Default
     * implementation is a no-op for backends that have nothing to prefetch.
     */
    suspend fun prewarm(sourceCode: String, targetCode: String) {}

    /**
     * Release any held resources (cached translators, HTTP clients, etc.).
     * Called when the repository is no longer needed.
     */
    fun close() {}
}

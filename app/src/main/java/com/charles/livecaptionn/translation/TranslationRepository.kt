package com.charles.livecaptionn.translation

interface TranslationRepository {
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
}

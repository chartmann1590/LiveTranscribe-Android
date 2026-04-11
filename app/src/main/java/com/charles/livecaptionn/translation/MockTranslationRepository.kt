package com.charles.livecaptionn.translation

class MockTranslationRepository : TranslationRepository {
    override suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String {
        return "[mock $sourceCode->$targetCode] ${text.trim()}"
    }
}

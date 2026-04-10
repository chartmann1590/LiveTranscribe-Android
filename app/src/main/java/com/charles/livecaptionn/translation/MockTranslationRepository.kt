package com.charles.livecaptionn.translation

import com.charles.livecaptionn.settings.AppLanguage

class MockTranslationRepository : TranslationRepository {
    override suspend fun translate(
        text: String,
        source: AppLanguage,
        target: AppLanguage,
        autoDetect: Boolean
    ): String {
        return "[mock ${source.code}->${target.code}] ${text.trim()}"
    }
}

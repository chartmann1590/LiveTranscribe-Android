package com.charles.livecaptionn.translation

import com.charles.livecaptionn.settings.AppLanguage

interface TranslationRepository {
    suspend fun translate(
        text: String,
        source: AppLanguage,
        target: AppLanguage,
        autoDetect: Boolean
    ): String
}

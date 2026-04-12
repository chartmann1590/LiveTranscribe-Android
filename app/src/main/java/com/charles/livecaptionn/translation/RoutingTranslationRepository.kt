package com.charles.livecaptionn.translation

import com.charles.livecaptionn.data.SettingsRepository
import com.charles.livecaptionn.settings.TranslationBackend
import kotlinx.coroutines.flow.first

/**
 * Dispatches each translation call to the backend selected in user settings.
 *
 * The repository is intentionally pass-through: both child repositories are
 * cheap to keep alive (ML Kit lazily downloads models, LibreTranslate lazily
 * builds Retrofit clients), so we can flip between them per-call without
 * re-initializing anything.
 */
class RoutingTranslationRepository(
    private val settingsRepository: SettingsRepository,
    private val mlKit: TranslationRepository,
    private val libre: TranslationRepository
) : TranslationRepository {

    override suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String {
        val backend = settingsRepository.settingsFlow.first().translationBackend
        val delegate = when (backend) {
            TranslationBackend.ML_KIT -> mlKit
            TranslationBackend.LIBRE_TRANSLATE -> libre
        }
        return delegate.translate(text, sourceCode, targetCode, autoDetect)
    }
}

package com.charles.livecaptionn.translation

import com.charles.livecaptionn.data.SettingsRepository
import com.charles.livecaptionn.settings.TranslationBackend
import kotlinx.coroutines.flow.first

/**
 * Dispatches each translation call to the backend selected in user settings.
 *
 * Caches the active backend so we don't read settings flow on every call.
 * Settings changes are rare relative to translation call frequency, so a
 * lazy fresh-on-mismatch strategy avoids coroutine overhead per call.
 */
class RoutingTranslationRepository(
    private val settingsRepository: SettingsRepository,
    private val mlKit: TranslationRepository,
    private val libre: TranslationRepository
) : TranslationRepository {

    private var cachedBackend: TranslationBackend? = null
    private var cachedDelegate: TranslationRepository? = null

    override suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String = activeDelegate().translate(text, sourceCode, targetCode, autoDetect)

    override suspend fun prewarm(sourceCode: String, targetCode: String) {
        activeDelegate().prewarm(sourceCode, targetCode)
    }

    private suspend fun activeDelegate(): TranslationRepository {
        val backend = settingsRepository.settingsFlow.first().translationBackend
        if (cachedBackend == backend && cachedDelegate != null) {
            return cachedDelegate!!
        }
        val delegate = when (backend) {
            TranslationBackend.ML_KIT -> mlKit
            TranslationBackend.LIBRE_TRANSLATE -> libre
        }
        cachedBackend = backend
        cachedDelegate = delegate
        return delegate
    }

    override fun close() {
        mlKit.close()
        libre.close()
    }
}

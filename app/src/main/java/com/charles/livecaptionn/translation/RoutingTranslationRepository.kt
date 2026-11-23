package com.charles.livecaptionn.translation

import com.charles.livecaptionn.data.SettingsRepository
import com.charles.livecaptionn.settings.TranslationBackend
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    private val mutableStatus = MutableStateFlow(TranslationStatus())
    override val statusFlow: StateFlow<TranslationStatus> = mutableStatus.asStateFlow()

    @Volatile
    private var cachedBackend: TranslationBackend? = null
    @Volatile
    private var cachedDelegate: TranslationRepository? = null

    override suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String {
        val delegate = activeDelegate()
        return withDelegateStatus(delegate) {
            delegate.translate(text, sourceCode, targetCode, autoDetect)
        }
    }

    override suspend fun prewarm(sourceCode: String, targetCode: String) {
        val delegate = activeDelegate()
        withDelegateStatus(delegate) {
            delegate.prewarm(sourceCode, targetCode)
        }
    }

    private suspend fun <T> withDelegateStatus(
        delegate: TranslationRepository,
        block: suspend () -> T
    ): T = coroutineScope {
        mutableStatus.value = delegate.statusFlow.value
        val mirrorJob = launch {
            delegate.statusFlow.collect { status ->
                mutableStatus.value = status
            }
        }
        try {
            block()
        } finally {
            mirrorJob.cancel()
            mutableStatus.value = delegate.statusFlow.value
        }
    }

    private suspend fun activeDelegate(): TranslationRepository {
        val backend = settingsRepository.settingsFlow.first().translationBackend
        val existing = cachedDelegate
        if (cachedBackend == backend && existing != null) {
            return existing
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

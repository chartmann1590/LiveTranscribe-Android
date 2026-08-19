package com.charles.livecaptionn.ui.l10n

import android.content.Context
import android.util.Log
import com.charles.livecaptionn.data.SettingsRepository
import com.charles.livecaptionn.translation.MlKitTranslationRepository
import com.charles.livecaptionn.translation.TranslationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Localizes the whole app UI via Google ML Kit with a server fallback.
 *
 * When the user picks a non-English interface language this:
 *   1. probes the en→target ML Kit model download (one-time, ~30 MB — the same
 *      models the caption service already uses),
 *   2. translates every string in [UiStringCatalog.ALL], preferring on-device
 *      ML Kit; if the model download stalls (GMS delivery can be gated on some
 *      devices/networks) it falls back to the configured translation server,
 *   3. caches the result so later launches render instantly without
 *      re-downloading the model or re-translating anything.
 *
 * English output is the pipeline's safety net: every translation failure falls
 * back to the English source, so the UI is always fully readable even while a
 * model is downloading or a machine translation is imperfect.
 */
class UiLocalizationRepository(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val translator: TranslationRepository = MlKitTranslationRepository(),
    private val fallbackTranslator: TranslationRepository? = null
) {
    private val cache = UiTranslationCache(context)

    /** Which phase of UI localization is running, shown to the user. */
    enum class UiLocalizationStage { DOWNLOADING, TRANSLATING }

    data class State(
        val languageCode: String = "en",
        val strings: UiStrings = UiStrings.EMPTY,
        val modelBusy: Boolean = false,
        val stage: UiLocalizationStage = UiLocalizationStage.DOWNLOADING,
        val translatedCount: Int = 0,
        val translatedTotal: Int = 0,
        val fromCache: Boolean = false,
        val error: String? = null
    ) {
        val isEnglish: Boolean get() = languageCode.equals("en", true)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Bumped on every select so a stale slow translation can't overwrite a newer one. */
    private var generation = 0

    /**
     * Called once at app startup to restore the persisted interface language.
     * Renders instantly from cache; kicks off a background re-translation
     * otherwise.
     */
    suspend fun restore() {
        val saved = settingsRepository.settingsFlow.first().uiLanguageCode.orEmpty()
            .ifBlank { "en" }
        if (saved.equals("en", true)) {
            _state.value = State(languageCode = "en")
            return
        }
        val cached = cache.read(saved)
        if (cached != null && cached.isNotEmpty()) {
            _state.value = State(
                languageCode = saved,
                strings = UiStrings(cached),
                fromCache = true
            )
        } else {
            select(saved)
        }
    }

    /** Switches (and persists) the interface language. */
    fun select(languageCode: String) {
        val lang = languageCode.orEmpty().ifBlank { "en" }
        scope.launch {
            settingsRepository.update { it.copy(uiLanguageCode = lang) }
            selectInternal(lang)
        }
    }

    /** Synchronous lookup for non-Compose consumers (overlay, notifications). */
    fun latestStrings(): UiStrings = _state.value.strings

    private suspend fun selectInternal(languageCode: String) {
        val lang = languageCode.ifBlank { "en" }
        val gen = ++generation
        if (lang.equals("en", true)) {
            _state.value = State(languageCode = "en")
            return
        }
        // Cache hit → instant, no model download or translation work.
        val cached = cache.read(lang)
        if (cached != null && cached.isNotEmpty()) {
            _state.value = State(
                languageCode = lang,
                strings = UiStrings(cached),
                fromCache = true,
                error = null
            )
            return
        }
        _state.value = State(
            languageCode = lang,
            modelBusy = true,
            stage = UiLocalizationStage.DOWNLOADING,
            error = null
        )
        try {
            // A stalled model download must not hang the UI forever — give the
            // whole pipeline (download + translate) a deadline.
            val translated = withTimeout(DOWNLOAD_TIMEOUT_MS) { translateWithPrimary(lang) }
            cache.write(lang, translated)
            if (generation == gen) {
                _state.value = State(
                    languageCode = lang,
                    strings = UiStrings(translated),
                    modelBusy = false
                )
            }
            Log.i(TAG, "UI translated to $lang via ML Kit (${translated.size} strings)")
        } catch (t: Throwable) {
            val cause = if (t is TimeoutCancellationException) "timed out" else t.message.orEmpty()
            Log.w(TAG, "ML Kit UI translation failed for $lang: $cause; falling back to server", t)
            if (generation != gen) return
            val fallback = fallbackTranslator
            if (fallback != null) {
                _state.value = _state.value.copy(
                    modelBusy = true,
                    stage = UiLocalizationStage.TRANSLATING,
                    translatedCount = 0,
                    translatedTotal = UiStringCatalog.ALL.distinct().filter { it.isNotBlank() }.size,
                    error = null
                )
                try {
                    val translated = withTimeout(DOWNLOAD_TIMEOUT_MS) { translateWithServer(fallback, lang) }
                    cache.write(lang, translated)
                    if (generation == gen) {
                        _state.value = State(
                            languageCode = lang,
                            strings = UiStrings(translated),
                            modelBusy = false
                        )
                    }
                    Log.i(TAG, "UI translated to $lang via server fallback (${translated.size} strings)")
                } catch (t2: Throwable) {
                    val cause2 = if (t2 is TimeoutCancellationException) "timed out" else t2.message.orEmpty()
                    Log.w(TAG, "Server UI translation failed for $lang: $cause2", t2)
                    if (generation == gen) {
                        _state.value = _state.value.copy(modelBusy = false, error = "Translation failed: $cause2")
                    }
                }
            } else {
                _state.value = _state.value.copy(modelBusy = false, error = "Translation failed: $cause")
            }
        }
    }

    private suspend fun translateWithPrimary(targetCode: String): Map<String, String> {
        val catalog = UiStringCatalog.ALL.distinct().filter { it.isNotBlank() }
        Log.i(TAG, "Downloading en → $targetCode translation model…")
        _state.value = _state.value.copy(stage = UiLocalizationStage.DOWNLOADING)
        // Short probe on the on-device download. If GMS delivery is stalled the
        // probe times out and the caller bails to the server fallback instead of
        // spinning on a model that never lands.
        val mlKit = translator as? MlKitTranslationRepository
        if (mlKit != null) {
            withTimeout(DOWNLOAD_PROBE_MS) { mlKit.requireModel("en", targetCode) }
        } else {
            translator.prewarm("en", targetCode)
        }
        Log.i(TAG, "Model ready; translating ${catalog.size} strings to $targetCode")
        _state.value = _state.value.copy(stage = UiLocalizationStage.TRANSLATING, translatedCount = 0)
        return translateCatalog(catalog, targetCode, translator)
    }

    private suspend fun translateWithServer(engine: TranslationRepository, targetCode: String): Map<String, String> {
        val catalog = UiStringCatalog.ALL.distinct().filter { it.isNotBlank() }
        Log.i(TAG, "Translating ${catalog.size} strings to $targetCode via server")
        _state.value = _state.value.copy(
            stage = UiLocalizationStage.TRANSLATING,
            translatedCount = 0,
            translatedTotal = catalog.size
        )
        return translateCatalog(catalog, targetCode, engine)
    }

    private suspend fun translateCatalog(
        catalog: List<String>,
        targetCode: String,
        engine: TranslationRepository
    ): Map<String, String> {
        val result = HashMap<String, String>()
        catalog.forEachIndexed { index, english ->
            val translated = translateTemplate(english, targetCode, engine)
            if (translated.isNotBlank() && translated != english) {
                result[english] = translated
            }
            if ((index + 1) % 5 == 0 || index + 1 == catalog.size) {
                _state.value = _state.value.copy(
                    translatedCount = index + 1,
                    translatedTotal = catalog.size
                )
            }
        }
        return result
    }

    private suspend fun translateTemplate(
        english: String,
        targetCode: String,
        engine: TranslationRepository
    ): String {
        val original = english.trim()
        if (!UiStringCatalog.isSegmentTranslatable(original)) return original
        val translated = engine.translate(original, "en", targetCode, autoDetect = false)
            .trim()
            .ifEmpty { original }
        // A machine translation that lost the %-placeholders would crash
        // String.format at render time; keep the English template instead.
        if (UiStringCatalog.placeholderCount(translated) != UiStringCatalog.placeholderCount(original)) {
            return original
        }
        return translated
    }

    companion object {
        private const val TAG = "UiLocalization"
        /** Ceiling for a whole translate pass so a stalled engine surfaces an
         *  error instead of an endless busy spinner. */
        private const val DOWNLOAD_TIMEOUT_MS = 6 * 60_000L
        /** How long the on-device model download may take before the pipeline
         *  bails to the server fallback. */
        private const val DOWNLOAD_PROBE_MS = 60_000L
    }
}
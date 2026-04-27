package com.charles.livecaptionn.ui

import com.charles.livecaptionn.service.CaptionRuntimeState
import com.charles.livecaptionn.settings.CaptionSettings
import com.charles.livecaptionn.settings.Language
import com.charles.livecaptionn.settings.TranslationBackend
import com.charles.livecaptionn.speech.VoskModelInfo
import com.charles.livecaptionn.translation.MlKitLanguages
import com.charles.livecaptionn.update.UpdateInfo

data class MainUiState(
    val settings: CaptionSettings = CaptionSettings(),
    val runtime: CaptionRuntimeState = CaptionRuntimeState(),
    val micPermissionGranted: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val libreLanguages: List<Language> = emptyList(),
    val libreLoading: Boolean = false,
    val libreError: String? = null,
    val voskModels: List<VoskModelInfo> = emptyList(),
    val voskDownloadProgress: Map<String, Float> = emptyMap(),
    val availableUpdate: UpdateInfo? = null,
    /** True when the running APK was installed from the Google Play Store. Used
     *  to warn Play Store users before they grab a (potentially unstable)
     *  GitHub release that would break Play auto-update. */
    val installedFromPlayStore: Boolean = false
) {
    /** Languages the user is allowed to pick as the speech source, given the
     *  current audio source + STT backend combination. */
    val availableSourceLanguages: List<Language>
        get() {
            val installedVosk = voskModels.filter { it.installed }
            return when (settings.sttBackend) {
                // Vosk does the recognition for BOTH mic and system audio, so
                // the picker has to limit to languages with an installed model
                // either way — otherwise the user picks something we can't
                // recognize and StreamingSttEngine errors with "No on-device
                // model installed".
                com.charles.livecaptionn.settings.SttBackend.LOCAL_VOSK ->
                    installedVosk
                        .distinctBy { it.languageCode.lowercase() }
                        .map { Language(it.languageCode, it.languageName) }
                // Remote Whisper (system audio) and AndroidSpeechRecognizer
                // (mic) can in principle handle any locale the translation
                // engine supports.
                com.charles.livecaptionn.settings.SttBackend.REMOTE_WHISPER ->
                    translationLanguages()
            }
        }

    /** Target language list is driven by whichever translation engine is active. */
    val availableTargetLanguages: List<Language>
        get() = translationLanguages()

    private fun translationLanguages(): List<Language> = when (settings.translationBackend) {
        TranslationBackend.ML_KIT -> MlKitLanguages.LIST
        TranslationBackend.LIBRE_TRANSLATE ->
            if (libreLanguages.isNotEmpty()) libreLanguages else Language.FALLBACK
    }
}

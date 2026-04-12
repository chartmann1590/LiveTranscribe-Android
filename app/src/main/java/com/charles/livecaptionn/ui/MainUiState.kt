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
    val availableUpdate: UpdateInfo? = null
) {
    /** Languages the user is allowed to pick as the speech source, given the
     *  current audio source + STT backend combination. */
    val availableSourceLanguages: List<Language>
        get() {
            val installedVosk = voskModels.filter { it.installed }
            return when {
                // System audio + local Vosk: hard limit to what's on device.
                settings.audioSource == com.charles.livecaptionn.settings.AudioSource.SYSTEM &&
                    settings.sttBackend == com.charles.livecaptionn.settings.SttBackend.LOCAL_VOSK -> {
                    installedVosk
                        .distinctBy { it.languageCode.lowercase() }
                        .map { Language(it.languageCode, it.languageName) }
                }
                // Every other path can, in principle, recognise whatever the
                // translation engine supports — the UI also shows a hint so
                // the user knows their locale may not actually be available.
                else -> translationLanguages()
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

package com.charles.livecaptionn.ui

import com.charles.livecaptionn.service.CaptionRuntimeState
import com.charles.livecaptionn.settings.CaptionSettings
import com.charles.livecaptionn.settings.Language
import com.charles.livecaptionn.speech.VoskModelInfo

data class MainUiState(
    val settings: CaptionSettings = CaptionSettings(),
    val runtime: CaptionRuntimeState = CaptionRuntimeState(),
    val micPermissionGranted: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val libreLanguages: List<Language> = emptyList(),
    val libreLoading: Boolean = false,
    val libreError: String? = null,
    val voskModels: List<VoskModelInfo> = emptyList(),
    val voskDownloadProgress: Map<String, Float> = emptyMap()
) {
    /** Languages the user is allowed to pick as the speech source, given the
     *  current audio source + STT backend combination. */
    val availableSourceLanguages: List<Language>
        get() {
            val installedVosk = voskModels.filter { it.installed }
            val libre = if (libreLanguages.isNotEmpty()) libreLanguages else Language.FALLBACK
            return when {
                // System audio + local Vosk: hard limit to what's on device.
                settings.audioSource == com.charles.livecaptionn.settings.AudioSource.SYSTEM &&
                    settings.sttBackend == com.charles.livecaptionn.settings.SttBackend.LOCAL_VOSK -> {
                    installedVosk.map { Language(it.languageCode, it.languageName) }
                }
                // Every other path can, in principle, recognise whatever the
                // translation server supports — the UI also shows a hint so
                // the user knows their locale may not actually be available.
                else -> libre
            }
        }

    /** Target language list is always driven by the translation server. */
    val availableTargetLanguages: List<Language>
        get() = if (libreLanguages.isNotEmpty()) libreLanguages else Language.FALLBACK
}

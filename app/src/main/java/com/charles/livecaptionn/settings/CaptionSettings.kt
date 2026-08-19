package com.charles.livecaptionn.settings

import com.charles.livecaptionn.BuildConfig

data class CaptionSettings(
    val sourceLanguageCode: String = "en",
    val targetLanguageCode: String = "vi",
    val autoDetectSource: Boolean = false,
    val textSizeSp: Float = 20f,
    val overlayOpacity: Float = 0.65f,
    val showOriginal: Boolean = true,
    val serverBaseUrl: String = DEFAULT_BASE_URL,
    val overlayX: Int = 60,
    val overlayY: Int = 220,
    val overlayMinimized: Boolean = false,
    val audioSource: AudioSource = AudioSource.MIC,
    val sttBackend: SttBackend = SttBackend.LOCAL_VOSK,
    val translationBackend: TranslationBackend = TranslationBackend.ML_KIT,
    val sttBaseUrl: String = DEFAULT_STT_URL,
    val overlayWidthDp: Int = DEFAULT_OVERLAY_WIDTH_DP,
    val overlayHeightDp: Int = DEFAULT_OVERLAY_HEIGHT_DP,
    /** Pro perk. Defaults render identically to the app's original look. */
    val overlayThemeId: String = "default",
    val overlayFontId: String = "default",
    /** Interface (UI) language, defaulting to English until the user opts in
     *  to an on-device ML Kit translation. Not the caption source language. */
    val uiLanguageCode: String = "en",
    /** True once the first-launch onboarding has been completed. */
    val onboardingComplete: Boolean = false
) {
    companion object {
        val DEFAULT_BASE_URL: String = BuildConfig.DEFAULT_TRANSLATE_URL
        val DEFAULT_STT_URL: String = BuildConfig.DEFAULT_STT_URL
        const val DEFAULT_OVERLAY_WIDTH_DP = 320
        const val DEFAULT_OVERLAY_HEIGHT_DP = 180
        const val MIN_OVERLAY_WIDTH_DP = 180
        const val MIN_OVERLAY_HEIGHT_DP = 80
    }
}

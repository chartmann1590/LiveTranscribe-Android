package com.charles.livecaptionn.settings

import com.charles.livecaptionn.BuildConfig

data class CaptionSettings(
    val sourceLanguage: AppLanguage = AppLanguage.ENGLISH,
    val targetLanguage: AppLanguage = AppLanguage.VIETNAMESE,
    val autoDetectSource: Boolean = false,
    val textSizeSp: Float = 20f,
    val overlayOpacity: Float = 0.65f,
    val showOriginal: Boolean = true,
    val serverBaseUrl: String = DEFAULT_BASE_URL,
    val overlayX: Int = 60,
    val overlayY: Int = 220,
    val overlayMinimized: Boolean = false,
    val audioSource: AudioSource = AudioSource.MIC,
    val sttBaseUrl: String = DEFAULT_STT_URL,
    val overlayWidthDp: Int = DEFAULT_OVERLAY_WIDTH_DP,
    val overlayHeightDp: Int = DEFAULT_OVERLAY_HEIGHT_DP
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

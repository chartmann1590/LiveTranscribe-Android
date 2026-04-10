package com.charles.livecaptionn.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.charles.livecaptionn.settings.AppLanguage
import com.charles.livecaptionn.settings.AudioSource
import com.charles.livecaptionn.settings.CaptionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "caption_settings")

class SettingsDataStore(private val context: Context) : SettingsRepository {
    override val settingsFlow: Flow<CaptionSettings> = context.dataStore.data.map { p ->
        CaptionSettings(
            sourceLanguage = AppLanguage.fromCode(p[SOURCE] ?: CaptionSettings().sourceLanguage.code),
            targetLanguage = AppLanguage.fromCode(p[TARGET] ?: CaptionSettings().targetLanguage.code),
            autoDetectSource = p[AUTO_DETECT] ?: false,
            textSizeSp = p[TEXT_SIZE] ?: 20f,
            overlayOpacity = p[OPACITY] ?: 0.65f,
            showOriginal = p[SHOW_ORIGINAL] ?: true,
            serverBaseUrl = p[BASE_URL] ?: CaptionSettings.DEFAULT_BASE_URL,
            overlayX = p[OVERLAY_X] ?: 60,
            overlayY = p[OVERLAY_Y] ?: 220,
            overlayMinimized = p[OVERLAY_MIN] ?: false,
            audioSource = AudioSource.fromName(p[AUDIO_SOURCE] ?: AudioSource.MIC.name),
            sttBaseUrl = p[STT_URL] ?: CaptionSettings.DEFAULT_STT_URL,
            overlayWidthDp = p[OVERLAY_W] ?: CaptionSettings.DEFAULT_OVERLAY_WIDTH_DP,
            overlayHeightDp = p[OVERLAY_H] ?: CaptionSettings.DEFAULT_OVERLAY_HEIGHT_DP
        )
    }

    override suspend fun update(transform: (CaptionSettings) -> CaptionSettings) {
        context.dataStore.edit { p ->
            val curr = CaptionSettings(
                sourceLanguage = AppLanguage.fromCode(p[SOURCE] ?: CaptionSettings().sourceLanguage.code),
                targetLanguage = AppLanguage.fromCode(p[TARGET] ?: CaptionSettings().targetLanguage.code),
                autoDetectSource = p[AUTO_DETECT] ?: false,
                textSizeSp = p[TEXT_SIZE] ?: 20f,
                overlayOpacity = p[OPACITY] ?: 0.65f,
                showOriginal = p[SHOW_ORIGINAL] ?: true,
                serverBaseUrl = p[BASE_URL] ?: CaptionSettings.DEFAULT_BASE_URL,
                overlayX = p[OVERLAY_X] ?: 60,
                overlayY = p[OVERLAY_Y] ?: 220,
                overlayMinimized = p[OVERLAY_MIN] ?: false,
                audioSource = AudioSource.fromName(p[AUDIO_SOURCE] ?: AudioSource.MIC.name),
                sttBaseUrl = p[STT_URL] ?: CaptionSettings.DEFAULT_STT_URL,
                overlayWidthDp = p[OVERLAY_W] ?: CaptionSettings.DEFAULT_OVERLAY_WIDTH_DP,
                overlayHeightDp = p[OVERLAY_H] ?: CaptionSettings.DEFAULT_OVERLAY_HEIGHT_DP
            )
            val next = transform(curr)
            p[SOURCE] = next.sourceLanguage.code
            p[TARGET] = next.targetLanguage.code
            p[AUTO_DETECT] = next.autoDetectSource
            p[TEXT_SIZE] = next.textSizeSp
            p[OPACITY] = next.overlayOpacity.coerceIn(0.2f, 1f)
            p[SHOW_ORIGINAL] = next.showOriginal
            p[BASE_URL] = next.serverBaseUrl.trim()
            p[OVERLAY_X] = next.overlayX
            p[OVERLAY_Y] = next.overlayY
            p[OVERLAY_MIN] = next.overlayMinimized
            p[AUDIO_SOURCE] = next.audioSource.name
            p[STT_URL] = next.sttBaseUrl.trim()
            p[OVERLAY_W] = next.overlayWidthDp
            p[OVERLAY_H] = next.overlayHeightDp
        }
    }

    private companion object {
        val SOURCE = stringPreferencesKey("source")
        val TARGET = stringPreferencesKey("target")
        val AUTO_DETECT = booleanPreferencesKey("auto_detect")
        val TEXT_SIZE = floatPreferencesKey("text_size")
        val OPACITY = floatPreferencesKey("opacity")
        val SHOW_ORIGINAL = booleanPreferencesKey("show_original")
        val BASE_URL = stringPreferencesKey("base_url")
        val OVERLAY_X = intPreferencesKey("overlay_x")
        val OVERLAY_Y = intPreferencesKey("overlay_y")
        val OVERLAY_MIN = booleanPreferencesKey("overlay_min")
        val AUDIO_SOURCE = stringPreferencesKey("audio_source")
        val STT_URL = stringPreferencesKey("stt_url")
        val OVERLAY_W = intPreferencesKey("overlay_w")
        val OVERLAY_H = intPreferencesKey("overlay_h")
    }
}

package com.charles.livecaptionn.data

import com.charles.livecaptionn.settings.CaptionSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settingsFlow: Flow<CaptionSettings>
    suspend fun update(transform: (CaptionSettings) -> CaptionSettings)
}

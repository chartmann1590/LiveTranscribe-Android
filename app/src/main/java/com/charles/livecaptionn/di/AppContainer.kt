package com.charles.livecaptionn.di

import android.content.Context
import com.charles.livecaptionn.data.SettingsDataStore
import com.charles.livecaptionn.data.SettingsRepository
import com.charles.livecaptionn.data.TranscriptHistoryStore
import com.charles.livecaptionn.service.CaptionRuntimeStore
import com.charles.livecaptionn.translation.LibreTranslateRepository
import com.charles.livecaptionn.translation.MockTranslationRepository
import com.charles.livecaptionn.translation.TranslationRepository

class AppContainer(context: Context) {
    val settingsRepository: SettingsRepository = SettingsDataStore(context.applicationContext)
    val runtimeStore: CaptionRuntimeStore = CaptionRuntimeStore()
    val translationRepository: TranslationRepository = LibreTranslateRepository(settingsRepository)
    val mockTranslationRepository: TranslationRepository = MockTranslationRepository()
    val transcriptHistory: TranscriptHistoryStore = TranscriptHistoryStore(context.applicationContext)
}

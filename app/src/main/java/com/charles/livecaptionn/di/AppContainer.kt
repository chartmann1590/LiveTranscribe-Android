package com.charles.livecaptionn.di

import android.content.Context
import com.charles.livecaptionn.data.SettingsDataStore
import com.charles.livecaptionn.data.SettingsRepository
import com.charles.livecaptionn.data.TranscriptHistoryStore
import com.charles.livecaptionn.service.CaptionRuntimeStore
import com.charles.livecaptionn.speech.LocalVoskSttClient
import com.charles.livecaptionn.speech.VoskModelRegistry
import com.charles.livecaptionn.translation.LanguageCatalogStore
import com.charles.livecaptionn.translation.LibreTranslateRepository
import com.charles.livecaptionn.translation.MockTranslationRepository
import com.charles.livecaptionn.translation.TranslationRepository
import com.charles.livecaptionn.update.UpdateChecker
import com.charles.livecaptionn.update.UpdateNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    /** Long-lived scope for container-owned background work (catalog fetches etc.). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository: SettingsRepository = SettingsDataStore(context.applicationContext)
    val runtimeStore: CaptionRuntimeStore = CaptionRuntimeStore()
    val translationRepository: TranslationRepository = LibreTranslateRepository(settingsRepository)
    val mockTranslationRepository: TranslationRepository = MockTranslationRepository()
    val transcriptHistory: TranscriptHistoryStore = TranscriptHistoryStore(context.applicationContext)
    val voskRegistry: VoskModelRegistry = VoskModelRegistry(context.applicationContext)
    val localVoskClient: LocalVoskSttClient = LocalVoskSttClient(voskRegistry)
    val languageCatalogStore: LanguageCatalogStore = LanguageCatalogStore(settingsRepository, appScope)
    val updateChecker: UpdateChecker = UpdateChecker()
    val updateNotifier: UpdateNotifier = UpdateNotifier(context.applicationContext)
}

package com.charles.livecaptionn.di

import android.content.Context
import com.charles.livecaptionn.billing.PremiumRepository
import com.charles.livecaptionn.data.PremiumLocalStore
import com.charles.livecaptionn.data.SettingsDataStore
import com.charles.livecaptionn.data.SettingsRepository
import com.charles.livecaptionn.data.TranscriptHistoryStore
import com.charles.livecaptionn.data.CaptionProfileRepository
import com.charles.livecaptionn.data.FileCaptionProfileRepository
import com.charles.livecaptionn.data.FileGlossaryRepository
import com.charles.livecaptionn.data.GlossaryRepository
import com.charles.livecaptionn.data.feedback.BugReportRepo
import com.charles.livecaptionn.service.CaptionRuntimeStore
import com.charles.livecaptionn.speech.LocalVoskSttClient
import com.charles.livecaptionn.speech.VoskModelRegistry
import com.charles.livecaptionn.translation.LanguageCatalogStore
import com.charles.livecaptionn.translation.LibreTranslateRepository
import com.charles.livecaptionn.translation.MlKitTranslationRepository
import com.charles.livecaptionn.translation.MockTranslationRepository
import com.charles.livecaptionn.translation.RoutingTranslationRepository
import com.charles.livecaptionn.translation.TranslationRepository
import com.charles.livecaptionn.ui.l10n.UiLocalizationRepository
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
    private val libreTranslationRepository: TranslationRepository = LibreTranslateRepository(settingsRepository)
    private val mlKitTranslationRepository: TranslationRepository = MlKitTranslationRepository()
    val translationRepository: TranslationRepository = RoutingTranslationRepository(
        settingsRepository = settingsRepository,
        mlKit = mlKitTranslationRepository,
        libre = libreTranslationRepository
    )
    val mockTranslationRepository: TranslationRepository = MockTranslationRepository()
    val transcriptHistory: TranscriptHistoryStore = TranscriptHistoryStore(context.applicationContext)
    val captionProfiles: CaptionProfileRepository = FileCaptionProfileRepository(context.applicationContext)
    val glossary: GlossaryRepository = FileGlossaryRepository(context.applicationContext)
    val voskRegistry: VoskModelRegistry = VoskModelRegistry(context.applicationContext)
    val localVoskClient: LocalVoskSttClient = LocalVoskSttClient(voskRegistry)
    val languageCatalogStore: LanguageCatalogStore = LanguageCatalogStore(settingsRepository, appScope)
    val updateChecker: UpdateChecker = UpdateChecker()
    val bugReportRepo: BugReportRepo = BugReportRepo(context.applicationContext)
    val premiumStore: PremiumLocalStore = PremiumLocalStore(context.applicationContext)
    val premiumRepository: PremiumRepository =
        createPremiumRepository(context.applicationContext, premiumStore, appScope)
    // Interface language. Prefers on-device ML Kit; falls back to the
    // configured LibreTranslate server when the model download is gated.
    val uiLocalization: UiLocalizationRepository = UiLocalizationRepository(
        context = context.applicationContext,
        settingsRepository = settingsRepository,
        scope = appScope,
        fallbackTranslator = libreTranslationRepository
    )
    val updateNotifier: UpdateNotifier = UpdateNotifier(
        context.applicationContext,
        uiStringsProvider = { uiLocalization.latestStrings() }
    )
}

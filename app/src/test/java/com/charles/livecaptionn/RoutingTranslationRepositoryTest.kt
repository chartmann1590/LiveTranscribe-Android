package com.charles.livecaptionn

import com.charles.livecaptionn.data.SettingsRepository
import com.charles.livecaptionn.settings.CaptionSettings
import com.charles.livecaptionn.settings.TranslationBackend
import com.charles.livecaptionn.translation.RoutingTranslationRepository
import com.charles.livecaptionn.translation.TranslationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutingTranslationRepositoryTest {

    @Test
    fun translate_routesToMlKit() = runTest {
        val mlKit = FakeTranslationRepository("mlkit-translated")
        val libre = FakeTranslationRepository("libre-translated")
        val repo = RoutingTranslationRepository(
            settingsRepository = FakeSettingsRepo(TranslationBackend.ML_KIT),
            mlKit = mlKit,
            libre = libre
        )
        val result = repo.translate("hello", "en", "vi", autoDetect = false)
        assertEquals("mlkit-translated", result)
        assertEquals(1, mlKit.translateCount)
        assertEquals(0, libre.translateCount)
    }

    @Test
    fun translate_routesToLibre() = runTest {
        val mlKit = FakeTranslationRepository("mlkit")
        val libre = FakeTranslationRepository("libre-translated")
        val repo = RoutingTranslationRepository(
            settingsRepository = FakeSettingsRepo(TranslationBackend.LIBRE_TRANSLATE),
            mlKit = mlKit,
            libre = libre
        )
        val result = repo.translate("hello", "en", "vi", autoDetect = false)
        assertEquals("libre-translated", result)
        assertEquals(0, mlKit.translateCount)
        assertEquals(1, libre.translateCount)
    }

    @Test
    fun translate_cachesBackend() = runTest {
        val mlKit = FakeTranslationRepository("mlkit")
        val libre = FakeTranslationRepository("libre")
        val repo = RoutingTranslationRepository(
            settingsRepository = FakeSettingsRepo(TranslationBackend.ML_KIT),
            mlKit = mlKit,
            libre = libre
        )
        repo.translate("a", "en", "vi", autoDetect = false)
        repo.translate("b", "en", "vi", autoDetect = false)
        repo.translate("c", "en", "vi", autoDetect = false)
        assertEquals(3, mlKit.translateCount)
        assertEquals(0, libre.translateCount)
    }

    @Test
    fun prewarm_routesCorrectly() = runTest {
        val mlKit = FakeTranslationRepository("x")
        val libre = FakeTranslationRepository("y")
        val repo = RoutingTranslationRepository(
            settingsRepository = FakeSettingsRepo(TranslationBackend.ML_KIT),
            mlKit = mlKit,
            libre = libre
        )
        repo.prewarm("en", "vi")
        assertEquals(1, mlKit.prewarmCount)
    }
}

private class FakeTranslationRepository(private val result: String) : TranslationRepository {
    var translateCount = 0
    var prewarmCount = 0

    override suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String {
        translateCount++
        return result
    }

    override suspend fun prewarm(sourceCode: String, targetCode: String) {
        prewarmCount++
    }
}

private class FakeSettingsRepo(private val backend: TranslationBackend) : SettingsRepository {
    override val settingsFlow: StateFlow<CaptionSettings> =
        MutableStateFlow(CaptionSettings(translationBackend = backend))

    override suspend fun update(transform: (CaptionSettings) -> CaptionSettings) {}
}

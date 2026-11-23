package com.charles.livecaptionn

import com.charles.livecaptionn.data.SettingsRepository
import com.charles.livecaptionn.settings.CaptionSettings
import com.charles.livecaptionn.settings.TranslationBackend
import com.charles.livecaptionn.translation.RoutingTranslationRepository
import com.charles.livecaptionn.translation.TranslationRepository
import com.charles.livecaptionn.translation.TranslationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun statusFlow_propagatesFromActiveDelegate() = runTest {
        val mlKit = FakeTranslationRepository(
            result = "",
            nextStatus = TranslationStatus(isError = true, message = "ML Kit download failed")
        )
        val libre = FakeTranslationRepository(
            result = "",
            nextStatus = TranslationStatus(isError = true, message = "LibreTranslate unreachable")
        )
        val settingsRepo = FakeSettingsRepo(TranslationBackend.ML_KIT)
        val repo = RoutingTranslationRepository(
            settingsRepository = settingsRepo,
            mlKit = mlKit,
            libre = libre
        )

        repo.translate("hello", "en", "vi", autoDetect = false)
        assertTrue(repo.statusFlow.value.isError)
        assertEquals("ML Kit download failed", repo.statusFlow.value.message)

        settingsRepo.setBackend(TranslationBackend.LIBRE_TRANSLATE)
        repo.translate("hello", "en", "vi", autoDetect = false)
        assertTrue(repo.statusFlow.value.isError)
        assertEquals("LibreTranslate unreachable", repo.statusFlow.value.message)
    }
}

private class FakeTranslationRepository(
    private val result: String,
    private val nextStatus: TranslationStatus? = null
) : TranslationRepository {
    var translateCount = 0
    var prewarmCount = 0

    private val mutableStatus = MutableStateFlow(TranslationStatus())
    override val statusFlow: StateFlow<TranslationStatus> = mutableStatus.asStateFlow()

    override suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String {
        translateCount++
        nextStatus?.let { mutableStatus.value = it }
        return result
    }

    override suspend fun prewarm(sourceCode: String, targetCode: String) {
        prewarmCount++
        nextStatus?.let { mutableStatus.value = it }
    }
}

private class FakeSettingsRepo(initialBackend: TranslationBackend) : SettingsRepository {
    private val mutable = MutableStateFlow(CaptionSettings(translationBackend = initialBackend))
    override val settingsFlow: StateFlow<CaptionSettings> = mutable

    fun setBackend(backend: TranslationBackend) {
        mutable.value = mutable.value.copy(translationBackend = backend)
    }

    override suspend fun update(transform: (CaptionSettings) -> CaptionSettings) {
        mutable.value = transform(mutable.value)
    }
}

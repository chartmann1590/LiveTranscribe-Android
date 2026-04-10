package com.charles.livecaptionn

import com.charles.livecaptionn.settings.AppLanguage
import com.charles.livecaptionn.translation.MockTranslationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MockTranslationRepositoryTest {

    private val repo = MockTranslationRepository()

    @Test
    fun translate_returnsFormattedMockText() = runBlocking {
        val result = repo.translate("Hello", AppLanguage.ENGLISH, AppLanguage.VIETNAMESE, false)
        assertEquals("[mock en->vi] Hello", result)
    }

    @Test
    fun translate_viToEn() = runBlocking {
        val result = repo.translate("Xin chao", AppLanguage.VIETNAMESE, AppLanguage.ENGLISH, false)
        assertEquals("[mock vi->en] Xin chao", result)
    }

    @Test
    fun translate_trimsInput() = runBlocking {
        val result = repo.translate("  Hello  ", AppLanguage.ENGLISH, AppLanguage.VIETNAMESE, false)
        assertEquals("[mock en->vi] Hello", result)
    }

    @Test
    fun translate_autoDetect_doesNotAffectOutput() = runBlocking {
        val result = repo.translate("Hello", AppLanguage.ENGLISH, AppLanguage.VIETNAMESE, true)
        assertEquals("[mock en->vi] Hello", result)
    }
}

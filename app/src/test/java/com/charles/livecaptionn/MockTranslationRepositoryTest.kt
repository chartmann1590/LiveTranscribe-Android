package com.charles.livecaptionn

import com.charles.livecaptionn.translation.MockTranslationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MockTranslationRepositoryTest {

    private val repo = MockTranslationRepository()

    @Test
    fun translate_returnsFormattedMockText() = runBlocking {
        val result = repo.translate("Hello", "en", "vi", false)
        assertEquals("[mock en->vi] Hello", result)
    }

    @Test
    fun translate_viToEn() = runBlocking {
        val result = repo.translate("Xin chao", "vi", "en", false)
        assertEquals("[mock vi->en] Xin chao", result)
    }

    @Test
    fun translate_trimsInput() = runBlocking {
        val result = repo.translate("  Hello  ", "en", "vi", false)
        assertEquals("[mock en->vi] Hello", result)
    }

    @Test
    fun translate_autoDetect_doesNotAffectOutput() = runBlocking {
        val result = repo.translate("Hello", "en", "vi", true)
        assertEquals("[mock en->vi] Hello", result)
    }
}

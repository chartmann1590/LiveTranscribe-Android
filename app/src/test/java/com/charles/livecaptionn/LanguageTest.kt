package com.charles.livecaptionn

import com.charles.livecaptionn.settings.Language
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageTest {

    @Test
    fun fallbackContainsCoreLanguages() {
        val codes = Language.FALLBACK.map { it.code }
        assertEquals(true, codes.contains("en"))
        assertEquals(true, codes.contains("vi"))
        assertEquals(true, codes.contains("es"))
        assertEquals(true, codes.contains("zh"))
    }

    @Test
    fun displayNameFor_usesCatalogEntryWhenPresent() {
        val catalog = listOf(Language("en", "Inglês"), Language("vi", "Tiếng Việt"))
        assertEquals("Inglês", Language.displayNameFor("en", catalog))
        assertEquals("Tiếng Việt", Language.displayNameFor("vi", catalog))
    }

    @Test
    fun displayNameFor_fallsBackToFallbackList() {
        val name = Language.displayNameFor("fr", emptyList())
        assertEquals("French", name)
    }

    @Test
    fun displayNameFor_unknownCode_returnsUppercasedCode() {
        assertEquals("XX", Language.displayNameFor("xx", emptyList()))
    }

    @Test
    fun displayNameFor_autoCode_returnsAutoLabel() {
        assertEquals(Language.AUTO.name, Language.displayNameFor(Language.AUTO_CODE, emptyList()))
    }
}

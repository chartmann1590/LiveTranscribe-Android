package com.charles.livecaptionn

import com.charles.livecaptionn.ui.l10n.UiStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStringsTest {

    @Test
    fun emptyStrings_returnsKeyForMissingEntry() {
        val t = UiStrings.EMPTY
        assertEquals("Start", t["Start"])
        assertEquals("Start", t.get("Start"))
    }

    @Test
    fun emptyStrings_isNotLocalized() {
        assertFalse(UiStrings.EMPTY.isLocalized)
    }

    @Test
    fun mapStrings_returnsTranslationWhenPresent() {
        val t = UiStrings(mapOf("Start" to "Bắt đầu"))
        assertEquals("Bắt đầu", t["Start"])
        assertTrue(t.isLocalized)
    }

    @Test
    fun mapStrings_fallsBackToKeyWhenMissing() {
        val t = UiStrings(mapOf("Start" to "Bắt đầu"))
        assertEquals("Stop", t["Stop"])
    }

    @Test
    fun translated_returnsNullForUnknownKey() {
        val t = UiStrings(mapOf("Start" to "Bắt đầu"))
        assertNull(t.translated("Stop"))
        assertEquals("Bắt đầu", t.translated("Start"))
    }

    @Test
    fun format_substitutesArgs() {
        val t = UiStrings(mapOf("Example: %s" to "Ví dụ: %s"))
        assertEquals("Ví dụ: http://x", t.format("Example: %s", "http://x"))
    }

    @Test
    fun format_usesEnglishTemplateWhenTranslationDroppedPlaceholder() {
        // Machine translation stripped "%s" — must not crash and must keep arg.
        val t = UiStrings(mapOf("Example: %s" to "Ví dụ:"))
        assertEquals("Example: http://x", t.format("Example: %s", "http://x"))
    }

    @Test
    fun format_handlesUnicodeArgs() {
        val t = UiStrings.EMPTY
        assertEquals("Hello, 世界", t.format("Hello, %s", "世界"))
    }
}
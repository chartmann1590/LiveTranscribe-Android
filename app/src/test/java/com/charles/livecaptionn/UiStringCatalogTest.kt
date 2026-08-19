package com.charles.livecaptionn

import com.charles.livecaptionn.ui.l10n.UiStringCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStringCatalogTest {

    @Test
    fun appName_isIncludedInCatalog() {
        assertTrue(UiStringCatalog.ALL.contains(UiStringCatalog.appName))
        assertEquals("Live CaptionN", UiStringCatalog.appName)
    }

    @Test
    fun allStrings_areDistinct() {
        assertEquals(UiStringCatalog.ALL.size, UiStringCatalog.ALL.toSet().size)
    }

    @Test
    fun allStrings_areNonBlank() {
        UiStringCatalog.ALL.forEach { assertFalse(it.isBlank()) }
    }

    @Test
    fun catalog_hasNoNaiveDuplicatesFromTemplating() {
        // Format templates with %s must appear exactly once as their raw English self.
        assertTrue(UiStringCatalog.ALL.contains("Example: %s"))
        assertTrue(UiStringCatalog.ALL.contains("%s is ready to install"))
    }

    @Test
    fun placeholderCount_countsPercentSpecifiers() {
        assertEquals(0, UiStringCatalog.placeholderCount("Hello world"))
        assertEquals(1, UiStringCatalog.placeholderCount("Example: %s"))
        assertEquals(2, UiStringCatalog.placeholderCount("Comments (%d) on %s"))
        // "%d" is one specifier and "%%" (literal percent) is another.
        assertEquals(2, UiStringCatalog.placeholderCount("Opacity: %d%%"))
        assertEquals(0, UiStringCatalog.placeholderCount("100% of something"))
    }

    @Test
    fun isSegmentTranslatable_rejectsNoise() {
        assertFalse(UiStringCatalog.isSegmentTranslatable(""))
        assertFalse(UiStringCatalog.isSegmentTranslatable("   "))
        assertFalse(UiStringCatalog.isSegmentTranslatable("…"))
        assertFalse(UiStringCatalog.isSegmentTranslatable("--"))
    }

    @Test
    fun isSegmentTranslatable_acceptsRealText() {
        assertTrue(UiStringCatalog.isSegmentTranslatable("Start"))
        assertTrue(UiStringCatalog.isSegmentTranslatable("Try a different search term."))
        assertTrue(UiStringCatalog.isSegmentTranslatable("Text size: %dsp"))
    }
}
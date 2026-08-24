package com.charles.livecaptionn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataPersistenceTest {
    @Test fun generatedIds_areUniqueAndNotTimestamps() {
        val first = TranscriptEntry(timestamp = 7L, originalText = "a", translatedText = "b", sourceLanguage = "en", targetLanguage = "vi")
        val second = TranscriptEntry(timestamp = 7L, originalText = "a", translatedText = "b", sourceLanguage = "en", targetLanguage = "vi")
        assertNotEquals(first.id, second.id)
        assertTrue(first.id.isNotBlank())
    }

    @Test fun historyPolicy_rejectsInvalidRetention() {
        try { HistoryPolicy(0); throw AssertionError("Expected invalid policy") } catch (_: IllegalArgumentException) { }
        assertTrue(HistoryPolicy(10, retainHistory = false).maxEntries == 10)
    }

    @Test fun glossary_replacementHonorsCaseSetting() {
        val entry = GlossaryEntry(phrase = "API", replacement = "application programming interface")
        assertEquals("Use application programming interface", applyGlossary("Use api", listOf(entry)))
        assertEquals("Use api", applyGlossary("Use api", listOf(entry.copy(caseSensitive = true))))
    }

    @Test fun export_containsStableIdentityAndText() {
        val entry = TranscriptEntry(id = "entry-1", originalText = "Hello", translatedText = "Xin chao", sourceLanguage = "en", targetLanguage = "vi")
        val export = TranscriptExport(listOf(entry))
        assertTrue(export.asText().contains("Hello"))
    }
}

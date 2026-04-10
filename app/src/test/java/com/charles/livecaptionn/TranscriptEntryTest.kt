package com.charles.livecaptionn

import com.charles.livecaptionn.data.TranscriptEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptEntryTest {

    @Test
    fun defaultTimestamp_isNonZero() {
        val entry = TranscriptEntry(
            originalText = "Hello",
            translatedText = "Xin chao",
            sourceLanguage = "en",
            targetLanguage = "vi"
        )
        assertTrue(entry.timestamp > 0)
    }

    @Test
    fun fields_areStoredCorrectly() {
        val entry = TranscriptEntry(
            timestamp = 12345L,
            originalText = "Hello",
            translatedText = "Xin chao",
            sourceLanguage = "en",
            targetLanguage = "vi"
        )
        assertEquals(12345L, entry.timestamp)
        assertEquals("Hello", entry.originalText)
        assertEquals("Xin chao", entry.translatedText)
        assertEquals("en", entry.sourceLanguage)
        assertEquals("vi", entry.targetLanguage)
    }

    @Test
    fun copy_changesOnlySpecifiedField() {
        val entry = TranscriptEntry(
            timestamp = 100L,
            originalText = "Hello",
            translatedText = "Xin chao",
            sourceLanguage = "en",
            targetLanguage = "vi"
        )
        val modified = entry.copy(translatedText = "Chao ban")
        assertEquals("Chao ban", modified.translatedText)
        assertEquals("Hello", modified.originalText)
        assertEquals(100L, modified.timestamp)
    }
}

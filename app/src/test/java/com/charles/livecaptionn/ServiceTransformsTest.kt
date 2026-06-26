package com.charles.livecaptionn

import com.charles.livecaptionn.service.CaptionRuntimeLine
import com.charles.livecaptionn.service.CaptionRuntimeState
import com.charles.livecaptionn.service.overlayTranscriptText
import com.charles.livecaptionn.service.recordTranscriptResult
import com.charles.livecaptionn.service.updateTranscriptTranslation
import com.charles.livecaptionn.speech.RecognitionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceTransformsTest {

    // ── recordTranscriptResult ──

    @Test
    fun recordTranscriptResult_blankText_returnsInput() {
        val lines = listOf(CaptionRuntimeLine("hello"))
        assertEquals(lines, recordTranscriptResult(lines, "", replaceOpenLine = false))
    }

    @Test
    fun recordTranscriptResult_duplicate_skips() {
        val lines = listOf(CaptionRuntimeLine("hello"))
        assertEquals(lines, recordTranscriptResult(lines, "hello", replaceOpenLine = false))
    }

    @Test
    fun recordTranscriptResult_replacesOpenLine() {
        val lines = listOf(CaptionRuntimeLine("hello"))
        val result = recordTranscriptResult(lines, "world", replaceOpenLine = true)
        assertEquals(1, result.size)
        assertEquals("world", result.first().originalText)
    }

    @Test
    fun recordTranscriptResult_appendsWhenNoOpenLine() {
        val lines = listOf(CaptionRuntimeLine("hello", "translated"))
        val result = recordTranscriptResult(lines, "world", replaceOpenLine = true)
        assertEquals(2, result.size)
        assertEquals("world", result[1].originalText)
    }

    @Test
    fun recordTranscriptResult_appendsWhenNotReplace() {
        val lines = listOf(CaptionRuntimeLine("hello"))
        val result = recordTranscriptResult(lines, "world", replaceOpenLine = false)
        assertEquals(2, result.size)
    }

    @Test
    fun recordTranscriptResult_trimsToMaxLines() {
        val lines = (1..31).map { CaptionRuntimeLine("line $it") }
        val result = recordTranscriptResult(lines, "line 32", replaceOpenLine = false)
        assertEquals(30, result.size)
        assertEquals("line 3", result.first().originalText)
        assertEquals("line 32", result.last().originalText)
    }

    // ── updateTranscriptTranslation ──

    @Test
    fun updateTranscriptTranslation_matchesExisting() {
        val lines = listOf(CaptionRuntimeLine("hello"), CaptionRuntimeLine("world"))
        val result = updateTranscriptTranslation(lines, "hello", "xin chao")
        assertEquals("xin chao", result[0].translatedText)
        assertEquals("", result[1].translatedText)
    }

    @Test
    fun updateTranscriptTranslation_noMatch_returnsUnchanged() {
        val lines = listOf(CaptionRuntimeLine("hello"))
        val result = updateTranscriptTranslation(lines, "nope", "translated")
        assertEquals(lines, result)
    }

    @Test
    fun updateTranscriptTranslation_blankOriginal_returnsInput() {
        val lines = listOf(CaptionRuntimeLine("hello"))
        assertEquals(lines, updateTranscriptTranslation(lines, "", "translated"))
    }

    @Test
    fun updateTranscriptTranslation_prefersLastMatch() {
        val lines = listOf(
            CaptionRuntimeLine("hello"),
            CaptionRuntimeLine("world"),
            CaptionRuntimeLine("hello")
        )
        val result = updateTranscriptTranslation(lines, "hello", "translated")
        assertEquals("", result[0].translatedText) // first unchanged
        assertEquals("translated", result[2].translatedText) // last matched
    }

    // ── overlayTranscriptText ──

    @Test
    fun overlayTranscriptText_empty() {
        val state = CaptionRuntimeState()
        assertEquals("", overlayTranscriptText(state))
    }

    @Test
    fun overlayTranscriptText_showsLivePartialOnly() {
        val state = CaptionRuntimeState(translatedText = "xin chao")
        assertEquals("xin chao", overlayTranscriptText(state))
    }

    @Test
    fun overlayTranscriptText_showsHistory() {
        val state = CaptionRuntimeState(
            transcriptLines = listOf(
                CaptionRuntimeLine("hello", "xin chao"),
                CaptionRuntimeLine("world", "the gioi")
            ),
            translatedText = "the gioi"
        )
        val result = overlayTranscriptText(state)
        assertEquals("xin chao\nthe gioi", result)
    }

    @Test
    fun overlayTranscriptText_dedupesDuplicateEnding() {
        val state = CaptionRuntimeState(
            transcriptLines = listOf(
                CaptionRuntimeLine("hello", "xin chao")
            ),
            translatedText = "xin chao"
        )
        assertEquals("xin chao", overlayTranscriptText(state))
    }

    @Test
    fun overlayTranscriptText_skipsUntranslatedLines() {
        val state = CaptionRuntimeState(
            transcriptLines = listOf(
                CaptionRuntimeLine("hello"),
                CaptionRuntimeLine("world", "the gioi")
            )
        )
        assertEquals("the gioi", overlayTranscriptText(state))
    }
}

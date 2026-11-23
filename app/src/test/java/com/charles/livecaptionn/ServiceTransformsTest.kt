package com.charles.livecaptionn

import com.charles.livecaptionn.service.CaptionRuntimeLine
import com.charles.livecaptionn.service.CaptionRuntimeState
import com.charles.livecaptionn.service.applyTranslationResult
import com.charles.livecaptionn.service.overlayTranscriptText
import com.charles.livecaptionn.service.recordTranscriptResult
import com.charles.livecaptionn.service.recordTranscriptResultWithId
import com.charles.livecaptionn.service.updateTranscriptTranslation
import com.charles.livecaptionn.speech.RecognitionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceTransformsTest {

    // ── recordTranscriptResult ──

    @Test
    fun recordTranscriptResult_blankText_returnsInput() {
        val lines = listOf(CaptionRuntimeLine(id = 1L, originalText = "hello"))
        assertEquals(lines, recordTranscriptResult(lines, "", isFinal = false, newLineId = 2L))
    }

    @Test
    fun recordTranscriptResult_duplicate_skips() {
        val lines = listOf(CaptionRuntimeLine(id = 1L, originalText = "hello"))
        assertEquals(lines, recordTranscriptResult(lines, "hello", isFinal = false, newLineId = 2L))
    }

    @Test
    fun recordTranscriptResult_replacesOpenLine() {
        val lines = listOf(CaptionRuntimeLine(id = 1L, originalText = "hello"))
        val result = recordTranscriptResult(lines, "world", isFinal = false, newLineId = 2L)
        assertEquals(1, result.size)
        assertEquals("world", result.first().originalText)
        assertEquals(2L, result.first().id)
    }

    @Test
    fun recordTranscriptResult_translatedButNotFinal_stillReplaces() {
        // Regression test for GitHub issue #1: a translation landing mid-sentence
        // (e.g. during a natural speech pause that fires the translate debounce
        // but not Vosk's finalizer) must not cause the next partial to be
        // appended as a duplicate line — it must still replace in place.
        val lines = listOf(
            CaptionRuntimeLine(id = 1L, originalText = "hello", translatedText = "xin chao", isFinal = false)
        )
        val result = recordTranscriptResult(lines, "hello world", isFinal = false, newLineId = 2L)
        assertEquals(1, result.size)
        assertEquals("hello world", result.first().originalText)
        assertEquals("", result.first().translatedText)
    }

    @Test
    fun recordTranscriptResult_finalClosesLine_thenNextPartialAppends() {
        val open = listOf(CaptionRuntimeLine(id = 1L, originalText = "hello"))
        val closed = recordTranscriptResult(open, "hello world", isFinal = true, newLineId = 2L)
        assertEquals(1, closed.size)
        assertEquals(true, closed.first().isFinal)

        val next = recordTranscriptResult(closed, "next", isFinal = false, newLineId = 3L)
        assertEquals(2, next.size)
        assertEquals("next", next[1].originalText)
    }

    @Test
    fun recordTranscriptResult_sameTextRepeatedFinalTransition_preservesTranslation() {
        val lines = listOf(
            CaptionRuntimeLine(id = 1L, originalText = "hello", translatedText = "xin chao", isFinal = false)
        )
        val result = recordTranscriptResult(lines, "hello", isFinal = true, newLineId = 2L)
        assertEquals(1, result.size)
        assertEquals(1L, result.first().id)
        assertEquals("xin chao", result.first().translatedText)
        assertEquals(true, result.first().isFinal)
    }

    @Test
    fun recordTranscriptResult_appendsWhenLastLineFinal() {
        val lines = listOf(CaptionRuntimeLine(id = 1L, originalText = "hello", isFinal = true))
        val result = recordTranscriptResult(lines, "world", isFinal = false, newLineId = 2L)
        assertEquals(2, result.size)
        assertEquals("world", result[1].originalText)
    }

    @Test
    fun recordTranscriptResult_trimsToMaxLines() {
        val lines = (1..31).map { CaptionRuntimeLine(id = it.toLong(), originalText = "line $it", isFinal = true) }
        val result = recordTranscriptResult(lines, "line 32", isFinal = true, newLineId = 32L)
        assertEquals(30, result.size)
        assertEquals("line 3", result.first().originalText)
        assertEquals("line 32", result.last().originalText)
    }

    @Test
    fun recordTranscriptResultWithId_partialThenFinalWithSameText_reusesIdForTranslation() {
        val partial = recordTranscriptResultWithId(
            lines = emptyList(),
            originalText = "hello",
            isFinal = false,
            newLineId = 1L
        )
        assertEquals(1L, partial.lineId)

        val finalized = recordTranscriptResultWithId(
            lines = partial.lines,
            originalText = "hello",
            isFinal = true,
            newLineId = 2L
        )
        assertEquals(1L, finalized.lineId)
        assertEquals(true, finalized.lines.single().isFinal)

        val translated = updateTranscriptTranslation(finalized.lines, finalized.lineId, "xin chao")
        assertEquals("xin chao", translated.single().translatedText)
    }

    @Test
    fun recordTranscriptResultWithId_duplicatePartial_returnsExistingId() {
        val first = recordTranscriptResultWithId(emptyList(), "hello", isFinal = false, newLineId = 10L)
        val dup = recordTranscriptResultWithId(first.lines, "hello", isFinal = false, newLineId = 11L)
        assertEquals(10L, dup.lineId)
        assertEquals(1, dup.lines.size)
    }

    @Test
    fun recordTranscriptResultWithId_evolvedPartial_returnsNewId() {
        val first = recordTranscriptResultWithId(emptyList(), "hello", isFinal = false, newLineId = 10L)
        val evolved = recordTranscriptResultWithId(first.lines, "hello world", isFinal = false, newLineId = 11L)
        assertEquals(11L, evolved.lineId)
        assertEquals(1, evolved.lines.size)
        assertEquals("hello world", evolved.lines.single().originalText)
    }

    @Test
    fun recordTranscriptResultWithId_blankText_returnsNoLineId() {
        val lines = listOf(CaptionRuntimeLine(id = 1L, originalText = "hello"))
        val blank = recordTranscriptResultWithId(lines, "   ", isFinal = false, newLineId = 2L, noLineId = -1L)
        assertEquals(-1L, blank.lineId)
        assertEquals(lines, blank.lines)
    }

    // ── updateTranscriptTranslation ──

    @Test
    fun updateTranscriptTranslation_matchesById() {
        val lines = listOf(
            CaptionRuntimeLine(id = 1L, originalText = "hello"),
            CaptionRuntimeLine(id = 2L, originalText = "world")
        )
        val result = updateTranscriptTranslation(lines, lineId = 1L, translatedText = "xin chao")
        assertEquals("xin chao", result[0].translatedText)
        assertEquals("", result[1].translatedText)
    }

    @Test
    fun updateTranscriptTranslation_idNotFound_returnsUnchanged() {
        val lines = listOf(CaptionRuntimeLine(id = 1L, originalText = "hello"))
        val result = updateTranscriptTranslation(lines, lineId = 99L, translatedText = "translated")
        assertEquals(lines, result)
    }

    @Test
    fun updateTranscriptTranslation_staleIdAfterTextEvolved_isNoOp() {
        val original = listOf(CaptionRuntimeLine(id = 1L, originalText = "hel"))
        val evolved = recordTranscriptResult(original, "hello world", isFinal = false, newLineId = 2L)
        // Translation for the stale id (1L) arrives after the line evolved to id 2L.
        val result = updateTranscriptTranslation(evolved, lineId = 1L, translatedText = "stale")
        assertEquals(evolved, result)
    }

    @Test
    fun updateTranscriptTranslation_distinctIdsDisambiguateSameText() {
        val lines = listOf(
            CaptionRuntimeLine(id = 1L, originalText = "hello", isFinal = true),
            CaptionRuntimeLine(id = 2L, originalText = "world", isFinal = true),
            CaptionRuntimeLine(id = 3L, originalText = "hello", isFinal = true)
        )
        val result = updateTranscriptTranslation(lines, lineId = 3L, translatedText = "translated")
        assertEquals("", result[0].translatedText) // first "hello" unchanged
        assertEquals("translated", result[2].translatedText) // matched by id, not text
    }

    // ── applyTranslationResult ──

    @Test
    fun applyTranslationResult_evolvedPartialWhileTranslating_updatesLiveTranslatedText() {
        val p1 = recordTranscriptResultWithId(emptyList(), "привет", isFinal = false, newLineId = 1L)
        val p2 = recordTranscriptResultWithId(p1.lines, "привет как", isFinal = false, newLineId = 2L)
        val p3 = recordTranscriptResultWithId(p2.lines, "привет как дела", isFinal = false, newLineId = 3L)

        val applied = applyTranslationResult(
            lines = p3.lines,
            currentTranslatedText = "",
            lastAppliedLiveLineId = -1L,
            requestLineId = p1.lineId,
            isFinal = false,
            translatedText = "Hello",
            noLineId = -1L
        )

        assertEquals("Hello", applied.translatedText)
        assertEquals(1L, applied.lastAppliedLiveLineId)
        assertEquals(3L, applied.lines.single().id)
        assertEquals("", applied.lines.single().translatedText)
        assertEquals(
            "Hello",
            overlayTranscriptText(
                CaptionRuntimeState(
                    originalText = "привет как дела",
                    translatedText = applied.translatedText,
                    transcriptLines = applied.lines
                )
            )
        )
    }

    @Test
    fun applyTranslationResult_stalePartialFromAlreadyFinalizedUtterance_isIgnored() {
        val lines = listOf(
            CaptionRuntimeLine(id = 4L, originalText = "привет", translatedText = "Hello", isFinal = true),
            CaptionRuntimeLine(id = 5L, originalText = "мир", isFinal = false)
        )
        val applied = applyTranslationResult(
            lines = lines,
            currentTranslatedText = "Hello",
            lastAppliedLiveLineId = 4L,
            requestLineId = 2L,
            isFinal = false,
            translatedText = "Hi",
            noLineId = -1L
        )
        assertEquals("Hello", applied.translatedText)
        assertEquals(4L, applied.lastAppliedLiveLineId)
        assertEquals(lines, applied.lines)
    }

    @Test
    fun applyTranslationResult_finalCompletesAfterNextUtteranceStarted_updatesLiveAndAvoidsDuplicate() {
        val lines = listOf(
            CaptionRuntimeLine(id = 4L, originalText = "привет как дела", isFinal = true),
            CaptionRuntimeLine(id = 5L, originalText = "следующая", isFinal = false)
        )
        val applied = applyTranslationResult(
            lines = lines,
            currentTranslatedText = "Hello how",
            lastAppliedLiveLineId = 2L,
            requestLineId = 4L,
            isFinal = true,
            translatedText = "Hello, how are you?",
            noLineId = -1L
        )
        assertEquals("Hello, how are you?", applied.lines[0].translatedText)
        assertEquals("Hello, how are you?", applied.translatedText)
        assertEquals(4L, applied.lastAppliedLiveLineId)
        assertEquals(
            "Hello, how are you?",
            overlayTranscriptText(
                CaptionRuntimeState(
                    originalText = "следующая",
                    translatedText = applied.translatedText,
                    transcriptLines = applied.lines
                )
            )
        )
    }

    @Test
    fun applyTranslationResult_outOfOrderOlderPartial_doesNotOverwriteNewerLive() {
        val lines = listOf(
            CaptionRuntimeLine(id = 5L, originalText = "привет как дела", isFinal = false)
        )
        val applied = applyTranslationResult(
            lines = lines,
            currentTranslatedText = "Hello how are",
            lastAppliedLiveLineId = 3L,
            requestLineId = 2L,
            isFinal = false,
            translatedText = "Hello how",
            noLineId = -1L
        )
        assertEquals("Hello how are", applied.translatedText)
        assertEquals(3L, applied.lastAppliedLiveLineId)
        assertEquals(lines, applied.lines)
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
                CaptionRuntimeLine(id = 1L, originalText = "hello", translatedText = "xin chao"),
                CaptionRuntimeLine(id = 2L, originalText = "world", translatedText = "the gioi")
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
                CaptionRuntimeLine(id = 1L, originalText = "hello", translatedText = "xin chao")
            ),
            translatedText = "xin chao"
        )
        assertEquals("xin chao", overlayTranscriptText(state))
    }

    @Test
    fun overlayTranscriptText_skipsUntranslatedLines() {
        val state = CaptionRuntimeState(
            transcriptLines = listOf(
                CaptionRuntimeLine(id = 1L, originalText = "hello"),
                CaptionRuntimeLine(id = 2L, originalText = "world", translatedText = "the gioi")
            )
        )
        assertEquals("the gioi", overlayTranscriptText(state))
    }
}

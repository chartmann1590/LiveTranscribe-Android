package com.charles.livecaptionn

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charles.livecaptionn.overlay.OverlayUiState
import com.charles.livecaptionn.overlay.computeOverlayTextDisplay
import com.charles.livecaptionn.service.CaptionRuntimeState
import com.charles.livecaptionn.service.applyTranslationResult
import com.charles.livecaptionn.service.overlayTranscriptText
import com.charles.livecaptionn.service.recordTranscriptResultWithId
import com.charles.livecaptionn.service.updateTranscriptTranslation
import com.charles.livecaptionn.translation.MlKitTranslationRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies real on-device ML Kit model download + translation and the service's
 * partial-then-final transcript reconciliation pipeline end-to-end.
 */
@RunWith(AndroidJUnit4::class)
class TranslationEndToEndTest {

    @Test
    fun mlKit_downloadsTranslatesAndCaches_freeTierPairs() = runBlocking {
        val sentence = "Hello, how are you today?"
        val followUp = "Good morning"
        val repo = MlKitTranslationRepository()
        try {
            for (target in listOf("vi", "es", "ar")) {
                val translated = withTimeoutOrNull(180_000L) {
                    repo.translate(sentence, "en", target, autoDetect = false)
                }.orEmpty()
                Log.i(TAG, "en->$target initial='$translated' status=${repo.statusFlow.value}")
                assertTrue(
                    "Expected non-blank translation for en->$target (status=${repo.statusFlow.value})",
                    translated.isNotBlank()
                )
                assertFalse(
                    "Expected translated output to differ from English source for en->$target",
                    translated.equals(sentence, ignoreCase = true)
                )
                assertFalse(repo.statusFlow.value.isError)
                assertFalse(repo.statusFlow.value.downloading)
                assertNull(repo.statusFlow.value.message)

                val cachedStart = System.currentTimeMillis()
                val second = withTimeoutOrNull(15_000L) {
                    repo.translate(followUp, "en", target, autoDetect = false)
                }.orEmpty()
                val elapsedMs = System.currentTimeMillis() - cachedStart
                Log.i(TAG, "en->$target cached='$second' elapsedMs=$elapsedMs")
                assertTrue("Expected cached translation for en->$target", second.isNotBlank())
                assertFalse(second.equals(followUp, ignoreCase = true))
            }
        } finally {
            repo.close()
        }
    }

    @Test
    fun partialThenFinalSameText_reconcilesAndRendersOverlayTranslation() = runBlocking {
        val sentence = "Hello, how are you today?"
        val repo = MlKitTranslationRepository()
        try {
            val partial = recordTranscriptResultWithId(
                lines = emptyList(),
                originalText = sentence,
                isFinal = false,
                newLineId = 1L
            )
            val finalized = recordTranscriptResultWithId(
                lines = partial.lines,
                originalText = sentence,
                isFinal = true,
                newLineId = 2L
            )
            assertEquals(1L, finalized.lineId)

            val translated = withTimeoutOrNull(180_000L) {
                repo.translate(sentence, "en", "es", autoDetect = false)
            }.orEmpty()
            assertTrue("Expected Spanish translation", translated.isNotBlank())

            val updatedLines = updateTranscriptTranslation(
                lines = finalized.lines,
                lineId = finalized.lineId,
                translatedText = translated
            )
            assertEquals(translated, updatedLines.single().translatedText)

            val overlayBody = overlayTranscriptText(
                CaptionRuntimeState(
                    originalText = sentence,
                    translatedText = translated,
                    transcriptLines = updatedLines
                )
            )
            assertEquals(translated, overlayBody)
        } finally {
            repo.close()
        }
    }

    @Test
    fun mlKit_russianToEnglish_voskLowercaseAndEvolvingPartials_rendersBelowOriginal() = runBlocking {
        val repo = MlKitTranslationRepository()
        try {
            val inputs = listOf(
                "привет",
                "привет как дела",
                "сегодня мы поговорим о новой игре"
            )
            val translations = mutableListOf<String>()
            for (input in inputs) {
                val translated = withTimeoutOrNull(180_000L) {
                    repo.translate(input, "ru", "en", autoDetect = true)
                }.orEmpty()
                Log.i(TAG, "ru->en '$input' -> '$translated' status=${repo.statusFlow.value}")
                assertTrue(
                    "Expected non-blank translation for ru->en '$input' (status=${repo.statusFlow.value})",
                    translated.isNotBlank()
                )
                assertFalse(
                    "Expected translated output to differ from Russian source for '$input'",
                    translated.equals(input, ignoreCase = true)
                )
                assertTrue(
                    "Expected English Latin letters in translation '$translated' for '$input'",
                    Regex("[A-Za-z]").containsMatchIn(translated)
                )
                assertFalse(repo.statusFlow.value.isError)
                assertFalse(repo.statusFlow.value.downloading)
                assertNull(repo.statusFlow.value.message)
                translations += translated
            }

            // Simulate continuous Vosk partials evolving (1 -> 2 -> 3) while partial 1 finishes translating.
            val p1 = recordTranscriptResultWithId(emptyList(), inputs[0], isFinal = false, newLineId = 1L)
            val p2 = recordTranscriptResultWithId(p1.lines, inputs[1], isFinal = false, newLineId = 2L)
            val p3 = recordTranscriptResultWithId(p2.lines, inputs[2], isFinal = false, newLineId = 3L)

            val appliedPartial = applyTranslationResult(
                lines = p3.lines,
                currentTranslatedText = "",
                lastAppliedLiveLineId = -1L,
                requestLineId = p1.lineId,
                isFinal = false,
                translatedText = translations[0],
                noLineId = -1L
            )
            val body = overlayTranscriptText(
                CaptionRuntimeState(
                    originalText = inputs[2],
                    translatedText = appliedPartial.translatedText,
                    transcriptLines = appliedPartial.lines
                )
            )
            val display = computeOverlayTextDisplay(
                OverlayUiState(
                    originalText = inputs[2],
                    translatedText = appliedPartial.translatedText,
                    transcriptText = body,
                    showOriginal = true
                )
            )
            assertTrue("Original Russian text should remain visible", display.showOriginal)
            assertEquals(inputs[2], display.originalText)
            assertEquals(translations[0], display.translatedText)
            assertFalse(display.translatedText == "…")
        } finally {
            repo.close()
        }
    }

    @Test
    fun mlKit_autoSource_returnsEmptyAndErrorStatus_notPassthrough() = runBlocking {
        val repo = MlKitTranslationRepository()
        try {
            val original = "привет как дела"
            val result = repo.translate(original, "auto", "en", autoDetect = true)
            assertEquals(
                "Auto source must not silently pass the original through",
                "",
                result
            )
            assertTrue(repo.statusFlow.value.isError)
            assertTrue(
                repo.statusFlow.value.message?.contains("Auto-detect", ignoreCase = true) == true
            )
        } finally {
            repo.close()
        }
    }

    private companion object {
        const val TAG = "TranslationE2ETest"
    }
}

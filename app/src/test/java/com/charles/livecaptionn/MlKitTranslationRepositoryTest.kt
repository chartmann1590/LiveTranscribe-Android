package com.charles.livecaptionn

import com.charles.livecaptionn.translation.MlKitLanguages
import com.charles.livecaptionn.translation.MlKitTranslationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the silent-passthrough hole in [MlKitTranslationRepository]: an
 * `"auto"` or otherwise unsupported source/target used to return the
 * original text unchanged (with an idle status), which on screen looks
 * exactly like the shipped Russian→English bug — original printed, no
 * translation below it. Unsupported pairs must now return an empty result
 * and surface a clear error on `statusFlow`.
 *
 * These cases return before any ML Kit SDK / Android call, so they run as
 * plain JVM unit tests. Actual ru→en translation is covered on-device by
 * `TranslationEndToEndTest`.
 */
class MlKitTranslationRepositoryTest {

    @Test
    fun translate_autoSource_returnsEmptyAndErrorStatus_notPassthrough() = runTest {
        val repo = MlKitTranslationRepository()
        val original = "привет как дела"
        val result = repo.translate(original, "auto", "en", autoDetect = true)
        assertEquals("", result)
        assertNotEquals(original, result)
        val status = repo.statusFlow.value
        assertTrue("Expected error status for auto source", status.isError)
        assertFalse(status.downloading)
        assertTrue(
            "Expected an auto-detect explanation, was: ${status.message}",
            status.message?.contains("Auto-detect", ignoreCase = true) == true
        )
    }

    @Test
    fun translate_unsupportedSource_returnsEmptyAndErrorStatus() = runTest {
        val repo = MlKitTranslationRepository()
        val result = repo.translate("hello", "zz", "en", autoDetect = false)
        assertEquals("", result)
        val status = repo.statusFlow.value
        assertTrue(status.isError)
        assertTrue(status.message?.contains("not supported", ignoreCase = true) == true)
    }

    @Test
    fun translate_unsupportedTarget_returnsEmptyAndErrorStatus() = runTest {
        val repo = MlKitTranslationRepository()
        val result = repo.translate("привет", "ru", "zz", autoDetect = false)
        assertEquals("", result)
        val status = repo.statusFlow.value
        assertTrue(status.isError)
        assertTrue(status.message?.contains("not supported", ignoreCase = true) == true)
    }

    @Test
    fun unsupportedPairMessage_validRussianToEnglish_returnsNull() {
        assertNull(MlKitLanguages.unsupportedPairMessage("ru", "en"))
        assertNull(MlKitLanguages.unsupportedPairMessage("ru-RU", "en-US"))
        assertTrue(MlKitLanguages.isSupported("ru"))
        assertTrue(MlKitLanguages.isSupported("en"))
    }

    @Test
    fun releasePair_withoutCachedTranslators_isNoOp() {
        // The all-languages sweep calls releasePair after every language to
        // free native translator resources; on a fresh repository (nothing
        // cached) and for auto/identity pairs it must simply do nothing.
        val repo = MlKitTranslationRepository()
        repo.releasePair("en", "ru")
        repo.releasePair("ru", "en")
        repo.releasePair("auto", "en")
        repo.releasePair("en", "en")
        repo.close()
    }

    @Test
    fun unsupportedPairMessage_autoAndUnknown_areRejected() {
        assertTrue(
            MlKitLanguages.unsupportedPairMessage("auto", "en")
                ?.contains("Auto-detect", ignoreCase = true) == true
        )
        assertFalse(MlKitLanguages.isSupported("auto"))
        assertFalse(MlKitLanguages.isSupported(""))
        assertFalse(MlKitLanguages.isSupported("zz"))
    }
}

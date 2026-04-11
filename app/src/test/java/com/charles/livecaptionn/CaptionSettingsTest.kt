package com.charles.livecaptionn

import com.charles.livecaptionn.settings.AudioSource
import com.charles.livecaptionn.settings.CaptionSettings
import com.charles.livecaptionn.settings.SttBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionSettingsTest {

    @Test
    fun defaults_sourceLanguage_isEnglish() {
        assertEquals("en", CaptionSettings().sourceLanguageCode)
    }

    @Test
    fun defaults_targetLanguage_isVietnamese() {
        assertEquals("vi", CaptionSettings().targetLanguageCode)
    }

    @Test
    fun defaults_audioSource_isMic() {
        assertEquals(AudioSource.MIC, CaptionSettings().audioSource)
    }

    @Test
    fun defaults_autoDetect_isFalse() {
        assertFalse(CaptionSettings().autoDetectSource)
    }

    @Test
    fun defaults_showOriginal_isTrue() {
        assertTrue(CaptionSettings().showOriginal)
    }

    @Test
    fun defaults_textSize_is20() {
        assertEquals(20f, CaptionSettings().textSizeSp)
    }

    @Test
    fun defaults_opacity_is065() {
        assertEquals(0.65f, CaptionSettings().overlayOpacity)
    }

    @Test
    fun defaults_serverBaseUrl_isNotEmpty() {
        assertTrue(CaptionSettings().serverBaseUrl.isNotBlank())
    }

    @Test
    fun defaults_sttBaseUrl_isNotEmpty() {
        assertTrue(CaptionSettings().sttBaseUrl.isNotBlank())
    }

    @Test
    fun defaults_sttBackend_isRemoteWhisper() {
        assertEquals(SttBackend.REMOTE_WHISPER, CaptionSettings().sttBackend)
    }

    @Test
    fun copy_preservesOtherFields() {
        val original = CaptionSettings()
        val modified = original.copy(sourceLanguageCode = "vi")
        assertEquals("vi", modified.sourceLanguageCode)
        assertEquals(original.targetLanguageCode, modified.targetLanguageCode)
        assertEquals(original.textSizeSp, modified.textSizeSp)
        assertEquals(original.audioSource, modified.audioSource)
    }
}

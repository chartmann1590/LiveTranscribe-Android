package com.charles.livecaptionn

import com.charles.livecaptionn.service.CaptionRuntimeState
import com.charles.livecaptionn.service.CaptionRuntimeStore
import com.charles.livecaptionn.speech.RecognitionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionRuntimeStoreTest {

    @Test
    fun initialState_isDefault() {
        val store = CaptionRuntimeStore()
        val state = store.state.value
        assertEquals("", state.originalText)
        assertEquals("", state.translatedText)
        assertEquals(RecognitionStatus.IDLE, state.status)
        assertFalse(state.running)
        assertFalse(state.paused)
        assertNull(state.lastError)
    }

    @Test
    fun update_changesState() {
        val store = CaptionRuntimeStore()
        store.update { it.copy(running = true, status = RecognitionStatus.LISTENING) }
        assertTrue(store.state.value.running)
        assertEquals(RecognitionStatus.LISTENING, store.state.value.status)
    }

    @Test
    fun update_preservesOtherFields() {
        val store = CaptionRuntimeStore()
        store.update { it.copy(originalText = "hello") }
        store.update { it.copy(translatedText = "xin chao") }
        assertEquals("hello", store.state.value.originalText)
        assertEquals("xin chao", store.state.value.translatedText)
    }

    @Test
    fun update_toDefault_resetsState() {
        val store = CaptionRuntimeStore()
        store.update { it.copy(running = true, originalText = "test") }
        store.update { CaptionRuntimeState() }
        assertFalse(store.state.value.running)
        assertEquals("", store.state.value.originalText)
    }
}

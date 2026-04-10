package com.charles.livecaptionn

import com.charles.livecaptionn.settings.AudioSource
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSourceTest {

    @Test
    fun fromName_MIC_returnsMic() {
        assertEquals(AudioSource.MIC, AudioSource.fromName("MIC"))
    }

    @Test
    fun fromName_SYSTEM_returnsSystem() {
        assertEquals(AudioSource.SYSTEM, AudioSource.fromName("SYSTEM"))
    }

    @Test
    fun fromName_unknown_defaultsToMic() {
        assertEquals(AudioSource.MIC, AudioSource.fromName("BLUETOOTH"))
    }

    @Test
    fun fromName_empty_defaultsToMic() {
        assertEquals(AudioSource.MIC, AudioSource.fromName(""))
    }

    @Test
    fun label_values_areCorrect() {
        assertEquals("Microphone", AudioSource.MIC.label)
        assertEquals("System Audio", AudioSource.SYSTEM.label)
    }
}

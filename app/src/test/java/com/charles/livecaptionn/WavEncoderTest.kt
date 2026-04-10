package com.charles.livecaptionn

import com.charles.livecaptionn.speech.WavEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavEncoderTest {

    @Test
    fun encode_producesValidWavHeader() {
        val pcm = ByteArray(100) { it.toByte() }
        val wav = WavEncoder.encode(pcm, sampleRate = 16000, channels = 1, bitsPerSample = 16)

        // WAV starts with "RIFF"
        assertEquals('R'.code.toByte(), wav[0])
        assertEquals('I'.code.toByte(), wav[1])
        assertEquals('F'.code.toByte(), wav[2])
        assertEquals('F'.code.toByte(), wav[3])

        // Offset 8: "WAVE"
        assertEquals('W'.code.toByte(), wav[8])
        assertEquals('A'.code.toByte(), wav[9])
        assertEquals('V'.code.toByte(), wav[10])
        assertEquals('E'.code.toByte(), wav[11])
    }

    @Test
    fun encode_totalSize_is44PlusPcmSize() {
        val pcm = ByteArray(200)
        val wav = WavEncoder.encode(pcm)
        assertEquals(44 + 200, wav.size)
    }

    @Test
    fun encode_emptyPcm_producesHeaderOnly() {
        val wav = WavEncoder.encode(ByteArray(0))
        assertEquals(44, wav.size)
    }

    @Test
    fun encode_dataChunkContainsPcm() {
        val pcm = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val wav = WavEncoder.encode(pcm)
        // Data starts at offset 44
        assertEquals(0x01.toByte(), wav[44])
        assertEquals(0x02.toByte(), wav[45])
        assertEquals(0x03.toByte(), wav[46])
        assertEquals(0x04.toByte(), wav[47])
    }

    @Test
    fun encode_fmtChunk_containsCorrectValues() {
        val wav = WavEncoder.encode(ByteArray(0), sampleRate = 16000, channels = 1, bitsPerSample = 16)

        // fmt chunk at offset 12: "fmt "
        assertEquals('f'.code.toByte(), wav[12])
        assertEquals('m'.code.toByte(), wav[13])
        assertEquals('t'.code.toByte(), wav[14])
        assertEquals(' '.code.toByte(), wav[15])

        // PCM format = 1 (little-endian at offset 20)
        assertEquals(1.toByte(), wav[20])
        assertEquals(0.toByte(), wav[21])

        // channels = 1 (offset 22)
        assertEquals(1.toByte(), wav[22])
        assertEquals(0.toByte(), wav[23])

        // sample rate = 16000 = 0x3E80 (offset 24, little-endian)
        assertEquals(0x80.toByte(), wav[24])
        assertEquals(0x3E.toByte(), wav[25])
    }

    @Test
    fun encode_riffChunkSize_isCorrect() {
        val pcm = ByteArray(100)
        val wav = WavEncoder.encode(pcm)
        // RIFF chunk size = 36 + dataSize = 136, at offset 4 (little-endian)
        val chunkSize = (wav[4].toInt() and 0xFF) or
                ((wav[5].toInt() and 0xFF) shl 8) or
                ((wav[6].toInt() and 0xFF) shl 16) or
                ((wav[7].toInt() and 0xFF) shl 24)
        assertEquals(136, chunkSize)
    }
}

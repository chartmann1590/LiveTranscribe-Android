package com.charles.livecaptionn.speech

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** Wraps raw PCM 16-bit mono audio in a WAV header. */
object WavEncoder {

    fun encode(pcmData: ByteArray, sampleRate: Int = 16000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize

        val out = ByteArrayOutputStream(44 + dataSize)
        DataOutputStream(out).use { d ->
            // RIFF header
            d.writeBytes("RIFF")
            d.writeIntLE(totalSize)
            d.writeBytes("WAVE")

            // fmt chunk
            d.writeBytes("fmt ")
            d.writeIntLE(16)          // chunk size
            d.writeShortLE(1)         // PCM format
            d.writeShortLE(channels)
            d.writeIntLE(sampleRate)
            d.writeIntLE(byteRate)
            d.writeShortLE(blockAlign)
            d.writeShortLE(bitsPerSample)

            // data chunk
            d.writeBytes("data")
            d.writeIntLE(dataSize)
            d.write(pcmData)
        }
        return out.toByteArray()
    }

    private fun DataOutputStream.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun DataOutputStream.writeShortLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }
}

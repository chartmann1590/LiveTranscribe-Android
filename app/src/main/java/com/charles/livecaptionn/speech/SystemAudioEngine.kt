package com.charles.livecaptionn.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Captures system/internal audio via MediaProjection + AudioPlaybackCapture,
 * buffers ~3 seconds of PCM, encodes to WAV, sends to a Whisper-compatible
 * STT endpoint, and delivers recognized text via callback.
 */
class SystemAudioEngine(
    private val projection: MediaProjection,
    private val sttUrl: String,
    private val languageCode: String,
    private val scope: CoroutineScope,
    private val onResult: (SpeechResult) -> Unit
) : SpeechEngine {

    private val statusMutable = MutableStateFlow(RecognitionStatus.IDLE)
    val status: StateFlow<RecognitionStatus> = statusMutable

    private val sttClient = WhisperSttClient()

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    @Volatile private var running = false
    @Volatile private var paused = false

    override fun start() {
        if (running) return
        running = true
        paused = false
        startCapture()
    }

    override fun stop() {
        running = false
        paused = false
        captureJob?.cancel()
        captureJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        projection.stop()
        statusMutable.value = RecognitionStatus.IDLE
    }

    override fun pause() {
        paused = true
        statusMutable.value = RecognitionStatus.PAUSED
    }

    override fun resume() {
        if (!running) return
        paused = false
        statusMutable.value = RecognitionStatus.LISTENING
    }

    private fun startCapture() {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE * 2) // at least 1 second buffer

        audioRecord = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("SystemAudioEngine", "AudioRecord failed to initialize")
            statusMutable.value = RecognitionStatus.ERROR
            stop()
            return
        }

        audioRecord?.startRecording()
        statusMutable.value = RecognitionStatus.LISTENING

        captureJob = scope.launch(Dispatchers.Default) {
            val chunkBytes = SAMPLE_RATE * 2 * CHUNK_SECONDS // 16-bit mono = 2 bytes/sample
            val readBuffer = ByteArray(4096)

            while (isActive && running) {
                if (paused) {
                    delay(200)
                    continue
                }

                val pcmBuffer = ByteArrayOutputStream(chunkBytes)
                val startTime = System.currentTimeMillis()

                // Collect ~CHUNK_SECONDS of audio
                while (pcmBuffer.size() < chunkBytes && isActive && running && !paused) {
                    val read = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                    if (read > 0) pcmBuffer.write(readBuffer, 0, read)
                    if (System.currentTimeMillis() - startTime > CHUNK_SECONDS * 1100L) break
                }

                if (pcmBuffer.size() < SAMPLE_RATE) continue // skip tiny chunks

                statusMutable.value = RecognitionStatus.PROCESSING
                val wavData = WavEncoder.encode(pcmBuffer.toByteArray(), SAMPLE_RATE)
                val text = sttClient.transcribe(wavData, sttUrl, languageCode)

                if (text.isNotBlank()) {
                    onResult(SpeechResult(text, isFinal = true))
                }

                if (!paused && running) {
                    statusMutable.value = RecognitionStatus.LISTENING
                }
            }
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SECONDS = 3
    }
}

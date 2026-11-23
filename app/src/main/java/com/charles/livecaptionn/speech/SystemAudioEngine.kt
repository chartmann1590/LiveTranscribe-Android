package com.charles.livecaptionn.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import com.charles.livecaptionn.settings.SttBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * Captures system audio continuously and processes audio chunks separately.
 */
class SystemAudioEngine(
    private val context: Context,
    private val projection: MediaProjection,
    private val sttUrl: String,
    private val languageCode: String?,
    private val sourceLanguageCode: String,
    private val sttBackend: SttBackend,
    private val localSttClient: LocalVoskSttClient,
    private val scope: CoroutineScope,
    private val onResult: (SpeechResult) -> Unit,
    private val onSttError: (String?) -> Unit = {}
) : SpeechEngine {

    private val statusMutable = MutableStateFlow(RecognitionStatus.IDLE)
    val status: StateFlow<RecognitionStatus> = statusMutable

    private val sttClient = WhisperSttClient()

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var processingJob: Job? = null

    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var captureGeneration = 0L

    private data class AudioChunk(
        val pcmData: ByteArray,
        val generation: Long,
        val averageAmplitude: Int
    )

    override fun start() {
        if (running) return
        running = true
        paused = false
        captureGeneration++
        startCapture()
    }

    override fun stop() {
        running = false
        paused = false
        captureGeneration++

        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }

        captureJob?.cancel()
        processingJob?.cancel()

        captureJob = null
        processingJob = null

        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        }

        audioRecord = null

        try {
            projection.stop()
        } catch (_: Throwable) {
        }

        statusMutable.value = RecognitionStatus.IDLE
    }

    override fun pause() {
        if (!running || paused) return
        paused = true
        captureGeneration++
        statusMutable.value = RecognitionStatus.PAUSED
    }

    override fun resume() {
        if (!running || !paused) return
        paused = false
        captureGeneration++
        statusMutable.value = RecognitionStatus.LISTENING
    }

    private fun startCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            onSttError("System audio capture requires Android 10 (API 29) or newer.")
            statusMutable.value = RecognitionStatus.ERROR
            stop()
            return
        }

        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE * 2)

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
            onSttError("AudioRecord failed to initialize")
            statusMutable.value = RecognitionStatus.ERROR
            stop()
            return
        }

        try {
            audioRecord?.startRecording()
        } catch (t: Throwable) {
            Log.e("SystemAudioEngine", "AudioRecord failed to start", t)
            onSttError("System audio capture failed to start")
            statusMutable.value = RecognitionStatus.ERROR
            stop()
            return
        }

        if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            Log.e("SystemAudioEngine", "AudioRecord did not enter recording state")
            onSttError("System audio capture did not start")
            statusMutable.value = RecognitionStatus.ERROR
            stop()
            return
        }

        statusMutable.value = RecognitionStatus.LISTENING

        // Keep only two waiting chunks to prevent unlimited memory growth.
        val audioQueue = Channel<AudioChunk>(
            capacity = QUEUE_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        // Consumer: performs STT independently from audio capture.
        processingJob = scope.launch(Dispatchers.IO) {
            try {
                for (chunk in audioQueue) {
                    if (!isActive || !running) break
                    if (paused || chunk.generation != captureGeneration) continue

                    statusMutable.value = RecognitionStatus.PROCESSING

                    val outcome = try {
                        when (sttBackend) {
                            SttBackend.REMOTE_WHISPER -> {
                                val wavData = WavEncoder.encode(
                                    chunk.pcmData,
                                    SAMPLE_RATE
                                )
                                sttClient.transcribe(wavData, sttUrl, languageCode)
                            }

                            SttBackend.LOCAL_VOSK -> {
                                localSttClient.transcribe(
                                    chunk.pcmData,
                                    SAMPLE_RATE,
                                    sourceLanguageCode
                                )
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (t: Throwable) {
                        Log.e("SystemAudioEngine", "STT processing failed", t)
                        onSttError(t.message ?: "Speech recognition failed")
                        if (running && !paused) {
                            statusMutable.value = RecognitionStatus.LISTENING
                        }
                        continue
                    }

                    // Ignore results from audio captured before pause/resume.
                    if (!running || paused ||
                        chunk.generation != captureGeneration
                    ) {
                        continue
                    }

                    val err = outcome.errorMessage

                    if (err != null) {
                        onSttError(err)
                        statusMutable.value = RecognitionStatus.ERROR
                    } else {
                        onSttError(null)

                        val transcript = outcome.text.trim()

                        if (transcript.isNotBlank()) {
                            val shouldSuppress =
                                sttBackend == SttBackend.REMOTE_WHISPER &&
                                isLikelyHallucinatedTranscript(transcript)

                            if (shouldSuppress) {
                                Log.w(
                                    "SystemAudioEngine",
                                    "Ignoring likely hallucinated transcript: $transcript"
                                )
                                onSttError("Ignored low-confidence speech result")
                            } else {
                                Log.d(
                                    "SystemAudioEngine",
                                    "STT text avgAmp=${chunk.averageAmplitude} text=$transcript"
                                )
                                onResult(SpeechResult(transcript, isFinal = true))
                            }
                        }

                        if (running && !paused) {
                            statusMutable.value = RecognitionStatus.LISTENING
                        }
                    }
                }
            } finally {
                audioQueue.cancel()
            }
        }

        // Producer: continuously drains AudioRecord and queues complete chunks.
        captureJob = scope.launch(Dispatchers.Default) {
            val chunkSeconds = when (sttBackend) {
                SttBackend.LOCAL_VOSK -> LOCAL_CHUNK_SECONDS
                SttBackend.REMOTE_WHISPER -> REMOTE_CHUNK_SECONDS
            }

            val chunkBytes = SAMPLE_RATE * 2 * chunkSeconds
            val readBuffer = ByteArray(4096)
            var consecutiveSilentChunks = 0
            var pcmBuffer = ByteArrayOutputStream(chunkBytes)
            var chunkGeneration = captureGeneration

            try {
                while (isActive && running) {
                    val read = audioRecord?.read(
                        readBuffer,
                        0,
                        readBuffer.size
                    ) ?: -1

                    if (read < 0) {
                        Log.e("SystemAudioEngine", "AudioRecord read failed: $read")
                        onSttError("System audio capture failed (AudioRecord $read)")
                        statusMutable.value = RecognitionStatus.ERROR
                        delay(500)
                        continue
                    }

                    if (read == 0) {
                        delay(10)
                        continue
                    }

                    // While paused, keep draining the recorder but discard audio.
                    if (paused) {
                        pcmBuffer.reset()
                        chunkGeneration = captureGeneration
                        continue
                    }

                    // A pause/resume happened while this chunk was being collected.
                    if (chunkGeneration != captureGeneration) {
                        pcmBuffer.reset()
                        chunkGeneration = captureGeneration
                    }

                    pcmBuffer.write(readBuffer, 0, read)

                    if (pcmBuffer.size() < chunkBytes) continue

                    val pcmData = pcmBuffer.toByteArray()
                    pcmBuffer.reset()

                    if (paused || chunkGeneration != captureGeneration) {
                        chunkGeneration = captureGeneration
                        continue
                    }

                    chunkGeneration = captureGeneration

                    if (pcmData.size < SAMPLE_RATE) continue

                    val averageAmplitude = averageAbsAmplitude(pcmData)

                    if (averageAmplitude < SILENCE_AVERAGE_ABS_THRESHOLD) {
                        consecutiveSilentChunks++

                        if (consecutiveSilentChunks >= SILENT_CHUNKS_BEFORE_HINT) {
                            onSttError(NO_CAPTURABLE_AUDIO_MESSAGE)
                        }
                        continue
                    }

                    consecutiveSilentChunks = 0

                    audioQueue.trySend(
                        AudioChunk(
                            pcmData = pcmData,
                            generation = captureGeneration,
                            averageAmplitude = averageAmplitude
                        )
                    )
                }
            } finally {
                audioQueue.close()
            }
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val REMOTE_CHUNK_SECONDS = 6
        private const val LOCAL_CHUNK_SECONDS = 2
        private const val QUEUE_CAPACITY = 2

        private const val SILENCE_AVERAGE_ABS_THRESHOLD = 120
        private const val SILENT_CHUNKS_BEFORE_HINT = 2

        private const val NO_CAPTURABLE_AUDIO_MESSAGE =
            "No capturable system audio yet. Play unmuted media in an app that allows audio capture."

        private fun averageAbsAmplitude(pcmData: ByteArray): Int {
            var sum = 0L
            var samples = 0
            var i = 0

            while (i + 1 < pcmData.size) {
                val low = pcmData[i].toInt() and 0xFF
                val high = pcmData[i + 1].toInt()
                val sample = (high shl 8) or low

                sum += abs(sample)
                samples++
                i += 2
            }

            return if (samples == 0) 0 else (sum / samples).toInt()
        }

        private fun isLikelyHallucinatedTranscript(text: String): Boolean {
            val normalized = text.lowercase().filter { it.isLetterOrDigit() }

            if (normalized.length <= 2) return true
            if (isRepeatedPattern(normalized)) return true

            val words = text.lowercase()
                .split(Regex("\\s+"))
                .map { it.trim { ch -> !ch.isLetterOrDigit() } }
                .filter { it.isNotEmpty() }

            return words.size >= 4 && words.distinct().size == 1
        }

        private fun isRepeatedPattern(value: String): Boolean {
            val maxUnit = (value.length / 3).coerceAtMost(12)

            for (unitSize in 1..maxUnit) {
                if (value.length % unitSize != 0) continue

                val unit = value.substring(0, unitSize)

                if (unit.repeat(value.length / unitSize) == value) {
                    return true
                }
            }

            return false
        }
    }
}

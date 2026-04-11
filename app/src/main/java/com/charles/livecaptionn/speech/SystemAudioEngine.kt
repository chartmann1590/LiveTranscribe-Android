package com.charles.livecaptionn.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.util.Log
import com.charles.livecaptionn.settings.SttBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * Captures system/internal audio via MediaProjection + AudioPlaybackCapture,
 * buffers a short PCM window, encodes to WAV, sends to a Whisper-compatible
 * STT endpoint, and delivers recognized text via callback.
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
    /** Null clears any STT error hint after a successful round-trip to the server. */
    private val onSttError: (String?) -> Unit = {}
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

        captureJob = scope.launch(Dispatchers.Default) {
            val chunkSeconds = when (sttBackend) {
                SttBackend.LOCAL_VOSK -> LOCAL_CHUNK_SECONDS
                SttBackend.REMOTE_WHISPER -> REMOTE_CHUNK_SECONDS
            }
            val chunkBytes = SAMPLE_RATE * 2 * chunkSeconds // 16-bit mono = 2 bytes/sample
            val readBuffer = ByteArray(4096)
            var consecutiveSilentChunks = 0

            while (isActive && running) {
                if (paused) {
                    delay(200)
                    continue
                }

                val pcmBuffer = ByteArrayOutputStream(chunkBytes)
                val startTime = System.currentTimeMillis()
                var readError: Int? = null

                // Collect a short audio window before running STT.
                while (pcmBuffer.size() < chunkBytes && isActive && running && !paused) {
                    val read = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                    when {
                        read > 0 -> pcmBuffer.write(readBuffer, 0, read)
                        read == 0 -> delay(20)
                        else -> {
                            readError = read
                            break
                        }
                    }
                    if (System.currentTimeMillis() - startTime > chunkSeconds * 1100L) break
                }

                if (readError != null) {
                    Log.e("SystemAudioEngine", "AudioRecord read failed: $readError")
                    onSttError("System audio capture failed (AudioRecord $readError)")
                    statusMutable.value = RecognitionStatus.ERROR
                    delay(1000)
                    continue
                }

                if (pcmBuffer.size() < SAMPLE_RATE) continue // skip tiny chunks

                val pcmData = pcmBuffer.toByteArray()
                val averageAmplitude = averageAbsAmplitude(pcmData)
                if (averageAmplitude < SILENCE_AVERAGE_ABS_THRESHOLD) {
                    consecutiveSilentChunks += 1
                    if (consecutiveSilentChunks >= SILENT_CHUNKS_BEFORE_HINT) {
                        onSttError(NO_CAPTURABLE_AUDIO_MESSAGE)
                    }
                    statusMutable.value = if (paused) RecognitionStatus.PAUSED else RecognitionStatus.LISTENING
                    continue
                }

                consecutiveSilentChunks = 0
                statusMutable.value = RecognitionStatus.PROCESSING
                val outcome = when (sttBackend) {
                    SttBackend.REMOTE_WHISPER -> {
                        val wavData = WavEncoder.encode(pcmData, SAMPLE_RATE)
                        sttClient.transcribe(wavData, sttUrl, languageCode)
                    }
                    SttBackend.LOCAL_VOSK -> {
                        localSttClient.transcribe(pcmData, SAMPLE_RATE, sourceLanguageCode)
                    }
                }
                val err = outcome.errorMessage
                if (err != null) {
                    onSttError(err)
                    statusMutable.value = RecognitionStatus.ERROR
                } else {
                    onSttError(null)
                    val transcript = outcome.text.trim()
                    if (transcript.isNotBlank()) {
                        val shouldSuppress = sttBackend == SttBackend.REMOTE_WHISPER &&
                            isLikelyHallucinatedTranscript(transcript)
                        if (shouldSuppress) {
                            Log.w("SystemAudioEngine", "Ignoring likely hallucinated transcript: $transcript")
                            onSttError("Ignored low-confidence speech result")
                        } else {
                            Log.d("SystemAudioEngine", "STT text avgAmp=$averageAmplitude text=$transcript")
                            onResult(SpeechResult(transcript, isFinal = true))
                        }
                    }
                    if (!paused && running) {
                        statusMutable.value = RecognitionStatus.LISTENING
                    }
                }
            }
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val REMOTE_CHUNK_SECONDS = 6
        private const val LOCAL_CHUNK_SECONDS = 2
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
                samples += 1
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
                if (unit.repeat(value.length / unitSize) == value) return true
            }
            return false
        }
    }
}

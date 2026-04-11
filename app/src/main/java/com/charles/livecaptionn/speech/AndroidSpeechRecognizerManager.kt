package com.charles.livecaptionn.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fallback engine that wraps Android's platform [SpeechRecognizer]. Prefers
 * the on-device recognizer introduced in Android 12 (API 31) — this is the
 * same service that powers Google Live Caption, so quality and latency match
 * when the user's device and language are supported. Falls back to the
 * default recognizer otherwise, with `EXTRA_PREFER_OFFLINE` set so it still
 * prefers local models when Google has them cached.
 *
 * The platform API is utterance-oriented: we immediately re-arm after each
 * `onResults`/`onError` so captions feel continuous.
 */
class AndroidSpeechRecognizerManager(
    private val context: Context,
    private val onSpeechResult: (SpeechResult) -> Unit
) : SpeechEngine {

    private val statusMutable = MutableStateFlow(RecognitionStatus.IDLE)
    val status: StateFlow<RecognitionStatus> = statusMutable

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var paused = false
    private var running = false
    private var languageCode: String = "en-US"

    fun setLanguage(languageCode: String) {
        this.languageCode = languageCode
    }

    override fun start() {
        if (running) return
        running = true
        paused = false
        ensureRecognizer()
        startListeningNow()
    }

    override fun stop() {
        running = false
        paused = false
        statusMutable.value = RecognitionStatus.IDLE
        mainHandler.removeCallbacksAndMessages(null)
        try { recognizer?.stopListening() } catch (_: Throwable) {}
        try { recognizer?.cancel() } catch (_: Throwable) {}
        try { recognizer?.destroy() } catch (_: Throwable) {}
        recognizer = null
    }

    override fun pause() {
        paused = true
        statusMutable.value = RecognitionStatus.PAUSED
        try { recognizer?.stopListening() } catch (_: Throwable) {}
    }

    override fun resume() {
        if (!running) return
        paused = false
        startListeningNow()
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return
        val created = createPreferredRecognizer()
        recognizer = created.apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusMutable.value = RecognitionStatus.LISTENING
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    statusMutable.value = RecognitionStatus.PROCESSING
                }

                override fun onError(error: Int) {
                    Log.w("SpeechManager", "SpeechRecognizer error: $error")
                    // ERROR_NO_MATCH / ERROR_SPEECH_TIMEOUT are normal mid-silence
                    // conditions on a continuous stream; just re-arm fast.
                    val isBenign = error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    if (!isBenign) statusMutable.value = RecognitionStatus.ERROR
                    restartSoon(if (isBenign) 0L else 250L)
                }

                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isNotBlank()) onSpeechResult(SpeechResult(text, isFinal = true))
                    // Re-arm immediately for the next utterance — no 700ms gap.
                    restartSoon(0L)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isNotBlank()) onSpeechResult(SpeechResult(text, isFinal = false))
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun createPreferredRecognizer(): SpeechRecognizer {
        // On-device recognizer (Android 12+) = same engine as Google Live Caption.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                }
            } catch (t: Throwable) {
                Log.w("SpeechManager", "On-device recognizer unavailable, falling back", t)
            }
        }
        // Try to route to Google's recognition service explicitly if installed —
        // it's more reliable than the platform default on OEM builds.
        val googleComponent = ComponentName(
            "com.google.android.googlequicksearchbox",
            "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
        )
        return try {
            SpeechRecognizer.createSpeechRecognizer(context, googleComponent)
        } catch (_: Throwable) {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    private fun startListeningNow() {
        if (!running || paused) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
            // Strongly prefer on-device / cached models so captions don't need the cloud.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Long-form captioning: let the recognizer run with minimal end-pointing
            // so we don't cut off mid-sentence.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500)
        }
        try {
            statusMutable.value = RecognitionStatus.LISTENING
            recognizer?.startListening(intent)
        } catch (t: Throwable) {
            Log.e("SpeechManager", "Failed to start listening", t)
            statusMutable.value = RecognitionStatus.ERROR
            restartSoon(500L)
        }
    }

    private fun restartSoon(delayMs: Long) {
        if (!running || paused) return
        mainHandler.removeCallbacksAndMessages(null)
        if (delayMs <= 0L) mainHandler.post { startListeningNow() }
        else mainHandler.postDelayed({ startListeningNow() }, delayMs)
    }
}

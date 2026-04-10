package com.charles.livecaptionn.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
        recognizer?.stopListening()
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    override fun pause() {
        paused = true
        statusMutable.value = RecognitionStatus.PAUSED
        recognizer?.stopListening()
    }

    override fun resume() {
        if (!running) return
        paused = false
        startListeningNow()
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusMutable.value = RecognitionStatus.LISTENING
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    statusMutable.value = RecognitionStatus.PROCESSING
                    restartSoon()
                }

                override fun onError(error: Int) {
                    Log.w("SpeechManager", "SpeechRecognizer error: $error")
                    statusMutable.value = RecognitionStatus.ERROR
                    restartSoon()
                }

                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isNotBlank()) onSpeechResult(SpeechResult(text, isFinal = true))
                    restartSoon()
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

    private fun startListeningNow() {
        if (!running || paused) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
        }
        try {
            statusMutable.value = RecognitionStatus.LISTENING
            recognizer?.startListening(intent)
        } catch (t: Throwable) {
            Log.e("SpeechManager", "Failed to start listening", t)
            statusMutable.value = RecognitionStatus.ERROR
            restartSoon()
        }
    }

    private fun restartSoon() {
        if (!running || paused) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({ startListeningNow() }, 700L)
    }
}

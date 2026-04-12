package com.charles.livecaptionn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.charles.livecaptionn.LiveCaptionApp
import com.charles.livecaptionn.MainActivity
import com.charles.livecaptionn.R
import com.charles.livecaptionn.data.TranscriptEntry
import com.charles.livecaptionn.overlay.OverlayController
import com.charles.livecaptionn.overlay.OverlayUiState
import com.charles.livecaptionn.settings.AudioSource
import com.charles.livecaptionn.settings.CaptionSettings
import com.charles.livecaptionn.settings.LocaleMap
import com.charles.livecaptionn.settings.SttBackend
import com.charles.livecaptionn.speech.AndroidSpeechRecognizerManager
import com.charles.livecaptionn.speech.RecognitionStatus
import com.charles.livecaptionn.speech.SpeechEngine
import com.charles.livecaptionn.speech.SpeechResult
import com.charles.livecaptionn.speech.StreamingSttEngine
import com.charles.livecaptionn.speech.SystemAudioEngine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(FlowPreview::class)
class CaptionForegroundService : Service() {
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var app: LiveCaptionApp

    private val captionSessionActive = AtomicBoolean(false)

    /** Bumps when the overlay view is attached so combine() re-runs (was skipping while overlay was null). */
    private val overlayReady = MutableStateFlow(0)

    private var speechEngine: SpeechEngine? = null
    private var overlayController: OverlayController? = null
    private var paused = false
    private var bufferedText = ""
    @Volatile
    private var historyOnNextTranslate = false

    /** Mic partials arrive faster than 400ms; cancel+restart jobs never finish. Debounce coalesces to one translate. */
    private val translateRequests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var currentAudioSource = AudioSource.MIC

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startFlow()
            ACTION_STOP -> stopFlow()
            ACTION_PAUSE_RESUME -> togglePause()
            ACTION_TOGGLE_MINIMIZE -> toggleMinimized()
        }
        return START_STICKY
    }

    private fun startFlow() {
        app = application as LiveCaptionApp
        if (!captionSessionActive.compareAndSet(false, true)) return
        if (!Settings.canDrawOverlays(this)) {
            captionSessionActive.set(false)
            Log.e("CaptionService", "Overlay permission missing.")
            stopSelf()
            return
        }

        serviceScope.launch {
            // Always start a fresh session un-minimized; stale overlayMinimized=true from a
            // prior run was hiding the caption body entirely.
            app.container.settingsRepository.update { it.copy(overlayMinimized = false) }
            val settings = app.container.settingsRepository.settingsFlow.first()
            Log.d("CaptionService", "startFlow settings minimized=${settings.overlayMinimized} w=${settings.overlayWidthDp}dp h=${settings.overlayHeightDp}dp audio=${settings.audioSource}")
            currentAudioSource = settings.audioSource
            val sttLanguageCode = settings.sourceLanguageCode.ifBlank { "en" }
            val localeForSpeechRec = LocaleMap.bcp47(sttLanguageCode)

            // Start foreground with appropriate service type
            startForeground(
                NOTIF_ID,
                buildNotification(),
                if (currentAudioSource == AudioSource.SYSTEM)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                else
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )

            app.container.runtimeStore.update {
                it.copy(running = true, paused = false, status = RecognitionStatus.LISTENING)
            }

            // Prewarm the active translation backend so the first spoken
            // word doesn't stall waiting on a 30 MB ML Kit model download.
            launch(Dispatchers.IO) {
                val s = app.container.settingsRepository.settingsFlow.first()
                runCatching {
                    app.container.translationRepository.prewarm(
                        s.sourceLanguageCode,
                        s.targetLanguageCode
                    )
                }
            }

            launch(Dispatchers.IO) {
                translateRequests
                    .debounce(TRANSLATE_DEBOUNCE_MS)
                    .collect {
                        val textSnapshot = bufferedText
                        if (textSnapshot.isBlank()) return@collect
                        val saveHistory = historyOnNextTranslate
                        historyOnNextTranslate = false
                        val captionSettings = app.container.settingsRepository.settingsFlow.first()
                        val translated = app.container.translationRepository.translate(
                            text = textSnapshot,
                            sourceCode = captionSettings.sourceLanguageCode,
                            targetCode = captionSettings.targetLanguageCode,
                            autoDetect = currentAudioSource == AudioSource.SYSTEM || captionSettings.autoDetectSource
                        )
                        // Empty result means the backend genuinely failed
                        // (network drop, model not yet downloaded, unsupported
                        // pair etc.). Preserve the last good translation
                        // instead of overwriting it with the raw source.
                        if (translated.isBlank()) {
                            app.container.runtimeStore.update {
                                it.copy(
                                    status = if (paused) RecognitionStatus.PAUSED else RecognitionStatus.LISTENING
                                )
                            }
                            return@collect
                        }
                        app.container.runtimeStore.update {
                            it.copy(
                                translatedText = translated,
                                transcriptLines = updateTranscriptTranslation(
                                    lines = it.transcriptLines,
                                    originalText = textSnapshot,
                                    translatedText = translated
                                ),
                                status = if (paused) RecognitionStatus.PAUSED else RecognitionStatus.LISTENING
                            )
                        }
                        if (saveHistory && textSnapshot.isNotBlank()) {
                            app.container.transcriptHistory.add(
                                TranscriptEntry(
                                    originalText = textSnapshot,
                                    translatedText = translated,
                                    sourceLanguage = if (currentAudioSource == AudioSource.SYSTEM) {
                                        "auto"
                                    } else {
                                        captionSettings.sourceLanguageCode
                                    },
                                    targetLanguage = captionSettings.targetLanguageCode
                                )
                            )
                        }
                    }
            }

            val onSpeechResult = { result: SpeechResult ->
                val transcript = result.text.trim()
                Log.d("CaptionService", "onSpeechResult final=${result.isFinal} text='$transcript'")
                app.container.runtimeStore.update {
                    it.copy(
                        originalText = transcript,
                        // Partials overwrite the open line; finals close it so the next
                        // utterance starts fresh. Works the same for mic and system audio
                        // now that both stream.
                        transcriptLines = recordTranscriptResult(
                            lines = it.transcriptLines,
                            originalText = transcript,
                            replaceOpenLine = !result.isFinal
                        ),
                        status = RecognitionStatus.PROCESSING,
                        lastError = null
                    )
                }
                queueTranslation(transcript, result.isFinal)
            }

            val mediaProjection = if (currentAudioSource == AudioSource.SYSTEM) {
                val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val data = MediaProjectionHolder.data
                if (data == null) {
                    Log.e("CaptionService", "MediaProjection data missing.")
                    captionSessionActive.set(false)
                    stopSelf()
                    return@launch
                }
                mpManager.getMediaProjection(MediaProjectionHolder.resultCode, data)
            } else {
                null
            }

            val errorSink: (String?) -> Unit = { msg ->
                app.container.runtimeStore.update { it.copy(lastError = msg) }
            }

            // Routing:
            //   LOCAL_VOSK  → streaming Vosk for both mic and system audio (preferred: low-latency, live partials)
            //   REMOTE_WHISPER:
            //       system  → legacy batch SystemAudioEngine (posts WAVs to remote Whisper)
            //       mic     → AndroidSpeechRecognizerManager (Google recognizer; kept as fallback)
            val backend = settings.sttBackend
            val useStreaming = backend == SttBackend.LOCAL_VOSK

            if (useStreaming) {
                val engine = StreamingSttEngine(
                    context = this@CaptionForegroundService,
                    audioSource = currentAudioSource,
                    mediaProjection = mediaProjection,
                    languageCode = sttLanguageCode,
                    localSttClient = app.container.localVoskClient,
                    scope = serviceScope,
                    onResult = onSpeechResult,
                    onError = errorSink
                )
                speechEngine = engine
                observeEngineStatus(engine.status)
                engine.start()
            } else {
                when (currentAudioSource) {
                    AudioSource.SYSTEM -> {
                        val engine = SystemAudioEngine(
                            context = this@CaptionForegroundService,
                            projection = mediaProjection!!,
                            sttUrl = settings.sttBaseUrl,
                            languageCode = sttLanguageCode,
                            sourceLanguageCode = sttLanguageCode,
                            sttBackend = settings.sttBackend,
                            localSttClient = app.container.localVoskClient,
                            scope = serviceScope,
                            onResult = onSpeechResult,
                            onSttError = errorSink
                        )
                        speechEngine = engine
                        observeEngineStatus(engine.status)
                        engine.start()
                    }
                    AudioSource.MIC -> {
                        val engine = AndroidSpeechRecognizerManager(
                            this@CaptionForegroundService, onSpeechResult
                        )
                        engine.setLanguage(localeForSpeechRec)
                        speechEngine = engine
                        observeEngineStatus(engine.status)
                        engine.start()
                    }
                }
            }

            showOverlay()
            observeOverlayUpdates()
        }
    }

    private fun observeEngineStatus(statusFlow: StateFlow<RecognitionStatus>) {
        serviceScope.launch {
            statusFlow.collectLatest { status ->
                app.container.runtimeStore.update { it.copy(status = status) }
            }
        }
    }

    /**
     * Drive the overlay from a single combined stream so rapid runtime + settings changes
     * cannot apply out-of-order (stale coroutines overwriting newer translation text).
     */
    private fun observeOverlayUpdates() {
        serviceScope.launch {
            combine(
                app.container.runtimeStore.state,
                app.container.settingsRepository.settingsFlow,
                overlayReady
            ) { runtime, settings, _ -> runtime to settings }
                .collectLatest { (runtime, settings) ->
                    val overlay = overlayController ?: return@collectLatest
                    val ui = buildOverlayUi(runtime, settings)
                    Log.d(
                        "CaptionService",
                        "overlay update lines=${runtime.transcriptLines.size} " +
                            "orig='${runtime.originalText.take(40)}' " +
                            "trans='${runtime.translatedText.take(40)}' " +
                            "body='${ui.transcriptText.take(60)}'"
                    )
                    overlay.update(ui)
                }
        }
    }

    private fun buildOverlayUi(
        runtime: CaptionRuntimeState,
        settings: CaptionSettings
    ) = OverlayUiState(
        originalText = runtime.originalText,
        translatedText = runtime.translatedText.ifBlank { runtime.originalText },
        transcriptText = overlayTranscriptText(runtime),
        status = runtime.status,
        statusDetail = runtime.lastError,
        textSizeSp = settings.textSizeSp,
        opacity = settings.overlayOpacity,
        showOriginal = settings.showOriginal,
        minimized = settings.overlayMinimized
    )

    /**
     * Build the scrolling caption body. The overlay only ever shows translated
     * text — lines that are still waiting on a translation are intentionally
     * hidden so the user doesn't see both the raw source and the translation
     * stacked together. Raw source is still kept in `transcriptLines` for the
     * transcript history screen; it just isn't rendered in the overlay.
     */
    private fun overlayTranscriptText(runtime: CaptionRuntimeState): String {
        val rendered = runtime.transcriptLines
            .mapNotNull { it.translatedText.trim().takeIf { t -> t.isNotBlank() } }
        val historyText = if (rendered.isNotEmpty()) rendered.joinToString("\n") else ""
        val livePartial = runtime.translatedText.trim()
        return when {
            historyText.isBlank() -> livePartial
            livePartial.isBlank() -> historyText
            historyText.endsWith(livePartial) -> historyText
            else -> "$historyText\n$livePartial"
        }
    }

    private fun queueTranslation(text: String, isFinal: Boolean = false) {
        bufferedText = text
        if (isFinal) historyOnNextTranslate = true
        translateRequests.tryEmit(Unit)
    }

    private fun recordTranscriptResult(
        lines: List<CaptionRuntimeLine>,
        originalText: String,
        replaceOpenLine: Boolean
    ): List<CaptionRuntimeLine> {
        if (originalText.isBlank()) return lines
        val last = lines.lastOrNull()
        if (last?.originalText == originalText && last.translatedText.isBlank()) return lines
        if (replaceOpenLine && last?.translatedText.isNullOrBlank()) {
            return (lines.dropLast(1) + CaptionRuntimeLine(originalText = originalText))
                .takeLast(MAX_TRANSCRIPT_LINES)
        }
        return (lines + CaptionRuntimeLine(originalText = originalText)).takeLast(MAX_TRANSCRIPT_LINES)
    }

    private fun updateTranscriptTranslation(
        lines: List<CaptionRuntimeLine>,
        originalText: String,
        translatedText: String
    ): List<CaptionRuntimeLine> {
        if (originalText.isBlank()) return lines
        val existingIndex = lines.indexOfLast { it.originalText == originalText }
        if (existingIndex >= 0) {
            return lines.mapIndexed { index, line ->
                if (index == existingIndex) line.copy(translatedText = translatedText) else line
            }
        }
        // No exact match — the live partial moved on between the debounce
        // trigger and the translation returning. Drop the stale result
        // rather than spawning an orphan line (which previously caused
        // double captions in the overlay). The next translate pass will
        // cover the current partial.
        return lines
    }

    private fun showOverlay() {
        if (overlayController != null) return
        serviceScope.launch {
            val settings = app.container.settingsRepository.settingsFlow.first()
            overlayController = OverlayController(
                context = this@CaptionForegroundService,
                scope = serviceScope,
                settingsRepository = app.container.settingsRepository,
                onPauseResume = { togglePause() },
                onClose = { stopFlow() },
                onToggleMinimize = { toggleMinimized() }
            ).apply {
                show(settings.overlayX, settings.overlayY, settings.overlayWidthDp, settings.overlayHeightDp)
            }
            val runtime = app.container.runtimeStore.state.value
            overlayController?.update(buildOverlayUi(runtime, settings))
            overlayReady.value = overlayReady.value + 1
        }
    }

    private fun togglePause() {
        paused = !paused
        if (paused) speechEngine?.pause() else speechEngine?.resume()
        app.container.runtimeStore.update {
            it.copy(
                paused = paused,
                status = if (paused) RecognitionStatus.PAUSED else RecognitionStatus.LISTENING
            )
        }
    }

    private fun toggleMinimized() {
        serviceScope.launch {
            app.container.settingsRepository.update { it.copy(overlayMinimized = !it.overlayMinimized) }
        }
    }

    private fun stopFlow() {
        captionSessionActive.set(false)
        try { speechEngine?.stop() } catch (_: Throwable) {}
        speechEngine = null
        overlayController?.hide()
        overlayController = null
        overlayReady.value = 0
        MediaProjectionHolder.clear()
        app.container.runtimeStore.update { CaptionRuntimeState() }
        serviceScope.cancel()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { stopFlow() } catch (_: Throwable) {}
    }

    private fun buildNotification(): Notification {
        createNotificationChannel()
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pauseIntent = PendingIntent.getService(
            this, 2,
            Intent(this, CaptionForegroundService::class.java).apply { action = ACTION_PAUSE_RESUME },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 3,
            Intent(this, CaptionForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pending)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause/Resume", pauseIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "caption.action.START"
        const val ACTION_STOP = "caption.action.STOP"
        const val ACTION_PAUSE_RESUME = "caption.action.PAUSE_RESUME"
        const val ACTION_TOGGLE_MINIMIZE = "caption.action.TOGGLE_MINIMIZE"

        private const val CHANNEL_ID = "caption_channel"
        private const val NOTIF_ID = 2001
        private const val TRANSLATE_DEBOUNCE_MS = 450L
        private const val MAX_TRANSCRIPT_LINES = 30
    }
}

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
import com.charles.livecaptionn.settings.AppLanguage
import com.charles.livecaptionn.settings.AudioSource
import com.charles.livecaptionn.speech.AndroidSpeechRecognizerManager
import com.charles.livecaptionn.speech.RecognitionStatus
import com.charles.livecaptionn.speech.SpeechEngine
import com.charles.livecaptionn.speech.SpeechResult
import com.charles.livecaptionn.speech.SystemAudioEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CaptionForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var app: LiveCaptionApp

    private var speechEngine: SpeechEngine? = null
    private var overlayController: OverlayController? = null
    private var paused = false
    private var translateJob: Job? = null
    private var bufferedText = ""
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
        if (!Settings.canDrawOverlays(this)) {
            Log.e("CaptionService", "Overlay permission missing.")
            stopSelf()
            return
        }

        serviceScope.launch {
            val settings = app.container.settingsRepository.settingsFlow.first()
            currentAudioSource = settings.audioSource
            val locale = if (settings.sourceLanguage == AppLanguage.VIETNAMESE) "vi" else "en"
            val localeForSpeechRec = if (settings.sourceLanguage == AppLanguage.VIETNAMESE) "vi-VN" else "en-US"

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

            val onSpeechResult = { result: SpeechResult ->
                app.container.runtimeStore.update {
                    it.copy(originalText = result.text, status = RecognitionStatus.PROCESSING)
                }
                queueTranslation(result.text, result.isFinal)
            }

            when (currentAudioSource) {
                AudioSource.SYSTEM -> {
                    val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val data = MediaProjectionHolder.data
                    if (data == null) {
                        Log.e("CaptionService", "MediaProjection data missing.")
                        stopSelf()
                        return@launch
                    }
                    val projection = mpManager.getMediaProjection(
                        MediaProjectionHolder.resultCode, data
                    )
                    val engine = SystemAudioEngine(
                        projection = projection,
                        sttUrl = settings.sttBaseUrl,
                        languageCode = locale,
                        scope = serviceScope,
                        onResult = onSpeechResult
                    )
                    speechEngine = engine
                    observeSystemAudioStatus(engine)
                    engine.start()
                }
                AudioSource.MIC -> {
                    val engine = AndroidSpeechRecognizerManager(
                        this@CaptionForegroundService, onSpeechResult
                    )
                    engine.setLanguage(localeForSpeechRec)
                    speechEngine = engine
                    observeMicStatus(engine)
                    engine.start()
                }
            }

            showOverlay()
            observeRuntimeState()
        }
    }

    private fun observeMicStatus(engine: AndroidSpeechRecognizerManager) {
        serviceScope.launch {
            engine.status.collectLatest { status ->
                app.container.runtimeStore.update { it.copy(status = status) }
                renderOverlay()
            }
        }
    }

    private fun observeSystemAudioStatus(engine: SystemAudioEngine) {
        serviceScope.launch {
            engine.status.collectLatest { status ->
                app.container.runtimeStore.update { it.copy(status = status) }
                renderOverlay()
            }
        }
    }

    private fun observeRuntimeState() {
        serviceScope.launch {
            app.container.runtimeStore.state.collectLatest { renderOverlay() }
        }
    }

    private fun queueTranslation(text: String, isFinal: Boolean = false) {
        bufferedText = text
        translateJob?.cancel()
        translateJob = serviceScope.launch(Dispatchers.IO) {
            delay(400)
            val settings = app.container.settingsRepository.settingsFlow.first()
            val translated = app.container.translationRepository.translate(
                text = bufferedText,
                source = settings.sourceLanguage,
                target = settings.targetLanguage,
                autoDetect = settings.autoDetectSource
            )
            val translatedResult = translated.ifBlank { bufferedText }
            app.container.runtimeStore.update {
                it.copy(
                    translatedText = translatedResult,
                    status = if (paused) RecognitionStatus.PAUSED else RecognitionStatus.LISTENING
                )
            }
            // Save final results to transcript history
            if (isFinal && bufferedText.isNotBlank()) {
                app.container.transcriptHistory.add(
                    TranscriptEntry(
                        originalText = bufferedText,
                        translatedText = translatedResult,
                        sourceLanguage = settings.sourceLanguage.code,
                        targetLanguage = settings.targetLanguage.code
                    )
                )
            }
        }
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
            renderOverlay()
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
            renderOverlay()
        }
    }

    private fun renderOverlay() {
        val overlay = overlayController ?: return
        serviceScope.launch {
            val settings = app.container.settingsRepository.settingsFlow.first()
            val runtime = app.container.runtimeStore.state.value
            overlay.update(
                OverlayUiState(
                    originalText = runtime.originalText,
                    translatedText = runtime.translatedText.ifBlank { runtime.originalText },
                    status = runtime.status,
                    textSizeSp = settings.textSizeSp,
                    opacity = settings.overlayOpacity,
                    showOriginal = settings.showOriginal,
                    minimized = settings.overlayMinimized
                )
            )
        }
    }

    private fun stopFlow() {
        try { speechEngine?.stop() } catch (_: Throwable) {}
        speechEngine = null
        overlayController?.hide()
        overlayController = null
        MediaProjectionHolder.clear()
        app.container.runtimeStore.update { CaptionRuntimeState() }
        serviceScope.cancel()
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
    }
}

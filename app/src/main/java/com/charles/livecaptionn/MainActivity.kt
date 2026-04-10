package com.charles.livecaptionn

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.charles.livecaptionn.service.CaptionForegroundService
import com.charles.livecaptionn.service.MediaProjectionHolder
import com.charles.livecaptionn.settings.AudioSource
import com.charles.livecaptionn.ui.HistoryScreen
import com.charles.livecaptionn.ui.MainScreen
import com.charles.livecaptionn.ui.MainViewModel
import com.charles.livecaptionn.ui.MainViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var showHistory by mutableStateOf(false)

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCaptioning()
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MediaProjectionHolder.set(result.resultCode, result.data!!.clone() as Intent)
            startCaptionService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LiveCaptionApp
        setContent {
            val vm: MainViewModel = viewModel(factory = MainViewModelFactory(app.container, application))
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (showHistory) {
                        HistoryScreen(
                            historyStore = app.container.transcriptHistory,
                            onBack = { showHistory = false }
                        )
                    } else {
                        MainScreen(
                            viewModel = vm,
                            onRequestAudioPermission = { requestAudioPermission() },
                            onStart = { startCaptioning() },
                            onStop = { stopCaptionService() },
                            onOpenOverlaySettings = { vm.openOverlayPermissionSettings(this) },
                            onHistory = { showHistory = true }
                        )
                    }
                }
            }
        }
    }

    private fun requestAudioPermission() {
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startCaptioning() {
        val app = application as LiveCaptionApp
        lifecycleScope.launch {
            val settings = app.container.settingsRepository.settingsFlow.first()
            when (settings.audioSource) {
                AudioSource.SYSTEM -> {
                    val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
                }
                AudioSource.MIC -> {
                    val hasMic = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasMic) {
                        startCaptionService()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        }
    }

    private fun startCaptionService() {
        val intent = Intent(this, CaptionForegroundService::class.java).apply {
            action = CaptionForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopCaptionService() {
        val intent = Intent(this, CaptionForegroundService::class.java).apply {
            action = CaptionForegroundService.ACTION_STOP
        }
        startService(intent)
    }
}

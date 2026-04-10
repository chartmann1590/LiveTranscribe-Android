package com.charles.livecaptionn.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.charles.livecaptionn.di.AppContainer
import com.charles.livecaptionn.settings.AppLanguage
import com.charles.livecaptionn.settings.AudioSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(
    private val container: AppContainer,
    application: Application
) : AndroidViewModel(application) {

    private val mutableState = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsRepository.settingsFlow.collectLatest { settings ->
                mutableState.value = mutableState.value.copy(
                    settings = settings,
                    micPermissionGranted = hasMicPermission(),
                    overlayPermissionGranted = hasOverlayPermission()
                )
            }
        }
        viewModelScope.launch {
            container.runtimeStore.state.collectLatest { runtime ->
                mutableState.value = mutableState.value.copy(
                    runtime = runtime,
                    micPermissionGranted = hasMicPermission(),
                    overlayPermissionGranted = hasOverlayPermission()
                )
            }
        }
    }

    fun refreshPermissionState() {
        mutableState.value = mutableState.value.copy(
            micPermissionGranted = hasMicPermission(),
            overlayPermissionGranted = hasOverlayPermission()
        )
    }

    fun updateSource(language: AppLanguage) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(sourceLanguage = language) } }
    }

    fun updateTarget(language: AppLanguage) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(targetLanguage = language) } }
    }

    fun setPresetEnToVi() {
        viewModelScope.launch {
            container.settingsRepository.update {
                it.copy(sourceLanguage = AppLanguage.ENGLISH, targetLanguage = AppLanguage.VIETNAMESE)
            }
        }
    }

    fun setPresetViToEn() {
        viewModelScope.launch {
            container.settingsRepository.update {
                it.copy(sourceLanguage = AppLanguage.VIETNAMESE, targetLanguage = AppLanguage.ENGLISH)
            }
        }
    }

    fun updateAutoDetect(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(autoDetectSource = enabled) } }
    }

    fun updateTextSize(size: Float) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(textSizeSp = size) } }
    }

    fun updateOpacity(opacity: Float) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(overlayOpacity = opacity) } }
    }

    fun updateShowOriginal(show: Boolean) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(showOriginal = show) } }
    }

    fun updateBaseUrl(url: String) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(serverBaseUrl = url) } }
    }

    fun updateAudioSource(source: AudioSource) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(audioSource = source) } }
    }

    fun updateSttUrl(url: String) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(sttBaseUrl = url) } }
    }

    fun openOverlayPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(getApplication())
}

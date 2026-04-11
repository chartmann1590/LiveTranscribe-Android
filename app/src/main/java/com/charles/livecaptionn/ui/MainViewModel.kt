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
import com.charles.livecaptionn.settings.AudioSource
import com.charles.livecaptionn.settings.SttBackend
import com.charles.livecaptionn.speech.VoskModelInfo
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
        viewModelScope.launch {
            container.languageCatalogStore.state.collectLatest { catalog ->
                mutableState.value = mutableState.value.copy(
                    libreLanguages = catalog.languages,
                    libreLoading = catalog.loading,
                    libreError = catalog.error
                )
            }
        }
        viewModelScope.launch {
            container.voskRegistry.models.collectLatest { models ->
                mutableState.value = mutableState.value.copy(voskModels = models)
            }
        }
        viewModelScope.launch {
            container.voskRegistry.downloadProgress.collectLatest { progress ->
                mutableState.value = mutableState.value.copy(voskDownloadProgress = progress)
            }
        }
    }

    fun refreshPermissionState() {
        mutableState.value = mutableState.value.copy(
            micPermissionGranted = hasMicPermission(),
            overlayPermissionGranted = hasOverlayPermission()
        )
    }

    fun updateSource(code: String) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(sourceLanguageCode = code) } }
    }

    fun updateTarget(code: String) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(targetLanguageCode = code) } }
    }

    fun swapLanguages() {
        viewModelScope.launch {
            container.settingsRepository.update {
                it.copy(
                    sourceLanguageCode = it.targetLanguageCode,
                    targetLanguageCode = it.sourceLanguageCode
                )
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
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(serverBaseUrl = url) }
            container.languageCatalogStore.refresh()
        }
    }

    fun refreshLibreCatalog() {
        container.languageCatalogStore.refresh()
    }

    fun updateAudioSource(source: AudioSource) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(audioSource = source) } }
    }

    fun updateSttBackend(backend: SttBackend) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(sttBackend = backend) } }
    }

    fun updateSttUrl(url: String) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(sttBaseUrl = url) } }
    }

    fun downloadVoskModel(model: VoskModelInfo) {
        viewModelScope.launch { container.voskRegistry.downloadAndInstall(model) }
    }

    fun deleteVoskModel(model: VoskModelInfo) {
        if (model.isBundled) return
        viewModelScope.launch { container.voskRegistry.uninstall(model.modelName) }
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

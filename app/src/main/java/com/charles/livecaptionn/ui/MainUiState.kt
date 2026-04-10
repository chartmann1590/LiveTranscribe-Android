package com.charles.livecaptionn.ui

import com.charles.livecaptionn.service.CaptionRuntimeState
import com.charles.livecaptionn.settings.CaptionSettings

data class MainUiState(
    val settings: CaptionSettings = CaptionSettings(),
    val runtime: CaptionRuntimeState = CaptionRuntimeState(),
    val micPermissionGranted: Boolean = false,
    val overlayPermissionGranted: Boolean = false
)

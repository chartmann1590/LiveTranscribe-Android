package com.charles.livecaptionn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.livecaptionn.settings.AppLanguage
import com.charles.livecaptionn.settings.AudioSource
import com.charles.livecaptionn.speech.RecognitionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestAudioPermission: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onHistory: () -> Unit = {}
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var translateUrlDraft by remember(ui.settings.serverBaseUrl) { mutableStateOf(ui.settings.serverBaseUrl) }
    var sttUrlDraft by remember(ui.settings.sttBaseUrl) { mutableStateOf(ui.settings.sttBaseUrl) }
    LaunchedEffect(Unit) { viewModel.refreshPermissionState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Live CaptionN", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Filled.History, contentDescription = "History")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Start / Stop ──
            CaptionControlCard(ui, onStart, onStop)

            // ── Permissions ──
            PermissionsCard(ui, onRequestAudioPermission, onOpenOverlaySettings)

            // ── Audio Source ──
            AudioSourceCard(ui, viewModel)

            // ── Languages ──
            LanguageCard(ui, viewModel)

            // ── Overlay Settings ──
            OverlaySettingsCard(ui, viewModel)

            // ── Server URLs ──
            ServerCard(
                translateUrl = translateUrlDraft,
                onTranslateUrlChange = { translateUrlDraft = it },
                onSaveTranslateUrl = { viewModel.updateBaseUrl(translateUrlDraft) },
                sttUrl = sttUrlDraft,
                onSttUrlChange = { sttUrlDraft = it },
                onSaveSttUrl = { viewModel.updateSttUrl(sttUrlDraft) },
                showStt = ui.settings.audioSource == AudioSource.SYSTEM
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

// ── Control Card ──

@Composable
private fun CaptionControlCard(ui: MainUiState, onStart: () -> Unit, onStop: () -> Unit) {
    val isRunning = ui.runtime.running

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status indicator
            val statusColor = when (ui.runtime.status) {
                RecognitionStatus.LISTENING -> MaterialTheme.colorScheme.primary
                RecognitionStatus.PROCESSING -> MaterialTheme.colorScheme.tertiary
                RecognitionStatus.PAUSED -> MaterialTheme.colorScheme.outline
                RecognitionStatus.ERROR -> MaterialTheme.colorScheme.error
                RecognitionStatus.IDLE -> MaterialTheme.colorScheme.outline
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "Status: ${ui.runtime.status.name}" else "Ready",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(12.dp))

            if (isRunning && ui.runtime.originalText.isNotBlank()) {
                Text(
                    text = ui.runtime.originalText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                if (ui.runtime.translatedText.isNotBlank()) {
                    Text(
                        text = ui.runtime.translatedText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStart,
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start")
                }
                Button(
                    onClick = onStop,
                    enabled = isRunning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stop")
                }
            }
        }
    }
}

// ── Permissions Card ──

@Composable
private fun PermissionsCard(
    ui: MainUiState,
    onRequestAudioPermission: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Permissions")

            PermissionRow("Microphone", ui.micPermissionGranted) { onRequestAudioPermission() }
            PermissionRow("Overlay", ui.overlayPermissionGranted) { onOpenOverlaySettings() }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        if (!granted) {
            FilledTonalButton(onClick = onGrant, shape = RoundedCornerShape(8.dp)) {
                Text("Grant")
            }
        }
    }
}

// ── Audio Source Card ──

@Composable
private fun AudioSourceCard(ui: MainUiState, viewModel: MainViewModel) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Audio Source")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AudioSourceChip(
                    label = "Microphone",
                    icon = Icons.Filled.Mic,
                    selected = ui.settings.audioSource == AudioSource.MIC,
                    onClick = { viewModel.updateAudioSource(AudioSource.MIC) },
                    modifier = Modifier.weight(1f)
                )
                AudioSourceChip(
                    label = "System Audio",
                    icon = Icons.Filled.SurroundSound,
                    selected = ui.settings.audioSource == AudioSource.SYSTEM,
                    onClick = { viewModel.updateAudioSource(AudioSource.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (ui.settings.audioSource == AudioSource.SYSTEM) {
                Text(
                    text = "Captures audio from videos and apps. Requires a Whisper STT server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Uses the device microphone to hear speech.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AudioSourceChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    val border = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, border, RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

// ── Language Card ──

@Composable
private fun LanguageCard(ui: MainUiState, viewModel: MainViewModel) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Languages")

            // Quick presets
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::setPresetEnToVi,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("EN \u2192 VI", fontSize = 13.sp) }

                OutlinedButton(
                    onClick = viewModel::setPresetViToEn,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("VI \u2192 EN", fontSize = 13.sp) }
            }

            // Source language
            Text("Source", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LangButton("English", ui.settings.sourceLanguage == AppLanguage.ENGLISH) {
                    viewModel.updateSource(AppLanguage.ENGLISH)
                }
                LangButton("Vietnamese", ui.settings.sourceLanguage == AppLanguage.VIETNAMESE) {
                    viewModel.updateSource(AppLanguage.VIETNAMESE)
                }
            }

            // Target language
            Text("Target", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LangButton("English", ui.settings.targetLanguage == AppLanguage.ENGLISH) {
                    viewModel.updateTarget(AppLanguage.ENGLISH)
                }
                LangButton("Vietnamese", ui.settings.targetLanguage == AppLanguage.VIETNAMESE) {
                    viewModel.updateTarget(AppLanguage.VIETNAMESE)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Auto-detect source", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = ui.settings.autoDetectSource,
                    onCheckedChange = viewModel::updateAutoDetect
                )
            }
        }
    }
}

@Composable
private fun LangButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, shape = RoundedCornerShape(8.dp)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, shape = RoundedCornerShape(8.dp)) { Text(label) }
    }
}

// ── Overlay Settings Card ──

@Composable
private fun OverlaySettingsCard(ui: MainUiState, viewModel: MainViewModel) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Overlay")

            Text("Text size: ${ui.settings.textSizeSp.toInt()}sp", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = ui.settings.textSizeSp,
                valueRange = 14f..40f,
                onValueChange = viewModel::updateTextSize
            )

            Text("Opacity: ${(ui.settings.overlayOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = ui.settings.overlayOpacity,
                valueRange = 0.2f..1f,
                onValueChange = viewModel::updateOpacity
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Show original text", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = ui.settings.showOriginal,
                    onCheckedChange = viewModel::updateShowOriginal
                )
            }
        }
    }
}

// ── Server Card ──

@Composable
private fun ServerCard(
    translateUrl: String,
    onTranslateUrlChange: (String) -> Unit,
    onSaveTranslateUrl: () -> Unit,
    sttUrl: String,
    onSttUrlChange: (String) -> Unit,
    onSaveSttUrl: () -> Unit,
    showStt: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                SectionLabel("Server")
            }

            OutlinedTextField(
                value = translateUrl,
                onValueChange = onTranslateUrlChange,
                label = { Text("Translation URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            FilledTonalButton(
                onClick = onSaveTranslateUrl,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save Translation URL") }

            if (showStt) {
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = sttUrl,
                    onValueChange = onSttUrlChange,
                    label = { Text("Speech-to-Text URL (Whisper)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                FilledTonalButton(
                    onClick = onSaveSttUrl,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save STT URL") }
            }
        }
    }
}

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.charles.livecaptionn.settings.AudioSource
import com.charles.livecaptionn.settings.Language
import com.charles.livecaptionn.settings.SttBackend
import com.charles.livecaptionn.speech.RecognitionStatus
import com.charles.livecaptionn.speech.VoskModelInfo

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
    var showVoskSheet by remember { mutableStateOf(false) }
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
            CaptionControlCard(ui, onStart, onStop)
            PermissionsCard(ui, onRequestAudioPermission, onOpenOverlaySettings)
            AudioSourceCard(ui, viewModel, onManageModels = { showVoskSheet = true })
            LanguageCard(
                ui = ui,
                viewModel = viewModel,
                onManageModels = { showVoskSheet = true }
            )
            OverlaySettingsCard(ui, viewModel)
            ServerCard(
                ui = ui,
                translateUrl = translateUrlDraft,
                onTranslateUrlChange = { translateUrlDraft = it },
                onSaveTranslateUrl = { viewModel.updateBaseUrl(translateUrlDraft) },
                onRefreshLibre = { viewModel.refreshLibreCatalog() },
                sttUrl = sttUrlDraft,
                onSttUrlChange = { sttUrlDraft = it },
                onSaveSttUrl = { viewModel.updateSttUrl(sttUrlDraft) },
                showStt = ui.settings.audioSource == AudioSource.SYSTEM &&
                    ui.settings.sttBackend == SttBackend.REMOTE_WHISPER
            )

            Spacer(Modifier.height(8.dp))
        }

        if (showVoskSheet) {
            VoskModelSheet(
                models = ui.voskModels,
                progress = ui.voskDownloadProgress,
                onDismiss = { showVoskSheet = false },
                onDownload = viewModel::downloadVoskModel,
                onDelete = viewModel::deleteVoskModel
            )
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
            val err = ui.runtime.lastError?.trim().orEmpty()
            if (isRunning && err.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
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

// ── Permissions ──

@Composable
private fun PermissionsCard(
    ui: MainUiState,
    onRequestAudioPermission: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
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
            FilledTonalButton(onClick = onGrant, shape = RoundedCornerShape(8.dp)) { Text("Grant") }
        }
    }
}

// ── Audio Source ──

@Composable
private fun AudioSourceCard(
    ui: MainUiState,
    viewModel: MainViewModel,
    onManageModels: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Audio Source")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceChip(
                    label = "Microphone",
                    icon = Icons.Filled.Mic,
                    selected = ui.settings.audioSource == AudioSource.MIC,
                    onClick = { viewModel.updateAudioSource(AudioSource.MIC) },
                    modifier = Modifier.weight(1f)
                )
                ChoiceChip(
                    label = "System Audio",
                    icon = Icons.Filled.SurroundSound,
                    selected = ui.settings.audioSource == AudioSource.SYSTEM,
                    onClick = { viewModel.updateAudioSource(AudioSource.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (ui.settings.audioSource == AudioSource.SYSTEM) {
                Text(
                    text = "Captures audio from videos and apps that allow playback capture. If Android asks, choose Share entire screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Transcription engine", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChoiceChip(
                        label = "Remote Whisper",
                        icon = Icons.Filled.Cloud,
                        selected = ui.settings.sttBackend == SttBackend.REMOTE_WHISPER,
                        onClick = { viewModel.updateSttBackend(SttBackend.REMOTE_WHISPER) },
                        modifier = Modifier.weight(1f)
                    )
                    ChoiceChip(
                        label = "Local Vosk",
                        icon = Icons.Filled.Mic,
                        selected = ui.settings.sttBackend == SttBackend.LOCAL_VOSK,
                        onClick = { viewModel.updateSttBackend(SttBackend.LOCAL_VOSK) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (ui.settings.sttBackend == SttBackend.LOCAL_VOSK) {
                    val installed = ui.voskModels.count { it.installed }
                    Text(
                        text = "Runs transcription fully on this device. $installed language${if (installed == 1) "" else "s"} installed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onManageModels,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Manage on-device models")
                    }
                } else {
                    Text(
                        text = "Sends captured audio to the configured Whisper ASR endpoint.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Uses the device microphone. Android's built-in recognizer decides which locales are available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChoiceChip(
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
private fun LanguageCard(
    ui: MainUiState,
    viewModel: MainViewModel,
    onManageModels: () -> Unit
) {
    val sourceOptions = ui.availableSourceLanguages
    val targetOptions = ui.availableTargetLanguages

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Languages")

            // Source
            Text("Source (spoken)", style = MaterialTheme.typography.labelMedium)
            LanguagePickerField(
                selectedCode = ui.settings.sourceLanguageCode,
                options = sourceOptions,
                placeholder = if (sourceOptions.isEmpty()) "No languages available" else "Pick a language",
                onPick = { viewModel.updateSource(it.code) }
            )

            // Swap + target
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Target (translation)", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = viewModel::swapLanguages) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Swap")
                }
            }
            LanguagePickerField(
                selectedCode = ui.settings.targetLanguageCode,
                options = targetOptions,
                placeholder = if (targetOptions.isEmpty()) "No languages available" else "Pick a language",
                onPick = { viewModel.updateTarget(it.code) }
            )

            // Context-specific helper text
            val vmAudio = ui.settings.audioSource
            val vmStt = ui.settings.sttBackend
            when {
                vmAudio == AudioSource.SYSTEM && vmStt == SttBackend.LOCAL_VOSK -> {
                    Text(
                        text = "On-device transcription: source language is limited to the Vosk models installed on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onManageModels) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download more languages")
                    }
                }
                ui.libreLanguages.isEmpty() && ui.libreError != null -> {
                    Text(
                        text = "Could not reach LibreTranslate at ${ui.settings.serverBaseUrl} — showing a fallback list. Save a valid URL to load the full language set from your server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                ui.libreLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Loading languages from LibreTranslate…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ui.libreLanguages.isNotEmpty() -> {
                    Text(
                        text = "${ui.libreLanguages.size} languages reported by LibreTranslate. Add more to your server by installing extra Argos Translate packages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
private fun LanguagePickerField(
    selectedCode: String,
    options: List<Language>,
    placeholder: String,
    onPick: (Language) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.code.equals(selectedCode, ignoreCase = true) }
    val label = selected?.let { "${it.name}  (${it.code})" }
        ?: selectedCode.takeIf { it.isNotBlank() }?.uppercase()
        ?: placeholder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(enabled = options.isNotEmpty()) { open = true }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (options.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
    }

    if (open) {
        LanguagePickerDialog(
            options = options,
            selectedCode = selectedCode,
            onDismiss = { open = false },
            onPick = {
                onPick(it)
                open = false
            }
        )
    }
}

@Composable
private fun LanguagePickerDialog(
    options: List<Language>,
    selectedCode: String,
    onDismiss: () -> Unit,
    onPick: (Language) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, options) {
        if (query.isBlank()) options
        else options.filter {
            it.name.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Choose language") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    items(filtered, key = { it.code }) { lang ->
                        val isSelected = lang.code.equals(selectedCode, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(lang) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelected) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            } else {
                                Spacer(Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(lang.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = lang.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        items(listOf(Unit)) {
                            Text(
                                text = "No languages match \"$query\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    )
}

// ── Vosk model management ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoskModelSheet(
    models: List<VoskModelInfo>,
    progress: Map<String, Float>,
    onDismiss: () -> Unit,
    onDownload: (VoskModelInfo) -> Unit,
    onDelete: (VoskModelInfo) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("On-device speech models", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Vosk models run fully offline on this phone. Downloading a model is a one-time step; uninstall it any time to free storage. Sizes are approximate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val installed = models.filter { it.installed }
            val available = models.filter { !it.installed }

            if (installed.isNotEmpty()) {
                Text("Installed", style = MaterialTheme.typography.labelLarge)
                installed.forEach { model ->
                    VoskRow(
                        model = model,
                        progress = progress[model.modelName],
                        onDownload = { onDownload(model) },
                        onDelete = { onDelete(model) }
                    )
                }
            }

            if (available.isNotEmpty()) {
                Text("Available to download", style = MaterialTheme.typography.labelLarge)
                available.forEach { model ->
                    VoskRow(
                        model = model,
                        progress = progress[model.modelName],
                        onDownload = { onDownload(model) },
                        onDelete = { onDelete(model) }
                    )
                }
            }

            Text(
                text = "Models are fetched from alphacephei.com/vosk/models over HTTPS.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VoskRow(
    model: VoskModelInfo,
    progress: Float?,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (model.installed)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${model.languageName} (${model.languageCode})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = buildString {
                            append(model.modelName)
                            if (model.sizeMb > 0) append(" · ~${model.sizeMb} MB")
                            if (model.isBundled) append(" · bundled")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    progress != null -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    model.installed && !model.isBundled -> {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                    model.installed && model.isBundled -> {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Installed",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    else -> {
                        FilledTonalButton(onClick = onDownload, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Get")
                        }
                    }
                }
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ── Overlay Settings ──

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
    ui: MainUiState,
    translateUrl: String,
    onTranslateUrlChange: (String) -> Unit,
    onSaveTranslateUrl: () -> Unit,
    onRefreshLibre: () -> Unit,
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
                label = { Text("LibreTranslate URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                text = "Example: http://192.168.1.50:5000. The app fetches /languages to populate the dropdowns.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onSaveTranslateUrl,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
                OutlinedButton(
                    onClick = onRefreshLibre,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh")
                }
            }
            if (ui.libreError != null && !ui.libreLoading) {
                Text(
                    text = "Language fetch error: ${ui.libreError}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (showStt) {
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = sttUrl,
                    onValueChange = onSttUrlChange,
                    label = { Text("Speech-to-Text URL (Whisper)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = "Example: http://192.168.1.50:9000/asr?output=json",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

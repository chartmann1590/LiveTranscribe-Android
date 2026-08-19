package com.charles.livecaptionn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.livecaptionn.ads.AdUnits
import com.charles.livecaptionn.ads.NativeAdCard
import com.charles.livecaptionn.settings.AudioSource
import com.charles.livecaptionn.settings.Language
import com.charles.livecaptionn.settings.SttBackend
import com.charles.livecaptionn.settings.TranslationBackend
import com.charles.livecaptionn.speech.RecognitionStatus
import com.charles.livecaptionn.speech.ModelQuality
import com.charles.livecaptionn.speech.VoskModelInfo
import com.charles.livecaptionn.translation.MlKitLanguages
import com.charles.livecaptionn.ui.feedback.FeedbackViewModel
import com.charles.livecaptionn.ui.feedback.IssueDetailsDialog
import com.charles.livecaptionn.ui.feedback.ReportProblemDialog
import com.charles.livecaptionn.ui.feedback.SubmitSuccessSnackbar
import com.charles.livecaptionn.ui.feedback.SupportAndFeedbackCard
import com.charles.livecaptionn.overlay.OverlayFontCatalog
import com.charles.livecaptionn.overlay.OverlayThemeCatalog
import com.charles.livecaptionn.ui.l10n.LocalUiStrings
import com.charles.livecaptionn.ui.l10n.UiLanguagePickerDialog
import com.charles.livecaptionn.ui.l10n.UiLocalizationRepository.UiLocalizationStage
import com.charles.livecaptionn.ui.premium.PremiumCard
import com.charles.livecaptionn.ui.premium.PremiumViewModel
import com.charles.livecaptionn.update.UpdateInfo
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestAudioPermission: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onHistory: () -> Unit = {},
    pendingCheckoutSessionId: String? = null,
    onCheckoutSessionConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val t = LocalUiStrings.current
    var translateUrlDraft by remember(ui.settings.serverBaseUrl) { mutableStateOf(ui.settings.serverBaseUrl) }
    var sttUrlDraft by remember(ui.settings.sttBaseUrl) { mutableStateOf(ui.settings.sttBaseUrl) }
    var showVoskSheet by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshPermissionState() }

    val feedbackCtx = LocalContext.current
    val feedbackApp = feedbackCtx.applicationContext as com.charles.livecaptionn.LiveCaptionApp
    val feedbackVm: FeedbackViewModel = viewModel(
        factory = FeedbackViewModel.Factory(feedbackCtx, feedbackApp.container.bugReportRepo)
    )
    val feedbackState by feedbackVm.state.collectAsStateWithLifecycle()

    val premiumVm: PremiumViewModel = viewModel(
        factory = PremiumViewModel.Factory(feedbackApp.container.premiumRepository)
    )
    val premiumState by premiumVm.state.collectAsStateWithLifecycle()
    val activity = feedbackCtx as? android.app.Activity

    LaunchedEffect(pendingCheckoutSessionId) {
        if (pendingCheckoutSessionId != null) {
            premiumVm.refresh(pendingCheckoutSessionId)
            onCheckoutSessionConsumed()
        }
    }

    // Re-check permission state on every resume so granting overlay/mic
    // access in system Settings is reflected immediately when the user
    // returns to the app — instead of requiring a close + reopen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissionState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(t["Live CaptionN"], fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Filled.History, contentDescription = t["History"])
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
            val updateCtx = LocalContext.current
            ui.availableUpdate?.let { info ->
                UpdateAvailableBanner(
                    info = info,
                    fromPlayStore = ui.installedFromPlayStore,
                    onDownload = { viewModel.openUpdateUrl(updateCtx, info) },
                    onDismiss = { viewModel.dismissUpdate() }
                )
            }
            CaptionControlCard(ui, onStart, onStop)
            PermissionsCard(ui, onRequestAudioPermission, onOpenOverlaySettings)
            AudioSourceCard(ui, viewModel, onManageModels = { showVoskSheet = true })
            LanguageCard(
                ui = ui,
                viewModel = viewModel,
                onManageModels = { showVoskSheet = true },
                hasPro = premiumState.premium.hasPro,
                onRequiresPro = {}
            )
            OverlaySettingsCard(
                ui = ui,
                viewModel = viewModel,
                hasPro = premiumState.premium.hasPro,
                onRequiresPro = {}
            )
            UiLanguageCard(
                languageCode = ui.uiLanguageCode,
                busy = ui.uiLocalizationBusy,
                error = ui.uiLocalizationError,
                stage = ui.uiLocalizationStage,
                translatedCount = ui.uiLocalizationTranslated,
                translatedTotal = ui.uiLocalizationTotal,
                onSelect = viewModel::updateUiLanguage
            )
            PremiumCard(
                state = premiumState,
                onPurchase = { product -> activity?.let { premiumVm.purchase(it, product) } },
                onManageSubscription = { activity?.let { premiumVm.manageSubscription(it) } },
                onRestoreEmailChange = { premiumVm.updateRestoreEmail(it) },
                onRestore = { premiumVm.restore() },
                onRefresh = { premiumVm.refresh() }
            )
            if (AdUnits.ENABLED && AdUnits.NATIVE.isNotBlank() && !premiumState.premium.hasAdFree) {
                NativeAdCard()
            }
            ServerCard(
                ui = ui,
                translateUrl = translateUrlDraft,
                onTranslateUrlChange = { translateUrlDraft = it },
                onSaveTranslateUrl = { viewModel.updateBaseUrl(translateUrlDraft) },
                onRefreshLibre = { viewModel.refreshLibreCatalog() },
                onSelectTranslationBackend = viewModel::updateTranslationBackend,
                sttUrl = sttUrlDraft,
                onSttUrlChange = { sttUrlDraft = it },
                onSaveSttUrl = { viewModel.updateSttUrl(sttUrlDraft) },
                showStt = ui.settings.audioSource == AudioSource.SYSTEM &&
                    ui.settings.sttBackend == SttBackend.REMOTE_WHISPER
            )

            SupportAndFeedbackCard(
                state = feedbackState,
                onReportProblem = { feedbackVm.showReportDialog() },
                onOpenReport = { feedbackVm.openIssueDetails(it) }
            )

            if (feedbackState.submitSuccess) {
                SubmitSuccessSnackbar(
                    message = t["Report submitted successfully!"],
                    onDismiss = { feedbackVm.clearSubmitSuccess() }
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        if (showVoskSheet) {
            VoskModelSheet(
                models = ui.voskModels,
                progress = ui.voskDownloadProgress,
                hasPro = premiumState.premium.hasPro,
                onDismiss = { showVoskSheet = false },
                onDownload = viewModel::downloadVoskModel,
                onDelete = viewModel::deleteVoskModel,
                onRequiresPro = { showVoskSheet = false }
            )
        }

        if (feedbackState.showReportDialog) {
            ReportProblemDialog(
                state = feedbackState,
                onDismiss = { feedbackVm.hideReportDialog() },
                onTitleChange = { feedbackVm.updateReportTitle(it) },
                onDescriptionChange = { feedbackVm.updateReportDescription(it) },
                onNameChange = { feedbackVm.updateReporterName(it) },
                onEmailChange = { feedbackVm.updateReporterEmail(it) },
                onIncludeDiagnosticsChange = { feedbackVm.updateIncludeDiagnostics(it) },
                onAttachmentSelected = { feedbackVm.updateAttachmentUri(it) },
                onClearAttachment = { feedbackVm.clearAttachment() },
                onSubmit = { feedbackVm.submitReport() }
            )
        }

        if (feedbackState.showIssueDetails) {
            IssueDetailsDialog(
                state = feedbackState,
                onDismiss = { feedbackVm.closeIssueDetails() },
                onReplyTextChange = { feedbackVm.updateReplyText(it) },
                onReplyAttachmentSelected = { feedbackVm.updateReplyAttachmentUri(it) },
                onClearReplyAttachment = { feedbackVm.clearReplyAttachment() },
                onPostReply = { feedbackVm.postReply() }
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
    val t = LocalUiStrings.current

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
                    text = if (isRunning) t["Status"] + ": " + t[ui.runtime.status.displayName]
                    else t["Ready"],
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
                    Text(t["Start"])
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
                    Text(t["Stop"])
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
    val t = LocalUiStrings.current
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(t["Permissions"])
            PermissionRow(t["Microphone"], ui.micPermissionGranted) { onRequestAudioPermission() }
            PermissionRow(t["Overlay"], ui.overlayPermissionGranted) { onOpenOverlaySettings() }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    val t = LocalUiStrings.current
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
            FilledTonalButton(onClick = onGrant, shape = RoundedCornerShape(8.dp)) { Text(t["Grant"]) }
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
    val t = LocalUiStrings.current
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(t["Audio Source"])

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceChip(
                    label = t["Microphone"],
                    icon = Icons.Filled.Mic,
                    selected = ui.settings.audioSource == AudioSource.MIC,
                    onClick = { viewModel.updateAudioSource(AudioSource.MIC) },
                    modifier = Modifier.weight(1f)
                )
                ChoiceChip(
                    label = t["System Audio"],
                    icon = Icons.Filled.SurroundSound,
                    selected = ui.settings.audioSource == AudioSource.SYSTEM,
                    onClick = { viewModel.updateAudioSource(AudioSource.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
            }

            val isSystem = ui.settings.audioSource == AudioSource.SYSTEM
            Text(
                text = if (isSystem)
                    t["Captures audio from videos and apps that allow playback capture. If Android asks, choose Share entire screen."]
                else
                    t["Uses the device microphone."],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(t["Transcription engine"], style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceChip(
                    label = if (isSystem) t["Remote Whisper"] else t["Android built-in"],
                    icon = Icons.Filled.Cloud,
                    selected = ui.settings.sttBackend == SttBackend.REMOTE_WHISPER,
                    onClick = { viewModel.updateSttBackend(SttBackend.REMOTE_WHISPER) },
                    modifier = Modifier.weight(1f)
                )
                ChoiceChip(
                    label = t["Local Vosk"],
                    icon = Icons.Filled.Mic,
                    selected = ui.settings.sttBackend == SttBackend.LOCAL_VOSK,
                    onClick = { viewModel.updateSttBackend(SttBackend.LOCAL_VOSK) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (ui.settings.sttBackend == SttBackend.LOCAL_VOSK) {
                val installed = ui.voskModels.count { it.installed }
                val anyLargeInstalled = ui.voskModels.any { it.installed && it.quality == ModelQuality.LARGE }
                val langWord = if (installed == 1) t["language"] else t["languages"]
                Text(
                    text = t["Runs transcription fully on this device."] +
                        " $installed $langWord ${t["installed."]}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!anyLargeInstalled) {
                    Text(
                        text = t["Tip: for noticeably stronger transcription, tap Manage models and install a LARGE server-grade model for the languages you use — 80 MB to 2 GB each, fully offline after download."],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                OutlinedButton(
                    onClick = onManageModels,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t["Manage on-device models"])
                }
            } else {
                Text(
                    text = if (isSystem)
                        t["Sends captured audio to the configured Whisper ASR endpoint."]
                    else
                        t["Uses Android's built-in recognizer. Available locales depend on what's installed on this device."],
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
    onManageModels: () -> Unit,
    hasPro: Boolean,
    onRequiresPro: () -> Unit
) {
    val sourceOptions = ui.availableSourceLanguages
    val targetOptions = ui.availableTargetLanguages
    val usingMlKitGate = ui.settings.translationBackend == TranslationBackend.ML_KIT
    val t = LocalUiStrings.current
    val languageRequiresPro: (Language) -> Boolean = { lang ->
        usingMlKitGate && !hasPro && MlKitLanguages.requiresPro(lang.code)
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(t["Languages"])

            // Source
            Text(t["Source (spoken)"], style = MaterialTheme.typography.labelMedium)
            LanguagePickerField(
                selectedCode = ui.settings.sourceLanguageCode,
                options = sourceOptions,
                placeholder = if (sourceOptions.isEmpty()) t["No languages available"] else t["Pick a language"],
                onPick = { viewModel.updateSource(it.code) },
                requiresPro = languageRequiresPro,
                onRequiresPro = onRequiresPro
            )

            // Swap + target
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(t["Target (translation)"], style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = viewModel::swapLanguages) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(t["Swap"])
                }
            }
            LanguagePickerField(
                selectedCode = ui.settings.targetLanguageCode,
                options = targetOptions,
                placeholder = if (targetOptions.isEmpty()) t["No languages available"] else t["Pick a language"],
                onPick = { viewModel.updateTarget(it.code) },
                requiresPro = languageRequiresPro,
                onRequiresPro = onRequiresPro
            )

            // Context-specific helper text
            val vmAudio = ui.settings.audioSource
            val vmStt = ui.settings.sttBackend
            val usingMlKit = ui.settings.translationBackend == TranslationBackend.ML_KIT
            when {
                vmAudio == AudioSource.SYSTEM && vmStt == SttBackend.LOCAL_VOSK -> {
                    Text(
                        text = t["On-device transcription: source language is limited to the Vosk models installed on this phone."],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onManageModels) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t["Download more languages"])
                    }
                }
                usingMlKit -> {
                    Text(
                        text = t.format(
                            "On-device translation via Google ML Kit — %d languages, fully offline after first download.",
                            MlKitLanguages.LIST.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ui.libreLanguages.isEmpty() && ui.libreError != null -> {
                    Text(
                        text = t.format(
                            "Could not reach LibreTranslate at %s — showing a fallback list. Save a valid URL to load the full language set from your server.",
                            ui.settings.serverBaseUrl
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                ui.libreLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = t["Loading languages from LibreTranslate…"],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ui.libreLanguages.isNotEmpty() -> {
                    Text(
                        text = t.format(
                            "%d languages reported by LibreTranslate. Add more to your server by installing extra Argos Translate packages.",
                            ui.libreLanguages.size
                        ),
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
                Text(t["Auto-detect source"], style = MaterialTheme.typography.bodyMedium)
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
    onPick: (Language) -> Unit,
    requiresPro: (Language) -> Boolean = { false },
    onRequiresPro: () -> Unit = {}
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
            },
            requiresPro = requiresPro,
            onRequiresPro = {
                open = false
                onRequiresPro()
            }
        )
    }
}

@Composable
private fun LanguagePickerDialog(
    options: List<Language>,
    selectedCode: String,
    onDismiss: () -> Unit,
    onPick: (Language) -> Unit,
    requiresPro: (Language) -> Boolean = { false },
    onRequiresPro: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val t = LocalUiStrings.current
    val filtered = remember(query, options) {
        if (query.isBlank()) options
        else options.filter {
            it.name.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(t["Close"]) } },
        title = { Text(t["Choose language"]) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(t["Search"]) },
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
                        val locked = requiresPro(lang)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (locked) onRequiresPro() else onPick(lang) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when {
                                locked -> Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = t["Requires Pro"],
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                isSelected -> Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                else -> Spacer(Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    lang.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (locked) "${lang.code} · ${t["Pro"]}" else lang.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        items(listOf(Unit)) {
                            Text(
                                text = t.format("No languages match \"%s\".", query),
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
    hasPro: Boolean,
    onDismiss: () -> Unit,
    onDownload: (VoskModelInfo) -> Unit,
    onDelete: (VoskModelInfo) -> Unit,
    onRequiresPro: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val t = LocalUiStrings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(t["On-device speech models"], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = t["Vosk models run fully offline on this phone. Downloading a model is a one-time step; uninstall any time to free storage. For the strongest transcription, pick the LARGE server-grade model for the languages you use most."],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val installed = models.filter { it.installed }
            val availableSmall = models.filter { !it.installed && it.quality == ModelQuality.SMALL }
            val availableLarge = models.filter { !it.installed && it.quality == ModelQuality.LARGE }

            if (installed.isNotEmpty()) {
                Text(t["Installed"], style = MaterialTheme.typography.labelLarge)
                installed.forEach { model ->
                    VoskRow(
                        model = model,
                        progress = progress[model.modelName],
                        locked = false,
                        onDownload = { onDownload(model) },
                        onDelete = { onDelete(model) },
                        onRequiresPro = onRequiresPro
                    )
                }
            }

            if (availableLarge.isNotEmpty()) {
                Text(t["Large · server-grade accuracy (Pro)"], style = MaterialTheme.typography.labelLarge)
                Text(
                    text = t["Full Vosk server models with the lowest error rates. Each one is 80 MB to 2 GB but runs entirely on-device after the one-time download. This is the strongest transcription option for every language."],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                availableLarge.forEach { model ->
                    VoskRow(
                        model = model,
                        progress = progress[model.modelName],
                        locked = !hasPro,
                        onDownload = { onDownload(model) },
                        onDelete = { onDelete(model) },
                        onRequiresPro = onRequiresPro
                    )
                }
            }

            if (availableSmall.isNotEmpty()) {
                Text(t["Small · fast & light"], style = MaterialTheme.typography.labelLarge)
                Text(
                    text = t["Compact ~40 MB models for quick installs or low-storage phones. Accuracy is noticeably lower than the large variants."],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                availableSmall.forEach { model ->
                    VoskRow(
                        model = model,
                        progress = progress[model.modelName],
                        locked = false,
                        onDownload = { onDownload(model) },
                        onDelete = { onDelete(model) },
                        onRequiresPro = onRequiresPro
                    )
                }
            }

            Text(
                text = t["Models are fetched from alphacephei.com/vosk/models over HTTPS."],
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
    locked: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onRequiresPro: () -> Unit
) {
    val t = LocalUiStrings.current
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
                            if (model.sizeMb > 0) append(" · ~${formatModelSize(model.sizeMb)}")
                            if (model.quality == ModelQuality.LARGE) append(" · ${t["LARGE"]}")
                            if (model.isBundled) append(" · ${t["bundled"]}")
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
                            Icon(Icons.Filled.Delete, contentDescription = t["Delete"])
                        }
                    }
                    model.installed && model.isBundled -> {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = t["Installed"],
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    locked -> {
                        FilledTonalButton(onClick = onRequiresPro, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t["Pro"])
                        }
                    }
                    else -> {
                        FilledTonalButton(onClick = onDownload, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t["Get"])
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

private fun formatModelSize(sizeMb: Int): String {
    if (sizeMb >= 1024) {
        val gb = sizeMb / 1024f
        return if (gb >= 10f) "${gb.toInt()} GB" else String.format("%.1f GB", gb)
    }
    return "$sizeMb MB"
}

// ── Overlay Settings ──

@Composable
private fun OverlaySettingsCard(
    ui: MainUiState,
    viewModel: MainViewModel,
    hasPro: Boolean,
    onRequiresPro: () -> Unit
) {
    val t = LocalUiStrings.current
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(t["Overlay"])

            Text(t.format("Text size: %dsp", ui.settings.textSizeSp.toInt()), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = ui.settings.textSizeSp,
                valueRange = 14f..40f,
                onValueChange = viewModel::updateTextSize
            )

            Text(t.format("Opacity: %d%%", (ui.settings.overlayOpacity * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
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
                Text(t["Show original text"], style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = ui.settings.showOriginal,
                    onCheckedChange = viewModel::updateShowOriginal
                )
            }

            Text(t["Overlay theme (Pro)"], style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverlayThemeCatalog.THEMES.forEach { theme ->
                    val locked = !hasPro && theme.id != OverlayThemeCatalog.FREE_THEME_ID
                    ChoiceChip(
                        label = theme.label,
                        icon = if (locked) Icons.Filled.Lock else Icons.Filled.Palette,
                        selected = ui.settings.overlayThemeId == theme.id,
                        onClick = { if (locked) onRequiresPro() else viewModel.updateOverlayTheme(theme.id) }
                    )
                }
            }

            Text(t["Overlay font (Pro)"], style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverlayFontCatalog.FONTS.forEach { font ->
                    val locked = !hasPro && font.id != OverlayFontCatalog.FREE_FONT_ID
                    ChoiceChip(
                        label = font.label,
                        icon = if (locked) Icons.Filled.Lock else Icons.Filled.TextFields,
                        selected = ui.settings.overlayFontId == font.id,
                        onClick = { if (locked) onRequiresPro() else viewModel.updateOverlayFont(font.id) }
                    )
                }
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
    onSelectTranslationBackend: (TranslationBackend) -> Unit,
    sttUrl: String,
    onSttUrlChange: (String) -> Unit,
    onSaveSttUrl: () -> Unit,
    showStt: Boolean
) {
    val t = LocalUiStrings.current
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                SectionLabel(t["Translation engine"])
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceChip(
                    label = t["On-device (ML Kit)"],
                    icon = Icons.Filled.Mic,
                    selected = ui.settings.translationBackend == TranslationBackend.ML_KIT,
                    onClick = { onSelectTranslationBackend(TranslationBackend.ML_KIT) },
                    modifier = Modifier.weight(1f)
                )
                ChoiceChip(
                    label = t["LibreTranslate"],
                    icon = Icons.Filled.Cloud,
                    selected = ui.settings.translationBackend == TranslationBackend.LIBRE_TRANSLATE,
                    onClick = { onSelectTranslationBackend(TranslationBackend.LIBRE_TRANSLATE) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (ui.settings.translationBackend == TranslationBackend.ML_KIT) {
                Text(
                    text = t["Google ML Kit translates fully on this device — no server required. The first time you use a language pair, a ~30 MB model downloads and is then cached offline forever."],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                OutlinedTextField(
                    value = translateUrl,
                    onValueChange = onTranslateUrlChange,
                    label = { Text(t["LibreTranslate URL"]) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = t.format("Example: %s", "http://192.168.1.50:5000") +
                        " The app fetches /languages to populate the dropdowns.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onSaveTranslateUrl,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text(t["Save"]) }
                    OutlinedButton(
                        onClick = onRefreshLibre,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t["Refresh"])
                    }
                }
                if (ui.libreError != null && !ui.libreLoading) {
                    Text(
                        text = t.format("Language fetch error: %s", ui.libreError),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (showStt) {
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = sttUrl,
                    onValueChange = onSttUrlChange,
                    label = { Text(t["Speech-to-Text URL (Whisper)"]) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = t.format("Example: %s", "http://192.168.1.50:9000/asr?output=json"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = onSaveSttUrl,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t["Save STT URL"]) }
            }
        }
    }
}

// ── Update Available Banner ──

@Composable
private fun UpdateAvailableBanner(
    info: UpdateInfo,
    fromPlayStore: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val t = LocalUiStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = t["Update available"],
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = info.releaseName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = info.notes.take(240),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 4
                )
            }
            if (fromPlayStore) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = t["You installed this app from the Play Store. The GitHub build is a pre-release and may be unstable. Installing it will also disable Play Store auto-updates for this app."],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDownload,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (info.apkDownloadUrl != null) t["Download APK"] else t["View release"])
                }
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp)
                ) { Text(t["Later"]) }
            }
        }
    }
}

// ── Interface Language ──

@Composable
private fun UiLanguageCard(
    languageCode: String,
    busy: Boolean,
    error: String?,
    stage: UiLocalizationStage = UiLocalizationStage.DOWNLOADING,
    translatedCount: Int = 0,
    translatedTotal: Int = 0,
    onSelect: (String) -> Unit
) {
    val t = LocalUiStrings.current
    var showPicker by remember { mutableStateOf(false) }
    val selected = MlKitLanguages.LIST.firstOrNull { it.code.equals(languageCode, ignoreCase = true) }
    val label = if (languageCode.equals("en", true)) {
        "${t["English"]} (en)"
    } else {
        "${selected?.name.orEmpty()} ($languageCode)"
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(t["Interface language"])

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .clickable(enabled = !busy) { showPicker = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }

            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = when (stage) {
                        UiLocalizationStage.TRANSLATING ->
                            if (translatedTotal > 0) {
                                t.format("Translating the interface… (%d/%d)", translatedCount, translatedTotal)
                            } else {
                                t["Translating the interface…"]
                            }
                        UiLocalizationStage.DOWNLOADING -> t["Downloading translation model…"]
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            error?.let {
                Text(
                    text = t["Translation failed. Tap to retry."],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = { onSelect(languageCode) }) { Text(t["Retry"]) }
            }
            if (!busy && error == null && !languageCode.equals("en", true)) {
                Text(
                    text = t["On-device ML Kit translation may be inaccurate."],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showPicker) {
        UiLanguagePickerDialog(
            currentCode = languageCode,
            busy = busy,
            error = error,
            stage = stage,
            translatedCount = translatedCount,
            translatedTotal = translatedTotal,
            onSelect = {
                showPicker = false
                onSelect(it)
            },
            onDismiss = { showPicker = false }
        )
    }
}


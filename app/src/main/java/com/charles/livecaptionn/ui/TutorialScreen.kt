package com.charles.livecaptionn.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.charles.livecaptionn.R
import com.charles.livecaptionn.compatibility.CompatibilityTier
import com.charles.livecaptionn.compatibility.DeviceSpecs
import java.util.Locale

data class TutorialStep(
    val title: String,
    val subtitle: String,
    val narration: String,
    val captionPreview: String,
    val details: List<String>,
    val dockerCommand: String? = null
)

val TUTORIAL_STEPS = listOf(
    TutorialStep(
        title = "1. Introduction to LiveCaptionN",
        subtitle = "Floating real-time speech captions & translation overlay",
        narration = "Welcome to LiveCaptionN! The app captures spoken words from your microphone or app audio and displays live draggable captions on top of any video, meeting, or streaming app.",
        captionPreview = "LiveCaptionN: Floating subtitles in real-time on any app.",
        details = listOf(
            "Floating subtitle window draggable anywhere on your screen.",
            "Choose between completely offline on-device processing or self-hosted remote servers.",
            "Supports 59+ translation languages with Google ML Kit or LibreTranslate."
        )
    ),
    TutorialStep(
        title = "2. System Requirements & Hardware Check",
        subtitle = "Why hardware determines On-Device vs Remote mode",
        narration = "On-device speech recognition and neural translation need substantial computing power: at least 4 gigabytes of RAM, a 64-bit multi-core processor, and 1.5 gigabytes of free disk space.",
        captionPreview = "Requirements: 4 GB+ RAM, 64-bit CPU, 1.5 GB free disk space.",
        details = listOf(
            "Recommended: 4 GB+ RAM, 64-bit multi-core CPU (Pixel, Galaxy S-series).",
            "Borderline: 2.5 - 3.8 GB RAM or 32-bit (e.g. Kindle Fire HD 8) — small models work, but remote mode is recommended.",
            "Unsupported: < 2.5 GB RAM or < 4 CPU cores — on-device is locked out to prevent crashes, and remote mode is automatically enabled."
        )
    ),
    TutorialStep(
        title = "3. Self-Hosted Remote Services Setup",
        subtitle = "Zero latency, server-grade Whisper transcription on your home network",
        narration = "You can host your own Whisper and LibreTranslate servers at home with Docker. This gives budget devices blazing fast speech recognition with 100% data privacy.",
        captionPreview = "Run Whisper ASR and LibreTranslate servers on your PC via Docker.",
        details = listOf(
            "Whisper ASR: Transcribes speech accurately with OpenAI Whisper.",
            "LibreTranslate: Translates text across dozens of language pairs.",
            "Runs on Windows, Mac, or Linux with Docker Desktop."
        ),
        dockerCommand = "docker run -d -p 9000:9000 -e ASR_MODEL=base onerahmet/openai-whisper-asr-webservice\n\ndocker run -d -p 5000:5000 libretranslate/libretranslate"
    ),
    TutorialStep(
        title = "4. Connect App to Your Remote Servers",
        subtitle = "Configure your server URLs in app settings",
        narration = "Find your computer's local IP address using ipconfig or ifconfig, enter the URLs into the app settings, and tap Test Connection to begin live remote captioning.",
        captionPreview = "Enter http://<YOUR_LOCAL_IP>:9000 and :5000 in Settings.",
        details = listOf(
            "1. Find your computer's IP address: Run 'ipconfig' on Windows (or 'ip a' on Linux/Mac).",
            "2. In LiveCaptionN Settings, enter: STT URL: http://<IP>:9000 and Translation URL: http://<IP>:5000",
            "3. Ensure both your phone and PC are connected to the same Wi-Fi network."
        )
    )
)

const val REMOTE_SETUP_DOC_URL = "https://chartmann1590.github.io/LiveTranscribe-Android/remote-setup.html"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(
    deviceSpecs: DeviceSpecs?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentStepIndex by remember { mutableIntStateOf(0) }
    val step = TUTORIAL_STEPS[currentStepIndex]
    var isTtsEnabled by remember { mutableStateOf(true) }
    var ttsReady by remember { mutableStateOf(false) }

    // TTS instance lifecycle
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
            }
        }
        ttsInstance = tts

        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    // Speak narration whenever step changes or TTS is toggled on
    LaunchedEffect(currentStepIndex, isTtsEnabled, ttsReady) {
        if (ttsReady && isTtsEnabled && ttsInstance != null) {
            ttsInstance?.speak(
                step.narration,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "step_${currentStepIndex}"
            )
        } else if (!isTtsEnabled) {
            ttsInstance?.stop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Interactive Tutorial & Guide",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Step ${currentStepIndex + 1} of ${TUTORIAL_STEPS.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        ttsInstance?.stop()
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close tutorial")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isTtsEnabled = !isTtsEnabled
                        if (!isTtsEnabled) ttsInstance?.stop()
                    }) {
                        Icon(
                            imageVector = if (isTtsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = if (isTtsEnabled) "Mute narration" else "Unmute narration",
                            tint = if (isTtsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step Progress Bar
            LinearProgressIndicator(
                progress = { (currentStepIndex + 1).toFloat() / TUTORIAL_STEPS.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Video Player Card with Live Caption Overlay
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    // Native VideoView
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val videoUri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.tutorial_video}")
                                setVideoURI(videoUri)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    mp.setVolume(0.3f, 0.3f)
                                    start()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Synchronized Floating Caption Overlay Simulation
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.78f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                tint = Color(0xFF8AB4F8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = step.captionPreview,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Current Step Content Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = step.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Spoken Narration Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = if (isTtsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Narration (Audio & Subtitles)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = step.narration,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Key Details Bullet Points
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        step.details.forEach { detail ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "•",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Docker command block if available
                    if (step.dockerCommand != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Docker Launch Commands:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1E2E))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = step.dockerCommand,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFFA6E3A1),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Current Device Hardware Status
            if (deviceSpecs != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (deviceSpecs.tier) {
                            CompatibilityTier.PASSED -> Color(0xFFE8F5E9)
                            CompatibilityTier.BORDERLINE -> Color(0xFFFFF8E1)
                            CompatibilityTier.UNSUPPORTED -> Color(0xFFFFEBEE)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (deviceSpecs.tier) {
                                CompatibilityTier.PASSED -> Icons.Default.CheckCircle
                                CompatibilityTier.BORDERLINE -> Icons.Default.Memory
                                CompatibilityTier.UNSUPPORTED -> Icons.Default.Close
                            },
                            contentDescription = null,
                            tint = when (deviceSpecs.tier) {
                                CompatibilityTier.PASSED -> Color(0xFF2E7D32)
                                CompatibilityTier.BORDERLINE -> Color(0xFFF57F17)
                                CompatibilityTier.UNSUPPORTED -> Color(0xFFC62828)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Your Device: ${deviceSpecs.deviceModel} (${deviceSpecs.tier.label})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.Black
                            )
                            Text(
                                text = "${deviceSpecs.totalRamMb} MB RAM • ${deviceSpecs.cpuCores} cores • ${if (deviceSpecs.is64Bit) "64-bit" else "32-bit"} • ${deviceSpecs.freeStorageMb} MB free",
                                fontSize = 11.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }

            // External Setup Guide Link Button
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(REMOTE_SETUP_DOC_URL)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Complete Web Setup Guide", fontWeight = FontWeight.SemiBold)
            }

            // Navigation Controls: Back / Next / Finish
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (currentStepIndex > 0) {
                            currentStepIndex--
                        }
                    },
                    enabled = currentStepIndex > 0,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Previous")
                }

                if (currentStepIndex < TUTORIAL_STEPS.size - 1) {
                    Button(
                        onClick = {
                            currentStepIndex++
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Next Step")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                } else {
                    Button(
                        onClick = {
                            ttsInstance?.stop()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Finish Tutorial")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

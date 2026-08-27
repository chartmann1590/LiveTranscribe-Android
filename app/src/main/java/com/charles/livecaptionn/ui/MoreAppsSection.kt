package com.charles.livecaptionn.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class CrossPromoApp(
    val name: String,
    val packageName: String,
    val tagline: String,
)

private val crossPromoApps: List<CrossPromoApp> = listOf(
    CrossPromoApp("NutriSnap: AI Calorie Tracker", "com.charles.nutrisnap", "Snap a meal, get instant calories & macros — 100% on-device AI, private."),
    CrossPromoApp("Aria: On-Device Assistant", "com.aria.assistant", "Private on-device voice AI with optional, source-backed web verification."),
    CrossPromoApp("ScamRadar: AI Scam Detector", "com.charles.scamradar.app", "On-device AI catches scams in texts, voicemails & notifications. Free."),
    CrossPromoApp("MeshTalk: Bluetooth Mesh Chat", "com.charles.meshtalk.app", "Chat, talk & AI over Bluetooth mesh. No internet, no accounts, fully offline."),
    CrossPromoApp("DriveVault Dashcam", "com.drivevault.dashcam", "Privacy-first dashcam: GPS overlays, dual-camera, background recording."),
    CrossPromoApp("PixelDream: Offline AI Images", "com.hartmann.pixeldream", "Private, offline AI image generator. Your prompts and pictures never leave."),
    CrossPromoApp("Pocket-Assistant", "com.charles.pocketassistant", "Local AI organizer: save bills & notes, chat, tasks, and reminders on-device."),
    CrossPromoApp("TextPilot AI Messaging", "com.charles.messenger.v2", "Clean, fast SMS app with AI smart replies and web browser access to your texts."),
    CrossPromoApp("Pixel Fish Tank", "com.charles.virtualpet.fishtank", "Cozy virtual pet game — feed, clean & customize your pixel fish. Play & relax!"),
    CrossPromoApp("TrailSage AI: Road Trip Guide", "com.charles.trailsage", "Private, offline GPS audio tour guide with on-device AI storytelling."),
    CrossPromoApp("Knightfall: Chess with AI Coach", "com.chartmann.knightfall", "Play chess against Stockfish AI with Gemma 4 coaching, online, or on the web!"),
    CrossPromoApp("Jury Simulator: Trial Verdict", "com.charles.jurysim", "Step into jury duty with AI trials, eleven jurors, and the verdict in your hands."),
    CrossPromoApp("Photobooth Event Camera", "com.charles.photobooth", "Turn any Android device into a fun event photo booth with sharing and prints."),
    CrossPromoApp("Path - Daily Bible Study", "com.biblereadingpath.app", "Build daily Bible study habits with gentle streaks."),
    CrossPromoApp("Dreamloom: AI Dream Journal", "com.charles.app.dreamloom", "Private dream journal with on-device AI insights, symbols, and weekly patterns."),
    CrossPromoApp("SkyPulse: Live Flight Tracker", "com.charles.skypulse.app", "Track live flights overhead in real time — aircraft, airports & smart alerts."),
    CrossPromoApp("Grocy Fridge Scanner", "com.charleshartmann.grocyfridge", "Snap your fridge. On-device AI updates your Grocy stock in seconds. No cloud."),
    CrossPromoApp("CrowdTransit: Bus & Train", "com.charles.crowdtransit.app", "Find your ride — free live transit stops, schedules & reviews, nationwide."),
)

@Composable
fun MoreAppsSection() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                "Other apps by Hartman Studios",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            "Check out our other privacy-first apps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        crossPromoApps.forEach { app ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val url = "https://play.google.com/store/apps/details?id=${app.packageName}"
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: ActivityNotFoundException) {}
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(app.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

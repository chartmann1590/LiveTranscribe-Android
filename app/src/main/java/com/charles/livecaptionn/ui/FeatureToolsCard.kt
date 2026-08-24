package com.charles.livecaptionn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charles.livecaptionn.data.CaptionProfile
import com.charles.livecaptionn.data.CaptionProfileRepository
import com.charles.livecaptionn.data.GlossaryEntry
import com.charles.livecaptionn.data.GlossaryRepository
import com.charles.livecaptionn.settings.CaptionSettings
import com.charles.livecaptionn.speech.VoskModelInfo
import com.charles.livecaptionn.ui.l10n.LocalUiStrings
import kotlinx.coroutines.launch

@Composable
fun FeatureToolsCard(
    settings: CaptionSettings,
    hasPro: Boolean,
    profiles: CaptionProfileRepository,
    glossary: GlossaryRepository,
    micPermissionGranted: Boolean,
    overlayPermissionGranted: Boolean,
    voskModels: List<VoskModelInfo>,
    onApplyProfile: (CaptionProfile) -> Unit,
    onSaveHistoryChange: (Boolean) -> Unit,
    onRequiresPro: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalUiStrings.current
    var profileItems by remember { mutableStateOf<List<CaptionProfile>>(emptyList()) }
    var glossaryItems by remember { mutableStateOf<List<GlossaryEntry>>(emptyList()) }
    var showGlossaryDialog by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        profileItems = profiles.list()
        glossaryItems = glossary.list()
    }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t["Tools and privacy"])
            val modelReady = settings.sttBackend.name != "LOCAL_VOSK" || voskModels.any {
                it.installed && it.languageCode.equals(settings.sourceLanguageCode, ignoreCase = true)
            }
            Text(
                t[if (overlayPermissionGranted && (settings.audioSource.name == "SYSTEM" || micPermissionGranted) && modelReady)
                    "Ready to start captioning"
                else "Setup needed before captioning"],
                color = if (overlayPermissionGranted && (settings.audioSource.name == "SYSTEM" || micPermissionGranted) && modelReady)
                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                else androidx.compose.material3.MaterialTheme.colorScheme.error
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = settings.saveHistory, onCheckedChange = onSaveHistoryChange)
                Text(t["Save transcripts to local history"])
            }
            Text(t["Presets"], modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val profile = CaptionProfile(
                        name = t["Current setup"],
                        sourceLanguage = settings.sourceLanguageCode,
                        targetLanguage = settings.targetLanguageCode,
                        textSizeSp = settings.textSizeSp,
                        showOriginal = settings.showOriginal,
                        proOnly = false
                    )
                    scope.launch { profiles.save(profile) }
                    profileItems = profileItems.filterNot { it.name == profile.name } + profile
                }) { Text(t["Save preset"]) }
                OutlinedButton(onClick = {
                    if (hasPro) {
                        val profile = CaptionProfile(
                            name = t["Pro setup"],
                            sourceLanguage = settings.sourceLanguageCode,
                            targetLanguage = settings.targetLanguageCode,
                            textSizeSp = settings.textSizeSp,
                            showOriginal = settings.showOriginal,
                            proOnly = true
                        )
                        scope.launch { profiles.save(profile) }
                        profileItems = profileItems.filterNot { it.name == profile.name } + profile
                    } else onRequiresPro()
                }) { Text(t["Save Pro preset"]) }
            }
            if (profileItems.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    items(profileItems, key = { it.id }) { profile ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.name, Modifier.weight(1f))
                            TextButton(onClick = { if (!profile.proOnly || hasPro) onApplyProfile(profile) else onRequiresPro() }) { Text(t["Use"]) }
                            IconButton(onClick = {
                                scope.launch { profiles.delete(profile.id) }
                                profileItems = profileItems.filterNot { it.id == profile.id }
                            }) { Icon(Icons.Filled.Delete, contentDescription = t["Delete"]) }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t["Glossary and phrase replacements"], Modifier.weight(1f))
                TextButton(onClick = { if (hasPro) showGlossaryDialog = true else onRequiresPro() }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(t["Add Pro"])
                }
            }
            glossaryItems.forEach { entry ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${entry.phrase} -> ${entry.replacement}", Modifier.weight(1f))
                    IconButton(onClick = {
                        scope.launch { glossary.delete(entry.id) }
                        glossaryItems = glossaryItems.filterNot { it.id == entry.id }
                    }) { Icon(Icons.Filled.Delete, contentDescription = t["Delete"]) }
                }
            }
        }
    }

    if (showGlossaryDialog) {
        AlertDialog(
            onDismissRequest = { showGlossaryDialog = false },
            title = { Text(t["Add glossary entry"]) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(phrase, { phrase = it }, label = { Text(t["Phrase"]) }, singleLine = true)
                    OutlinedTextField(replacement, { replacement = it }, label = { Text(t["Replacement"]) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = phrase.isNotBlank() && replacement.isNotBlank(), onClick = {
                    val entry = GlossaryEntry(phrase = phrase.trim(), replacement = replacement.trim())
                    scope.launch { glossary.save(entry) }
                    glossaryItems = glossaryItems + entry
                    showGlossaryDialog = false
                    phrase = ""
                    replacement = ""
                }) { Text(t["Add"]) }
            },
            dismissButton = { TextButton(onClick = { showGlossaryDialog = false }) { Text(t["Cancel"]) } }
        )
    }
}

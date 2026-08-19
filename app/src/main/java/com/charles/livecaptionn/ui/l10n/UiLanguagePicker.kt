package com.charles.livecaptionn.ui.l10n

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charles.livecaptionn.translation.MlKitLanguages
import com.charles.livecaptionn.ui.l10n.UiLocalizationRepository.UiLocalizationStage

/**
 * picker for the interface language (settings + onboarding share it).
 * Selecting a non-English language first asks for confirmation and shows the
 * on-device ML Kit accuracy disclaimer.
 */
@Composable
fun UiLanguagePickerDialog(
    currentCode: String,
    busy: Boolean,
    error: String?,
    stage: UiLocalizationStage = UiLocalizationStage.DOWNLOADING,
    translatedCount: Int = 0,
    translatedTotal: Int = 0,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val t = LocalUiStrings.current
    var query by remember { mutableStateOf("") }
    var pendingCode by remember { mutableStateOf<String?>(null) }

    val filtered = remember(query) {
        if (query.isBlank()) MlKitLanguages.LIST
        else MlKitLanguages.LIST.filter {
            it.name.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true)
        }
    }

    pendingCode?.let { pending ->
        val lang = MlKitLanguages.LIST.firstOrNull { it.code == pending }
        AlertDialog(
            onDismissRequest = { pendingCode = null },
            icon = { Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(lang?.let { "${it.name} (${it.code})" } ?: pending) },
            text = {
                Column {
                    Text(
                        t["UI text is translated on-device by Google ML Kit, not by human translators, and may be inaccurate."],
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        t["You can change this later in Settings."],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingCode = null
                    onSelect(pending)
                }) { Text(t["Switch"]) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCode = null }) { Text(t["Cancel"]) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t["Interface language"]) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(t["Search"]) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (stage) {
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
                    Spacer(Modifier.height(8.dp))
                    Text(
                        t["Translation failed. Tap to retry."],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    items(filtered, key = { it.code }) { lang ->
                        val isCurrent = lang.code.equals(currentCode, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) {
                                    if (lang.code.equals("en", true) || isCurrent) onSelect(lang.code)
                                    else pendingCode = lang.code
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCurrent) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Spacer(Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    lang.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else null
                                )
                                Text(
                                    lang.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = t["On-device ML Kit translation may be inaccurate."],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(t["Close"]) }
        }
    )
}
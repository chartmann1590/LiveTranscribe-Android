package com.charles.livecaptionn.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.livecaptionn.translation.MlKitLanguages
import com.charles.livecaptionn.ui.l10n.LocalUiStrings
import com.charles.livecaptionn.ui.l10n.UiLanguagePickerDialog
import com.charles.livecaptionn.ui.l10n.UiLocalizationRepository.UiLocalizationStage

/**
 * First-launch onboarding: welcome + interface-language selection with the
 * on-device ML Kit accuracy disclaimer. Rendered in English (the translations
 * haven't been chosen yet); the selected language applies to the main app.
 */
@Composable
fun OnboardingScreen(
    viewModel: MainViewModel,
    onRequestAudioPermission: () -> Unit = {},
    onOpenOverlaySettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val t = LocalUiStrings.current
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var showLanguagePicker by remember { mutableStateOf(false) }
    var selectedCode by remember { mutableStateOf(ui.uiLanguageCode.takeIf { it.isNotBlank() } ?: "en") }

    val selectedLanguage = MlKitLanguages.LIST.firstOrNull { it.code == selectedCode }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    t["Live CaptionN"],
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    t["Live captions & translation on your device"],
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            t["Choose your interface language"],
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            t["You can change this later in Settings."],
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { showLanguagePicker = true },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (selectedCode.equals("en", true)) {
                        "${t["English"]} ($selectedCode)"
                    } else {
                        "${selectedLanguage?.name.orEmpty()} ($selectedCode)"
                    },
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }

        if (ui.uiLocalizationBusy) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(
                when (ui.uiLocalizationStage) {
                    UiLocalizationStage.TRANSLATING ->
                        if (ui.uiLocalizationTotal > 0) {
                            t.format("Translating the interface… (%d/%d)", ui.uiLocalizationTranslated, ui.uiLocalizationTotal)
                        } else {
                            t["Translating the interface…"]
                        }
                    UiLocalizationStage.DOWNLOADING -> t["Downloading translation model…"]
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ui.uiLocalizationError?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                t["Translation failed. Tap to retry."],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            t["On-device ML Kit translation may be inaccurate."],
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            t["UI text is translated on-device by Google ML Kit, not by human translators, and may be inaccurate."],
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(Modifier.height(18.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t["Before your first session"], fontWeight = FontWeight.SemiBold)
                Text(t["1. Allow microphone access for microphone captions."], style = MaterialTheme.typography.bodySmall)
                Text(t["2. Allow display over other apps so captions can float above video."], style = MaterialTheme.typography.bodySmall)
                Text(t["3. The app includes English and Vietnamese offline speech models. More models can be downloaded later."], style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRequestAudioPermission, modifier = Modifier.weight(1f)) { Text(t["Microphone"]) }
                    OutlinedButton(onClick = onOpenOverlaySettings, modifier = Modifier.weight(1f)) { Text(t["Overlay"]) }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.completeOnboarding(selectedCode) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(t["Get started"])
        }
    }

    if (showLanguagePicker) {
        UiLanguagePickerDialog(
            currentCode = selectedCode,
            busy = ui.uiLocalizationBusy,
            error = ui.uiLocalizationError,
            stage = ui.uiLocalizationStage,
            translatedCount = ui.uiLocalizationTranslated,
            translatedTotal = ui.uiLocalizationTotal,
            onSelect = { code ->
                selectedCode = code
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false }
        )
    }
}

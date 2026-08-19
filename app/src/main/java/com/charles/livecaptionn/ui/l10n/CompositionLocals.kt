package com.charles.livecaptionn.ui.l10n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Provides the current localized strings to every screen in the app. Defaults
 * to English so a composable previews or renders safely outside the provider.
 */
val LocalUiStrings = staticCompositionLocalOf<UiStrings> { UiStrings.EMPTY }

/**
 * Wraps the app content in [LocalUiStrings]. Reads the container's
 * [UiLocalizationRepository] state so the entire tree recomposes with
 * translated text the moment a language finishes downloading/translating.
 */
@Composable
fun UiStringsProvider(container: com.charles.livecaptionn.di.AppContainer, content: @Composable () -> Unit) {
    val l10nState = container.uiLocalization.state.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalUiStrings provides l10nState.value.strings) {
        content()
    }
}

/** Convenience accessor inside composables. */
@Composable
fun rememberUiStrings(): UiStrings = LocalUiStrings.current
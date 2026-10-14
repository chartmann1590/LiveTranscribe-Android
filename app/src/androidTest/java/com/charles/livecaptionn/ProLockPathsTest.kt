package com.charles.livecaptionn

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charles.livecaptionn.settings.Language
import com.charles.livecaptionn.speech.ModelQuality
import com.charles.livecaptionn.speech.VoskModelInfo
import com.charles.livecaptionn.translation.MlKitLanguages
import com.charles.livecaptionn.ui.LanguagePickerDialog
import com.charles.livecaptionn.ui.ProUpgradeDialog
import com.charles.livecaptionn.ui.VoskRow
import com.charles.livecaptionn.ui.premium.PremiumUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every Pro lock must lead to the upgrade prompt, and the prompt must offer the
 * purchase. A Play review (Oct 2026) came from a locked Indonesian translation
 * language whose "upgrade" button only closed the dialog.
 *
 * On-device test (Compose UI tests need a device or emulator):
 * `./gradlew connectedPlaystoreDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class ProLockPathsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun lockedTranslationLanguage_opensUpgradePrompt_notThePick() {
        var upgradeAsked = 0
        var picked: Language? = null
        val indonesian = Language("id", "Indonesian")
        composeTestRule.setContent {
            MaterialTheme {
                LanguagePickerDialog(
                    options = listOf(Language("en", "English"), indonesian),
                    selectedCode = "en",
                    onDismiss = {},
                    onPick = { picked = it },
                    requiresPro = { MlKitLanguages.requiresPro(it.code) },
                    onRequiresPro = { upgradeAsked++ }
                )
            }
        }
        assertTrue("Indonesian must stay a Pro translation language", MlKitLanguages.requiresPro("id"))
        composeTestRule.onNodeWithText("Indonesian").performClick()
        assertEquals(1, upgradeAsked)
        assertEquals(null, picked)
    }

    @Test
    fun lockedLargeModel_opensUpgradePrompt_notTheDownload() {
        var upgradeAsked = 0
        var downloads = 0
        val large = VoskModelInfo(
            languageCode = "en",
            languageName = "English",
            modelName = "vosk-model-en-us-0.22",
            sizeMb = 1800,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22.zip",
            quality = ModelQuality.LARGE
        )
        composeTestRule.setContent {
            MaterialTheme {
                VoskRow(
                    model = large,
                    progress = null,
                    locked = true,
                    onDownload = { downloads++ },
                    onDelete = {},
                    onRequiresPro = { upgradeAsked++ }
                )
            }
        }
        composeTestRule.onNodeWithText("Pro").performClick()
        assertEquals(1, upgradeAsked)
        assertEquals(0, downloads)
    }

    @Test
    fun failedDownload_showsTheReason_andOffersRetry() {
        val small = VoskModelInfo(
            languageCode = "ko",
            languageName = "Korean",
            modelName = "vosk-model-small-ko-0.22",
            sizeMb = 82,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip"
        )
        composeTestRule.setContent {
            MaterialTheme {
                VoskRow(
                    model = small,
                    progress = null,
                    locked = false,
                    onDownload = {},
                    onDelete = {},
                    onRequiresPro = {},
                    error = "Download failed: no internet connection"
                )
            }
        }
        composeTestRule.onNodeWithText("Download failed: no internet connection").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun upgradePrompt_offersGoPro_andShowsBillingErrors() {
        var goPro = 0
        composeTestRule.setContent {
            MaterialTheme {
                ProUpgradeDialog(
                    state = PremiumUiState(error = "Google Play couldn't be reached. Check your connection and try again"),
                    onGoPro = { goPro++ },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Google Play couldn't be reached. Check your connection and try again").assertIsDisplayed()
        composeTestRule.onNodeWithText("Go Pro").assertIsDisplayed().performClick()
        assertEquals(1, goPro)
    }
}

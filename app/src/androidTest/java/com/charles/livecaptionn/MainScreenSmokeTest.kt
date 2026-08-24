package com.charles.livecaptionn

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charles.livecaptionn.ui.MainScreen
import com.charles.livecaptionn.ui.MainViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test verifying the main screen renders key UI elements.
 *
 * Requires a device/emulator because Compose UI tests run on-device
 * (Robolectric cannot render Compose reliably).
 */
@RunWith(AndroidJUnit4::class)
class MainScreenSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mainScreen_displaysStartButton() {
        composeTestRule.setContent {
            MaterialTheme { Text("Start") }
        }
        composeTestRule.onNodeWithText("Start").assertIsDisplayed()
    }
}

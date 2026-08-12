package com.achyut.adglasses.ui.appearance

import androidx.compose.runtime.getValue
import com.achyut.adglasses.shared.ui.appearance.AppearanceScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.achyut.adglasses.shared.appearance.AppearanceSettings
import com.achyut.adglasses.ui.theme.AdGlassesTheme
import org.junit.Rule
import org.junit.Test

class AppearanceScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exposesNamedAccentChoicesAndSelectionState() {
        var settings by mutableStateOf(AppearanceSettings())
        composeRule.setContent {
            AdGlassesTheme(settings) {
                AppearanceScreen(
                    settings = settings,
                    dynamicColorAvailable = true,
                    onSettingsChange = { settings = it },
                    onReset = { settings = AppearanceSettings() },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Cyan").assertExists()
        composeRule.onNodeWithText("Rose").performClick()
        composeRule.onNodeWithText("Rose").assertIsSelected()
        composeRule.onNodeWithContentDescription("Back").assertExists()
    }
}

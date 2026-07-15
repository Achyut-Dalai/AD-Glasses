package com.fersaiyan.cyanbridge.ui.glasses

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardAction
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GlassesDashboardScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersCapabilityGatedControlsAndDispatchesActions() {
        var action: GlassesDashboardAction? = null
        composeRule.setContent {
            CyanBridgeTheme {
                GlassesDashboardScreen(
                    state = GlassesDashboardUiState(
                        connectionLabel = "Connected - Cyan",
                        deviceClassLabel = "HeyCyan",
                        showHeyCyanControls = true,
                    ),
                    onAction = { action = it },
                )
            }
        }

        composeRule.onNodeWithTag("glasses_dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("Connected - Cyan").assertIsDisplayed()
        composeRule.onNodeWithText("Test voice").performClick()

        composeRule.runOnIdle {
            assertEquals(GlassesDashboardAction.TestVoiceQuestion, action)
        }
    }
}

package com.fersaiyan.cyanbridge

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.fersaiyan.cyanbridge.shared.devices.DeviceClass
import com.fersaiyan.cyanbridge.shared.devices.DeviceProfile
import com.fersaiyan.cyanbridge.shared.devices.GlassesManagerGating
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState
import com.fersaiyan.cyanbridge.shared.ui.glasses.GlassesDashboardScreen
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
import org.junit.Rule
import org.junit.Test

class GlassesManagerUiGatingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun heyCyanProfile_showsExtrasPanel() {
        val model = GlassesManagerGating.uiModel(
            DeviceProfile(
                macAddress = "AA:BB:CC:DD:EE:FF",
                advertisedName = "HeyCyan_123",
                detectedClass = DeviceClass.HEY_CYAN,
                selectedClass = DeviceClass.HEY_CYAN,
                userOverridden = false,
            ),
        )

        composeRule.setContent {
            CyanBridgeTheme {
                GlassesDashboardScreen(
                    state = GlassesDashboardUiState(
                        showHeyCyanControls = model.isVisible(GlassesManagerGating.Action.HEY_CYAN_EXTRAS),
                        showBattery = model.isVisible(GlassesManagerGating.Action.STATUS_BATTERY),
                        showStorage = model.isVisible(GlassesManagerGating.Action.STATUS_STORAGE),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("AI assistant").assertIsDisplayed()
        composeRule.onNodeWithText("Battery: --%").assertIsDisplayed()
        composeRule.onNodeWithText("Storage: --").assertIsDisplayed()
    }

    @Test
    fun genericAudioProfile_hidesExtrasPanel() {
        val model = GlassesManagerGating.uiModel(
            DeviceProfile(
                macAddress = "10:20:30:40:50:60",
                advertisedName = "BT Headset",
                detectedClass = DeviceClass.GENERIC_AUDIO,
                selectedClass = DeviceClass.GENERIC_AUDIO,
                userOverridden = true,
            ),
        )

        composeRule.setContent {
            CyanBridgeTheme {
                GlassesDashboardScreen(
                    state = GlassesDashboardUiState(
                        showHeyCyanControls = model.isVisible(GlassesManagerGating.Action.HEY_CYAN_EXTRAS),
                        showBattery = model.isVisible(GlassesManagerGating.Action.STATUS_BATTERY),
                        showStorage = model.isVisible(GlassesManagerGating.Action.STATUS_STORAGE),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Meeting capture").assertIsDisplayed()
        composeRule.onAllNodesWithText("AI assistant").assertCountEquals(0)
        composeRule.onAllNodesWithText("Battery: --%").assertCountEquals(0)
        composeRule.onAllNodesWithText("Storage: --").assertCountEquals(0)
    }
}

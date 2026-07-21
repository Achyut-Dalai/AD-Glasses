package com.fersaiyan.cyanbridge.ui.localmodels

import androidx.compose.ui.test.assertIsDisplayed
import com.fersaiyan.cyanbridge.shared.ui.localmodels.LocalModelsConfigureScreen
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.fersaiyan.cyanbridge.shared.localmodels.LocalModelsAction
import com.fersaiyan.cyanbridge.shared.localmodels.LocalModelsConfigureUiState
import com.fersaiyan.cyanbridge.shared.localmodels.LocalModelDownloadUiState
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LocalModelsConfigureScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersRuntimeAndDispatchesImportAction() {
        var action: LocalModelsAction? = null
        composeRule.setContent {
            CyanBridgeTheme {
                LocalModelsConfigureScreen(
                    state = LocalModelsConfigureUiState(
                        engineStatus = "Runtimes available: llama.cpp + LiteRT",
                        emptyStateMessage = "No local model installed yet.",
                    ),
                    onAction = { action = it },
                )
            }
        }

        composeRule.onNodeWithTag("local_models_configure").assertIsDisplayed()
        composeRule.onNodeWithText("Import model file").performClick()

        composeRule.runOnIdle {
            assertEquals(LocalModelsAction.ImportModel, action)
        }
    }

    @Test
    fun activeDownloadIsVisibleNearTopAndCanBeCancelled() {
        var action: LocalModelsAction? = null
        composeRule.setContent {
            CyanBridgeTheme {
                LocalModelsConfigureScreen(
                    state = LocalModelsConfigureUiState(
                        download = LocalModelDownloadUiState(
                            isInFlight = true,
                            message = "Downloading qwen: 42%",
                            progressPercent = 42,
                        ),
                    ),
                    onAction = { action = it },
                )
            }
        }

        composeRule.onNodeWithTag("local_model_download_progress").assertIsDisplayed()
        composeRule.onNodeWithText("Downloading qwen: 42%").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel download").performClick()

        composeRule.runOnIdle {
            assertEquals(LocalModelsAction.CancelDownload, action)
        }
    }

    @Test
    fun composeBackConfirmsEditedSettingsBeforeLeaving() {
        var action: LocalModelsAction? = null
        composeRule.setContent {
            CyanBridgeTheme {
                LocalModelsConfigureScreen(
                    state = LocalModelsConfigureUiState(hasUnsavedChanges = true),
                    onAction = { action = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Unsaved changes").assertIsDisplayed()
        composeRule.onNodeWithText("Discard").performClick()

        composeRule.runOnIdle {
            assertEquals(LocalModelsAction.DiscardChangesAndBack, action)
        }
    }
}

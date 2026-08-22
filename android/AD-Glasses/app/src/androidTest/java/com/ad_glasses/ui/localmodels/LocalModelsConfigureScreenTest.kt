package com.ad_glasses.ui.localmodels

import androidx.compose.ui.test.assertIsDisplayed
import com.ad_glasses.shared.ui.localmodels.LocalModelsConfigureScreen
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.ad_glasses.shared.localmodels.LocalModelsAction
import com.ad_glasses.shared.localmodels.LocalModelsConfigureUiState
import com.ad_glasses.shared.localmodels.LocalModelDownloadUiState
import com.ad_glasses.ui.theme.ADGlassesTheme
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
            ADGlassesTheme {
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
    fun activeDownloadIsVisibleAndCanBeCancelled() {
        var action: LocalModelsAction? = null
        composeRule.setContent {
            ADGlassesTheme {
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
    fun activeDownloadRemainsVisibleWhenScrolledToBottomAndCanBeCancelled() {
        var action: LocalModelsAction? = null
        val catalogEntries = List(10) { index ->
            com.ad_glasses.shared.localmodels.LocalModelCatalogUiItem(
                id = "model_$index",
                title = "Catalog Model $index",
                details = "Test description $index · 1.5 GB",
                status = "Available",
                downloadLabel = "Download",
                canDownload = true,
            )
        }
        composeRule.setContent {
            ADGlassesTheme {
                LocalModelsConfigureScreen(
                    state = LocalModelsConfigureUiState(
                        catalog = catalogEntries,
                        download = LocalModelDownloadUiState(
                            isInFlight = true,
                            message = "Downloading qwen: 75%",
                            progressPercent = 75,
                        ),
                    ),
                    onAction = { action = it },
                )
            }
        }

        // Scroll the list down to the bottom
        composeRule.onNodeWithTag("local_models_configure")
            .performScrollToNode(androidx.compose.ui.test.hasText("Catalog Model 9"))

        // Assert progress bar and cancel button are still persistently visible on screen
        composeRule.onNodeWithTag("local_model_download_progress").assertIsDisplayed()
        composeRule.onNodeWithText("Downloading qwen: 75%").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel download").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(LocalModelsAction.CancelDownload, action)
        }
    }

    @Test
    fun composeBackConfirmsEditedSettingsBeforeLeaving() {
        var action: LocalModelsAction? = null
        composeRule.setContent {
            ADGlassesTheme {
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

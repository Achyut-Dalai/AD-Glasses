package com.fersaiyan.cyanbridge.ui.localmodels

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fersaiyan.cyanbridge.shared.localmodels.LocalModelsAction
import com.fersaiyan.cyanbridge.shared.localmodels.LocalModelsConfigureUiState
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
}

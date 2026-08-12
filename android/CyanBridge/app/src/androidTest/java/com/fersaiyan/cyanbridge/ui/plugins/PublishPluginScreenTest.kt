package com.achyut.adglasses.ui.plugins

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.achyut.adglasses.shared.plugins.PublishPluginUiState
import com.achyut.adglasses.shared.ui.plugins.PublishPluginScreen
import com.achyut.adglasses.ui.theme.CyanBridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PublishPluginScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun updatesFormFieldsAndSubmitsThroughCallback() {
        var state by mutableStateOf(PublishPluginUiState())
        var submitCount = 0

        composeRule.setContent {
            CyanBridgeTheme {
                PublishPluginScreen(
                    state = state,
                    categories = listOf("Productivity", "Other"),
                    onTitleChanged = { state = state.copy(title = it) },
                    onAuthorChanged = { state = state.copy(author = it) },
                    onDescriptionChanged = { state = state.copy(description = it) },
                    onCategorySelected = { state = state.copy(category = it) },
                    onTaskerNetLinkChanged = { state = state.copy(taskerNetLink = it) },
                    onSubmit = { submitCount += 1 },
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("publish_plugin_category_Productivity").performClick()
        composeRule.onNodeWithTag("publish_plugin_title").performTextInput("Meeting helper")
        composeRule.onNodeWithTag("publish_plugin_author").performTextInput("cyanlabs")
        composeRule.onNodeWithTag("publish_plugin_submit").performClick()

        composeRule.runOnIdle {
            assertEquals("Meeting helper", state.title)
            assertEquals("cyanlabs", state.author)
            assertEquals("Productivity", state.category)
            assertEquals(1, submitCount)
        }
    }
}

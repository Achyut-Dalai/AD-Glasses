package com.ad_glasses.ui.chat

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ad_glasses.shared.chat.ChatAppearanceMenuAction
import com.ad_glasses.shared.ui.chat.ChatAppearanceMenuDialog
import com.ad_glasses.ui.theme.ADGlassesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatAppearanceMenuDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exposesComposeActionsAndRoutesTheSelectedOption() {
        var selected: ChatAppearanceMenuAction? = null

        composeRule.setContent {
            ADGlassesTheme {
                ChatAppearanceMenuDialog(
                    modelOptionLabel = "Change relay AI model",
                    onDismissRequest = {},
                    onAction = { selected = it },
                )
            }
        }

        composeRule.onNodeWithText("Change relay AI model").assertExists()
        composeRule.onNodeWithTag("chat_appearance_action_CHANGE_ASSISTANT_BUBBLE_COLOR")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(ChatAppearanceMenuAction.CHANGE_ASSISTANT_BUBBLE_COLOR, selected)
        }
    }
}

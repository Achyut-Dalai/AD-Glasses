package com.fersaiyan.cyanbridge.shared.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.fersaiyan.cyanbridge.shared.chat.ChatAppearanceMenuAction

/** Compose-owned overflow menu; host callbacks retain preference and picker work. */
@Composable
fun ChatAppearanceMenuDialog(
    modelOptionLabel: String?,
    onDismissRequest: () -> Unit,
    onAction: (ChatAppearanceMenuAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Chat appearance") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ChatAppearanceMenuItem(
                    label = "Change user bubble color",
                    action = ChatAppearanceMenuAction.CHANGE_USER_BUBBLE_COLOR,
                    onAction = onAction,
                )
                ChatAppearanceMenuItem(
                    label = "Change assistant bubble color",
                    action = ChatAppearanceMenuAction.CHANGE_ASSISTANT_BUBBLE_COLOR,
                    onAction = onAction,
                )
                ChatAppearanceMenuItem(
                    label = "Choose wallpaper from gallery",
                    action = ChatAppearanceMenuAction.CHOOSE_WALLPAPER,
                    onAction = onAction,
                )
                ChatAppearanceMenuItem(
                    label = "Remove wallpaper",
                    action = ChatAppearanceMenuAction.REMOVE_WALLPAPER,
                    onAction = onAction,
                )
                ChatAppearanceMenuItem(
                    label = "Reset chat appearance",
                    action = ChatAppearanceMenuAction.RESET_APPEARANCE,
                    onAction = onAction,
                )
                modelOptionLabel?.let { label ->
                    ChatAppearanceMenuItem(
                        label = label,
                        action = ChatAppearanceMenuAction.CHANGE_MODEL,
                        onAction = onAction,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ChatAppearanceMenuItem(
    label: String,
    action: ChatAppearanceMenuAction,
    onAction: (ChatAppearanceMenuAction) -> Unit,
) {
    TextButton(
        onClick = { onAction(action) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_appearance_action_${action.name}"),
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
    }
}

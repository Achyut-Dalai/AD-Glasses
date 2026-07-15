package com.fersaiyan.cyanbridge.shared.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChatPresentationModelsTest {
    @Test
    fun composerDefaultsToAPlainSendAction() {
        val state = ChatComposerUiState()

        assertEquals("Message", state.hint)
        assertEquals(ChatComposerPrimaryAction.SEND, state.primaryAction)
        assertFalse(state.isMediaEnabled)
    }

    @Test
    fun appearanceActionKeepsItsPlatformNeutralIntent() {
        val action = ChatAppearanceMenuAction.CHANGE_ASSISTANT_BUBBLE_COLOR

        assertEquals(ChatAppearanceMenuAction.CHANGE_ASSISTANT_BUBBLE_COLOR, action)
    }
}

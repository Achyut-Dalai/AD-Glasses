package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantConversationPolicyTest {

    @Test
    fun conversationControls_areRecognizedWithoutPersistableFreeTextFalsePositives() {
        assertEquals(
            AssistantConversationCommand.START_FRESH,
            AssistantConversationPolicy.parseCommand("New topic!"),
        )
        assertEquals(
            AssistantConversationCommand.START_FRESH,
            AssistantConversationPolicy.parseCommand("Start a new conversation."),
        )
        assertEquals(
            AssistantConversationCommand.FORGET_CURRENT,
            AssistantConversationPolicy.parseCommand("FORGET this conversation"),
        )

        assertNull(AssistantConversationPolicy.parseCommand("Suggest a new topic"))
        assertNull(AssistantConversationPolicy.parseCommand("What does forget this conversation do?"))
    }
}

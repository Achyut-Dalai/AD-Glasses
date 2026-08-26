package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantWebPolicyTest {
    @Test
    fun legacyPolicyNeverInfersWebFromTextOrHistory() {
        assertFalse(AssistantWebPolicy.shouldUseWeb("Search the web for today's cricket score"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("What is the current Bitcoin price?"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("Find KFC near me"))

        val history = listOf(
            user("What's the weather today?", 1),
            assistant("Sunny.", 2),
            user("And tomorrow?", 3),
        )
        assertFalse(AssistantWebPolicy.shouldUseWeb("And tomorrow?", history = history))
    }

    @Test
    fun explicitPerTurnPreferenceIsStillHonored() {
        assertTrue(AssistantWebPolicy.shouldUseWeb("Explain aperture", requested = true))
        assertFalse(AssistantWebPolicy.shouldUseWeb("What's the latest news?", requested = false))
    }

    private fun user(content: String, createdAt: Long) = ChatMessage(
        id = "u-$createdAt",
        chatId = "thread",
        role = ChatRole.USER,
        content = content,
        createdAt = createdAt,
    )

    private fun assistant(content: String, createdAt: Long) = ChatMessage(
        id = "a-$createdAt",
        chatId = "thread",
        role = ChatRole.ASSISTANT,
        content = content,
        createdAt = createdAt,
    )
}

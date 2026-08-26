package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantWebPolicyTest {
    @Test
    fun explicitWebPhrasesEnableNetworkUse() {
        assertTrue(AssistantWebPolicy.shouldUseWeb("Search the web for the newest Pixel price"))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Browse the internet for this"))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Check online whether this model was recalled"))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Verify this with web sources"))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Look up when the museum opens"))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Google this for me"))
    }

    @Test
    fun clearlyTimeSensitiveFactFamiliesEnableAutomaticWeb() {
        listOf(
            "What's the weather tomorrow?",
            "Is it going to rain?",
            "Do I need an umbrella today?",
            "What's the latest news about Android?",
            "Give me the local news",
            "Is this store open now?",
            "When does the museum open?",
            "What is the USD to INR exchange rate?",
            "What is Apple's stock price?",
            "How much is Bitcoin?",
            "What's ETH worth?",
            "What's the final match score?",
            "Who won the match?",
            "Who is winning the race?",
            "When is the next match?",
            "What is the latest Android version?",
            "Any recent developments in quantum computing?",
            "Who is the president of France?",
            "Who is the CEO of OpenAI?",
            "Who won the election?",
            "What are today's election results?",
            "What is the current flight status?",
            "Is flight AI302 delayed?",
            "What is the service status?",
        ).forEach { text ->
            assertTrue(text, AssistantWebPolicy.shouldUseWeb(text))
        }
    }

    @Test
    fun ambiguousGenericWordsDoNotLeakToWeb() {
        listOf(
            "What is my current location?",
            "Explain electrical current in a wire",
            "What is a current account?",
            "What makes a good restaurant?",
            "Find me a coffee shop nearby",
            "Give me the newest recipe ideas",
            "What is the next prime number after 17?",
            "Explain price elasticity",
            "What does availability mean in distributed systems?",
            "What is a musical score?",
            "Find the bug in this function",
            "Find the square root of 81",
            "Search your feelings",
            "Search for the maximum value in this array",
            "Search for a matching string in this list",
            "Forecast sales for next quarter",
            "What makes a good headline?",
            "What is a stock variable in programming?",
            "Who is a president?",
            "Explain what a CEO does",
            "What is service status in a state machine?",
        ).forEach { text ->
            assertFalse(text, AssistantWebPolicy.shouldUseWeb(text))
        }
    }

    @Test
    fun ordinaryConversationStaysOfflineByDefault() {
        assertFalse(AssistantWebPolicy.shouldUseWeb("Explain how aperture affects depth of field"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("Summarize this paragraph"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("What is temperature in physics?"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("What is recursion?"))
    }

    @Test
    fun shortFollowUpsInheritOnlyARecentUserWebIntent() {
        val weatherHistory = listOf(
            user("What's the weather today?", 1),
            assistant("Warm and cloudy.", 2),
            user("And tomorrow?", 3),
        )
        assertTrue(
            AssistantWebPolicy.shouldUseWeb(
                text = "And tomorrow?",
                history = weatherHistory,
            ),
        )

        val priceHistory = listOf(
            user("What's the Bitcoin price?", 1),
            assistant("It is ...", 2),
            user("And now?", 3),
        )
        assertTrue(AssistantWebPolicy.shouldUseWeb("And now?", history = priceHistory))

        val offlineHistory = listOf(
            user("Explain recursion", 1),
            assistant("Recursion is...", 2),
            user("And tomorrow?", 3),
        )
        assertFalse(AssistantWebPolicy.shouldUseWeb("And tomorrow?", history = offlineHistory))

        val assistantOnlyFreshness = listOf(
            user("Tell me a joke", 1),
            assistant("Tomorrow's weather might be sunny.", 2),
            user("And tomorrow?", 3),
        )
        assertFalse(AssistantWebPolicy.shouldUseWeb("And tomorrow?", history = assistantOnlyFreshness))
    }

    @Test
    fun explicitPerTurnStateControlsAutomaticFreshnessAndFollowUps() {
        assertFalse(AssistantWebPolicy.shouldUseWeb("What's the latest news?", requested = false))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Explain aperture", requested = true))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Search the web", requested = false))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Look up this company", requested = false))

        val history = listOf(
            user("What's the weather today?", 1),
            assistant("Sunny.", 2),
            user("And tomorrow?", 3),
        )
        assertFalse(AssistantWebPolicy.shouldUseWeb("And tomorrow?", requested = false, history = history))
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

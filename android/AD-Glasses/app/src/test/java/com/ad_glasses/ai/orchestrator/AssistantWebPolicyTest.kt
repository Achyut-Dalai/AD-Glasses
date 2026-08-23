package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantWebPolicyTest {
    @Test
    fun explicitWebPhrasesEnableNetworkUse() {
        assertTrue(AssistantWebPolicy.shouldUseWeb("Search the web for the newest Pixel price"))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Browse the web for this"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("Check online"))
    }

    @Test
    fun freshnessAndLocationLanguageDoNotImplicitlyEnableNetworkUse() {
        assertFalse(AssistantWebPolicy.shouldUseWeb("What's the weather tomorrow?"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("What's the latest news?"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("Find me a coffee shop nearby"))
    }

    @Test
    fun ordinaryConversationStaysOfflineByDefault() {
        assertFalse(AssistantWebPolicy.shouldUseWeb("Explain how aperture affects depth of field"))
    }

    @Test
    fun explicitPerTurnStateEnablesStandardChatWebUse() {
        assertFalse(AssistantWebPolicy.shouldUseWeb("What's the latest news?", requested = false))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Explain aperture", requested = true))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Search the web", requested = true))
    }
}

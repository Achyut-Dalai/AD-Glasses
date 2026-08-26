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
    fun inherentlyFreshQuestionsEnableNetworkWhenNoPerTurnChoiceWasSupplied() {
        assertTrue(AssistantWebPolicy.shouldUseWeb("What's the weather tomorrow?"))
        assertTrue(AssistantWebPolicy.shouldUseWeb("What's the latest news?"))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Is this store open now?"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("Find me a coffee shop nearby"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("What is my current location?"))
    }

    @Test
    fun ordinaryConversationStaysOfflineByDefault() {
        assertFalse(AssistantWebPolicy.shouldUseWeb("Explain how aperture affects depth of field"))
    }

    @Test
    fun explicitPerTurnStateControlsAutomaticFreshness() {
        assertFalse(AssistantWebPolicy.shouldUseWeb("What's the latest news?", requested = false))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Explain aperture", requested = true))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Search the web", requested = false))
    }
}

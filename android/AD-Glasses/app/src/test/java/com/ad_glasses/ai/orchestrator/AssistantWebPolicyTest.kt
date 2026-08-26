package com.ad_glasses.ai.orchestrator

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
    }

    @Test
    fun clearlyTimeSensitiveFactFamiliesEnableAutomaticWeb() {
        assertTrue(AssistantWebPolicy.shouldUseWeb("What's the weather tomorrow?"))
        assertTrue(AssistantWebPolicy.shouldUseWeb("What's the latest news about Android?"))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Is this store open now?"))
        assertTrue(AssistantWebPolicy.shouldUseWeb("What is the USD to INR exchange rate?"))
        assertTrue(AssistantWebPolicy.shouldUseWeb("What is Apple's stock price?"))
        assertTrue(AssistantWebPolicy.shouldUseWeb("What's the final match score?"))
        assertTrue(AssistantWebPolicy.shouldUseWeb("What is the latest Android version?"))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Any recent developments in quantum computing?"))
    }

    @Test
    fun ambiguousFreshnessWordsDoNotLeakToWeb() {
        assertFalse(AssistantWebPolicy.shouldUseWeb("What is my current location?"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("Explain electrical current in a wire"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("What is a current account?"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("What makes a good restaurant?"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("Find me a coffee shop nearby"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("Give me the newest recipe ideas"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("What is the next prime number after 17?"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("Explain price elasticity"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("What does availability mean in distributed systems?"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("What is a musical score?"))
    }

    @Test
    fun ordinaryConversationStaysOfflineByDefault() {
        assertFalse(AssistantWebPolicy.shouldUseWeb("Explain how aperture affects depth of field"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("Summarize this paragraph"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("What is temperature in physics?"))
    }

    @Test
    fun explicitPerTurnStateControlsAutomaticFreshness() {
        assertFalse(AssistantWebPolicy.shouldUseWeb("What's the latest news?", requested = false))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Explain aperture", requested = true))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Search the web", requested = false))
    }
}

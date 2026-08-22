package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantWebPolicyTest {
    @Test
    fun explicitWebRequestEnablesGrounding() {
        assertFalse(AssistantWebPolicy.shouldUseWeb("Search the web for the newest Pixel price"))
    }

    @Test
    fun freshnessSensitiveRequestEnablesGrounding() {
        assertFalse(AssistantWebPolicy.shouldUseWeb("What's the weather tomorrow?"))
        assertFalse(AssistantWebPolicy.shouldUseWeb("Find me a coffee shop nearby"))
    }

    @Test
    fun ordinaryConversationStaysOfflineByDefault() {
        assertFalse(AssistantWebPolicy.shouldUseWeb("Explain how aperture affects depth of field"))
    }

    @Test
    fun explicitPreferenceOverridesAutomaticPolicy() {
        assertFalse(AssistantWebPolicy.shouldUseWeb("What's the latest news?", requested = false))
        assertTrue(AssistantWebPolicy.shouldUseWeb("Explain aperture", requested = true))
    }
}

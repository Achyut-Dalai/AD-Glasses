package com.achyut.adglasses.ai.router

import com.achyut.adglasses.shared.glasses.GlassesAssistantMode
import com.achyut.adglasses.shared.settings.AgentProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassesAssistantRoutingPolicyTest {
    @Test
    fun phoneAssistantIgnoresCustomProviderSetting() {
        AgentProviderType.entries.forEach { provider ->
            assertEquals(
                GlassesAssistantRoute.PHONE_ASSISTANT,
                GlassesAssistantRoutingPolicy.resolve(GlassesAssistantMode.PHONE_ASSISTANT, provider),
            )
        }
    }

    @Test
    fun customModeRoutesEachSettingsChoiceExplicitly() {
        assertEquals(
            GlassesAssistantRoute.LOCAL,
            GlassesAssistantRoutingPolicy.resolve(
                GlassesAssistantMode.CUSTOM_AI_PROVIDER,
                AgentProviderType.LOCAL_AGENT,
            ),
        )
        assertEquals(
            GlassesAssistantRoute.PRO,
            GlassesAssistantRoutingPolicy.resolve(
                GlassesAssistantMode.CUSTOM_AI_PROVIDER,
                AgentProviderType.CLOUD,
            ),
        )
        assertEquals(
            GlassesAssistantRoute.TASKER_EXTERNAL_UI,
            GlassesAssistantRoutingPolicy.resolve(
                GlassesAssistantMode.CUSTOM_AI_PROVIDER,
                AgentProviderType.TASKER,
            ),
        )
    }
}

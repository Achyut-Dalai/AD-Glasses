package com.fersaiyan.cyanbridge.ai.router

import com.fersaiyan.cyanbridge.shared.glasses.GlassesAssistantMode
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassesAssistantRoutingPolicyTest {
    @Test
    fun legacyPhoneAssistantModeStillRoutesToAdOwnedProviders() {
        assertEquals(
            GlassesAssistantRoute.LOCAL,
            GlassesAssistantRoutingPolicy.resolve(
                GlassesAssistantMode.PHONE_ASSISTANT,
                AgentProviderType.LOCAL_AGENT,
            ),
        )
        assertEquals(
            GlassesAssistantRoute.PRO,
            GlassesAssistantRoutingPolicy.resolve(
                GlassesAssistantMode.PHONE_ASSISTANT,
                AgentProviderType.PRO_SUBSCRIPTION,
            ),
        )
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
                AgentProviderType.PRO_SUBSCRIPTION,
            ),
        )
    }
}

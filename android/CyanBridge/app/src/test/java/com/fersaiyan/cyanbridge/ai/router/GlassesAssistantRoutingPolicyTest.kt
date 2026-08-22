package com.fersaiyan.cyanbridge.ai.router

import com.fersaiyan.cyanbridge.shared.glasses.GlassesAssistantMode
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassesAssistantRoutingPolicyTest {
    @Test
    fun adOwnedModeRoutesOnlyToCloudOrLocalInference() {
        assertEquals(
            GlassesAssistantRoute.LOCAL,
            GlassesAssistantRoutingPolicy.resolve(
                GlassesAssistantMode.CUSTOM_AI_PROVIDER,
                AgentProviderType.LOCAL_AGENT,
            ),
        )
        assertEquals(
            GlassesAssistantRoute.CLOUD,
            GlassesAssistantRoutingPolicy.resolve(
                GlassesAssistantMode.CUSTOM_AI_PROVIDER,
                AgentProviderType.CLOUD_AI,
            ),
        )
    }
}

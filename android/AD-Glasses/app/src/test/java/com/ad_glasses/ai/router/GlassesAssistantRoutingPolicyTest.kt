package com.ad_glasses.ai.router

import com.ad_glasses.shared.glasses.GlassesAssistantMode
import com.ad_glasses.shared.settings.AgentProviderType
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

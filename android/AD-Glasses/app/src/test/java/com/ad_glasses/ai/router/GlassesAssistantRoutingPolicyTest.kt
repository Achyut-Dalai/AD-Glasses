package com.ad_glasses.ai.router

import com.ad_glasses.shared.settings.AgentProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassesAssistantRoutingPolicyTest {
    @Test
    fun adOwnedAssistantRoutesOnlyToCloudOrLocalInference() {
        assertEquals(
            GlassesAssistantRoute.LOCAL,
            GlassesAssistantRoutingPolicy.resolve(AgentProviderType.LOCAL_AGENT),
        )
        assertEquals(
            GlassesAssistantRoute.CLOUD,
            GlassesAssistantRoutingPolicy.resolve(AgentProviderType.CLOUD_AI),
        )
    }
}

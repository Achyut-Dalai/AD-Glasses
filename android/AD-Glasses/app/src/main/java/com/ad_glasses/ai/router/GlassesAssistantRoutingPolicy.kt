package com.ad_glasses.ai.router

import com.ad_glasses.shared.settings.AgentProviderType

enum class GlassesAssistantRoute {
    CLOUD,
    LOCAL,
}

/** Assistant invocation is always AD-owned: direct cloud API or on-device local model. */
object GlassesAssistantRoutingPolicy {
    fun resolve(provider: AgentProviderType): GlassesAssistantRoute = when (provider) {
        AgentProviderType.CLOUD_AI -> GlassesAssistantRoute.CLOUD
        AgentProviderType.LOCAL_AGENT -> GlassesAssistantRoute.LOCAL
    }
}

package com.fersaiyan.cyanbridge.ai.router

import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType

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

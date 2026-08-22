package com.fersaiyan.cyanbridge.ai.router

import com.fersaiyan.cyanbridge.shared.glasses.GlassesAssistantMode
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType

enum class GlassesAssistantRoute {
    /** Compatibility token for inherited host code only. Resolution never returns this route. */
    @Deprecated("Consumer assistant handoff is retired")
    PHONE_ASSISTANT,
    LOCAL,
    PRO,
}

/** Assistant invocation is always AD-owned: direct API token or local model. */
object GlassesAssistantRoutingPolicy {
    fun resolve(
        mode: GlassesAssistantMode,
        customProvider: AgentProviderType,
    ): GlassesAssistantRoute = when (customProvider) {
        AgentProviderType.LOCAL_AGENT -> GlassesAssistantRoute.LOCAL
        AgentProviderType.PRO_SUBSCRIPTION -> GlassesAssistantRoute.PRO
    }
}

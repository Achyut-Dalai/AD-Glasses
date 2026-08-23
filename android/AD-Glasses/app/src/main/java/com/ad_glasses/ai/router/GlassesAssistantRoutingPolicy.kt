package com.ad_glasses.ai.router

import com.ad_glasses.shared.settings.AgentProviderType

enum class GlassesAssistantRoute {
    CLOUD,
}

/** Every AD assistant invocation uses the active encrypted Cloud AI profile. */
object GlassesAssistantRoutingPolicy {
    fun resolve(provider: AgentProviderType): GlassesAssistantRoute = GlassesAssistantRoute.CLOUD
}

package com.fersaiyan.cyanbridge.ai.router

import com.fersaiyan.cyanbridge.shared.glasses.GlassesAssistantMode
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType

enum class GlassesAssistantRoute {
    PHONE_ASSISTANT,
    LOCAL,
    PRO,
    TASKER_EXTERNAL_UI,
}

object GlassesAssistantRoutingPolicy {
    fun resolve(
        mode: GlassesAssistantMode,
        customProvider: AgentProviderType,
    ): GlassesAssistantRoute {
        if (mode == GlassesAssistantMode.PHONE_ASSISTANT) {
            return GlassesAssistantRoute.PHONE_ASSISTANT
        }
        return when (customProvider) {
            AgentProviderType.LOCAL_AGENT -> GlassesAssistantRoute.LOCAL
            AgentProviderType.CLOUD -> GlassesAssistantRoute.PRO
            AgentProviderType.TASKER -> GlassesAssistantRoute.TASKER_EXTERNAL_UI
        }
    }
}

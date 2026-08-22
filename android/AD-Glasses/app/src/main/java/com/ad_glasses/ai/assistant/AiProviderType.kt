package com.ad_glasses.ai.assistant

/**
 * Legacy assistant-package provider token kept small and AD-owned.
 *
 * Consumer Gemini/ChatGPT app identities are retired. Old serialized values migrate to CLOUD so
 * they cannot recreate an external-app handoff after upgrade.
 */
enum class AiProviderType(
    val wireName: String,
    val label: String,
) {
    CLOUD(wireName = "cloud", label = "Cloud AI"),
    LOCAL_AGENT(wireName = "local_agent", label = "Local AI"),
    ;

    companion object {
        fun fromWireName(wireName: String?): AiProviderType? {
            if (wireName.isNullOrBlank()) return null
            return when (wireName.trim().lowercase()) {
                LOCAL_AGENT.wireName -> LOCAL_AGENT
                CLOUD.wireName,
                "gemini",
                "chatgpt",
                "phone_assistant",
                "default_assistant" -> CLOUD
                else -> null
            }
        }
    }
}

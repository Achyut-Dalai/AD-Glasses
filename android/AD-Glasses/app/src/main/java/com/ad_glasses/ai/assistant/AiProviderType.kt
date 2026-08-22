package com.ad_glasses.ai.assistant

/**
 * Legacy assistant-package provider token kept small and AD-owned.
 *
 * Legacy external-assistant provider tokens are retired. Old serialized values migrate to CLOUD
 * so upgrades remain on the AD-owned Cloud/Local routing architecture.
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

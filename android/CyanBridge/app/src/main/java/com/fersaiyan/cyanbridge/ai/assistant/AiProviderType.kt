package com.fersaiyan.cyanbridge.ai.assistant

/**
 * Canonical set of AI assistant backends.
 *
 * "wireName" is the stable, serialized identifier for storage/interop.
 * "label" is intended for UI.
 */
enum class AiProviderType(
    val wireName: String,
    val label: String,
) {
    /** Local/on-device LLM runtime (no cloud by default). */
    LOCAL_AGENT(wireName = "local_agent", label = "Local Agent (on-device)"),

    // External-app identities retained for old serialized UI state. They are not internal
    // inference backends and cannot return a response to AD.
    GEMINI(wireName = "gemini", label = "Gemini"),
    CHATGPT(wireName = "chatgpt", label = "ChatGPT"),
    ;

    companion object {
        fun fromWireName(wireName: String?): AiProviderType? {
            if (wireName.isNullOrBlank()) return null
            return entries.firstOrNull { it.wireName == wireName }
        }
    }
}

package com.fersaiyan.cyanbridge.ai.orchestrator

import com.fersaiyan.cyanbridge.shared.chat.ChatMessage
import com.fersaiyan.cyanbridge.shared.chat.ChatRole

/**
 * Decides whether a turn should prefer fresh web-grounded information.
 *
 * Network use is explicit. Text such as "weather today" is not permission to contact a relay;
 * callers must supply requested=true from a visible control or an explicit voice confirmation.
 */
object AssistantWebPolicy {
    fun shouldUseWeb(
        text: String,
        requested: Boolean? = null,
        history: List<ChatMessage> = emptyList(),
    ): Boolean {
        return requested == true
    }

}

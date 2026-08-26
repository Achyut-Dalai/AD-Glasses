package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage

/**
 * Legacy compatibility shim for callers that still pass an explicit per-turn web preference.
 *
 * Automatic keyword, phrase, and history-based web routing was intentionally removed. Text turns
 * are now classified by GroundingIntentRouter. This object must never infer SEARCH from user text.
 */
object AssistantWebPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun shouldUseWeb(
        text: String,
        requested: Boolean? = null,
        history: List<ChatMessage> = emptyList(),
    ): Boolean = requested == true
}

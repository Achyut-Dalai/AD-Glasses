package com.ad_glasses.ai.router

/**
 * Provider-neutral streaming activity used by the wearable watchdog.
 *
 * These events intentionally carry no provider text. Reasoning content may be received by a
 * provider adapter solely to establish liveness, but it is discarded before this signal leaves
 * the transport layer.
 */
enum class CloudStreamActivity {
    PROVIDER_DATA,
    REASONING,
}

internal fun openAiCompatibleHasReasoningActivity(
    reasoningContent: String?,
    reasoning: String?,
    reasoningDetailsCount: Int,
    visibleContent: String?,
): Boolean =
    !reasoningContent.isNullOrBlank() ||
        !reasoning.isNullOrBlank() ||
        reasoningDetailsCount > 0 ||
        containsInlineReasoningTag(visibleContent)

internal fun openAiResponsesHasReasoningActivity(
    eventType: String?,
    outputItemType: String?,
): Boolean =
    eventType.orEmpty().startsWith("response.reasoning") || outputItemType == "reasoning"

internal fun containsInlineReasoningTag(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val normalized = text.lowercase()
    return "<think" in normalized || "<thought" in normalized
}

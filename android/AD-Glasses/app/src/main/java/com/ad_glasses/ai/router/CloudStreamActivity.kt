package com.ad_glasses.ai.router

/**
 * Provider-neutral streaming activity used by the wearable watchdog.
 *
 * These events intentionally carry no provider text. Reasoning content may be received by a
 * provider adapter solely to establish liveness, but it is discarded before this signal leaves
 * the transport layer.
 */
enum class CloudStreamActivity {
    /** HTTP request was accepted and response headers are available. */
    HTTP_READY,

    /** At least one non-terminal SSE data event arrived from the provider. */
    PROVIDER_DATA,

    /** Structured/inline reasoning activity was observed; the reasoning text itself is discarded. */
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

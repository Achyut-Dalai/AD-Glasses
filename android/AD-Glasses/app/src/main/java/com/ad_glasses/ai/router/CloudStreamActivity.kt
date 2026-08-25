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

/**
 * Whether streaming should ask a provider to expose structured reasoning metadata solely as a
 * liveness heartbeat. This never changes the turn's requested reasoning depth and the returned
 * reasoning content is still discarded before display, persistence, or TTS.
 *
 * Explicit reasoned turns always opt in. Concise Gemini turns opt in only when the model policy
 * still requires non-zero/dynamic thinking (for example Gemini 3.7 Flash at `low`). Models whose
 * concise policy truly disables thinking do not pay the extra thought-summary traffic.
 */
internal fun shouldRequestReasoningHeartbeat(
    profile: CloudAiProfile,
    mode: CloudGenerationMode,
): Boolean {
    if (mode == CloudGenerationMode.DEFAULT) return false
    if (mode == CloudGenerationMode.REASONED_CONVERSATION) return true
    if (profile.provider != ApiProvider.GOOGLE) return false

    val tuning = CloudModelPolicy.requestTuning(profile, mode)
    return tuning.geminiThinkingLevel != null || (tuning.geminiThinkingBudget ?: 0) > 0
}

/**
 * A concise OpenRouter request may route to a model that unexpectedly spends the shared completion
 * allowance on reasoning before emitting answer text. Retry exactly once with the existing
 * mandatory-reasoning ceiling only after the failed stream proves that this happened. Normal
 * OpenRouter/Groq/Gemini/OpenAI/DeepSeek turns keep their original budgets and never enter here.
 */
internal fun shouldRetryOpenRouterReasoningOnly(
    provider: ApiProvider,
    mode: CloudGenerationMode,
    requestedTokens: Int,
    reasoningSeen: Boolean,
    visibleText: String,
): Boolean =
    provider == ApiProvider.OPENROUTER &&
        mode == CloudGenerationMode.CONCISE_CONVERSATION &&
        requestedTokens < CloudModelPolicy.CONCISE_MANDATORY_REASONING_TOKENS &&
        reasoningSeen &&
        visibleText.isBlank()

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

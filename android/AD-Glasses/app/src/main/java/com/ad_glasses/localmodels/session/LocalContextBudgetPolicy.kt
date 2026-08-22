package com.ad_glasses.localmodels.session

import kotlin.math.max
import kotlin.math.min

internal data class LocalContextBudget(
    val contextTokens: Int,
    val promptTokens: Int,
    val reservedHeadroomTokens: Int,
    val requestedOutputTokens: Int,
    val effectiveOutputTokens: Int,
) {
    val wasOutputClamped: Boolean
        get() = effectiveOutputTokens < requestedOutputTokens
}

/** Conservative output budget for small on-device context windows. */
internal object LocalContextBudgetPolicy {
    private const val MIN_HEADROOM_TOKENS = 64
    private const val MAX_HEADROOM_TOKENS = 512
    private const val MAX_REASONABLE_OUTPUT_TOKENS = 2048
    private const val MIN_USEFUL_OUTPUT_TOKENS = 32

    fun resolve(
        contextTokens: Int,
        promptTokens: Int,
        requestedOutputTokens: Int,
    ): LocalContextBudget {
        require(contextTokens > 0) { "Context size must be positive" }
        require(requestedOutputTokens > 0) { "Requested output size must be positive" }

        val normalizedPromptTokens = promptTokens.coerceAtLeast(0)
        val reservedHeadroom = max(MIN_HEADROOM_TOKENS, contextTokens / 16)
            .coerceAtMost(MAX_HEADROOM_TOKENS)
        val availableOutputTokens = contextTokens - normalizedPromptTokens - reservedHeadroom
        val minimumRequiredOutput = min(requestedOutputTokens, MIN_USEFUL_OUTPUT_TOKENS)

        require(availableOutputTokens >= minimumRequiredOutput) {
            "Prompt is too long for the loaded model's $contextTokens-token context window. " +
                "Shorten the conversation or start a new topic."
        }

        // Keep phone-class local generation bounded. In particular, a 2,048-token Qwen context
        // receives at most 512 output tokens instead of allowing the output to consume the window.
        val reasonableOutputCap = (contextTokens / 4)
            .coerceAtLeast(1)
            .coerceAtMost(MAX_REASONABLE_OUTPUT_TOKENS)
        val effectiveOutputTokens = min(
            requestedOutputTokens,
            min(availableOutputTokens, reasonableOutputCap),
        ).coerceAtLeast(1)

        return LocalContextBudget(
            contextTokens = contextTokens,
            promptTokens = normalizedPromptTokens,
            reservedHeadroomTokens = reservedHeadroom,
            requestedOutputTokens = requestedOutputTokens,
            effectiveOutputTokens = effectiveOutputTokens,
        )
    }
}

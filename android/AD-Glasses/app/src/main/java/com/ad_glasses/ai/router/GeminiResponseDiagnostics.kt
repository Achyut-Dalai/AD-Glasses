package com.ad_glasses.ai.router

/**
 * Non-content Gemini completion metadata. This is safe to log because it contains only provider
 * finish state and token counts, never user text, final answer text, or hidden reasoning text.
 */
internal data class GeminiResponseDiagnostics(
    val finishReason: String? = null,
    val promptTokens: Int? = null,
    val candidateTokens: Int? = null,
    val thoughtTokens: Int? = null,
    val totalTokens: Int? = null,
) {
    fun hasSignal(): Boolean =
        !finishReason.isNullOrBlank() ||
            promptTokens != null ||
            candidateTokens != null ||
            thoughtTokens != null ||
            totalTokens != null

    fun merge(newer: GeminiResponseDiagnostics): GeminiResponseDiagnostics = GeminiResponseDiagnostics(
        finishReason = newer.finishReason ?: finishReason,
        promptTokens = newer.promptTokens ?: promptTokens,
        candidateTokens = newer.candidateTokens ?: candidateTokens,
        thoughtTokens = newer.thoughtTokens ?: thoughtTokens,
        totalTokens = newer.totalTokens ?: totalTokens,
    )
}

/** Provider-facing detail used in logs/errors without exposing model reasoning or response content. */
internal fun geminiNoVisibleAnswerDetail(
    model: String,
    diagnostics: GeminiResponseDiagnostics,
): String = buildString {
    append("Gemini model ")
    append(model.ifBlank { "unknown" })
    append(" returned no visible answer")
    diagnostics.finishReason?.takeIf { it.isNotBlank() }?.let {
        append("; finishReason=")
        append(it)
    }
    diagnostics.candidateTokens?.let {
        append("; candidateTokens=")
        append(it)
    }
    diagnostics.thoughtTokens?.let {
        append("; thoughtTokens=")
        append(it)
    }
    diagnostics.totalTokens?.let {
        append("; totalTokens=")
        append(it)
    }
}

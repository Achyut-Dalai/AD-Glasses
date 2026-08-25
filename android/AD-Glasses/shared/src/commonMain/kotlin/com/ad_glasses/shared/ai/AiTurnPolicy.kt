package com.ad_glasses.shared.ai

/**
 * Cross-platform product intent for one AI turn.
 *
 * This is deliberately provider-agnostic. Android/iOS transports map the intent onto each
 * provider's supported reasoning controls rather than inferring behavior from a token ceiling.
 */
enum class AiReasoningMode {
    /** Fast, cheap, final-answer-only behavior for normal conversation, voice and Lens Q&A. */
    CONCISE,

    /** Extra model work is explicitly requested because the task benefits from deeper reasoning. */
    REASONED,
}

/** How much source-image detail should survive preprocessing before a cloud vision request. */
enum class AiVisionDetail {
    /** Scene understanding, object identification and ordinary visual Q&A. */
    STANDARD,

    /** Reading text, code, documents, labels, screens or other small visual details. */
    TEXT_DETAIL,
}

/**
 * Pure decision policy that can be reused by Android and the existing KMP iOS host.
 *
 * Heavy reasoning is opt-in: ordinary comparisons such as "Java vs Python" stay concise. We only
 * select [AiReasoningMode.REASONED] for explicit requests to spend more effort. Features may also
 * force a mode directly later instead of relying on prompt wording.
 */
object AiTurnPolicy {
    fun reasoningMode(prompt: String, forceReasoning: Boolean = false): AiReasoningMode {
        if (forceReasoning) return AiReasoningMode.REASONED
        val normalized = prompt.lowercase().replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return AiReasoningMode.CONCISE
        return if (REASONING_CUES.any(normalized::contains)) {
            AiReasoningMode.REASONED
        } else {
            AiReasoningMode.CONCISE
        }
    }

    fun visionDetail(prompt: String): AiVisionDetail {
        val normalized = prompt.lowercase().replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return AiVisionDetail.STANDARD
        return if (VISION_TEXT_CUES.any(normalized::contains)) {
            AiVisionDetail.TEXT_DETAIL
        } else {
            AiVisionDetail.STANDARD
        }
    }

    private val REASONING_CUES = listOf(
        "think carefully",
        "think deeply",
        "reason carefully",
        "reason through",
        "reason step by step",
        "analyze deeply",
        "analyse deeply",
        "deep analysis",
        "work through this carefully",
        "work this out carefully",
        "consider all the tradeoffs",
        "consider all tradeoffs",
    )

    private val VISION_TEXT_CUES = listOf(
        "read the text",
        "read this",
        "read all",
        "what does this say",
        "what does it say",
        "extract text",
        "transcribe",
        "ocr",
        "document",
        "receipt",
        "invoice",
        "menu",
        "sign",
        "label",
        "serial number",
        "license plate",
        "licence plate",
        "screen",
        "code",
        "small text",
        "tiny text",
    )
}

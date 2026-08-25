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

/** How much user-visible text the feature actually asks the model to return. */
enum class AiResponseMode {
    /** Normal AD conversation: <=50 words / <=3 short sentences. */
    CONCISE,

    /** Explicitly copy/transcribe the visible text instead of summarizing it. */
    TEXT_EXTRACTION,
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
 *
 * Output size is independent from reasoning. A Lens OCR request may need lots of visible text while
 * still requiring no hidden reasoning; a difficult reasoning request may spend more internal work
 * while still returning AD's normal short final answer.
 */
object AiTurnPolicy {
    fun reasoningMode(prompt: String, forceReasoning: Boolean = false): AiReasoningMode {
        if (forceReasoning) return AiReasoningMode.REASONED
        val normalized = normalize(prompt)
        if (normalized.isBlank()) return AiReasoningMode.CONCISE
        return if (REASONING_CUES.any(normalized::contains)) {
            AiReasoningMode.REASONED
        } else {
            AiReasoningMode.CONCISE
        }
    }

    fun responseMode(prompt: String, hasImage: Boolean = false): AiResponseMode {
        if (!hasImage) return AiResponseMode.CONCISE
        val normalized = normalize(prompt)
        if (normalized.isBlank()) return AiResponseMode.CONCISE
        return if (FULL_TEXT_EXTRACTION_CUES.any(normalized::contains)) {
            AiResponseMode.TEXT_EXTRACTION
        } else {
            AiResponseMode.CONCISE
        }
    }

    fun visionDetail(prompt: String): AiVisionDetail {
        val normalized = normalize(prompt)
        if (normalized.isBlank()) return AiVisionDetail.STANDARD
        return if (VISION_TEXT_CUES.any(normalized::contains)) {
            AiVisionDetail.TEXT_DETAIL
        } else {
            AiVisionDetail.STANDARD
        }
    }

    private fun normalize(prompt: String): String =
        prompt.lowercase().replace(Regex("\\s+"), " ").trim()

    private val REASONING_CUES = listOf(
        "think carefully",
        "think deeply",
        "reason carefully",
        "reason through",
        "reason step by step",
        "step by step reasoning",
        "analyze deeply",
        "analyse deeply",
        "deep analysis",
        "work through this carefully",
        "work this out carefully",
        "consider all the tradeoffs",
        "consider all tradeoffs",
        "derive this",
        "prove this",
    )

    private val FULL_TEXT_EXTRACTION_CUES = listOf(
        "read all",
        "read every",
        "extract all text",
        "extract the text",
        "extract text",
        "transcribe all",
        "transcribe this",
        "copy all text",
        "copy the text",
        "give me all the text",
        "give me all text",
        "ocr this",
    )

    private val VISION_TEXT_CUES = listOf(
        "read the text",
        "read this",
        "read all",
        "read every",
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
        "fine detail",
        "fine details",
        "describe every detail",
        "zoom in",
    )
}

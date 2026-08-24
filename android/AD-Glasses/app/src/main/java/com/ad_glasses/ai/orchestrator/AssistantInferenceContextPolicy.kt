package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatRole

/** Keeps Cloud AI context small while durable ChatStore history remains untouched. */
object AssistantInferenceContextPolicy {
    /**
     * Preserve roughly the same worst-case payload as the old 3 x 900-character rule, but spend
     * that budget on as many recent short turns as fit. A short arithmetic/conversational chain
     * therefore does not lose a referenced value merely because it is four messages old.
     */
    const val MAX_PRIOR_CONTEXT_CHARS = 2_700

    // Source-compatible aliases only. Message count no longer controls context behavior.
    @Deprecated("Context is bounded by MAX_PRIOR_CONTEXT_CHARS, not message count.")
    const val MAX_PRIOR_MESSAGES = 3

    @Deprecated("Context no longer expires by time; it is bounded by character budget.")
    const val VOICE_INACTIVITY_TTL_MS = 45_000L

    @Deprecated("Context is bounded by MAX_PRIOR_CONTEXT_CHARS, not message count.")
    const val MAX_VOICE_PRIOR_MESSAGES = MAX_PRIOR_MESSAGES

    @Deprecated("Context is bounded by MAX_PRIOR_CONTEXT_CHARS, not message count.")
    const val MAX_TEXT_PRIOR_MESSAGES = MAX_PRIOR_MESSAGES

    const val MAX_MESSAGE_CHARS = 900
    const val MAX_ARTIFACT_CHARS = 6_000
    const val MAX_GLASS_ARTIFACT_CHARS = 3_000

    /**
     * [history] includes the user message for the turn currently being handled.
     *
     * Select newest prior messages until the fixed character budget is full. This keeps the same
     * upper bound as the previous three-message policy while allowing many short turns to survive.
     * Transient assistant failures may be visible in Chats, but never consume inference context.
     */
    @Suppress("UNUSED_PARAMETER")
    fun priorMessages(
        history: List<ChatMessage>,
        surface: AssistantInputSurface = AssistantInputSurface.GLASSES_VOICE,
        nowMs: Long = System.currentTimeMillis(),
    ): List<ChatMessage> {
        if (history.size <= 1) return emptyList()

        val selectedNewestFirst = mutableListOf<ChatMessage>()
        var usedChars = 0
        history.dropLast(1).asReversed().forEach { message ->
            if (!isInferenceEligible(message)) return@forEach
            val contentChars = message.content.trim().take(MAX_MESSAGE_CHARS).length
            if (contentChars == 0 || usedChars + contentChars > MAX_PRIOR_CONTEXT_CHARS) {
                return@forEach
            }
            selectedNewestFirst += message
            usedChars += contentChars
        }
        return selectedNewestFirst.asReversed()
    }

    fun isTransientAssistantFailure(content: String): Boolean {
        val clean = content.trim()
        return TRANSIENT_ASSISTANT_FAILURES.any { failure -> clean.equals(failure, ignoreCase = true) }
    }

    private fun isInferenceEligible(message: ChatMessage): Boolean =
        message.content.isNotBlank() &&
            !(message.role == ChatRole.ASSISTANT && isTransientAssistantFailure(message.content))

    fun artifactLimit(surface: AssistantInputSurface): Int = when (surface) {
        AssistantInputSurface.GLASSES_VOICE,
        AssistantInputSurface.GLASSES_VISION,
        AssistantInputSurface.PHONE_VOICE -> MAX_GLASS_ARTIFACT_CHARS
        AssistantInputSurface.PHONE_TEXT,
        AssistantInputSurface.AUTOMATION -> MAX_ARTIFACT_CHARS
    }

    private val TRANSIENT_ASSISTANT_FAILURES = setOf(
        "I didn’t get a usable answer. Please try again.",
        "The AI didn’t produce a final answer. Please try again.",
        "The AI returned an invalid response. Please try again.",
        "The AI returned an empty answer. Please try again.",
        "Cloud AI authentication failed. Check the API key.",
        "Cloud AI is rate limited right now. Try again shortly.",
        "Cloud AI is temporarily unavailable. Try again.",
        "I couldn't get an answer. Try again.",
    )
}

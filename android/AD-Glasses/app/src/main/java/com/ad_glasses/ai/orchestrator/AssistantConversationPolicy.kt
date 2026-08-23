package com.ad_glasses.ai.orchestrator

import java.util.Locale

/** Commands that change AD's durable conversation without becoming chat messages themselves. */
internal enum class AssistantConversationCommand {
    START_FRESH,
    FORGET_CURRENT,
}

/** Pure parsing policy for explicit conversation controls. */
internal object AssistantConversationPolicy {
    const val THREAD_TITLE = "AD conversation"

    private val startFreshPhrases = setOf(
        "new topic",
        "start new topic",
        "start a new topic",
        "new conversation",
        "start new conversation",
        "start a new conversation",
    )

    private val forgetPhrases = setOf(
        "forget this conversation",
        "forget the conversation",
        "forget current conversation",
        "forget this chat",
        "forget the chat",
        "forget current chat",
    )

    fun parseCommand(text: String): AssistantConversationCommand? {
        val normalized = text
            .trim()
            .lowercase(Locale.ROOT)
            .replace(NON_WORD, " ")
            .replace(WHITESPACE, " ")
            .trim()

        return when (normalized) {
            in startFreshPhrases -> AssistantConversationCommand.START_FRESH
            in forgetPhrases -> AssistantConversationCommand.FORGET_CURRENT
            else -> null
        }
    }

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val WHITESPACE = Regex("\\s+")
}

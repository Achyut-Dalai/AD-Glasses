package com.fersaiyan.cyanbridge.ai.orchestrator

import com.fersaiyan.cyanbridge.shared.chat.ChatThread
import java.util.Locale

/** Commands that change AD's conversation context without becoming chat messages themselves. */
internal enum class AssistantConversationCommand {
    START_FRESH,
    FORGET_CURRENT,
}

/**
 * Pure policy for ephemeral AD conversations. Keeping parsing and expiry selection free of
 * Android dependencies makes the safety-sensitive rules straightforward to unit test.
 */
internal object AssistantConversationPolicy {
    const val THREAD_TITLE = "AD conversation"
    const val RETENTION_MS = 7L * 24L * 60L * 60L * 1_000L

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

    fun expiredThreadIds(
        threads: List<ChatThread>,
        managedThreadIds: Set<String>,
        nowMs: Long,
        retentionMs: Long = RETENTION_MS,
    ): Set<String> {
        require(retentionMs > 0L) { "retentionMs must be positive" }
        return threads
            .asSequence()
            .filter { it.id in managedThreadIds || it.title == THREAD_TITLE }
            .filter { thread ->
                nowMs >= thread.updatedAt && nowMs - thread.updatedAt >= retentionMs
            }
            .mapTo(linkedSetOf()) { it.id }
    }

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val WHITESPACE = Regex("\\s+")
}

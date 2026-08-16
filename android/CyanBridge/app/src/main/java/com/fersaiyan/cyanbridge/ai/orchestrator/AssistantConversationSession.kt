package com.fersaiyan.cyanbridge.ai.orchestrator

import android.content.Context
import com.fersaiyan.cyanbridge.chat.ChatStore
import com.fersaiyan.cyanbridge.shared.chat.ChatMessage
import com.fersaiyan.cyanbridge.shared.chat.ChatRole

/**
 * Keeps glasses and phone turns in one durable ChatStore thread.
 *
 * A session is intentionally explicit instead of being tied to an Activity lifecycle: the
 * glasses may initiate a turn while the phone UI is not visible, and the phone may later
 * reopen the same thread for rich review/continuation.
 */
class AssistantConversationSession private constructor(
    private val context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun activeThreadId(): String {
        val saved = prefs.getString(KEY_ACTIVE_THREAD_ID, null)
        if (!saved.isNullOrBlank() && ChatStore.getThread(saved) != null) return saved

        val created = ChatStore.createThread(title = "AD conversation")
        prefs.edit().putString(KEY_ACTIVE_THREAD_ID, created.id).apply()
        return created.id
    }

    @Synchronized
    fun startNewConversation(): String {
        val created = ChatStore.createThread(title = "AD conversation")
        prefs.edit().putString(KEY_ACTIVE_THREAD_ID, created.id).apply()
        return created.id
    }

    /**
     * Select an existing durable thread when a compatibility/deep-link route points at it.
     * Invalid or already-deleted thread ids are ignored rather than creating phantom history.
     */
    @Synchronized
    fun selectThread(threadId: String?): Boolean {
        val candidate = threadId?.trim().orEmpty()
        if (candidate.isBlank() || ChatStore.getThread(candidate) == null) return false
        prefs.edit().putString(KEY_ACTIVE_THREAD_ID, candidate).apply()
        return true
    }

    fun messages(): List<ChatMessage> = ChatStore.listMessages(activeThreadId())

    fun addUserTurn(text: String): ChatMessage = ChatStore.addMessage(
        chatId = activeThreadId(),
        role = ChatRole.USER,
        content = text.trim(),
    )

    fun addAssistantTurn(text: String): ChatMessage = ChatStore.addMessage(
        chatId = activeThreadId(),
        role = ChatRole.ASSISTANT,
        content = text.trim(),
    )

    companion object {
        private const val PREFS = "ad_assistant_session"
        private const val KEY_ACTIVE_THREAD_ID = "active_thread_id"

        @Volatile
        private var instance: AssistantConversationSession? = null

        fun get(context: Context): AssistantConversationSession = instance ?: synchronized(this) {
            instance ?: AssistantConversationSession(context.applicationContext).also { instance = it }
        }
    }
}

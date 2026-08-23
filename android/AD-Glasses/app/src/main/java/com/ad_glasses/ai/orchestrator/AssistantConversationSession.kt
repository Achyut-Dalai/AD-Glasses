package com.ad_glasses.ai.orchestrator

import android.content.Context
import com.ad_glasses.chat.ChatStore
import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatThread
import com.ad_glasses.shared.chat.ChatRole

/**
 * Keeps glasses and phone turns in the same durable ChatStore conversation.
 *
 * The active inference context is intentionally short-lived and is handled separately by
 * [AssistantInferenceContextPolicy]. Chat history is not an inference cache: it remains available
 * until the user explicitly starts managing it with Delete, Clear all, or a forget command.
 * Keeping the active thread independent of an Activity lifecycle also lets glasses-originated turns
 * appear on the phone later without creating a second conversation system.
 */
class AssistantConversationSession private constructor(
    private val context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun activeThreadId(): String {
        val saved = prefs.getString(KEY_ACTIVE_THREAD_ID, null)
        if (!saved.isNullOrBlank() && ChatStore.getThread(saved) != null) return saved

        return createAndActivate()
    }

    @Synchronized
    fun startNewConversation(): String = createAndActivate()

    /** List only conversations owned by the AD assistant, newest first. */
    @Synchronized
    fun conversations(): List<ChatThread> {
        val managed = managedThreadIds()
        val conversations = ChatStore.listThreads()
            .filter { it.id in managed || it.title == AssistantConversationPolicy.THREAD_TITLE }
            .sortedByDescending { it.updatedAt }
        val legacyIds = conversations
            .asSequence()
            .filter { it.title == AssistantConversationPolicy.THREAD_TITLE }
            .mapTo(linkedSetOf()) { it.id }
        if (!managed.containsAll(legacyIds)) saveManagedThreadIds(managed + legacyIds)
        return conversations
    }

    /** Rename an AD-owned conversation without allowing unrelated ChatStore data to be claimed. */
    @Synchronized
    fun renameConversation(threadId: String, title: String): Boolean {
        val thread = ChatStore.getThread(threadId) ?: return false
        val managed = managedThreadIds()
        if (thread.id !in managed && thread.title != AssistantConversationPolicy.THREAD_TITLE) return false
        trackManagedThread(thread.id)
        return ChatStore.updateThreadTitle(thread.id, title)
    }

    /** Delete one AD-owned conversation and return the conversation that should remain active. */
    @Synchronized
    fun deleteConversation(threadId: String): String {
        val thread = ChatStore.getThread(threadId)
        val managed = managedThreadIds()
        val owned = thread != null &&
            (thread.id in managed || thread.title == AssistantConversationPolicy.THREAD_TITLE)
        if (!owned) return activeThreadId()

        val activeBeforeDelete = prefs.getString(KEY_ACTIVE_THREAD_ID, null)
            ?.takeIf { ChatStore.getThread(it) != null }

        ChatStore.deleteThread(threadId)
        saveManagedThreadIds(managed - threadId)

        if (activeBeforeDelete != null && activeBeforeDelete != threadId) {
            return activeBeforeDelete
        }

        prefs.edit().remove(KEY_ACTIVE_THREAD_ID).apply()
        val next = conversations().firstOrNull()?.id
        if (next != null) {
            prefs.edit().putString(KEY_ACTIVE_THREAD_ID, next).apply()
            return next
        }
        return createAndActivate()
    }

    /** Delete the current AD thread immediately, then activate a clean empty one. */
    @Synchronized
    fun forgetCurrentConversation(): String {
        val current = prefs.getString(KEY_ACTIVE_THREAD_ID, null)
            ?.takeIf { ChatStore.getThread(it) != null }
        if (current != null) {
            ChatStore.deleteThread(current)
            saveManagedThreadIds(managedThreadIds() - current)
        }
        prefs.edit().remove(KEY_ACTIVE_THREAD_ID).apply()
        return createAndActivate()
    }

    /** Delete every AD-owned conversation without touching unrelated ChatStore data. */
    @Synchronized
    fun clearAllConversations(): Int {
        val threads = ChatStore.listThreads()
        val managed = managedThreadIds()
        val adThreadIds = threads
            .asSequence()
            .filter { it.id in managed || it.title == AssistantConversationPolicy.THREAD_TITLE }
            .mapTo(linkedSetOf()) { it.id }

        adThreadIds.forEach(ChatStore::deleteThread)
        prefs.edit()
            .remove(KEY_ACTIVE_THREAD_ID)
            .remove(KEY_MANAGED_THREAD_IDS)
            .apply()
        return adThreadIds.size
    }

    /**
     * Select an existing thread when a compatibility/deep-link route points at it.
     * Invalid or already-deleted thread ids are ignored rather than creating phantom history.
     */
    @Synchronized
    fun selectThread(threadId: String?): Boolean {
        val candidate = threadId?.trim().orEmpty()
        if (candidate.isBlank() || ChatStore.getThread(candidate) == null) return false
        ChatStore.touchThread(candidate)
        trackManagedThread(candidate)
        prefs.edit().putString(KEY_ACTIVE_THREAD_ID, candidate).apply()
        return true
    }

    @Synchronized
    fun messages(threadId: String = activeThreadId()): List<ChatMessage> =
        if (ChatStore.getThread(threadId) != null) ChatStore.listMessages(threadId) else emptyList()

    /** Atomically captures the active thread and persists the user turn to that exact thread. */
    @Synchronized
    fun addUserTurn(text: String): ChatMessage {
        val threadId = activeThreadId()
        return requireNotNull(addUserTurn(threadId, text)) { "The active conversation was cleared" }
    }

    /** Add a queued turn only to the conversation that accepted it; never recreate a deleted one. */
    @Synchronized
    fun addUserTurn(threadId: String, text: String): ChatMessage? {
        if (ChatStore.getThread(threadId) == null) return null
        return ChatStore.addMessage(
            chatId = threadId,
            role = ChatRole.USER,
            content = text.trim(),
        )
    }

    /**
     * Persist a result only to the thread captured when its user turn began. If the user said
     * "forget this conversation" or deleted that thread while inference was running, the late
     * result is deliberately not written into a new conversation.
     */
    @Synchronized
    fun addAssistantTurn(threadId: String, text: String): ChatMessage? {
        if (ChatStore.getThread(threadId) == null) return null
        return ChatStore.addMessage(
            chatId = threadId,
            role = ChatRole.ASSISTANT,
            content = text.trim(),
        )
    }

    private fun createAndActivate(nowMs: Long = System.currentTimeMillis()): String {
        val created = ChatStore.createThread(
            title = "New chat",
            nowMs = nowMs,
        )
        trackManagedThread(created.id)
        prefs.edit().putString(KEY_ACTIVE_THREAD_ID, created.id).apply()
        return created.id
    }

    private fun managedThreadIds(): Set<String> =
        prefs.getStringSet(KEY_MANAGED_THREAD_IDS, emptySet()).orEmpty().toSet()

    private fun trackManagedThread(threadId: String) {
        saveManagedThreadIds(managedThreadIds() + threadId)
    }

    private fun saveManagedThreadIds(ids: Set<String>) {
        // SharedPreferences may retain the provided mutable Set instance; always pass a copy.
        prefs.edit().putStringSet(KEY_MANAGED_THREAD_IDS, ids.toSet()).apply()
    }

    companion object {
        private const val PREFS = "ad_assistant_session"
        private const val KEY_ACTIVE_THREAD_ID = "active_thread_id"
        private const val KEY_MANAGED_THREAD_IDS = "managed_thread_ids"

        @Volatile
        private var instance: AssistantConversationSession? = null

        fun get(context: Context): AssistantConversationSession = instance ?: synchronized(this) {
            instance ?: AssistantConversationSession(context.applicationContext).also { instance = it }
        }
    }
}

package com.ad_glasses.chat

import com.ad_glasses.shared.chat.ChatRepository
import com.ad_glasses.shared.chat.ChatRole
import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android persistence adapter for the portable chat contract. The existing
 * ChatStore remains the source of truth while the Compose thread consumes the
 * portable presentation contract.
 */
class ChatStoreRepository : ChatRepository {
    override suspend fun listThreads(): List<ChatThread> = onStore { ChatStore.listThreads() }

    override suspend fun listNonEmptyThreads(): List<ChatThread> = onStore { ChatStore.listNonEmptyThreads() }

    override suspend fun getThread(chatId: String): ChatThread? = onStore { ChatStore.getThread(chatId) }

    override suspend fun createThread(title: String?, nowMs: Long): ChatThread = onStore {
        ChatStore.createThread(title = title, nowMs = nowMs)
    }

    override suspend fun listMessages(chatId: String): List<ChatMessage> = onStore {
        ChatStore.listMessages(chatId)
    }

    override suspend fun addMessage(
        chatId: String,
        role: ChatRole,
        content: String,
        nowMs: Long,
    ): ChatMessage = onStore {
        ChatStore.addMessage(chatId, role, content, nowMs)
    }

    override suspend fun updateThreadTitle(chatId: String, title: String, nowMs: Long): Boolean = onStore {
        ChatStore.updateThreadTitle(chatId, title, nowMs)
    }

    override suspend fun deleteThread(chatId: String) {
        onStore { ChatStore.deleteThread(chatId) }
    }

    override suspend fun clearAll() {
        onStore { ChatStore.clearAll() }
    }

    private suspend fun <T> onStore(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}

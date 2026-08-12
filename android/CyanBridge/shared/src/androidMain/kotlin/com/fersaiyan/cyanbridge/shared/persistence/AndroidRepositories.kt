package com.achyut.adglasses.shared.persistence

import com.achyut.adglasses.shared.platform.PlatformLogger

/**
 * Android implementation of [ChatRepository] that delegates to Room DAOs.
 * The actual Room DAO is injected from the Android app module.
 */
class AndroidChatRepository(
    private val roomDao: Any,  // Room DAO injected from app module
) : ChatRepository {

    override suspend fun getAllChats(): List<ChatEntity> {
        // TODO: Delegate to Room DAO
        PlatformLogger.d(TAG, "getAllChats - delegating to Room")
        return emptyList()
    }

    override suspend fun getChat(id: String): ChatEntity? = null

    override suspend fun insertChat(chat: ChatEntity) {
        PlatformLogger.d(TAG, "insertChat: ${chat.id}")
    }

    override suspend fun updateChat(chat: ChatEntity) {}

    override suspend fun deleteChat(id: String) {}

    override suspend fun getMessages(chatId: String): List<ChatMessageEntity> = emptyList()

    override suspend fun insertMessage(message: ChatMessageEntity) {}

    override suspend fun deleteMessage(id: String) {}

    override suspend fun deleteMessagesForChat(chatId: String) {}

    companion object {
        private const val TAG = "AndroidChatRepo"
    }
}

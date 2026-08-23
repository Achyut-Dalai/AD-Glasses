package com.ad_glasses.chat

import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatRole
import com.ad_glasses.shared.chat.ChatThread
import com.ad_glasses.data.local.entity.Chat as ChatEntity
import com.ad_glasses.data.local.entity.Message as MessageEntity
import com.ad_glasses.data.repository.ADGlassesRepository
import com.ad_glasses.ui.MyApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * In-process chat store used by Activities and Compose for quick synchronous access.
 *
 * The in-memory cache is authoritative for the active process. Room is a durability layer when
 * available. A damaged or incompatible on-device database must never make Chats or Privacy crash;
 * if a Room operation fails, persistence is disabled for the rest of this process and the cache
 * continues to operate normally. A later app process can try Room again after migration/repair.
 */
object ChatStore {

    private val lock = Any()

    @Volatile
    private var loaded = false

    @Volatile
    private var persistenceAvailable = true

    private val threads = mutableListOf<ChatThread>()
    private val messagesByChatId = linkedMapOf<String, MutableList<ChatMessage>>()

    private fun repositoryOrNull(): ADGlassesRepository? {
        if (!persistenceAvailable) return null
        // Local JVM unit tests do not initialize MyApplication/Room.
        return runCatching { MyApplication.repository }
            .onFailure { persistenceAvailable = false }
            .getOrNull()
    }

    /**
     * Execute one Room operation behind a process-safe failure boundary.
     *
     * Room can throw while opening the database, validating a migrated schema, creating an index,
     * or executing a DAO method. Those are persistence failures, not reasons to terminate the UI.
     */
    private fun <T> withRepository(block: suspend (ADGlassesRepository) -> T): T? {
        val repository = repositoryOrNull() ?: return null
        return runCatching {
            runBlocking(Dispatchers.IO) { block(repository) }
        }.onFailure {
            persistenceAvailable = false
        }.getOrNull()
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return

            val chats = withRepository { it.getAllChatsOnce() }
            if (chats != null) {
                threads.clear()
                threads.addAll(
                    chats.map {
                        ChatThread(
                            id = it.id,
                            title = it.title,
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt,
                        )
                    }
                )
                messagesByChatId.clear()
            }

            loaded = true
        }
    }

    private fun ensureMessagesLoaded(chatId: String) {
        ensureLoaded()
        if (messagesByChatId.containsKey(chatId)) return

        synchronized(lock) {
            if (messagesByChatId.containsKey(chatId)) return

            val msgs = withRepository { it.getMessagesForChatOnce(chatId) }
            if (msgs != null) {
                messagesByChatId[chatId] = msgs.mapNotNull { entity ->
                    // Be tolerant to historical role casing/whitespace mismatches.
                    val role = runCatching { ChatRole.valueOf(entity.role.trim().uppercase()) }
                        .getOrNull()
                        ?: return@mapNotNull null
                    ChatMessage(
                        id = entity.id,
                        chatId = entity.chatId,
                        role = role,
                        content = entity.content,
                        createdAt = entity.createdAt,
                    )
                }.toMutableList()
            } else {
                messagesByChatId.putIfAbsent(chatId, mutableListOf())
            }
        }
    }

    @Synchronized
    fun listThreads(): List<ChatThread> {
        ensureLoaded()
        return threads.toList().sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun listNonEmptyThreads(): List<ChatThread> {
        ensureLoaded()
        return threads
            .asSequence()
            .filter { listMessages(it.id).isNotEmpty() }
            .sortedByDescending { it.updatedAt }
            .toList()
    }

    @Synchronized
    fun getThread(chatId: String): ChatThread? {
        ensureLoaded()
        return threads.firstOrNull { it.id == chatId }
    }

    @Synchronized
    fun createThread(title: String? = null, nowMs: Long = System.currentTimeMillis()): ChatThread {
        ensureLoaded()

        val id = UUID.randomUUID().toString()
        val thread = ChatThread(
            id = id,
            title = title?.takeIf { it.isNotBlank() } ?: "New chat",
            createdAt = nowMs,
            updatedAt = nowMs,
        )

        threads.add(thread)
        messagesByChatId[id] = mutableListOf()

        withRepository<Unit> { repository ->
            repository.insertChat(
                ChatEntity(
                    id = thread.id,
                    title = thread.title,
                    createdAt = thread.createdAt,
                    updatedAt = thread.updatedAt,
                )
            )
        }

        return thread
    }

    @Synchronized
    fun listMessages(chatId: String): List<ChatMessage> {
        ensureMessagesLoaded(chatId)
        return messagesByChatId[chatId]?.toList().orEmpty()
    }

    @Synchronized
    fun addMessage(
        chatId: String,
        role: ChatRole,
        content: String,
        nowMs: Long = System.currentTimeMillis(),
    ): ChatMessage {
        require(content.isNotBlank()) { "message content cannot be blank" }
        ensureMessagesLoaded(chatId)

        val threadIndex = threads.indexOfFirst { it.id == chatId }
        if (threadIndex < 0) error("Unknown chatId=$chatId")
        val thread = threads[threadIndex]

        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            role = role,
            content = content,
            createdAt = nowMs,
        )

        val list = messagesByChatId.getOrPut(chatId) { mutableListOf() }
        list.add(message)

        val updatedThread = thread.copy(
            title = if (thread.title == "New chat" && role == ChatRole.USER) {
                content.trim().take(32).ifBlank { "New chat" }
            } else {
                thread.title
            },
            updatedAt = nowMs,
        )
        threads[threadIndex] = updatedThread

        withRepository<Unit> { repository ->
            repository.insertMessage(
                MessageEntity(
                    id = message.id,
                    chatId = message.chatId,
                    role = message.role.name,
                    content = message.content,
                    createdAt = message.createdAt,
                )
            )
            repository.insertChat(
                ChatEntity(
                    id = updatedThread.id,
                    title = updatedThread.title,
                    createdAt = updatedThread.createdAt,
                    updatedAt = updatedThread.updatedAt,
                )
            )
        }

        return message
    }

    @Synchronized
    fun updateThreadTitle(chatId: String, title: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val normalized = title.trim()
        if (normalized.isBlank()) return false
        ensureLoaded()

        val threadIndex = threads.indexOfFirst { it.id == chatId }
        if (threadIndex < 0) return false
        val thread = threads[threadIndex]
        if (thread.title == normalized) return false

        val updatedThread = thread.copy(
            title = normalized,
            updatedAt = maxOf(thread.updatedAt, nowMs),
        )
        threads[threadIndex] = updatedThread

        withRepository<Unit> { repository ->
            repository.insertChat(
                ChatEntity(
                    id = updatedThread.id,
                    title = updatedThread.title,
                    createdAt = updatedThread.createdAt,
                    updatedAt = updatedThread.updatedAt,
                )
            )
        }
        return true
    }

    /** Mark an existing thread as recently used without adding a synthetic message. */
    @Synchronized
    fun touchThread(chatId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        ensureLoaded()

        val threadIndex = threads.indexOfFirst { it.id == chatId }
        if (threadIndex < 0) return false
        val thread = threads[threadIndex]
        if (nowMs <= thread.updatedAt) return false

        val updatedThread = thread.copy(updatedAt = nowMs)
        threads[threadIndex] = updatedThread

        withRepository<Unit> { repository ->
            repository.insertChat(
                ChatEntity(
                    id = updatedThread.id,
                    title = updatedThread.title,
                    createdAt = updatedThread.createdAt,
                    updatedAt = updatedThread.updatedAt,
                )
            )
        }
        return true
    }

    @Synchronized
    fun deleteThread(chatId: String) {
        ensureLoaded()
        withRepository<Unit> { repository ->
            repository.deleteMessagesForChat(chatId)
            repository.deleteChatById(chatId)
        }
        threads.removeAll { it.id == chatId }
        messagesByChatId.remove(chatId)
    }

    @Synchronized
    fun clearAll() {
        withRepository<Unit> { repository ->
            // Messages first, then chats.
            repository.deleteAllMessages()
            repository.deleteAllChats()
        }

        threads.clear()
        messagesByChatId.clear()
        loaded = true
    }
}

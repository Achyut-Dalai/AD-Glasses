package com.achyut.adglasses.chat

import com.achyut.adglasses.shared.chat.ChatMessage
import com.achyut.adglasses.shared.chat.ChatRole
import com.achyut.adglasses.shared.chat.ChatThread
import com.achyut.adglasses.data.local.entity.Chat as ChatEntity
import com.achyut.adglasses.data.local.entity.Message as MessageEntity
import com.achyut.adglasses.data.repository.CyanBridgeRepository
import com.achyut.adglasses.ui.MyApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * In-process chat store used by Activities for quick synchronous access.
 *
 * This keeps a small memory cache (`threads` + `messagesByChatId`) and mirrors writes to
 * Room through [MyApplication.repository].
 *
 * Why this shape:
 * - Existing UI code expects simple blocking reads.
 * - MVP scope favors deterministic behavior over reactive streams in every screen.
 *
 * Tradeoff:
 * - We intentionally use `runBlocking(Dispatchers.IO)` in a few places to keep call sites
 *   synchronous and avoid rewriting the UI layer around coroutines.
 */
object ChatStore {

    private val lock = Any()

    @Volatile
    private var loaded = false

    private val threads = mutableListOf<ChatThread>()
    private val messagesByChatId = linkedMapOf<String, MutableList<ChatMessage>>()

    private fun repositoryOrNull(): CyanBridgeRepository? {
        // Local JVM unit tests do not initialize MyApplication/Room.
        return runCatching { MyApplication.repository }.getOrNull()
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return

            val repository = repositoryOrNull()

            // First access hydrates in-memory thread cache from Room when available.
            if (repository != null) {
                runBlocking(Dispatchers.IO) {
                    val chats = repository.getAllChatsOnce()
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
            }

            loaded = true
        }
    }

    private fun ensureMessagesLoaded(chatId: String) {
        ensureLoaded()
        if (messagesByChatId.containsKey(chatId)) return

        synchronized(lock) {
            if (messagesByChatId.containsKey(chatId)) return

            val repository = repositoryOrNull()
            if (repository != null) {
                runBlocking(Dispatchers.IO) {
                    val msgs = repository.getMessagesForChatOnce(chatId)
                    messagesByChatId[chatId] = msgs.mapNotNull { e ->
                        // Be tolerant to historical role casing/whitespace mismatches.
                        val role = runCatching { ChatRole.valueOf(e.role.trim().uppercase()) }.getOrNull() ?: return@mapNotNull null
                        ChatMessage(
                            id = e.id,
                            chatId = e.chatId,
                            role = role,
                            content = e.content,
                            createdAt = e.createdAt,
                        )
                    }.toMutableList()
                }
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
        val t = ChatThread(
            id = id,
            title = title?.takeIf { it.isNotBlank() } ?: "New chat",
            createdAt = nowMs,
            updatedAt = nowMs,
        )

        threads.add(t)
        messagesByChatId[id] = mutableListOf()

        val repository = repositoryOrNull()
        if (repository != null) {
            runBlocking(Dispatchers.IO) {
                repository.insertChat(
                    ChatEntity(
                        id = t.id,
                        title = t.title,
                        createdAt = t.createdAt,
                        updatedAt = t.updatedAt,
                    )
                )
            }
        }

        return t
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

        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            role = role,
            content = content,
            createdAt = nowMs,
        )

        val list = messagesByChatId.getOrPut(chatId) { mutableListOf() }
        list.add(msg)

        val updatedThread = thread.copy(
            title = if (thread.title == "New chat" && role == ChatRole.USER) {
                content.trim().take(32).ifBlank { "New chat" }
            } else {
                thread.title
            },
            updatedAt = nowMs,
        )
        threads[threadIndex] = updatedThread

        val repository = repositoryOrNull()
        if (repository != null) {
            runBlocking(Dispatchers.IO) {
                // Upsert message
                repository.insertMessage(
                    MessageEntity(
                        id = msg.id,
                        chatId = msg.chatId,
                        role = msg.role.name,
                        content = msg.content,
                        createdAt = msg.createdAt,
                    )
                )

                // Update chat title + updatedAt
                repository.insertChat(
                    ChatEntity(
                        id = updatedThread.id,
                        title = updatedThread.title,
                        createdAt = updatedThread.createdAt,
                        updatedAt = updatedThread.updatedAt,
                    )
                )
            }
        }

        return msg
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

        val repository = repositoryOrNull()
        if (repository != null) {
            runBlocking(Dispatchers.IO) {
                repository.insertChat(
                    ChatEntity(
                        id = updatedThread.id,
                        title = updatedThread.title,
                        createdAt = updatedThread.createdAt,
                        updatedAt = updatedThread.updatedAt,
                    )
                )
            }
        }
        return true
    }

    @Synchronized
    fun deleteThread(chatId: String) {
        ensureLoaded()
        val repository = repositoryOrNull()
        if (repository != null) {
            runBlocking(Dispatchers.IO) {
                repository.deleteMessagesForChat(chatId)
                repository.deleteChatById(chatId)
            }
        }
        threads.removeAll { it.id == chatId }
        messagesByChatId.remove(chatId)
    }

    @Synchronized
    fun clearAll() {
        val repository = repositoryOrNull()
        if (repository != null) {
            // Clear DB first, then local cache.
            runBlocking(Dispatchers.IO) {
                // Messages first, then chats.
                repository.deleteAllMessages()
                repository.deleteAllChats()
            }
        }

        threads.clear()
        messagesByChatId.clear()
        loaded = true
    }
}

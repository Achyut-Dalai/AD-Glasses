package com.ad_glasses.shared.persistence

/**
 * Cross-platform persistence abstraction for ADGlasses data.
 * Android uses Room; iOS uses SQLite or file-based storage.
 */

// ── Chat persistence ──

data class ChatEntity(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int = 0,
)

data class ChatMessageEntity(
    val id: String,
    val chatId: String,
    val role: String,  // "user", "assistant", "system"
    val content: String,
    val timestamp: Long,
    val metadata: String? = null,  // JSON-encoded metadata
)

interface ChatRepository {
    suspend fun getAllChats(): List<ChatEntity>
    suspend fun getChat(id: String): ChatEntity?
    suspend fun insertChat(chat: ChatEntity)
    suspend fun updateChat(chat: ChatEntity)
    suspend fun deleteChat(id: String)
    suspend fun getMessages(chatId: String): List<ChatMessageEntity>
    suspend fun insertMessage(message: ChatMessageEntity)
    suspend fun deleteMessage(id: String)
    suspend fun deleteMessagesForChat(chatId: String)
}

// ── Notes persistence ──

data class NoteEntity(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val source: String? = null,  // "meeting", "manual", "agent"
)

interface NotesRepository {
    suspend fun getAllNotes(): List<NoteEntity>
    suspend fun getNote(id: String): NoteEntity?
    suspend fun insertNote(note: NoteEntity)
    suspend fun updateNote(note: NoteEntity)
    suspend fun deleteNote(id: String)
    suspend fun searchNotes(query: String): List<NoteEntity>
}

// ── Device profiles ──

data class DeviceProfileEntity(
    val macAddress: String,
    val advertisedName: String?,
    val detectedClass: String,
    val selectedClass: String,
    val userOverridden: Boolean,
    val lastConnectedAt: Long,
)

interface DeviceProfileRepository {
    suspend fun getAll(): List<DeviceProfileEntity>
    suspend fun get(macAddress: String): DeviceProfileEntity?
    suspend fun upsert(profile: DeviceProfileEntity)
    suspend fun delete(macAddress: String)
}

// ── Memory vault items ──

data class MemoryVaultItemEntity(
    val id: String,
    val content: String,
    val sourceType: String,
    val createdAt: Long,
    val updatedAt: Long,
    val embedding: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryVaultItemEntity) return false
        return id == other.id && content == other.content
    }

    override fun hashCode(): Int = id.hashCode() * 31 + content.hashCode()
}

interface MemoryVaultRepository {
    suspend fun getAll(): List<MemoryVaultItemEntity>
    suspend fun get(id: String): MemoryVaultItemEntity?
    suspend fun insert(item: MemoryVaultItemEntity)
    suspend fun update(item: MemoryVaultItemEntity)
    suspend fun delete(id: String)
    suspend fun search(query: String, limit: Int = 10): List<MemoryVaultItemEntity>
}

// ── Media records ──

data class MediaRecordEntity(
    val id: String,
    val filename: String,
    val mimeType: String,
    val filePath: String,
    val downloadedAt: Long,
    val fileSize: Long,
    val source: String = "glasses",
)

interface MediaRecordRepository {
    suspend fun getAll(): List<MediaRecordEntity>
    suspend fun get(id: String): MediaRecordEntity?
    suspend fun getByFilename(filename: String): MediaRecordEntity?
    suspend fun insert(record: MediaRecordEntity)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}

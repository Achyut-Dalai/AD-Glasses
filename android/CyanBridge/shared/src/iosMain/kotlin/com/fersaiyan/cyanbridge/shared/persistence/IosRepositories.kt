package com.fersaiyan.cyanbridge.shared.persistence

import com.fersaiyan.cyanbridge.shared.platform.PlatformLogger
import com.fersaiyan.cyanbridge.shared.platform.PlatformPreferences
import com.fersaiyan.cyanbridge.shared.platform.createPlatformPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * iOS implementation of persistence repositories using PlatformPreferences
 * (NSUserDefaults) for simple storage.
 *
 * For production, this should use SQLDelight or a proper SQLite wrapper.
 */

@Serializable
private data class ChatJson(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int = 0,
)

@Serializable
private data class MessageJson(
    val id: String,
    val chatId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val metadata: String? = null,
)

class IosChatRepository : ChatRepository {
    private val prefs = createPlatformPreferences("cyanbridge_chats")
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getAllChats(): List<ChatEntity> {
        val data = prefs.getString("chats_json", "[]")
        return try {
            json.decodeFromString<List<ChatJson>>(data).map { it.toEntity() }
        } catch (e: Exception) {
            PlatformLogger.e(TAG, "Failed to parse chats", e)
            emptyList()
        }
    }

    override suspend fun getChat(id: String): ChatEntity? =
        getAllChats().find { it.id == id }

    override suspend fun insertChat(chat: ChatEntity) {
        val chats = getAllChats().toMutableList()
        chats.removeAll { it.id == chat.id }
        chats.add(chat)
        saveChats(chats)
    }

    override suspend fun updateChat(chat: ChatEntity) = insertChat(chat)

    override suspend fun deleteChat(id: String) {
        val chats = getAllChats().toMutableList()
        chats.removeAll { it.id == id }
        saveChats(chats)
    }

    override suspend fun getMessages(chatId: String): List<ChatMessageEntity> {
        val data = prefs.getString("messages_$chatId", "[]")
        return try {
            json.decodeFromString<List<MessageJson>>(data).map { it.toEntity() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun insertMessage(message: ChatMessageEntity) {
        val messages = getMessages(message.chatId).toMutableList()
        messages.removeAll { it.id == message.id }
        messages.add(message)
        prefs.putString("messages_${message.chatId}", json.encodeToString(messages.map { it.toJson() }))
    }

    override suspend fun deleteMessage(id: String) {
        // Find and remove from the appropriate chat
        val allChats = getAllChats()
        for (chat in allChats) {
            val messages = getMessages(chat.id).toMutableList()
            if (messages.removeAll { it.id == id }) {
                prefs.putString("messages_${chat.id}", json.encodeToString(messages.map { it.toJson() }))
                break
            }
        }
    }

    override suspend fun deleteMessagesForChat(chatId: String) {
        prefs.putString("messages_$chatId", "[]")
    }

    private fun saveChats(chats: List<ChatEntity>) {
        prefs.putString("chats_json", json.encodeToString(chats.map { it.toJson() }))
    }

    private fun ChatJson.toEntity() = ChatEntity(id, title, createdAt, updatedAt, messageCount)
    private fun ChatEntity.toJson() = ChatJson(id, title, createdAt, updatedAt, messageCount)
    private fun MessageJson.toEntity() = ChatMessageEntity(id, chatId, role, content, timestamp, metadata)
    private fun ChatMessageEntity.toJson() = MessageJson(id, chatId, role, content, timestamp, metadata)

    companion object {
        private const val TAG = "IosChatRepo"
    }
}

class IosNotesRepository : NotesRepository {
    private val prefs = createPlatformPreferences("cyanbridge_notes")
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class NoteJson(
        val id: String,
        val title: String,
        val content: String,
        val createdAt: Long,
        val updatedAt: Long,
        val source: String? = null,
    )

    override suspend fun getAllNotes(): List<NoteEntity> {
        val data = prefs.getString("notes_json", "[]")
        return try {
            json.decodeFromString<List<NoteJson>>(data).map {
                NoteEntity(it.id, it.title, it.content, it.createdAt, it.updatedAt, it.source)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getNote(id: String): NoteEntity? =
        getAllNotes().find { it.id == id }

    override suspend fun insertNote(note: NoteEntity) {
        val notes = getAllNotes().toMutableList()
        notes.removeAll { it.id == note.id }
        notes.add(note)
        prefs.putString("notes_json", json.encodeToString(notes.map {
            NoteJson(it.id, it.title, it.content, it.createdAt, it.updatedAt, it.source)
        }))
    }

    override suspend fun updateNote(note: NoteEntity) = insertNote(note)

    override suspend fun deleteNote(id: String) {
        val notes = getAllNotes().toMutableList()
        notes.removeAll { it.id == id }
        prefs.putString("notes_json", json.encodeToString(notes.map {
            NoteJson(it.id, it.title, it.content, it.createdAt, it.updatedAt, it.source)
        }))
    }

    override suspend fun searchNotes(query: String): List<NoteEntity> {
        val lower = query.lowercase()
        return getAllNotes().filter {
            it.title.lowercase().contains(lower) || it.content.lowercase().contains(lower)
        }
    }
}

class IosDeviceProfileRepository : DeviceProfileRepository {
    private val prefs = createPlatformPreferences("cyanbridge_devices")

    override suspend fun getAll(): List<DeviceProfileEntity> = emptyList() // TODO: implement

    override suspend fun get(macAddress: String): DeviceProfileEntity? = null

    override suspend fun upsert(profile: DeviceProfileEntity) {}

    override suspend fun delete(macAddress: String) {}
}

class IosMemoryVaultRepository : MemoryVaultRepository {
    override suspend fun getAll(): List<MemoryVaultItemEntity> = emptyList()
    override suspend fun get(id: String): MemoryVaultItemEntity? = null
    override suspend fun insert(item: MemoryVaultItemEntity) {}
    override suspend fun update(item: MemoryVaultItemEntity) {}
    override suspend fun delete(id: String) {}
    override suspend fun search(query: String, limit: Int): List<MemoryVaultItemEntity> = emptyList()
}

class IosMediaRecordRepository : MediaRecordRepository {
    private val prefs = createPlatformPreferences("cyanbridge_media")

    override suspend fun getAll(): List<MediaRecordEntity> = emptyList()
    override suspend fun get(id: String): MediaRecordEntity? = null
    override suspend fun getByFilename(filename: String): MediaRecordEntity? = null
    override suspend fun insert(record: MediaRecordEntity) {}
    override suspend fun delete(id: String) {}
    override suspend fun deleteAll() {}
}

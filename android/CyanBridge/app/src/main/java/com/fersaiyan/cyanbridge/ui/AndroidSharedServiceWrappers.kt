package com.fersaiyan.cyanbridge.ui

import com.fersaiyan.cyanbridge.shared.ai.AiModel
import com.fersaiyan.cyanbridge.shared.ai.AiModelRegistry
import com.fersaiyan.cyanbridge.shared.ai.ChatAiService
import com.fersaiyan.cyanbridge.shared.ai.ChatMessage
import com.fersaiyan.cyanbridge.shared.ai.ChatResponse
import com.fersaiyan.cyanbridge.shared.ai.ImageAiService
import com.fersaiyan.cyanbridge.shared.ai.VoiceAiService
import com.fersaiyan.cyanbridge.shared.network.P2pConnectionState
import com.fersaiyan.cyanbridge.shared.network.P2pPeer
import com.fersaiyan.cyanbridge.shared.network.WifiP2pManager
import com.fersaiyan.cyanbridge.shared.persistence.ChatEntity
import com.fersaiyan.cyanbridge.shared.persistence.ChatMessageEntity
import com.fersaiyan.cyanbridge.shared.persistence.ChatRepository
import com.fersaiyan.cyanbridge.shared.persistence.DeviceProfileEntity
import com.fersaiyan.cyanbridge.shared.persistence.DeviceProfileRepository
import com.fersaiyan.cyanbridge.shared.persistence.MediaRecordEntity
import com.fersaiyan.cyanbridge.shared.persistence.MediaRecordRepository
import com.fersaiyan.cyanbridge.shared.persistence.MemoryVaultItemEntity
import com.fersaiyan.cyanbridge.shared.persistence.MemoryVaultRepository
import com.fersaiyan.cyanbridge.shared.persistence.NoteEntity
import com.fersaiyan.cyanbridge.shared.persistence.NotesRepository
import com.fersaiyan.cyanbridge.shared.platform.PlatformLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

// ── Wi-Fi P2P Manager wrapper ──

class AndroidWifiP2pManagerWrapper : WifiP2pManager {
    private val _isAvailable = MutableStateFlow(true)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _connectionState = MutableStateFlow(P2pConnectionState.IDLE)
    override val connectionState: Flow<P2pConnectionState> = _connectionState.asStateFlow()

    private val _glassesIpAddress = MutableStateFlow<String?>(null)
    override val glassesIpAddress: StateFlow<String?> = _glassesIpAddress.asStateFlow()

    override fun discoverPeers(): Flow<P2pPeer> = flow {
        // Delegate to WifiP2pManagerSingleton
        PlatformLogger.i(TAG, "Wi-Fi P2P discovery - delegating to Android WifiP2pManagerSingleton")
    }

    override fun stopDiscovery() {
        // TODO: Delegate to WifiP2pManagerSingleton
    }

    override suspend fun connect(peerAddress: String) {
        _connectionState.value = P2pConnectionState.CONNECTING
        // TODO: Delegate to WifiP2pManagerSingleton
        _connectionState.value = P2pConnectionState.CONNECTED
    }

    override suspend fun disconnect() {
        _connectionState.value = P2pConnectionState.DISCONNECTING
        // TODO: Delegate to WifiP2pManagerSingleton
        _connectionState.value = P2pConnectionState.IDLE
    }

    override fun isConnected(): Boolean = _connectionState.value == P2pConnectionState.CONNECTED

    override fun setGlassesIpAddress(ip: String) {
        _glassesIpAddress.value = ip
    }

    override suspend fun bindToP2pNetwork(): Boolean {
        // TODO: Delegate to Android ConnectivityManager.bindProcessToNetwork()
        return true
    }

    override fun cancelConnection() {
        _connectionState.value = P2pConnectionState.IDLE
    }

    companion object {
        private const val TAG = "AndroidWifiP2p"
    }
}

// ── Chat Repository wrapper ──

class AndroidChatRepositoryWrapper : ChatRepository {
    override suspend fun getAllChats(): List<ChatEntity> {
        // TODO: Delegate to Room DAO
        return emptyList()
    }
    override suspend fun getChat(id: String): ChatEntity? = null
    override suspend fun insertChat(chat: ChatEntity) {}
    override suspend fun updateChat(chat: ChatEntity) {}
    override suspend fun deleteChat(id: String) {}
    override suspend fun getMessages(chatId: String): List<ChatMessageEntity> = emptyList()
    override suspend fun insertMessage(message: ChatMessageEntity) {}
    override suspend fun deleteMessage(id: String) {}
    override suspend fun deleteMessagesForChat(chatId: String) {}
}

// ── Notes Repository wrapper ──

class AndroidNotesRepositoryWrapper : NotesRepository {
    override suspend fun getAllNotes(): List<NoteEntity> = emptyList()
    override suspend fun getNote(id: String): NoteEntity? = null
    override suspend fun insertNote(note: NoteEntity) {}
    override suspend fun updateNote(note: NoteEntity) {}
    override suspend fun deleteNote(id: String) {}
    override suspend fun searchNotes(query: String): List<NoteEntity> = emptyList()
}

// ── Device Profile Repository wrapper ──

class AndroidDeviceProfileRepositoryWrapper : DeviceProfileRepository {
    override suspend fun getAll(): List<DeviceProfileEntity> = emptyList()
    override suspend fun get(macAddress: String): DeviceProfileEntity? = null
    override suspend fun upsert(profile: DeviceProfileEntity) {}
    override suspend fun delete(macAddress: String) {}
}

// ── Memory Vault Repository wrapper ──

class AndroidMemoryVaultRepositoryWrapper : MemoryVaultRepository {
    override suspend fun getAll(): List<MemoryVaultItemEntity> = emptyList()
    override suspend fun get(id: String): MemoryVaultItemEntity? = null
    override suspend fun insert(item: MemoryVaultItemEntity) {}
    override suspend fun update(item: MemoryVaultItemEntity) {}
    override suspend fun delete(id: String) {}
    override suspend fun search(query: String, limit: Int): List<MemoryVaultItemEntity> = emptyList()
}

// ── Media Record Repository wrapper ──

class AndroidMediaRecordRepositoryWrapper : MediaRecordRepository {
    override suspend fun getAll(): List<MediaRecordEntity> = emptyList()
    override suspend fun get(id: String): MediaRecordEntity? = null
    override suspend fun getByFilename(filename: String): MediaRecordEntity? = null
    override suspend fun insert(record: MediaRecordEntity) {}
    override suspend fun delete(id: String) {}
    override suspend fun deleteAll() {}
}

// ── AI Services wrappers ──

class AndroidChatAiService : ChatAiService {
    override suspend fun chat(messages: List<ChatMessage>, model: String?): ChatResponse {
        // TODO: Delegate to CliRelayClient or DirectApiClient
        return ChatResponse(
            message = ChatMessage("assistant", "Android AI service - connect to relay or local model."),
        )
    }
}

class AndroidVoiceAiService : VoiceAiService {
    override suspend fun transcribe(audioData: ByteArray, mimeType: String): String {
        // TODO: Delegate to TranscriptionService
        return ""
    }
}

class AndroidImageAiService : ImageAiService {
    override suspend fun analyzeImage(imageData: ByteArray, prompt: String, mimeType: String): String {
        // TODO: Delegate to CliRelayClient.imageQuery()
        return "Android image analysis - connect to relay."
    }
}

class AndroidAiModelRegistry : AiModelRegistry {
    override suspend fun listModels(): List<AiModel> = listOf(
        AiModel("relay-chat", "Relay Chat", "cyanbridge"),
        AiModel("relay-vision", "Relay Vision", "cyanbridge"),
        AiModel("local-llama", "Local llama.cpp", "local", isLocal = true),
    )

    override fun getDefaultModelId(): String = "relay-chat"
}

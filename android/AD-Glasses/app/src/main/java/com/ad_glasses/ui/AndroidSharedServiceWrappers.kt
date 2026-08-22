package com.ad_glasses.ui

import android.content.Context
import com.ad_glasses.agent.LocalAgentPrefs
import com.ad_glasses.ai.router.ApiTokenClient
import com.ad_glasses.localmodels.provider.LocalModelsProvider
import com.ad_glasses.shared.settings.AgentProviderType
import com.ad_glasses.shared.ai.AiModel
import com.ad_glasses.shared.ai.AiModelRegistry
import com.ad_glasses.shared.ai.ChatAiService
import com.ad_glasses.shared.ai.ChatMessage
import com.ad_glasses.shared.ai.ChatResponse
import com.ad_glasses.shared.ai.ImageAiService
import com.ad_glasses.shared.ai.VoiceAiService
import com.ad_glasses.shared.network.P2pConnectionState
import com.ad_glasses.shared.network.P2pPeer
import com.ad_glasses.shared.network.WifiP2pManager
import com.ad_glasses.shared.persistence.ChatEntity
import com.ad_glasses.shared.persistence.ChatMessageEntity
import com.ad_glasses.shared.persistence.ChatRepository
import com.ad_glasses.shared.persistence.DeviceProfileEntity
import com.ad_glasses.shared.persistence.DeviceProfileRepository
import com.ad_glasses.shared.persistence.MediaRecordEntity
import com.ad_glasses.shared.persistence.MediaRecordRepository
import com.ad_glasses.shared.persistence.MemoryVaultItemEntity
import com.ad_glasses.shared.persistence.MemoryVaultRepository
import com.ad_glasses.shared.persistence.NoteEntity
import com.ad_glasses.shared.persistence.NotesRepository
import com.ad_glasses.shared.platform.PlatformLogger
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

class AndroidChatAiService(context: Context) : ChatAiService {
    private val appContext = context.applicationContext
    private val localProvider = LocalModelsProvider()

    override suspend fun chat(messages: List<ChatMessage>, model: String?): ChatResponse {
        val cleanMessages = messages.mapNotNull { message ->
            val role = message.role.trim().lowercase()
            val content = message.content.trim()
            if (role.isBlank() || content.isBlank()) null else mapOf("role" to role, "content" to content)
        }
        require(cleanMessages.isNotEmpty()) { "A non-empty AI message is required" }
        require(cleanMessages.any { it["role"] == "user" }) { "A user message is required" }
        val reply = when (LocalAgentPrefs.getProviderType(appContext)) {
            AgentProviderType.LOCAL_AGENT -> localProvider.streamChat(
                context = appContext,
                messages = cleanMessages,
            )
            AgentProviderType.CLOUD_AI -> ApiTokenClient.chat(
                context = appContext,
                messages = cleanMessages,
            ).getOrThrow()
        }
        return ChatResponse(
            message = ChatMessage("assistant", reply),
        )
    }
}

class AndroidVoiceAiService : VoiceAiService {
    override suspend fun transcribe(audioData: ByteArray, mimeType: String): String {
        throw UnsupportedOperationException(
            "Shared voice transcription has no implicit provider. Use Moonshine or Android speech recognition explicitly.",
        )
    }
}

class AndroidImageAiService : ImageAiService {
    override suspend fun analyzeImage(imageData: ByteArray, prompt: String, mimeType: String): String {
        throw UnsupportedOperationException(
            "Shared image analysis has no implicit upload route. Use the explicit Local or Cloud media pipeline.",
        )
    }
}

class AndroidAiModelRegistry : AiModelRegistry {
    override suspend fun listModels(): List<AiModel> = listOf(
        AiModel("local-llama", "Local llama.cpp", "local", isLocal = true),
    )

    override fun getDefaultModelId(): String = "local-llama"
}

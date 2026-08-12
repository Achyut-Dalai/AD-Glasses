package com.achyut.adglasses.shared.platform

import com.achyut.adglasses.shared.ai.AiModelRegistry
import com.achyut.adglasses.shared.ai.ChatAiService
import com.achyut.adglasses.shared.ai.ImageAiService
import com.achyut.adglasses.shared.ai.VoiceAiService
import com.achyut.adglasses.shared.ble.BleManager
import com.achyut.adglasses.shared.network.WifiP2pManager
import com.achyut.adglasses.shared.persistence.ChatRepository
import com.achyut.adglasses.shared.persistence.DeviceProfileRepository
import com.achyut.adglasses.shared.persistence.MediaRecordRepository
import com.achyut.adglasses.shared.persistence.MemoryVaultRepository
import com.achyut.adglasses.shared.persistence.NotesRepository

/**
 * Service locator for cross-platform services.
 * Each platform provides its own implementations during initialization.
 *
 * Usage:
 * ```
 * // In platform init code:
 * CyanBridgeServices.initialize(
 *     bleManager = IosBleManager(),
 *     chatRepository = IosChatRepository(),
 *     // ...
 * )
 *
 * // In shared code:
 * val ble = CyanBridgeServices.bleManager
 * ```
 */
object CyanBridgeServices {
    private var initialized = false

    lateinit var bleManager: BleManager
        private set

    lateinit var wifiP2pManager: WifiP2pManager
        private set

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var notesRepository: NotesRepository
        private set

    lateinit var deviceProfileRepository: DeviceProfileRepository
        private set

    lateinit var memoryVaultRepository: MemoryVaultRepository
        private set

    lateinit var mediaRecordRepository: MediaRecordRepository
        private set

    lateinit var chatAiService: ChatAiService
        private set

    lateinit var voiceAiService: VoiceAiService
        private set

    lateinit var imageAiService: ImageAiService
        private set

    lateinit var aiModelRegistry: AiModelRegistry
        private set

    /**
     * Initialize all platform services. Must be called once during app startup.
     */
    fun initialize(
        bleManager: BleManager,
        wifiP2pManager: WifiP2pManager,
        chatRepository: ChatRepository,
        notesRepository: NotesRepository,
        deviceProfileRepository: DeviceProfileRepository,
        memoryVaultRepository: MemoryVaultRepository,
        mediaRecordRepository: MediaRecordRepository,
        chatAiService: ChatAiService,
        voiceAiService: VoiceAiService,
        imageAiService: ImageAiService,
        aiModelRegistry: AiModelRegistry,
    ) {
        check(!initialized) { "CyanBridgeServices already initialized" }
        this.bleManager = bleManager
        this.wifiP2pManager = wifiP2pManager
        this.chatRepository = chatRepository
        this.notesRepository = notesRepository
        this.deviceProfileRepository = deviceProfileRepository
        this.memoryVaultRepository = memoryVaultRepository
        this.mediaRecordRepository = mediaRecordRepository
        this.chatAiService = chatAiService
        this.voiceAiService = voiceAiService
        this.imageAiService = imageAiService
        this.aiModelRegistry = aiModelRegistry
        initialized = true
        PlatformLogger.i(TAG, "CyanBridgeServices initialized")
    }

    fun isInitialized(): Boolean = initialized

    private const val TAG = "CyanBridgeServices"
}

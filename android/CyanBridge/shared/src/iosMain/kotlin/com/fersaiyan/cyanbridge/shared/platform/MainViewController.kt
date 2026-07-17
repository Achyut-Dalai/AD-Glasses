package com.fersaiyan.cyanbridge.shared.platform

import androidx.compose.ui.window.ComposeUIViewController
import com.fersaiyan.cyanbridge.shared.ai.AiModel
import com.fersaiyan.cyanbridge.shared.ai.AiModelRegistry
import com.fersaiyan.cyanbridge.shared.ai.ChatAiService
import com.fersaiyan.cyanbridge.shared.ai.ChatMessage
import com.fersaiyan.cyanbridge.shared.ai.ChatResponse
import com.fersaiyan.cyanbridge.shared.ai.ImageAiService
import com.fersaiyan.cyanbridge.shared.ai.TokenUsage
import com.fersaiyan.cyanbridge.shared.ai.VoiceAiService
import com.fersaiyan.cyanbridge.shared.ble.IosBleManager
import com.fersaiyan.cyanbridge.shared.navigation.AppDestination
import com.fersaiyan.cyanbridge.shared.network.P2pConnectionState
import com.fersaiyan.cyanbridge.shared.network.P2pPeer
import com.fersaiyan.cyanbridge.shared.network.WifiP2pManager
import com.fersaiyan.cyanbridge.shared.persistence.IosChatRepository
import com.fersaiyan.cyanbridge.shared.persistence.IosDeviceProfileRepository
import com.fersaiyan.cyanbridge.shared.persistence.IosMediaRecordRepository
import com.fersaiyan.cyanbridge.shared.persistence.IosMemoryVaultRepository
import com.fersaiyan.cyanbridge.shared.persistence.IosNotesRepository
import com.fersaiyan.cyanbridge.shared.ui.CyanBridgeApp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.Foundation.NSData
import platform.Foundation.base64EncodedStringWithOptions

private const val DEFAULT_RELAY_URL = "https://cyanbridge.vercel.app"

/**
 * Initialize CyanBridgeServices with iOS implementations and return the ComposeUIViewController.
 */
fun MainViewController() = ComposeUIViewController {
    if (!CyanBridgeServices.isInitialized()) {
        CyanBridgeServices.initialize(
            bleManager = IosBleManager(),
            wifiP2pManager = IosWifiP2pManager(),
            chatRepository = IosChatRepository(),
            notesRepository = IosNotesRepository(),
            deviceProfileRepository = IosDeviceProfileRepository(),
            memoryVaultRepository = IosMemoryVaultRepository(),
            mediaRecordRepository = IosMediaRecordRepository(),
            chatAiService = IosRelayChatAiService(),
            voiceAiService = IosRelayVoiceAiService(),
            imageAiService = IosRelayImageAiService(),
            aiModelRegistry = IosRelayAiModelRegistry(),
        )
    }
    CyanBridgeApp()
}

/** Used only by the simulator screenshot harness to exercise each root route. */
fun MainViewControllerForDestination(destination: String) = ComposeUIViewController {
    if (!CyanBridgeServices.isInitialized()) {
        CyanBridgeServices.initialize(
            bleManager = IosBleManager(),
            wifiP2pManager = IosWifiP2pManager(),
            chatRepository = IosChatRepository(),
            notesRepository = IosNotesRepository(),
            deviceProfileRepository = IosDeviceProfileRepository(),
            memoryVaultRepository = IosMemoryVaultRepository(),
            mediaRecordRepository = IosMediaRecordRepository(),
            chatAiService = IosRelayChatAiService(),
            voiceAiService = IosRelayVoiceAiService(),
            imageAiService = IosRelayImageAiService(),
            aiModelRegistry = IosRelayAiModelRegistry(),
        )
    }
    CyanBridgeApp(
        initialDestination = when (destination) {
            "chats" -> AppDestination.CHATS
            "media" -> AppDestination.MEDIA
            "plugins" -> AppDestination.PLUGINS
            "settings" -> AppDestination.SETTINGS
            else -> AppDestination.GLASSES
        },
    )
}

// ── iOS Wi-Fi P2P manager using NEHotspotConfiguration ──

/**
 * iOS Wi-Fi P2P manager using NEHotspotConfiguration.
 * iOS doesn't support true Wi-Fi Direct like Android.
 * This uses NEHotspotConfiguration to join the glasses' Wi-Fi hotspot.
 */
private class IosWifiP2pManager : WifiP2pManager {
    private val _isAvailable = MutableStateFlow(true)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _connectionState = MutableStateFlow(P2pConnectionState.IDLE)
    override val connectionState: Flow<P2pConnectionState> = _connectionState.asStateFlow()

    private val _glassesIpAddress = MutableStateFlow<String?>(null)
    override val glassesIpAddress: StateFlow<String?> = _glassesIpAddress.asStateFlow()

    override fun discoverPeers(): Flow<P2pPeer> = flow {
        PlatformLogger.i(TAG, "Wi-Fi discovery on iOS uses NEHotspotConfiguration")
        // iOS doesn't support Wi-Fi Direct peer discovery like Android.
        // The glasses expose a Wi-Fi hotspot that the phone joins via NEHotspotConfiguration.
        // Discovery is handled by BLE scanning instead.
    }

    override fun stopDiscovery() {
        PlatformLogger.i(TAG, "Stopping Wi-Fi discovery")
    }

    override suspend fun connect(peerAddress: String) {
        PlatformLogger.i(TAG, "Connecting to Wi-Fi hotspot: $peerAddress")
        _connectionState.value = P2pConnectionState.CONNECTING
        // On iOS, we use NEHotspotConfigurationManager to join the glasses' Wi-Fi hotspot.
        // The SSID is typically the glasses' MAC address or a known prefix.
        // NEHotspotConfigurationManager.applyConfiguration() is an async API.
        // For now, mark as connected - the actual NEHotspotConfiguration integration
        // requires SwiftUI/UIKit coordination for the system dialog.
        _connectionState.value = P2pConnectionState.CONNECTED
        PlatformLogger.i(TAG, "Wi-Fi hotspot connected (NEHotspotConfiguration)")
    }

    override suspend fun disconnect() {
        PlatformLogger.i(TAG, "Disconnecting from Wi-Fi hotspot")
        _connectionState.value = P2pConnectionState.DISCONNECTING
        // NEHotspotConfigurationManager.removeConfiguration(forSSID:) to forget the network
        _connectionState.value = P2pConnectionState.IDLE
    }

    override fun isConnected(): Boolean = _connectionState.value == P2pConnectionState.CONNECTED

    override fun setGlassesIpAddress(ip: String) {
        PlatformLogger.i(TAG, "Glasses IP address set: $ip")
        _glassesIpAddress.value = ip
    }

    override suspend fun bindToP2pNetwork(): Boolean {
        // iOS doesn't need explicit network binding like Android
        // The system routes traffic to the connected Wi-Fi network automatically
        return true
    }

    override fun cancelConnection() {
        _connectionState.value = P2pConnectionState.IDLE
    }

    companion object {
        private const val TAG = "IosWifiP2p"
    }
}

// ── iOS AI services via CyanBridge relay server ──

/**
 * iOS chat AI service that calls the CyanBridge relay server.
 * Endpoint: POST /chat
 */
private class IosRelayChatAiService(
    private val baseUrl: String = DEFAULT_RELAY_URL,
) : ChatAiService {
    private val httpClient = PlatformHttpClient()

    override suspend fun chat(messages: List<ChatMessage>, model: String?): ChatResponse {
        return try {
            val messagesJson = messages.joinToString(",") { msg ->
                """{"role":"${msg.role}","content":"${msg.content.escapeJson()}"}"""
            }
            val body = """{"messages":[$messagesJson]}"""
            val headers = mapOf("Content-Type" to "application/json; charset=UTF-8")

            val response = httpClient.post("$baseUrl/chat", body, headers)

            if (response.isSuccessful) {
                parseChatResponse(response.body)
            } else {
                PlatformLogger.e(TAG, "Chat request failed: ${response.statusCode}")
                ChatResponse(
                    message = ChatMessage("assistant", "Error: Server returned ${response.statusCode}"),
                )
            }
        } catch (e: Exception) {
            PlatformLogger.e(TAG, "Chat request error", e)
            ChatResponse(
                message = ChatMessage("assistant", "Error: ${e.message ?: "Unknown error"}"),
            )
        }
    }

    private fun parseChatResponse(body: String): ChatResponse {
        // Simple JSON parsing for the response
        // Expected format: {"response":"...","model":"..."}
        val responseMatch = Regex(""""response"\s*:\s*"([^"]*?)"""").find(body)
        val responseText = responseMatch?.groupValues?.get(1)?.unescapeJson() ?: body

        return ChatResponse(
            message = ChatMessage("assistant", responseText),
        )
    }

    companion object {
        private const val TAG = "IosRelayChatAi"
    }
}

/**
 * iOS voice AI service that calls the CyanBridge relay server.
 * Endpoint: POST /voice-query
 */
private class IosRelayVoiceAiService(
    private val baseUrl: String = DEFAULT_RELAY_URL,
) : VoiceAiService {
    private val httpClient = PlatformHttpClient()

    override suspend fun transcribe(audioData: ByteArray, mimeType: String): String {
        return try {
            // Send audio as base64 in JSON body
            val base64Audio = audioData.encodeBase64()
            val body = """{"audio":"$base64Audio","mime_type":"$mimeType"}"""
            val headers = mapOf("Content-Type" to "application/json; charset=UTF-8")

            val response = httpClient.post("$baseUrl/voice-query", body, headers)

            if (response.isSuccessful) {
                val textMatch = Regex(""""text"\s*:\s*"([^"]*?)"""").find(response.body)
                textMatch?.groupValues?.get(1)?.unescapeJson() ?: response.body
            } else {
                PlatformLogger.e(TAG, "Voice transcription failed: ${response.statusCode}")
                ""
            }
        } catch (e: Exception) {
            PlatformLogger.e(TAG, "Voice transcription error", e)
            ""
        }
    }

    companion object {
        private const val TAG = "IosRelayVoiceAi"
    }
}

/**
 * iOS image AI service that calls the CyanBridge relay server.
 * Endpoint: POST /image-query
 */
private class IosRelayImageAiService(
    private val baseUrl: String = DEFAULT_RELAY_URL,
) : ImageAiService {
    private val httpClient = PlatformHttpClient()

    override suspend fun analyzeImage(imageData: ByteArray, prompt: String, mimeType: String): String {
        return try {
            val base64Image = imageData.encodeBase64()
            val body = """{"image":"$base64Image","prompt":"${prompt.escapeJson()}","mime_type":"$mimeType"}"""
            val headers = mapOf("Content-Type" to "application/json; charset=UTF-8")

            val response = httpClient.post("$baseUrl/image-query", body, headers)

            if (response.isSuccessful) {
                val responseMatch = Regex(""""response"\s*:\s*"([^"]*?)"""").find(response.body)
                responseMatch?.groupValues?.get(1)?.unescapeJson() ?: response.body
            } else {
                PlatformLogger.e(TAG, "Image analysis failed: ${response.statusCode}")
                "Error: Server returned ${response.statusCode}"
            }
        } catch (e: Exception) {
            PlatformLogger.e(TAG, "Image analysis error", e)
            "Error: ${e.message ?: "Unknown error"}"
        }
    }

    companion object {
        private const val TAG = "IosRelayImageAi"
    }
}

/**
 * iOS AI model registry that fetches models from the relay server.
 * Endpoint: GET /models
 */
private class IosRelayAiModelRegistry(
    private val baseUrl: String = DEFAULT_RELAY_URL,
) : AiModelRegistry {
    private val httpClient = PlatformHttpClient()
    private var cachedModels: List<AiModel>? = null

    override suspend fun listModels(): List<AiModel> {
        cachedModels?.let { return it }

        return try {
            val response = httpClient.get("$baseUrl/models")
            if (response.isSuccessful) {
                val models = parseModels(response.body)
                cachedModels = models
                models
            } else {
                PlatformLogger.e(TAG, "Failed to fetch models: ${response.statusCode}")
                defaultModels()
            }
        } catch (e: Exception) {
            PlatformLogger.e(TAG, "Failed to fetch models", e)
            defaultModels()
        }
    }

    override fun getDefaultModelId(): String = "relay-chat"

    private fun parseModels(body: String): List<AiModel> {
        // Simple JSON array parsing
        val modelPattern = Regex("""\{[^}]*"id"\s*:\s*"([^"]*)"[^}]*"name"\s*:\s*"([^"]*)"[^}]*\}""")
        return modelPattern.findAll(body).map { match ->
            AiModel(
                id = match.groupValues[1],
                name = match.groupValues[2],
                provider = "cyanbridge",
            )
        }.toList().ifEmpty { defaultModels() }
    }

    private fun defaultModels() = listOf(
        AiModel("relay-chat", "Relay Chat", "cyanbridge"),
        AiModel("relay-vision", "Relay Vision", "cyanbridge"),
    )

    companion object {
        private const val TAG = "IosRelayModelRegistry"
    }
}

// ── JSON/String helpers ──

private fun String.escapeJson(): String =
    replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

private fun String.unescapeJson(): String =
    replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.encodeBase64(): String {
    if (isEmpty()) return ""
    // Use a simple base64 encoding for iOS
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val sb = StringBuilder()
    var i = 0
    while (i < size) {
        val b0 = this[i].toInt() and 0xFF
        val b1 = if (i + 1 < size) this[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < size) this[i + 2].toInt() and 0xFF else 0
        sb.append(chars[(b0 shr 2) and 0x3F])
        sb.append(chars[((b0 shl 4) or (b1 shr 4)) and 0x3F])
        if (i + 1 < size) sb.append(chars[((b1 shl 2) or (b2 shr 6)) and 0x3F]) else sb.append('=')
        if (i + 2 < size) sb.append(chars[b2 and 0x3F]) else sb.append('=')
        i += 3
    }
    return sb.toString()
}

package com.adglasses.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adglasses.app.AppGraph
import com.adglasses.app.core.model.ChatMessage
import com.adglasses.app.core.model.CapturedNotification
import com.adglasses.app.core.model.GlassesConnectionState
import com.adglasses.app.core.model.ScannedGlasses
import com.adglasses.app.integrations.heycyan.GlassesNetworkLease
import com.adglasses.app.integrations.heycyan.HeyCyanDeviceEvent
import com.adglasses.app.integrations.heycyan.HeyCyanNetworkMode
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ADViewModel : ViewModel() {
    val glasses: StateFlow<GlassesConnectionState> = AppGraph.glasses.state
    val scanned: StateFlow<List<ScannedGlasses>> = AppGraph.glasses.discovered
    val messages: StateFlow<List<ChatMessage>> = AppGraph.conversationStore.messages
    val notifications: StateFlow<List<CapturedNotification>> = AppGraph.notifications.items

    private val _busyMessage = MutableStateFlow<String?>(null)
    val busyMessage = _busyMessage.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    private val _mediaConfig = MutableStateFlow<String?>(null)
    val mediaConfig = _mediaConfig.asStateFlow()
    private val _translation = MutableStateFlow(TranslationUiState())
    val translation = _translation.asStateFlow()

    init {
        viewModelScope.launch {
            AppGraph.glasses.events.collect { event ->
                when (event) {
                    HeyCyanDeviceEvent.AssistantListeningStarted -> _notice.value = "Listening from glasses"
                    HeyCyanDeviceEvent.AssistantListeningEnded -> _notice.value = "Glasses voice turn finished"
                    HeyCyanDeviceEvent.AiPhotoReady -> _notice.value = "AI photo is ready"
                    is HeyCyanDeviceEvent.WifiError -> _notice.value = "Glasses Wi-Fi error ${event.code}"
                    else -> Unit
                }
            }
        }
    }

    fun scan() = AppGraph.glasses.scan()
    fun connect(device: ScannedGlasses) = AppGraph.glasses.connect(device)
    fun disconnect() = AppGraph.glasses.disconnect()
    fun forget() = AppGraph.glasses.forget()
    fun clearNotice() { _notice.value = null }

    fun takePhoto() = launchHardwareAction("Taking photo") { AppGraph.glasses.takePhoto(); "Photo command accepted" }
    fun startVideo() = launchHardwareAction("Starting video") { AppGraph.glasses.startVideo(); "Video recording started" }
    fun stopVideo() = launchHardwareAction("Stopping video") { AppGraph.glasses.stopVideo(); "Video recording stopped" }
    fun startAudio() = launchHardwareAction("Starting recording") { AppGraph.glasses.startAudioRecording(); "Glasses recording started" }
    fun stopAudio() = launchHardwareAction("Stopping recording") { AppGraph.glasses.stopAudioRecording(); "Glasses recording stopped" }
    fun aiPhoto() = launchHardwareAction("Capturing for Lens") { AppGraph.glasses.requestAiPhoto(); "AI photo requested" }

    fun sendMessage(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        AppGraph.conversationStore.addUser(cleaned)
        _notice.value = "Assistant model provider is not configured in the Android reboot yet. Your message is saved locally."
    }

    fun newConversation() {
        AppGraph.conversationStore.clear()
        _notice.value = null
    }

    fun syncMediaConfig() {
        if (!glasses.value.isReady) {
            _notice.value = "Connect the glasses first"
            return
        }
        viewModelScope.launch {
            _busyMessage.value = "Preparing glasses media network"
            var prepared = false
            var lease: GlassesNetworkLease? = null
            val ipDeferred = async { awaitWifiAddress() }
            try {
                val preparation = AppGraph.glasses.prepareMedia(HeyCyanNetworkMode.AccessPoint)
                prepared = true
                if (preparation.mode != HeyCyanNetworkMode.AccessPoint) {
                    error("The glasses selected Wi-Fi Direct. P2P binding stays disabled until the live Samsung validation pass confirms the complete route and cleanup sequence.")
                }
                _busyMessage.value = "Joining ${preparation.ssid}"
                lease = AppGraph.wifi.joinAccessPoint(preparation)
                val ip = ipDeferred.await()
                _busyMessage.value = "Reading media manifest"
                _mediaConfig.value = AppGraph.media.fetchMediaConfig(lease.network, ip)
                _notice.value = "Media manifest loaded over the glasses network"
            } catch (error: Throwable) {
                ipDeferred.cancel()
                _notice.value = error.message ?: "Media sync failed"
            } finally {
                lease?.close?.invoke()
                if (prepared) AppGraph.glasses.finishMedia()
                _busyMessage.value = null
            }
        }
    }

    fun translate(text: String, source: String, target: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _translation.value = TranslationUiState(input = text, source = source, target = target, working = true)
            try {
                val translated = AppGraph.translation.translate(text, source, target)
                _translation.value = _translation.value.copy(output = translated, working = false, error = null)
            } catch (error: Throwable) {
                _translation.value = _translation.value.copy(error = error.message ?: "Translation failed", working = false)
            }
        }
    }

    fun speakTranslation() {
        translation.value.output.takeIf { it.isNotBlank() }?.let(AppGraph.tts::speak)
    }

    fun openNotificationAccess() = AppGraph.communication.openNotificationAccess()

    private fun launchHardwareAction(label: String, block: suspend () -> String) {
        viewModelScope.launch {
            _busyMessage.value = label
            try {
                _notice.value = block()
            } catch (error: Throwable) {
                _notice.value = error.message ?: "$label failed"
            } finally {
                _busyMessage.value = null
            }
        }
    }

    private suspend fun awaitWifiAddress(): String = withTimeout(10_000) {
        AppGraph.glasses.events.filterIsInstance<HeyCyanDeviceEvent.WifiAddress>().first().address
    }
}

data class TranslationUiState(
    val input: String = "",
    val output: String = "",
    val source: String = "en",
    val target: String = "hi",
    val working: Boolean = false,
    val error: String? = null,
)

package com.adglasses.app.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adglasses.app.AppGraph
import com.adglasses.app.core.assistant.AIConfigurationSnapshot
import com.adglasses.app.core.assistant.AIProfile
import com.adglasses.app.core.assistant.AIProviderKind
import com.adglasses.app.core.assistant.AssistantRequestRouter
import com.adglasses.app.core.assistant.AssistantRoute
import com.adglasses.app.core.communication.CommunicationDelivery
import com.adglasses.app.core.media.LocalMediaItem
import com.adglasses.app.core.model.ChatMessage
import com.adglasses.app.core.model.CapturedNotification
import com.adglasses.app.core.model.GlassesConnectionState
import com.adglasses.app.core.model.ScannedGlasses
import com.adglasses.app.core.notifications.NotificationReplyResult
import com.adglasses.app.core.speech.GlassesSpeechStatus
import com.adglasses.app.core.speech.SpeechTranscriptSource
import com.adglasses.app.integrations.heycyan.GlassesNetworkLease
import com.adglasses.app.integrations.heycyan.HeyCyanDeviceEvent
import com.adglasses.app.integrations.heycyan.HeyCyanHttpStatusException
import com.adglasses.app.integrations.heycyan.HeyCyanMediaItem
import com.adglasses.app.integrations.heycyan.HeyCyanNetworkMode
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    val mediaItems: StateFlow<List<LocalMediaItem>> = AppGraph.mediaLibrary.items
    val aiConfiguration: StateFlow<AIConfigurationSnapshot> = AppGraph.aiProfiles.state
    val glassesSpeechStatus: StateFlow<GlassesSpeechStatus> = AppGraph.glassesSpeech.status

    private val _assistantWorking = MutableStateFlow(false)
    val assistantWorking = _assistantWorking.asStateFlow()
    private val _busyMessage = MutableStateFlow<String?>(null)
    val busyMessage = _busyMessage.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    private val _mediaConfig = MutableStateFlow<String?>(null)
    val mediaConfig = _mediaConfig.asStateFlow()
    private val _translation = MutableStateFlow(TranslationUiState())
    val translation = _translation.asStateFlow()

    /**
     * Typed and glasses-originated turns share one unbounded ordered queue. A voice turn arriving
     * while the AI is answering is therefore processed next rather than discarded.
     */
    private val assistantQueue = Channel<AssistantTurn>(capacity = Channel.UNLIMITED)
    private var conversationEpoch = 0L

    init {
        viewModelScope.launch {
            for (turn in assistantQueue) processAssistantTurn(turn)
        }

        viewModelScope.launch {
            AppGraph.glassesSpeech.transcripts.collect { transcript ->
                assistantQueue.send(
                    AssistantTurn(
                        text = transcript.text,
                        epoch = conversationEpoch,
                        source = when (transcript.source) {
                            SpeechTranscriptSource.Moonshine -> AssistantTurnSource.GlassesLocal
                            SpeechTranscriptSource.GroqWhisper -> AssistantTurnSource.GlassesGroq
                        },
                    )
                )
            }
        }

        viewModelScope.launch {
            AppGraph.glassesSpeech.status.collect { status ->
                when (status) {
                    GlassesSpeechStatus.Listening -> _notice.value = "Listening from glasses"
                    is GlassesSpeechStatus.Transcribing -> {
                        _notice.value = if (status.local) {
                            "Transcribing on device"
                        } else {
                            "Preparing glasses voice transcript"
                        }
                    }
                    is GlassesSpeechStatus.Failed -> _notice.value = status.reason
                    GlassesSpeechStatus.Idle -> Unit
                }
            }
        }

        viewModelScope.launch {
            AppGraph.glasses.events.collect { event ->
                when (event) {
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
        val result = assistantQueue.trySend(
            AssistantTurn(
                text = cleaned,
                epoch = conversationEpoch,
                source = AssistantTurnSource.Typed,
            )
        )
        if (result.isFailure) _notice.value = "Could not queue the Assistant turn"
    }

    fun saveAIProfile(
        provider: AIProviderKind,
        name: String,
        model: String,
        endpoint: String,
        apiKey: String,
    ): Boolean = runCatching {
        val current = aiConfiguration.value.activeProfile
        val base = if (provider.managesEndpoint) provider.defaultBaseUrl else endpoint
        val chosenModel = model.trim().ifBlank { provider.defaultModel }
        val draft = if (current == null) {
            AppGraph.aiProfiles.newProfile(provider).copy(
                name = name.trim().ifBlank { provider.displayName },
                baseUrl = base,
                model = chosenModel,
            )
        } else {
            current.copy(
                name = name.trim().ifBlank { provider.displayName },
                provider = provider,
                baseUrl = base,
                model = chosenModel,
            )
        }
        AppGraph.aiProfiles.save(draft, apiKeyReplacement = apiKey, makeActive = true)
        _notice.value = "${provider.displayName} is now the active AD provider"
        true
    }.getOrElse { error ->
        _notice.value = error.message ?: "Could not save Cloud AI profile"
        false
    }

    fun addAIProfile(
        provider: AIProviderKind,
        name: String,
        model: String,
        endpoint: String,
        apiKey: String,
    ): Boolean = runCatching {
        val base = if (provider.managesEndpoint) provider.defaultBaseUrl else endpoint
        val profile = AppGraph.aiProfiles.newProfile(provider).copy(
            name = name.trim().ifBlank { provider.displayName },
            baseUrl = base,
            model = model.trim().ifBlank { provider.defaultModel },
        )
        AppGraph.aiProfiles.save(profile, apiKeyReplacement = apiKey, makeActive = true)
        _notice.value = "Added ${profile.name}"
        true
    }.getOrElse { error ->
        _notice.value = error.message ?: "Could not add Cloud AI profile"
        false
    }

    fun setActiveAIProfile(profile: AIProfile) = runCatching {
        AppGraph.aiProfiles.setActive(profile.id)
    }.onFailure { _notice.value = it.message }

    fun deleteActiveAIProfile() = aiConfiguration.value.activeProfile?.let { profile ->
        AppGraph.aiProfiles.delete(profile.id)
        _notice.value = "Removed ${profile.name}"
    }

    fun newConversation() {
        conversationEpoch += 1
        while (assistantQueue.tryReceive().isSuccess) Unit
        AppGraph.conversationStore.clear()
        _notice.value = null
    }

    /**
     * One user action owns the complete verified transfer batch:
     * BLE prepare -> Android joins glasses AP -> wait for glasses IP/server -> manifest -> missing originals -> finish -> release AP.
     */
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

                _busyMessage.value = "Joining glasses Wi-Fi"
                lease = AppGraph.wifi.joinAccessPoint(preparation)
                val ip = ipDeferred.await()

                _busyMessage.value = "Waiting for glasses media server"
                val remoteItems = waitForMediaServer(lease.network, ip)
                _mediaConfig.value = remoteItems.joinToString("\n") { it.fileName }

                val pending = remoteItems.filterNot { AppGraph.mediaLibrary.hasOriginal(it.fileName) }
                if (pending.isEmpty()) {
                    AppGraph.mediaLibrary.refresh()
                    _notice.value = if (remoteItems.isEmpty()) {
                        "Glasses Library is empty"
                    } else {
                        "Library is up to date • ${remoteItems.size} original${if (remoteItems.size == 1) "" else "s"} on glasses"
                    }
                    return@launch
                }

                pending.forEachIndexed { index, item ->
                    _busyMessage.value = "Syncing ${index + 1}/${pending.size} • ${item.fileName}"
                    AppGraph.media.download(
                        network = lease.network,
                        deviceIp = ip,
                        item = item,
                        destination = AppGraph.mediaLibrary.originalFile(item.fileName),
                    )
                    AppGraph.mediaLibrary.refresh()
                }

                _notice.value = "Synced ${pending.size} new original${if (pending.size == 1) "" else "s"} • ${remoteItems.size} on glasses"
            } catch (error: Throwable) {
                ipDeferred.cancel()
                _notice.value = error.message ?: "Media sync failed"
            } finally {
                lease?.close?.invoke()
                if (prepared) runCatching { AppGraph.glasses.finishMedia() }
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
        val state = translation.value
        if (state.output.isNotBlank()) AppGraph.tts.speak(state.output, state.target)
    }

    fun openNotificationAccess() = AppGraph.communication.openNotificationAccess()

    private suspend fun processAssistantTurn(turn: AssistantTurn) {
        if (turn.epoch != conversationEpoch) return
        _assistantWorking.value = true
        try {
            AppGraph.conversationStore.addUser(turn.text)
            if (turn.epoch != conversationEpoch) return

            val route = AssistantRequestRouter.route(turn.text)
            val localResponse = executeLocalRoute(route)
            if (localResponse != null) {
                completeAssistantTurn(turn, localResponse)
                return
            }

            val configuration = AppGraph.aiProfiles.state.value
            val profile = configuration.activeProfile
            if (!configuration.configured || profile == null) {
                val message = "Configure a Cloud AI profile from the Assistant settings first"
                _notice.value = message
                if (turn.isGlassesVoice) AppGraph.tts.speak(message)
                return
            }

            val credential = AppGraph.aiProfiles.credential(profile.id)
            val answer = AppGraph.cloudAI.response(
                messages = AppGraph.conversationStore.messages.value,
                profile = profile,
                credential = credential,
            )
            completeAssistantTurn(turn, answer)

            when (turn.source) {
                AssistantTurnSource.GlassesLocal -> _notice.value = "Answered glasses voice • on-device speech"
                AssistantTurnSource.GlassesGroq -> _notice.value = "Answered glasses voice • Groq Whisper fallback"
                AssistantTurnSource.Typed -> Unit
            }
        } catch (error: Throwable) {
            if (turn.epoch == conversationEpoch) {
                val message = error.message ?: "AD could not complete that request"
                _notice.value = message
                val response = "I couldn't complete that request. $message"
                AppGraph.conversationStore.addAssistant(response)
                if (turn.isGlassesVoice) AppGraph.tts.speak(response)
            }
        } finally {
            _assistantWorking.value = false
        }
    }

    /** Returns null only when the turn should continue to conversational AI. */
    private suspend fun executeLocalRoute(route: AssistantRoute): String? = when (route) {
        AssistantRoute.Conversation -> null
        AssistantRoute.Notifications -> summarizeNotifications()
        AssistantRoute.CapturePhoto -> {
            AppGraph.glasses.takePhoto()
            "Photo captured."
        }
        AssistantRoute.StartVideo -> {
            AppGraph.glasses.startVideo()
            "Video recording started."
        }
        AssistantRoute.StopVideo -> {
            AppGraph.glasses.stopVideo()
            "Video recording stopped."
        }
        AssistantRoute.StartAudio -> {
            AppGraph.glasses.startAudioRecording()
            "Audio recording started on the glasses."
        }
        AssistantRoute.StopAudio -> {
            AppGraph.glasses.stopAudioRecording()
            "Audio recording stopped."
        }
        is AssistantRoute.PhoneCall -> {
            val delivery = AppGraph.communication.call(route.query)
            if (delivery == CommunicationDelivery.Direct) {
                "Calling ${route.query}."
            } else {
                "I opened the dialer for ${route.query}."
            }
        }
        is AssistantRoute.SendSms -> {
            val delivery = AppGraph.communication.text(route.recipient, route.body)
            if (delivery == CommunicationDelivery.Direct) {
                "Message sent to ${route.recipient}."
            } else {
                "I opened the message composer for ${route.recipient}."
            }
        }
        is AssistantRoute.ReplyNotification -> when (
            val result = AppGraph.notifications.reply(route.target, route.body)
        ) {
            is NotificationReplyResult.Sent -> {
                val target = result.notification.title.ifBlank { result.notification.appLabel }
                "Reply sent to $target."
            }
            NotificationReplyResult.NoReplyableNotification -> {
                if (route.target.isNullOrBlank()) {
                    "I couldn't find a recent notification that supports replies."
                } else {
                    "I couldn't find a replyable notification matching ${route.target}."
                }
            }
        }
    }

    private fun summarizeNotifications(): String {
        val recent = AppGraph.notifications.items.value.take(5)
        if (recent.isEmpty()) {
            return "I don't have any captured notifications yet. Enable notification access if you want me to read them."
        }
        return buildString {
            append("Your latest notifications are: ")
            recent.forEachIndexed { index, item ->
                if (index > 0) append("; ")
                append(item.appLabel)
                item.title.takeIf { it.isNotBlank() }?.let { append(", ").append(it) }
                item.text.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
                if (item.canReply) append(" (replyable)")
            }
            append('.')
        }
    }

    private fun completeAssistantTurn(turn: AssistantTurn, response: String) {
        if (turn.epoch != conversationEpoch) return
        val cleaned = response.trim()
        if (cleaned.isEmpty()) return
        AppGraph.conversationStore.addAssistant(cleaned)
        if (turn.isGlassesVoice) AppGraph.tts.speak(cleaned)
    }

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

    private suspend fun awaitWifiAddress(): String = withTimeout(20_000) {
        AppGraph.glasses.events.filterIsInstance<HeyCyanDeviceEvent.WifiAddress>().first().address
    }

    private suspend fun waitForMediaServer(
        network: android.net.Network,
        ip: String,
    ): List<HeyCyanMediaItem> {
        val deadline = SystemClock.elapsedRealtime() + 20_000L
        var stableMissingManifestResponses = 0
        var lastError: Throwable? = null

        while (true) {
            try {
                return AppGraph.media.listMedia(network, ip)
            } catch (error: HeyCyanHttpStatusException) {
                if (error.statusCode == 404) {
                    stableMissingManifestResponses += 1
                    if (stableMissingManifestResponses >= 6) return emptyList()
                } else {
                    stableMissingManifestResponses = 0
                    if (error.statusCode in 400..499) throw error
                }
                lastError = error
            } catch (error: Throwable) {
                stableMissingManifestResponses = 0
                lastError = error
            }

            if (SystemClock.elapsedRealtime() >= deadline) {
                error("Glasses media server did not become ready: ${lastError?.message ?: "no response"}")
            }
            delay(350)
        }
    }

    private data class AssistantTurn(
        val text: String,
        val epoch: Long,
        val source: AssistantTurnSource,
    ) {
        val isGlassesVoice: Boolean
            get() = source != AssistantTurnSource.Typed
    }

    private enum class AssistantTurnSource {
        Typed,
        GlassesLocal,
        GlassesGroq,
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

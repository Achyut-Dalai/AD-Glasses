package com.adglasses.app

import android.app.Application
import com.adglasses.app.core.assistant.AIProfileStore
import com.adglasses.app.core.assistant.AIProviderKind
import com.adglasses.app.core.assistant.CloudAIClient
import com.adglasses.app.core.assistant.ConversationStore
import com.adglasses.app.core.background.CompanionPresenceManager
import com.adglasses.app.core.bluetooth.ClassicBluetoothManager
import com.adglasses.app.core.communication.CommunicationManager
import com.adglasses.app.core.media.MediaLibraryStore
import com.adglasses.app.core.notifications.NotificationHub
import com.adglasses.app.core.speech.CloudTranscriptionClient
import com.adglasses.app.core.speech.GlassesSpeechCoordinator
import com.adglasses.app.core.speech.GroqSpeechAccess
import com.adglasses.app.core.speech.MoonshineSpeechEngine
import com.adglasses.app.core.speech.SystemTtsEngine
import com.adglasses.app.core.translation.MlKitTranslationEngine
import com.adglasses.app.integrations.heycyan.HeyCyanBleTransport
import com.adglasses.app.integrations.heycyan.HeyCyanMediaClient
import com.adglasses.app.integrations.heycyan.HeyCyanRepository
import com.adglasses.app.integrations.heycyan.HeyCyanWifiCoordinator

object AppGraph {
    private lateinit var application: Application

    lateinit var glasses: HeyCyanRepository
        private set
    lateinit var conversationStore: ConversationStore
        private set
    lateinit var aiProfiles: AIProfileStore
        private set
    lateinit var cloudAI: CloudAIClient
        private set
    lateinit var mediaLibrary: MediaLibraryStore
        private set
    lateinit var translation: MlKitTranslationEngine
        private set
    lateinit var tts: SystemTtsEngine
        private set
    lateinit var notifications: NotificationHub
        private set
    lateinit var communication: CommunicationManager
        private set
    lateinit var companionPresence: CompanionPresenceManager
        private set
    lateinit var classicBluetooth: ClassicBluetoothManager
        private set
    lateinit var wifi: HeyCyanWifiCoordinator
        private set
    lateinit var media: HeyCyanMediaClient
        private set
    lateinit var localSpeech: MoonshineSpeechEngine
        private set
    lateinit var cloudSpeech: CloudTranscriptionClient
        private set
    lateinit var glassesSpeech: GlassesSpeechCoordinator
        private set

    fun initialize(app: Application) {
        if (::application.isInitialized) return
        application = app
        notifications = NotificationHub(app)
        conversationStore = ConversationStore(app)
        aiProfiles = AIProfileStore(app)
        cloudAI = CloudAIClient()
        mediaLibrary = MediaLibraryStore(app)
        translation = MlKitTranslationEngine()
        tts = SystemTtsEngine(app)
        communication = CommunicationManager(app)
        companionPresence = CompanionPresenceManager(app)
        classicBluetooth = ClassicBluetoothManager(app)
        wifi = HeyCyanWifiCoordinator(app)
        media = HeyCyanMediaClient()
        glasses = HeyCyanRepository(
            context = app,
            transport = HeyCyanBleTransport(app),
            onClassicBluetoothRequestFinished = { name ->
                classicBluetooth.ensureLink(name)
            },
        )
        companionPresence.bindGlassesState(glasses.state)

        localSpeech = MoonshineSpeechEngine(app)
        cloudSpeech = CloudTranscriptionClient()
        glassesSpeech = GlassesSpeechCoordinator(
            glasses = glasses,
            localSpeech = localSpeech,
            cloudSpeech = cloudSpeech,
            groqAccess = ::resolveGroqSpeechAccess,
            outputActive = tts::isOutputActive,
        )
    }

    /**
     * Reuse an encrypted Groq Cloud AI profile for Whisper rather than creating a second speech
     * secret. Prefer the active profile when it is Groq, otherwise use the first configured Groq
     * profile so a user can keep another provider active for Assistant reasoning.
     */
    private fun resolveGroqSpeechAccess(): GroqSpeechAccess? {
        val snapshot = aiProfiles.state.value
        val ordered = buildList {
            snapshot.activeProfile?.let(::add)
            snapshot.profiles.forEach { profile ->
                if (none { it.id == profile.id }) add(profile)
            }
        }
        val profile = ordered.firstOrNull {
            it.provider == AIProviderKind.Groq && aiProfiles.hasCredential(it.id)
        } ?: return null
        val apiKey = runCatching { aiProfiles.credential(profile.id) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return GroqSpeechAccess(
            apiKey = apiKey,
            baseUrl = profile.baseUrl.ifBlank { AIProviderKind.Groq.defaultBaseUrl },
        )
    }
}

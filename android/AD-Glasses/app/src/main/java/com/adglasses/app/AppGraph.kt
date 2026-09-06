package com.adglasses.app

import android.app.Application
import com.adglasses.app.core.assistant.ConversationStore
import com.adglasses.app.core.communication.CommunicationManager
import com.adglasses.app.core.notifications.NotificationHub
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
    lateinit var translation: MlKitTranslationEngine
        private set
    lateinit var tts: SystemTtsEngine
        private set
    lateinit var notifications: NotificationHub
        private set
    lateinit var communication: CommunicationManager
        private set
    lateinit var wifi: HeyCyanWifiCoordinator
        private set
    lateinit var media: HeyCyanMediaClient
        private set

    fun initialize(app: Application) {
        if (::application.isInitialized) return
        application = app
        notifications = NotificationHub()
        conversationStore = ConversationStore(app)
        translation = MlKitTranslationEngine()
        tts = SystemTtsEngine(app)
        communication = CommunicationManager(app)
        wifi = HeyCyanWifiCoordinator(app)
        media = HeyCyanMediaClient()
        glasses = HeyCyanRepository(app, HeyCyanBleTransport(app))
    }
}

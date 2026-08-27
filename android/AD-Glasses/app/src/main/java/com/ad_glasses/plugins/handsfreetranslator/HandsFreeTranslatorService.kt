package com.ad_glasses.plugins.handsfreetranslator

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.ad_glasses.ai.voice.KokoroSpeechService
import com.ad_glasses.ai.voice.SpeechCallbacks
import com.ad_glasses.ai.voice.SpeechQueueMode
import com.ad_glasses.bridge.core.DisplayCommand
import com.ad_glasses.bridge.core.GlassesBridge
import com.ad_glasses.plugins.PluginVoiceRecognizer
import com.ad_glasses.plugins.startPluginVoiceForeground
import com.ad_glasses.plugins.startPluginVoiceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Translates recognized speech from the phone or a connected Bluetooth glasses microphone. */
class HandsFreeTranslatorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val translatorStore = HandsFreeTranslatorStore()
    private val translationQueue = Channel<String>(capacity = Channel.BUFFERED)
    private val translationEngine = OnDeviceTranslationEngine()
    private var voiceRecognizer: PluginVoiceRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        HandsFreeTranslatorNotificationHelper.ensureChannel(this)
        translatorStore.load(this)
        KokoroSpeechService.get(this).prepare(
            onError = { error -> Log.w(TAG, "Kokoro preparation failed; speech will retry on demand", error) },
        )
        scope.launch {
            for (phrase in translationQueue) processPhrase(phrase)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTranslation()
            ACTION_STOP -> stopTranslation()
            ACTION_TRANSLATE_PHRASE -> intent.getStringExtra(EXTRA_PHRASE)?.let(::translatePhrase)
            null -> if (HandsFreeTranslatorPreferences.isEnabled(this)) startTranslation() else stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        voiceRecognizer?.stop()
        voiceRecognizer = null
        HandsFreeTranslatorPreferences.setEnabled(this, false)
        translationQueue.close()
        translationEngine.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun startTranslation() {
        if (voiceRecognizer != null) {
            HandsFreeTranslatorPreferences.setEnabled(this, true)
            return
        }
        if (!startPluginVoiceForeground(
                service = this,
                notificationId = HandsFreeTranslatorNotificationHelper.NOTIFICATION_ID,
                notification = HandsFreeTranslatorNotificationHelper.buildNotification(this, "Starting translator..."),
            )
        ) {
            HandsFreeTranslatorPreferences.setEnabled(this, false)
            Log.w(TAG, "Missing microphone or notification permission")
            stopSelf()
            return
        }

        val languageTag = HandsFreeTranslatorPreferences
            .getSourceLanguage(this)
            .takeIf { !HandsFreeTranslatorPreferences.isAutoDetect(this) }
        val recognizer = PluginVoiceRecognizer(
            context = this,
            languageTag = languageTag,
            onPartialText = { partial ->
                HandsFreeTranslatorNotificationHelper.updateNotification(
                    this,
                    "Listening: ${partial.take(NOTIFICATION_TEXT_LIMIT)}",
                )
            },
            onFinalText = ::translatePhrase,
            onError = { message ->
                Log.w(TAG, message)
                HandsFreeTranslatorNotificationHelper.updateNotification(this, message)
            },
        )
        if (!recognizer.start()) {
            HandsFreeTranslatorPreferences.setEnabled(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        voiceRecognizer = recognizer
        HandsFreeTranslatorPreferences.setEnabled(this, true)
        HandsFreeTranslatorNotificationHelper.updateNotification(this, "Listening for speech to translate...")
    }

    private fun stopTranslation() {
        HandsFreeTranslatorPreferences.setEnabled(this, false)
        voiceRecognizer?.stop()
        voiceRecognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun translatePhrase(phrase: String) {
        val normalized = phrase.trim().takeIf { it.isNotBlank() } ?: return
        if (translationQueue.trySend(normalized).isFailure) {
            HandsFreeTranslatorNotificationHelper.updateNotification(
                this,
                "Translator is catching up. Pause briefly, then repeat the last phrase.",
            )
        }
    }

    private suspend fun processPhrase(phrase: String) {
        try {
            val result = translationEngine.translate(
                text = phrase,
                configuredSourceLanguage = HandsFreeTranslatorPreferences.getSourceLanguage(this),
                configuredTargetLanguage = HandsFreeTranslatorPreferences.getTargetLanguage(this),
                autoDetect = HandsFreeTranslatorPreferences.isAutoDetect(this),
                requireWifiForModelDownload = true,
            )
            val translation = TranslationEntry(
                timestampMs = System.currentTimeMillis(),
                originalText = phrase,
                translatedText = result.translatedText,
                sourceLanguage = result.sourceLanguage,
                targetLanguage = result.targetLanguage,
                confidence = 1f,
            )
            translatorStore.addTranslation(translation, HandsFreeTranslatorPreferences.getMaxHistory(this))
            translatorStore.persist(this, HandsFreeTranslatorPreferences.getMaxHistory(this))
            HandsFreeTranslatorNotificationHelper.updateNotification(
                this,
                "Translation: ${translation.translatedText.take(NOTIFICATION_TEXT_LIMIT)}",
            )
            GlassesBridge.showCard(DisplayCommand.Card(title = "Translation", body = translation.translatedText))
            if (HandsFreeTranslatorPreferences.isSpeakTranslation(this)) {
                speakTranslationAndWait(translation.translatedText)
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to translate speech", error)
            HandsFreeTranslatorNotificationHelper.updateNotification(
                this,
                error.message?.take(NOTIFICATION_TEXT_LIMIT)
                    ?: "Offline translation failed. Check the language pack and try again.",
            )
        }
    }

    private suspend fun speakTranslationAndWait(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return

        val completion = CompletableDeferred<Unit>()
        voiceRecognizer?.pause()
        HandsFreeTranslatorNotificationHelper.updateNotification(
            this,
            "Speaking translation — listening resumes immediately after",
        )
        KokoroSpeechService.get(this).speak(
            text = clean,
            queueMode = SpeechQueueMode.FLUSH,
            utteranceId = "translation_${System.nanoTime()}",
            callbacks = SpeechCallbacks(
                onDone = { if (!completion.isCompleted) completion.complete(Unit) },
                onStopped = { if (!completion.isCompleted) completion.complete(Unit) },
                onError = { error ->
                    Log.w(TAG, "Kokoro translation speech failed", error)
                    if (!completion.isCompleted) completion.complete(Unit)
                },
            ),
        )
        completion.await()
        voiceRecognizer?.resume()
    }

    companion object {
        private const val TAG = "HandsFreeTranslator"
        private const val NOTIFICATION_TEXT_LIMIT = 100

        const val ACTION_START = "com.ad_glasses.ACTION_START_TRANSLATOR"
        const val ACTION_STOP = "com.ad_glasses.ACTION_STOP_TRANSLATOR"
        const val ACTION_TRANSLATE_PHRASE = "com.ad_glasses.ACTION_TRANSLATE_PHRASE"
        const val EXTRA_PHRASE = "phrase"

        fun start(context: Context) {
            startPluginVoiceService(
                context,
                Intent(context, HandsFreeTranslatorService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, HandsFreeTranslatorService::class.java).setAction(ACTION_STOP),
            )
        }

        fun translate(context: Context, phrase: String) {
            startPluginVoiceService(
                context,
                Intent(context, HandsFreeTranslatorService::class.java)
                    .setAction(ACTION_TRANSLATE_PHRASE)
                    .putExtra(EXTRA_PHRASE, phrase),
            )
        }
    }
}

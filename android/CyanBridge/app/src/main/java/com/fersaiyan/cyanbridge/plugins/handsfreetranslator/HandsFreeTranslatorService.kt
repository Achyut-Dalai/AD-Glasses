package com.fersaiyan.cyanbridge.plugins.handsfreetranslator

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.fersaiyan.cyanbridge.bridge.core.DisplayCommand
import com.fersaiyan.cyanbridge.bridge.core.GlassesBridge
import com.fersaiyan.cyanbridge.plugins.PluginVoiceRecognizer
import com.fersaiyan.cyanbridge.plugins.startPluginVoiceForeground
import com.fersaiyan.cyanbridge.plugins.startPluginVoiceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Translates recognized speech from the phone or a connected Bluetooth glasses microphone. */
class HandsFreeTranslatorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val translatorStore = HandsFreeTranslatorStore()
    private val translationQueue = Channel<String>(capacity = Channel.BUFFERED)
    private val translationEngine = OnDeviceTranslationEngine()
    private val pendingUtterances = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private var voiceRecognizer: PluginVoiceRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate() {
        super.onCreate()
        HandsFreeTranslatorNotificationHelper.ensureChannel(this)
        translatorStore.load(this)
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                tts?.setOnUtteranceProgressListener(translationSpeechListener)
            }
        }
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
        pendingUtterances.values.forEach { it.complete(Unit) }
        pendingUtterances.clear()
        tts?.stop()
        tts?.shutdown()
        tts = null
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
            if (HandsFreeTranslatorPreferences.isSpeakTranslation(this) && ttsReady) {
                speakTranslationAndWait(translation.translatedText, translation.targetLanguage)
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

    private suspend fun speakTranslationAndWait(text: String, language: String) {
        tts?.language = when (language) {
            "en" -> Locale.US
            "es" -> Locale("es", "ES")
            "fr" -> Locale.FRANCE
            "de" -> Locale.GERMANY
            "it" -> Locale.ITALY
            "pt" -> Locale("pt", "BR")
            "zh" -> Locale.CHINA
            "ja" -> Locale.JAPAN
            "ko" -> Locale.KOREA
            else -> Locale.US
        }
        val utteranceId = "translation_${System.nanoTime()}"
        val completion = CompletableDeferred<Unit>()
        pendingUtterances[utteranceId] = completion
        voiceRecognizer?.pause()
        HandsFreeTranslatorNotificationHelper.updateNotification(
            this,
            "Speaking translation — listening resumes immediately after",
        )
        val queued = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS
        if (!queued) pendingUtterances.remove(utteranceId)?.complete(Unit)
        completion.await()
        voiceRecognizer?.resume()
    }

    private val translationSpeechListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = completeUtterance(utteranceId)
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = completeUtterance(utteranceId)
        override fun onError(utteranceId: String?, errorCode: Int) = completeUtterance(utteranceId)
    }

    private fun completeUtterance(utteranceId: String?) {
        utteranceId?.let { pendingUtterances.remove(it)?.complete(Unit) }
    }

    companion object {
        private const val TAG = "HandsFreeTranslator"
        private const val NOTIFICATION_TEXT_LIMIT = 100

        const val ACTION_START = "com.fersaiyan.cyanbridge.ACTION_START_TRANSLATOR"
        const val ACTION_STOP = "com.fersaiyan.cyanbridge.ACTION_STOP_TRANSLATOR"
        const val ACTION_TRANSLATE_PHRASE = "com.fersaiyan.cyanbridge.ACTION_TRANSLATE_PHRASE"
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

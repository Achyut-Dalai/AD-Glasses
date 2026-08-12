package com.achyut.adglasses.plugins.livecaptioncloud

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.achyut.adglasses.ai.router.CliCloudClient
import com.achyut.adglasses.bridge.core.DisplayCommand
import com.achyut.adglasses.bridge.core.GlassesBridge
import com.achyut.adglasses.plugins.PluginVoiceRecognizer
import com.achyut.adglasses.plugins.startPluginVoiceForeground
import com.achyut.adglasses.plugins.startPluginVoiceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Provides live phone captions from the selected Android microphone or connected glasses mic. */
class LiveCaptionCloudService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val captionStore = LiveCaptionCloudStore()
    private val translating = AtomicBoolean(false)
    private var voiceRecognizer: PluginVoiceRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        LiveCaptionCloudNotificationHelper.ensureChannel(this)
        captionStore.load(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCaptioning()
            ACTION_STOP -> stopCaptioning()
            null -> if (LiveCaptionCloudPreferences.isEnabled(this)) startCaptioning() else stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        voiceRecognizer?.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun startCaptioning() {
        if (voiceRecognizer != null) return
        if (!startPluginVoiceForeground(
                service = this,
                notificationId = LiveCaptionCloudNotificationHelper.NOTIFICATION_ID,
                notification = LiveCaptionCloudNotificationHelper.buildNotification(this, "Starting live captions..."),
            )
        ) {
            Log.w(TAG, "Missing microphone or notification permission")
            stopSelf()
            return
        }

        val sourceLanguage = LiveCaptionCloudPreferences.getSourceLanguage(this)
        val recognizer = PluginVoiceRecognizer(
            context = this,
            languageTag = sourceLanguage,
            onPartialText = { partial ->
                LiveCaptionCloudNotificationHelper.updateNotification(
                    this,
                    "Listening: ${partial.take(NOTIFICATION_TEXT_LIMIT)}",
                )
            },
            onFinalText = ::saveCaption,
            onError = { message ->
                Log.w(TAG, message)
                LiveCaptionCloudNotificationHelper.updateNotification(this, message)
            },
        )
        if (!recognizer.start()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        voiceRecognizer = recognizer
        LiveCaptionCloudNotificationHelper.updateNotification(this, "Listening for speech...")
    }

    private fun stopCaptioning() {
        voiceRecognizer?.stop()
        voiceRecognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun saveCaption(text: String) {
        val sourceLanguage = LiveCaptionCloudPreferences.getSourceLanguage(this)
        if (!LiveCaptionCloudPreferences.isTranslationEnabled(this)) {
            persistCaption(
                CaptionEntry(
                    timestampMs = System.currentTimeMillis(),
                    originalText = text,
                    translatedText = null,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = null,
                    confidence = 0f,
                ),
            )
            return
        }

        if (!translating.compareAndSet(false, true)) {
            persistCaption(
                CaptionEntry(
                    timestampMs = System.currentTimeMillis(),
                    originalText = text,
                    translatedText = null,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = null,
                    confidence = 0f,
                ),
            )
            return
        }
        scope.launch {
            try {
                val targetLanguage = LiveCaptionCloudPreferences.getTargetLanguage(this@LiveCaptionCloudService)
                val translated = translateCaption(text, sourceLanguage, targetLanguage)
                persistCaption(
                    CaptionEntry(
                        timestampMs = System.currentTimeMillis(),
                        originalText = text,
                        translatedText = translated,
                        sourceLanguage = sourceLanguage,
                        targetLanguage = targetLanguage,
                        confidence = 0f,
                    ),
                )
            } catch (error: Throwable) {
                Log.e(TAG, "Caption processing failed", error)
                persistCaption(
                    CaptionEntry(
                        timestampMs = System.currentTimeMillis(),
                        originalText = text,
                        translatedText = null,
                        sourceLanguage = sourceLanguage,
                        targetLanguage = null,
                        confidence = 0f,
                    ),
                )
            } finally {
                translating.set(false)
            }
        }
    }

    private fun persistCaption(caption: CaptionEntry) {
        captionStore.addCaption(caption, LiveCaptionCloudPreferences.getMaxHistory(this))
        captionStore.persist(this, LiveCaptionCloudPreferences.getMaxHistory(this))
        val displayText = caption.translatedText ?: caption.originalText
        LiveCaptionCloudNotificationHelper.updateNotification(
            this,
            "Caption: ${displayText.take(NOTIFICATION_TEXT_LIMIT)}",
        )
        scope.launch {
            // MYVU maps this through its on-lens teleprompter; other active
            // adapters can render the same native-plugin result in their format.
            GlassesBridge.showText(DisplayCommand.Text("Caption\n$displayText"))
        }
    }

    private suspend fun translateCaption(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): String? {
        val customPrompt = LiveCaptionCloudPreferences.getCustomPrompt(this)
        val prompt = buildString {
            append("Translate this live caption from $sourceLanguage to $targetLanguage. ")
            append("Return only the translated caption. Caption: \"$text\". ")
            if (customPrompt.isNotBlank()) append("Additional instructions: $customPrompt")
        }
        return CliCloudClient.chat(
            context = this,
            chatId = "live_caption_${System.currentTimeMillis()}",
            prompt = prompt,
            messages = listOf(mapOf("role" to "user", "content" to prompt)),
            modelOverride = LiveCaptionCloudPreferences.getCloudModelId(this),
        ).fold(
            onSuccess = { it.trim().takeIf(String::isNotBlank) },
            onFailure = { error ->
                Log.e(TAG, "Caption translation failed", error)
                null
            },
        )
    }

    companion object {
        private const val TAG = "LiveCaptionCloud"
        private const val NOTIFICATION_TEXT_LIMIT = 100

        const val ACTION_START = "com.achyut.adglasses.ACTION_START_CAPTION"
        const val ACTION_STOP = "com.achyut.adglasses.ACTION_STOP_CAPTION"

        fun start(context: Context) {
            startPluginVoiceService(
                context,
                Intent(context, LiveCaptionCloudService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, LiveCaptionCloudService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}

package com.fersaiyan.cyanbridge.ai.live

import android.content.Context
import com.fersaiyan.cyanbridge.ai.AiQuestionForegroundService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bounded, displayless Gemini Live session owned by AD rather than an Activity.
 *
 * The glasses runtime starts this after a wake event, feeds glasses PCM/camera frames, and stops
 * it after idle/explicit end. Keeping a Live cloud socket open all day is intentionally not part
 * of the product architecture.
 */
object ADGeminiLiveSession {
    interface Listener : GeminiLiveClient.Listener

    private val active = AtomicBoolean(false)
    @Volatile private var client: GeminiLiveClient? = null
    @Volatile private var appContext: Context? = null

    fun start(
        context: Context,
        language: String,
        imagePrompt: String,
        listener: Listener,
    ): Boolean {
        if (!active.compareAndSet(false, true)) return false
        val application = context.applicationContext
        appContext = application
        AiQuestionForegroundService.start(application, "AD is listening through your glasses")
        val live = GeminiLiveClient(
            context = application,
            listener = object : GeminiLiveClient.Listener {
                override fun onStateChanged(state: GeminiLiveState, detail: String) {
                    listener.onStateChanged(state, detail)
                    if (state == GeminiLiveState.STOPPED || state == GeminiLiveState.ERROR) {
                        finish(application)
                    }
                }

                override fun onInterrupted() = listener.onInterrupted()

                override fun onNetworkChanged(available: Boolean) = listener.onNetworkChanged(available)
            },
            audioInput = GeminiLiveAudioInput.GLASSES_PCM,
            toolExecutor = ADGeminiLiveToolExecutor(),
        )
        client = live
        live.start(language, imagePrompt)
        return true
    }

    fun offerPcm(pcm: ShortArray, sampleRateHz: Int) {
        client?.offerGlassesPcm(pcm, sampleRateHz)
    }

    fun offerImage(jpegBytes: ByteArray) {
        client?.sendImage(jpegBytes)
    }

    fun stop() {
        val context = appContext
        client?.stop()
        client?.close()
        client = null
        active.set(false)
        if (context != null) AiQuestionForegroundService.stop(context)
        appContext = null
    }

    fun isActive(): Boolean = active.get()

    private fun finish(context: Context) {
        client = null
        active.set(false)
        AiQuestionForegroundService.stop(context)
        appContext = null
    }
}

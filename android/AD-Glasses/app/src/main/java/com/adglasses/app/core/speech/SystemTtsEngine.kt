package com.adglasses.app.core.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

class SystemTtsEngine(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        tts.language = Locale.getDefault()
        selectBestOfflineVoice()?.let { tts.voice = it }
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ad-${System.nanoTime()}")
    }

    fun stop() { if (ready) tts.stop() }

    fun shutdown() {
        ready = false
        tts.stop()
        tts.shutdown()
    }

    private fun selectBestOfflineVoice(): Voice? = tts.voices
        ?.asSequence()
        ?.filter { !it.isNetworkConnectionRequired && it.locale.language == Locale.getDefault().language }
        ?.maxByOrNull { it.quality }
}

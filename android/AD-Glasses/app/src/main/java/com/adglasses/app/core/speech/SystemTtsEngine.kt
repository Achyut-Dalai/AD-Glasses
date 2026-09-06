package com.adglasses.app.core.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SystemTtsEngine(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false
    private val ttsLock = Any()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _speaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _speaking.value = false
                }

                @Deprecated("Legacy TextToSpeech callback")
                override fun onError(utteranceId: String?) {
                    _speaking.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _speaking.value = false
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    _speaking.value = false
                }
            }
        )
        synchronized(ttsLock) {
            configureLocale(Locale.getDefault())
        }
    }

    fun speak(text: String) = speak(text, null)

    fun speak(text: String, languageTag: String?) {
        if (!ready || text.isBlank()) return
        synchronized(ttsLock) {
            val locale = languageTag
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(Locale::forLanguageTag)
                ?.takeIf { it.language.isNotBlank() }
                ?: Locale.getDefault()
            configureLocale(locale)
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ad-${System.nanoTime()}")
            if (result == TextToSpeech.ERROR) _speaking.value = false
        }
    }

    /** Includes the platform's immediate state to close the gap before onStart arrives. */
    fun isOutputActive(): Boolean = ready && (_speaking.value || tts.isSpeaking)

    fun stop() {
        if (!ready) return
        synchronized(ttsLock) { tts.stop() }
        _speaking.value = false
    }

    fun shutdown() {
        ready = false
        synchronized(ttsLock) {
            tts.stop()
            tts.shutdown()
        }
        _speaking.value = false
    }

    /**
     * Prefer an installed offline voice for the requested language. Exact locale matches win,
     * followed by another offline voice in the same language, then the TTS engine's normal locale
     * selection if no offline voice exists. This keeps translation speech local whenever the phone
     * has a suitable voice installed without falsely claiming every language is available offline.
     */
    private fun configureLocale(locale: Locale) {
        val availability = tts.isLanguageAvailable(locale)
        if (availability < TextToSpeech.LANG_AVAILABLE) return

        val offlineVoice = selectBestOfflineVoice(locale)
        if (offlineVoice != null) {
            tts.voice = offlineVoice
        } else {
            tts.language = locale
        }
    }

    private fun selectBestOfflineVoice(locale: Locale): Voice? = tts.voices
        ?.asSequence()
        ?.filter { voice ->
            !voice.isNetworkConnectionRequired && voice.locale.language == locale.language
        }
        ?.maxWithOrNull(
            compareBy<Voice> { voice ->
                when {
                    voice.locale == locale -> 2
                    locale.country.isNotBlank() && voice.locale.country == locale.country -> 1
                    else -> 0
                }
            }.thenBy { it.quality }
        )
}

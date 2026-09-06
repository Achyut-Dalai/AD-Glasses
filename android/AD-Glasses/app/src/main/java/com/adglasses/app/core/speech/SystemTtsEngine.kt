package com.adglasses.app.core.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SystemTtsEngine(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val tts = TextToSpeech(appContext, this)
    @Volatile private var ready = false
    private val ttsLock = Any()
    private var pendingSpeech: PendingSpeech? = null
    private var activeUtteranceId: String? = null
    private var communicationRouteActive = false
    private var previousAudioMode = AudioManager.MODE_NORMAL

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) {
            synchronized(ttsLock) { pendingSpeech = null }
            return
        }
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId == activeUtteranceId) _speaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    finishUtterance(utteranceId)
                }

                @Deprecated("Legacy TextToSpeech callback")
                override fun onError(utteranceId: String?) {
                    finishUtterance(utteranceId)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    finishUtterance(utteranceId)
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    finishUtterance(utteranceId)
                }
            }
        )

        val queued = synchronized(ttsLock) {
            configureLocale(Locale.getDefault())
            pendingSpeech.also { pendingSpeech = null }
        }
        queued?.let { speak(it.text, it.languageTag) }
    }

    fun speak(text: String) = speak(text, null)

    fun speak(text: String, languageTag: String?) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return

        synchronized(ttsLock) {
            if (!ready) {
                // TextToSpeech initialization is asynchronous. Keep the most recent requested
                // response instead of silently dropping the first Assistant utterance.
                pendingSpeech = PendingSpeech(cleaned, languageTag)
                return
            }

            val locale = languageTag
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(Locale::forLanguageTag)
                ?.takeIf { it.language.isNotBlank() }
                ?: Locale.getDefault()
            configureLocale(locale)

            val routedToGlasses = requestGlassesCommunicationRoute()
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(
                        if (routedToGlasses) {
                            AudioAttributes.USAGE_VOICE_COMMUNICATION
                        } else {
                            AudioAttributes.USAGE_ASSISTANT
                        }
                    )
                    .build()
            )

            val utteranceId = "ad-${System.nanoTime()}"
            activeUtteranceId = utteranceId
            val result = tts.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                activeUtteranceId = null
                _speaking.value = false
                releaseCommunicationRoute()
            } else {
                // Close the small interval before UtteranceProgressListener.onStart arrives. This
                // is also used by the glasses-microphone echo guard.
                _speaking.value = true
            }
        }
    }

    /** Includes the platform's immediate state to close the gap before onStart arrives. */
    fun isOutputActive(): Boolean = ready && (_speaking.value || tts.isSpeaking)

    fun stop() {
        synchronized(ttsLock) {
            pendingSpeech = null
            if (ready) tts.stop()
            activeUtteranceId = null
            _speaking.value = false
            releaseCommunicationRoute()
        }
    }

    fun shutdown() {
        ready = false
        synchronized(ttsLock) {
            pendingSpeech = null
            tts.stop()
            tts.shutdown()
            activeUtteranceId = null
            _speaking.value = false
            releaseCommunicationRoute()
        }
    }

    private fun finishUtterance(utteranceId: String?) {
        synchronized(ttsLock) {
            if (utteranceId != null && utteranceId != activeUtteranceId) return
            activeUtteranceId = null
            _speaking.value = false
            releaseCommunicationRoute()
        }
    }

    /**
     * Android 12+ exposes a public communication-device selector. When the paired JS-01 headset is
     * available, selecting its sink asks Android to route communication output there and lets the
     * platform choose the matching input automatically. If no matching headset is available, TTS
     * falls back to Android's normal Assistant route rather than failing silently.
     */
    private fun requestGlassesCommunicationRoute(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val matching = runCatching {
            audioManager.availableCommunicationDevices.firstOrNull { device ->
                device.isSink && isGlassesAudioDevice(device)
            }
        }.getOrNull() ?: return false

        return runCatching {
            previousAudioMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (audioManager.setCommunicationDevice(matching)) {
                communicationRouteActive = true
                true
            } else {
                audioManager.mode = previousAudioMode
                false
            }
        }.getOrElse {
            runCatching { audioManager.mode = previousAudioMode }
            false
        }
    }

    private fun releaseCommunicationRoute() {
        if (!communicationRouteActive || Build.VERSION.SDK_INT < 31) return
        runCatching { audioManager.clearCommunicationDevice() }
        runCatching { audioManager.mode = previousAudioMode }
        communicationRouteActive = false
    }

    private fun isGlassesAudioDevice(device: AudioDeviceInfo): Boolean {
        val name = device.productName?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val supportedName = name.startsWith("js-01") ||
            name.startsWith("js01") ||
            name.contains("heycyan") ||
            name.contains("hey cyan") ||
            name.startsWith("o_") ||
            name.startsWith("q_")
        if (!supportedName) return false

        return when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> true
            else -> false
        }
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

    private data class PendingSpeech(
        val text: String,
        val languageTag: String?,
    )
}

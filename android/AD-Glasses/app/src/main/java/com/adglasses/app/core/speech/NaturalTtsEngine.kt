package com.adglasses.app.core.speech

import android.content.Context
import ai.moonshine.voice.TextToSpeech as MoonshineTextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface NaturalVoiceState {
    data object NotLoaded : NaturalVoiceState
    data object Loading : NaturalVoiceState
    data object Ready : NaturalVoiceState
    data class Failed(val reason: String) : NaturalVoiceState
}

/**
 * AD's local spoken voice.
 *
 * Kokoro `af_heart` is the preferred fully on-device voice. Model assets are fetched into
 * Moonshine's managed cache on first use. Android's best offline system voice remains an immediate
 * fallback while the Kokoro assets are downloading or if local synthesis fails, so voice output
 * never depends on a cloud TTS service.
 */
class NaturalTtsEngine(context: Context) {
    companion object {
        const val DEFAULT_VOICE = "kokoro_af_heart"
        private const val LANGUAGE = "en_us"
    }

    private val appContext = context.applicationContext
    private val fallback = SystemTtsEngine(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loadMutex = Mutex()

    @Volatile private var local: MoonshineTextToSpeech? = null
    private val _state = MutableStateFlow<NaturalVoiceState>(NaturalVoiceState.NotLoaded)
    val state: StateFlow<NaturalVoiceState> = _state.asStateFlow()

    fun prewarm() {
        scope.launch { ensureLocal() }
    }

    fun speak(text: String) {
        val spoken = prepareForSpeech(text)
        if (spoken.isBlank()) return
        val current = local
        if (current != null && _state.value == NaturalVoiceState.Ready) {
            scope.launch(Dispatchers.IO) {
                runCatching { current.say(spoken) }
                    .onFailure { fallback.speak(spoken) }
            }
        } else {
            // Do not make the first spoken response wait for a potentially large one-time model
            // download. The app starts warming Kokoro immediately and uses the system offline voice
            // only until the local neural voice is ready.
            fallback.speak(spoken)
            prewarm()
        }
    }

    fun stop() {
        fallback.stop()
        // Moonshine's blocking say() owns its AudioTrack internally. We intentionally do not tear
        // down the cached model between turns; that would make conversational latency much worse.
    }

    private suspend fun ensureLocal(): MoonshineTextToSpeech? {
        local?.let { return it }
        return loadMutex.withLock {
            local?.let { return@withLock it }
            _state.value = NaturalVoiceState.Loading
            try {
                val engine = withContext(Dispatchers.IO) {
                    MoonshineTextToSpeech(appContext)
                        .language(LANGUAGE)
                        .voice(DEFAULT_VOICE)
                        .also { it.load() }
                }
                local = engine
                _state.value = NaturalVoiceState.Ready
                engine
            } catch (error: Throwable) {
                _state.value = NaturalVoiceState.Failed(
                    error.message ?: "Could not load the local Kokoro voice"
                )
                null
            }
        }
    }

    private fun prepareForSpeech(raw: String): String = raw
        .replace(Regex("```[\\s\\S]*?```"), " code omitted ")
        .replace(Regex("[`*_#>]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

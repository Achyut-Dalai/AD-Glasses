package com.ad_glasses.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.ad_glasses.ai.transcription.moonshine.MoonshineRecognitionService
import java.util.Locale

/**
 * Assistant speech I/O policy.
 *
 * Speech input is exclusively the vendored Moonshine runtime exposed through an in-app Android
 * RecognitionService bridge. There is deliberately no platform/system ASR fallback. Speech output
 * still prefers an installed TTS voice that does not require a network connection.
 */
object AndroidAssistantVoiceIo {
    private const val TAG = "AssistantTiming"

    fun createRecognizer(context: Context): SpeechRecognizer {
        val component = ComponentName(context, MoonshineRecognitionService::class.java)
        Log.i(TAG, "stage=asr_engine engine=moonshine component=${component.flattenToShortString()}")
        return SpeechRecognizer.createSpeechRecognizer(context, component)
    }

    fun recognitionIntent(languageTag: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    /** Prefer a downloaded/embedded voice for [locale]. Falls back to the engine's locale handling. */
    fun preferOfflineVoice(tts: TextToSpeech, locale: Locale): Voice? {
        val voices = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet<Voice>())
        val offline = voices.filter { !it.isNetworkConnectionRequired }
        val selected = offline.firstOrNull { it.locale.toLanguageTag() == locale.toLanguageTag() }
            ?: offline.firstOrNull { it.locale.language == locale.language }

        if (selected != null) {
            val result = runCatching { tts.setVoice(selected) }.getOrDefault(TextToSpeech.ERROR)
            Log.i(
                TAG,
                "stage=tts_voice offline=true locale=${selected.locale.toLanguageTag()} result=$result name=${selected.name}",
            )
            if (result == TextToSpeech.SUCCESS) return selected
        }

        val languageResult = runCatching { tts.setLanguage(locale) }.getOrDefault(TextToSpeech.ERROR)
        Log.i(
            TAG,
            "stage=tts_voice offline=false locale=${locale.toLanguageTag()} languageResult=$languageResult",
        )
        return null
    }

    /**
     * Recognition deliberately releases the Bluetooth communication route as soon as Moonshine has
     * a final transcript. Re-establish it only after inference, immediately before the caller hands
     * the response to Android TTS, so Cloud AI latency never keeps the microphone/SCO path open.
     * Returns true only when a Bluetooth communication route was actually requested successfully.
     */
    @Suppress("DEPRECATION")
    fun prepareSpeechOutputRoute(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val device = audioManager.availableCommunicationDevices.firstOrNull { candidate ->
                    candidate.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        candidate.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (device == null) {
                    Log.i(TAG, "stage=tts_route bluetooth=false sdk=${Build.VERSION.SDK_INT}")
                    false
                } else {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    val selected = audioManager.setCommunicationDevice(device)
                    Log.i(
                        TAG,
                        "stage=tts_route bluetooth=true sdk=${Build.VERSION.SDK_INT} selected=$selected type=${device.type}",
                    )
                    selected
                }
            } else if (audioManager.isBluetoothScoAvailableOffCall) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.i(TAG, "stage=tts_route bluetooth=true sdk=${Build.VERSION.SDK_INT} legacySco=true")
                true
            } else {
                Log.i(TAG, "stage=tts_route bluetooth=false sdk=${Build.VERSION.SDK_INT}")
                false
            }
        }.onFailure { error ->
            Log.w(TAG, "stage=tts_route_failed", error)
        }.getOrDefault(false)
    }

    fun installVoiceDataIntent(): Intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
}

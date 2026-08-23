package com.ad_glasses.ai

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Android-owned speech I/O policy for the assistant.
 *
 * This intentionally does not know about Moonshine/sherpa. It lets us measure the existing Android
 * path cleanly: prefer the platform's explicit on-device recognizer when it exists, shorten wearable
 * endpointing, and choose an installed TTS voice that does not require a network connection.
 */
object AndroidAssistantVoiceIo {
    private const val TAG = "AssistantTiming"

    const val COMPLETE_SILENCE_MS = 900L
    const val POSSIBLY_COMPLETE_SILENCE_MS = 650L
    const val MINIMUM_SPEECH_MS = 300L

    fun createRecognizer(context: Context): SpeechRecognizer {
        val onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        if (onDevice) {
            runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
                .onSuccess {
                    Log.i(TAG, "stage=asr_engine onDevice=true sdk=${Build.VERSION.SDK_INT}")
                    return it
                }
                .onFailure { error ->
                    Log.w(TAG, "Explicit on-device recognizer failed; using system recognizer", error)
                }
        }
        Log.i(TAG, "stage=asr_engine onDevice=false sdk=${Build.VERSION.SDK_INT}")
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    fun recognitionIntent(languageTag: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
        // Explicit on-device recognizers are used when Android exposes one. On the fallback system
        // recognizer this is still only a preference (some engines may ignore it), but it avoids
        // needlessly choosing a network recognizer when an offline path is available.
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, COMPLETE_SILENCE_MS)
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
            POSSIBLY_COMPLETE_SILENCE_MS,
        )
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, MINIMUM_SPEECH_MS)
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

    fun installVoiceDataIntent(): Intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
}

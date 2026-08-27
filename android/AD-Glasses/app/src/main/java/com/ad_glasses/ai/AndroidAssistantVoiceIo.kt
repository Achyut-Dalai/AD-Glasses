package com.ad_glasses.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.ad_glasses.ai.transcription.moonshine.MoonshineRecognitionService

/**
 * Assistant speech I/O policy.
 *
 * Speech input is exclusively the vendored Moonshine runtime exposed through an in-app Android
 * RecognitionService bridge. Speech output is Kokoro-82M through KokoroSpeechService; this policy
 * object only owns recognizer creation and generic Bluetooth speech-output routing.
 */
object AndroidAssistantVoiceIo {
    private const val TAG = "AssistantTiming"

    fun createRecognizer(context: Context): SpeechRecognizer {
        MoonshineRecognitionService.prewarm(context.applicationContext)
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

    /**
     * Recognition releases the Bluetooth communication route after Moonshine obtains a final
     * transcript. Re-establish it only immediately before Kokoro playback so cloud/model latency
     * never keeps the microphone/SCO path open. Returns true only when a Bluetooth route was
     * actually requested successfully.
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
                    Log.i(TAG, "stage=voice_route bluetooth=false sdk=${Build.VERSION.SDK_INT}")
                    false
                } else {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    val selected = audioManager.setCommunicationDevice(device)
                    Log.i(
                        TAG,
                        "stage=voice_route bluetooth=true sdk=${Build.VERSION.SDK_INT} selected=$selected type=${device.type}",
                    )
                    selected
                }
            } else if (audioManager.isBluetoothScoAvailableOffCall) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.i(TAG, "stage=voice_route bluetooth=true sdk=${Build.VERSION.SDK_INT} legacySco=true")
                true
            } else {
                Log.i(TAG, "stage=voice_route bluetooth=false sdk=${Build.VERSION.SDK_INT}")
                false
            }
        }.onFailure { error ->
            Log.w(TAG, "stage=voice_route_failed", error)
        }.getOrDefault(false)
    }
}

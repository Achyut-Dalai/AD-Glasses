package com.ad_glasses.ai.voice

import android.content.Context

/** Java-friendly completion callback for upstream integrations that now speak through Kokoro. */
fun interface KokoroSpeechCompletion {
    fun onComplete(success: Boolean)
}

/**
 * Small Java interop surface for vendored integrations.
 *
 * Keeping this adapter outside the upstream sources lets AD Glasses replace Android's platform
 * TextToSpeech implementation without forking the MYVU submodule or changing its conversation
 * protocol. All local speech still shares the same process-wide Kokoro queue and model pack.
 */
object KokoroJavaSpeechBridge {
    @JvmStatic
    fun prepare(context: Context) {
        KokoroSpeechService.get(context.applicationContext).prepare()
    }

    @JvmStatic
    fun speak(
        context: Context,
        text: String,
        completion: KokoroSpeechCompletion,
    ) {
        KokoroSpeechService.get(context.applicationContext).speak(
            text = text,
            queueMode = SpeechQueueMode.FLUSH,
            callbacks = SpeechCallbacks(
                onDone = { completion.onComplete(true) },
                onStopped = { completion.onComplete(false) },
                onError = { completion.onComplete(false) },
            ),
        )
    }

    @JvmStatic
    fun stop(context: Context) {
        KokoroSpeechService.get(context.applicationContext).stop()
    }
}

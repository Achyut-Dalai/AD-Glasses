package com.myvu.client.ai;

import android.content.Context;

import com.ad_glasses.ai.voice.KokoroJavaSpeechBridge;

/**
 * AD Glasses replacement for the upstream MYVU speech player.
 *
 * All speech is synthesized by the shared offline Kokoro engine. The legacy HTTP-TTS method is
 * intentionally retained only as an API-compatibility shim for the upstream MYVU conversation
 * code; it also routes through Kokoro so this app has one speech backend and no Android TTS or
 * removed MYVU HTTP-TTS dependencies.
 */
public class TtsPlayer {

    public interface Callback {
        void onSpoken(boolean success);
    }

    private final Context context;
    private int requestGeneration;

    public TtsPlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Prewarms the shared Kokoro model/runtime; safe to call repeatedly. */
    public void init() {
        KokoroJavaSpeechBridge.prepare(context);
    }

    /** Speaks {@code text} through Kokoro and reports when playback actually ends. */
    public void speak(String text, Callback cb) {
        final int generation = ++requestGeneration;
        KokoroJavaSpeechBridge.speak(context, text, success -> {
            if (generation == requestGeneration && cb != null) {
                cb.onSpoken(success);
            }
        });
    }

    /**
     * Compatibility entry point for upstream MYVU callers that still expose an HTTP-TTS setting.
     * AD Glasses is Kokoro-only, so the provider parameters are deliberately ignored.
     */
    public void speakHttp(String text, String endpoint, String apiKey, String model,
                          String voice, Callback cb) {
        speak(text, cb);
    }

    public void shutdown() {
        requestGeneration++;
        KokoroJavaSpeechBridge.stop(context);
    }
}

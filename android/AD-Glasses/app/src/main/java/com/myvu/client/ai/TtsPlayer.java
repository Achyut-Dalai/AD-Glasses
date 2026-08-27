package com.myvu.client.ai;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import com.ad_glasses.ai.voice.KokoroJavaSpeechBridge;
import com.myvu.client.core.LogBus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AD Glasses replacement for the upstream MYVU speech player.
 *
 * Local speech is synthesized by the shared offline Kokoro engine. The optional explicit HTTP
 * provider is preserved for users who configured it; neither path uses Android platform TTS.
 */
public class TtsPlayer {

    public interface Callback {
        void onSpoken(boolean success);
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private MediaPlayer mediaPlayer;
    private File mediaFile;
    private Callback pending;
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
        stopMedia();
        pending = cb;
        final int generation = ++requestGeneration;
        KokoroJavaSpeechBridge.speak(context, text, success -> {
            if (generation == requestGeneration) {
                flushPending(success);
            }
        });
    }

    /** Fetches WAV audio from the explicitly configured HTTP service and plays it as media. */
    public void speakHttp(String text, String endpoint, String apiKey, String model,
                          String voice, Callback cb) {
        KokoroJavaSpeechBridge.stop(context);
        stopMedia();
        pending = cb;
        final int generation = ++requestGeneration;
        final HttpTtsClient client = new HttpTtsClient(endpoint, apiKey, model, voice);
        network.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] audio = client.synthesize(text);
                    File file = File.createTempFile("myvu-speech-", ".wav", context.getCacheDir());
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        out.write(audio);
                    }
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (generation != requestGeneration) {
                                delete(file);
                                return;
                            }
                            playFile(file);
                        }
                    });
                } catch (Exception e) {
                    LogBus.error("HTTP speech synthesis failed", e);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (generation == requestGeneration) flushPending(false);
                        }
                    });
                }
            }
        });
    }

    private void playFile(File file) {
        stopMedia();
        mediaFile = file;
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnPreparedListener(MediaPlayer::start);
        mediaPlayer.setOnCompletionListener(player -> {
            stopMedia();
            flushPending(true);
        });
        mediaPlayer.setOnErrorListener((player, what, extra) -> {
            LogBus.warn("HTTP speech playback failed (what " + what + ", extra " + extra + ")");
            stopMedia();
            flushPending(false);
            return true;
        });
        try {
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.prepareAsync();
        } catch (IOException | IllegalStateException e) {
            LogBus.error("could not prepare HTTP speech audio", e);
            stopMedia();
            flushPending(false);
        }
    }

    private void flushPending(boolean success) {
        Callback cb = pending;
        pending = null;
        if (cb != null) cb.onSpoken(success);
    }

    private void stopMedia() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mediaFile != null) {
            delete(mediaFile);
            mediaFile = null;
        }
    }

    private static void delete(File file) {
        if (file.exists() && !file.delete()) {
            LogBus.warn("could not delete temporary speech audio " + file.getAbsolutePath());
        }
    }

    public void shutdown() {
        requestGeneration++;
        KokoroJavaSpeechBridge.stop(context);
        stopMedia();
        network.shutdownNow();
        pending = null;
    }
}

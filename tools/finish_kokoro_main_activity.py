#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "android/AD-Glasses/app/src/main/java/com/ad_glasses/MainActivity.kt"
text = PATH.read_text(encoding="utf-8")
original = text

# Remove the Android platform speech engine imports and add the offline Kokoro output layer.
text = text.replace("import android.speech.tts.TextToSpeech\n", "")
text = text.replace("import android.speech.tts.UtteranceProgressListener\n", "")
anchor = "import com.ad_glasses.ai.AndroidAssistantVoiceIo\n"
voice_imports = (
    "import com.ad_glasses.ai.voice.AssistantListeningCuePlayer\n"
    "import com.ad_glasses.ai.voice.KokoroSpeechService\n"
    "import com.ad_glasses.ai.voice.SpeechCallbacks\n"
    "import com.ad_glasses.ai.voice.SpeechQueueMode\n"
    "import com.ad_glasses.shared.voice.KokoroHeartVoice\n"
)
if voice_imports not in text:
    if anchor not in text:
        raise SystemExit("Could not find AndroidAssistantVoiceIo import anchor")
    text = text.replace(anchor, anchor + voice_imports, 1)

# Replace the Activity-level Android TextToSpeech lifecycle and queue bookkeeping in one bounded block.
start_pattern = re.compile(
    r"class MainActivity : AppCompatActivity\(\), TextToSpeech\.OnInitListener \{\n"
    r".*?"
    r"    private fun cancelActiveAssistantTurnForVoice\(reason: String\) \{",
    re.DOTALL,
)
replacement = '''class MainActivity : AppCompatActivity() {
    private val lastAssistantSpeechEndAtMs = AtomicLong(0L)
    private val assistantRequestRouter = AssistantRequestRouter()
    private var pendingVoiceImageQuestion: String? = null
    private var pendingImageQuestionOfferSpokenQuestion = false

    // Optional Local Agent UI status
    private var agentReceiverRegistered = false
    private val agentStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            val status = intent.getStringExtra(LocalAgentIntents.EXTRA_STATUS)
            val lastError = intent.getStringExtra(LocalAgentIntents.EXTRA_LAST_ERROR)
            val isTerminal = intent.getBooleanExtra(LocalAgentIntents.EXTRA_IS_TERMINAL, false)
            val userMessage = intent.getStringExtra(LocalAgentIntents.EXTRA_USER_MESSAGE)

            if (!status.isNullOrBlank()) {
                LocalAgentPrefs.setStatus(this@MainActivity, status)
            }
            if (!lastError.isNullOrBlank()) {
                LocalAgentPrefs.setLastError(this@MainActivity, lastError)
            }

            if (isTerminal && !userMessage.isNullOrBlank()) {
                Toast.makeText(this@MainActivity, userMessage, Toast.LENGTH_SHORT).show()
            }

            refreshAgentStatusUi()
        }
    }

    private fun speak(text: String) {
        speak(text, languageTag = null, utteranceId = null, onDone = null, streamType = null)
    }

    private fun speakVision(text: String, onDone: (() -> Unit)? = null) {
        speak(
            text = text,
            languageTag = ImageQuestionPreferences.get(this).appLanguageTag,
            utteranceId = null,
            onDone = onDone,
            streamType = null,
        )
    }

    private fun speak(
        text: String,
        languageTag: String? = null,
        utteranceId: String?,
        streamType: Int? = null,
        onDone: (() -> Unit)? = null,
    ) {
        val clean = text.trim()
        if (clean.isBlank()) {
            onDone?.invoke()
            return
        }
        val id = utteranceId ?: "voice_${System.currentTimeMillis()}"
        val queuedAt = android.os.SystemClock.elapsedRealtime()
        languageTag?.takeIf { it.isNotBlank() }?.let { tag ->
            Log.i("ImageQuestionAudio", "Kokoro voice=${KokoroHeartVoice.VOICE_ID} languageTag=$tag")
        }
        streamType?.let { stream ->
            Log.d("ImageQuestionAudio", "Kokoro ignores legacy streamType=$stream; AudioTrack owns routing")
        }
        AudioSessionCoordinator.markBusy()
        Log.i("AssistantTiming", "stage=voice_enqueued id=$id chars=${clean.length}")
        KokoroSpeechService.get(this).speak(
            text = clean,
            queueMode = SpeechQueueMode.FLUSH,
            utteranceId = id,
            callbacks = SpeechCallbacks(
                onStart = {
                    val now = android.os.SystemClock.elapsedRealtime()
                    val speechEndedAt = lastAssistantSpeechEndAtMs.get()
                    val speechEndToStartMs = if (speechEndedAt > 0L) now - speechEndedAt else -1L
                    Log.i(
                        "AssistantTiming",
                        "stage=voice_start id=$id enqueueToStartMs=${now - queuedAt} speechEndToVoiceStartMs=$speechEndToStartMs",
                    )
                    if (speechEndToStartMs >= 0L) {
                        lastAssistantSpeechEndAtMs.compareAndSet(speechEndedAt, 0L)
                    }
                },
                onDone = {
                    Log.i(
                        "AssistantTiming",
                        "stage=voice_done id=$id totalMs=${android.os.SystemClock.elapsedRealtime() - queuedAt}",
                    )
                    try {
                        onDone?.invoke()
                    } finally {
                        AudioSessionCoordinator.markIdle()
                    }
                },
                onStopped = {
                    Log.i("AssistantTiming", "stage=voice_stopped id=$id")
                    AudioSessionCoordinator.markIdle()
                },
                onError = { error ->
                    Log.e("ImageQuestionAudio", "Kokoro speech failed id=$id", error)
                    try {
                        onDone?.invoke()
                    } finally {
                        AudioSessionCoordinator.markIdle()
                    }
                },
            ),
        )
    }

    private fun enqueueStreamingSpeech(
        text: String,
        utteranceId: String,
        onDone: () -> Unit,
    ) {
        val clean = text.trim()
        if (clean.isBlank()) {
            onDone()
            return
        }
        val queuedAt = android.os.SystemClock.elapsedRealtime()
        AudioSessionCoordinator.markBusy()
        Log.i("AssistantTiming", "stage=voice_stream_enqueued id=$utteranceId chars=${clean.length}")
        KokoroSpeechService.get(this).speak(
            text = clean,
            queueMode = SpeechQueueMode.ADD,
            utteranceId = utteranceId,
            callbacks = SpeechCallbacks(
                onStart = {
                    Log.i(
                        "AssistantTiming",
                        "stage=voice_stream_start id=$utteranceId enqueueToStartMs=${android.os.SystemClock.elapsedRealtime() - queuedAt}",
                    )
                },
                onDone = onDone,
                onStopped = onDone,
                onError = { error ->
                    Log.e("ImageQuestionAudio", "Kokoro streaming speech failed id=$utteranceId", error)
                    onDone()
                },
            ),
        )
    }

    private fun interruptAssistantPlayback(reason: String) {
        val engine = KokoroSpeechService.get(this)
        val wasActive = engine.isSpeaking() || AudioSessionCoordinator.isBusy()
        if (!wasActive) return
        AssistantListeningCuePlayer.stop()
        engine.stop()
        AudioSessionCoordinator.markIdle()
        Log.i("AssistantTiming", "stage=voice_interrupted reason=$reason")
    }

    private fun cancelActiveAssistantTurnForVoice(reason: String) {'''
text, count = start_pattern.subn(replacement, text, count=1)
if count != 1:
    raise SystemExit(f"Expected one Activity speech-lifecycle block, replaced {count}")

# Replace onCreate's platform engine setup with a lazy/background Kokoro model prewarm.
oncreate_pattern = re.compile(
    r"        // Initialize TTS\n.*?\n\n        // Ensure we always listen for HeyCyan reports\.",
    re.DOTALL,
)
oncreate_replacement = '''        // Prewarm the offline Kokoro model/runtime. First install happens in app-private storage.
        KokoroSpeechService.get(this).prepare(
            onReady = {
                Log.i(
                    "AssistantTiming",
                    "stage=voice_ready model=${KokoroHeartVoice.MODEL_ID} voice=${KokoroHeartVoice.VOICE_ID} speaker=${KokoroHeartVoice.SPEAKER_ID}",
                )
            },
            onError = { error ->
                Log.e("ImageQuestionAudio", "Kokoro model/runtime preparation failed", error)
            },
        )

        // Ensure we always listen for HeyCyan reports.'''
text, count = oncreate_pattern.subn(oncreate_replacement, text, count=1)
if count != 1:
    raise SystemExit(f"Expected one onCreate Android speech block, replaced {count}")

# Activity teardown stops active assistant audio but keeps the process-wide model reusable by
# notification announcements and future Activity instances.
old_destroy = '''        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
        resetTtsAudioState()
'''
new_destroy = '''        AssistantListeningCuePlayer.stop()
        KokoroSpeechService.get(this).stop()
        AudioSessionCoordinator.markIdle()
'''
if old_destroy not in text:
    raise SystemExit("Could not find onDestroy Android speech cleanup")
text = text.replace(old_destroy, new_destroy, 1)

# The image-question cue now goes through the same Kokoro AudioTrack path as every other spoken
# response. Remove the direct platform-engine AudioAttributes mutation and old utterance bookkeeping.
old_image_audio_attrs = '''            tts?.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
'''
if old_image_audio_attrs not in text:
    raise SystemExit("Could not find image-question platform speech attributes block")
text = text.replace(old_image_audio_attrs, "", 1)

old_image_fallback = '''            // Avoid blocking the image question if the system TTS service never returns a callback.
            lifecycleScope.launch {
                delay(2_000L)
                if (!completed.get()) {
                    discardTtsUtterance(utteranceId)
                    runCatching { tts?.stop() }
                    AudioSessionCoordinator.markIdle()
                }
                complete("2s fallback")
            }
            cont.invokeOnCancellation {
                discardTtsUtterance(utteranceId)
            }
'''
new_image_fallback = '''            // Avoid blocking the image question if Kokoro never returns a completion callback.
            lifecycleScope.launch {
                delay(2_000L)
                if (!completed.get()) {
                    KokoroSpeechService.get(this).stop()
                    AudioSessionCoordinator.markIdle()
                }
                complete("2s fallback")
            }
            cont.invokeOnCancellation {
                KokoroSpeechService.get(this).stop()
                AudioSessionCoordinator.markIdle()
            }
'''
if old_image_fallback not in text:
    raise SystemExit("Could not find image-question speech timeout block")
text = text.replace(old_image_fallback, new_image_fallback, 1)

old_voice_fallback = '''                onDone = { startListeningAfterCue("tts callback") },
            )
            // Do not leave Test Voice unresponsive if a TTS engine never reports completion.
            delay(VOICE_CUE_CALLBACK_TIMEOUT_MS)
            if (!listeningStarted.get()) {
                discardTtsUtterance(cueUtteranceId)
                runCatching { tts?.stop() }
                AudioSessionCoordinator.markIdle()
            }
            startListeningAfterCue("tts callback timeout")
'''
new_voice_fallback = '''                onDone = { startListeningAfterCue("voice callback") },
            )
            // Do not leave Test Voice unresponsive if Kokoro never reports completion.
            delay(VOICE_CUE_CALLBACK_TIMEOUT_MS)
            if (!listeningStarted.get()) {
                KokoroSpeechService.get(this).stop()
                AudioSessionCoordinator.markIdle()
            }
            startListeningAfterCue("voice callback timeout")
'''
if old_voice_fallback not in text:
    raise SystemExit("Could not find Test Voice speech timeout block")
text = text.replace(old_voice_fallback, new_voice_fallback, 1)

# Rename remaining helper call sites/log vocabulary away from the removed Android engine.
text = text.replace("enqueueStreamingTts", "enqueueStreamingSpeech")
text = text.replace("waitForTtsToFinish", "waitForSpeechToFinish")
text = text.replace("isTtsSpeaking", "isSpeechSpeaking")
text = text.replace("ttsRouteBluetooth", "voiceRouteBluetooth")
text = text.replace("stage=tts_", "stage=voice_")
text = text.replace("speechEndToTtsStartMs", "speechEndToVoiceStartMs")
text = text.replace("TTS still speaking", "Kokoro still speaking")
text = text.replace("tts callback", "voice callback")
text = text.replace("TTS engine", "Kokoro engine")
text = text.replace("system TTS service", "Kokoro speech engine")

# Straightforward residual runtime references map directly to Kokoro.
text = text.replace("tts?.isSpeaking == true", "KokoroSpeechService.get(this).isSpeaking()")
text = text.replace("tts?.stop()", "KokoroSpeechService.get(this).stop()")
text = text.replace(
    '"ttsReady=$ttsReady route=${audioRouteSummary(audioManager)}"',
    '"kokoroReady=${KokoroSpeechService.get(this).isModelInstalled()} route=${audioRouteSummary(audioManager)}"',
)

# Diagnostics are deliberately strict. Do not commit a half-migration.
forbidden = [
    r"android\.speech\.tts",
    r"\bTextToSpeech\b",
    r"\bUtteranceProgressListener\b",
    r"\btts\b",
    r"\bTTS\b",
    r"ttsReady",
    r"completeTtsUtterance",
    r"discardTtsUtterance",
    r"resetTtsAudioState",
    r"preferOfflineVoice",
    r"enqueueStreamingTts",
    r"waitForTtsToFinish",
    r"isTtsSpeaking",
]
leftovers: list[str] = []
for lineno, line in enumerate(text.splitlines(), start=1):
    if any(re.search(pattern, line) for pattern in forbidden):
        leftovers.append(f"{lineno}: {line}")
if leftovers:
    print("Residual Android TextToSpeech references remain after migration:")
    print("\n".join(leftovers[:160]))
    raise SystemExit(2)

if text == original:
    print("MainActivity already migrated; no changes needed")
else:
    PATH.write_text(text, encoding="utf-8")
    print(f"Migrated {PATH.relative_to(ROOT)} to Kokoro-only speech output")

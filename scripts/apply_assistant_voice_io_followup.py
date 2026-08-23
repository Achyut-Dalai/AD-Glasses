from pathlib import Path

path = Path("android/AD-Glasses/app/src/main/java/com/ad_glasses/MainActivity.kt")
text = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "import java.util.concurrent.atomic.AtomicReference\n",
    "import java.util.concurrent.atomic.AtomicReference\nimport java.util.concurrent.atomic.AtomicLong\n",
    "AtomicLong import",
)
replace_once(
    "    private val ttsEnqueuedAtMs = ConcurrentHashMap<String, Long>()\n"
    "    private val assistantRequestRouter = AssistantRequestRouter()\n",
    "    private val ttsEnqueuedAtMs = ConcurrentHashMap<String, Long>()\n"
    "    private val lastAssistantSpeechEndAtMs = AtomicLong(0L)\n"
    "    private val assistantRequestRouter = AssistantRequestRouter()\n",
    "speech-end state",
)
replace_once(
    "        private const val IMAGE_QUESTION_MAX_IMAGE_AGE_MS = 3L * 60L * 1000L\n",
    "        private const val IMAGE_QUESTION_MAX_IMAGE_AGE_MS = 45_000L\n",
    "visual context expiry",
)
replace_once(
    "            override fun onStart(utteranceId: String?) {\n"
    "                val now = android.os.SystemClock.elapsedRealtime()\n"
    "                val queuedAt = utteranceId?.let(ttsEnqueuedAtMs::get)\n"
    "                Log.i(\n"
    "                    \"AssistantTiming\",\n"
    "                    \"stage=tts_start id=$utteranceId enqueueToStartMs=${queuedAt?.let { now - it } ?: -1}\",\n"
    "                )\n",
    "            override fun onStart(utteranceId: String?) {\n"
    "                val now = android.os.SystemClock.elapsedRealtime()\n"
    "                val queuedAt = utteranceId?.let(ttsEnqueuedAtMs::get)\n"
    "                val isListeningCue = utteranceId?.startsWith(\"voice_listening_\") == true ||\n"
    "                    utteranceId?.startsWith(\"image_question_cue_\") == true\n"
    "                val speechEndedAt = lastAssistantSpeechEndAtMs.get()\n"
    "                val speechEndToStartMs = if (!isListeningCue && speechEndedAt > 0L) now - speechEndedAt else -1L\n"
    "                Log.i(\n"
    "                    \"AssistantTiming\",\n"
    "                    \"stage=tts_start id=$utteranceId enqueueToStartMs=${queuedAt?.let { now - it } ?: -1} speechEndToTtsStartMs=$speechEndToStartMs\",\n"
    "                )\n"
    "                if (speechEndToStartMs >= 0L) lastAssistantSpeechEndAtMs.compareAndSet(speechEndedAt, 0L)\n",
    "speech-end to playback metric",
)
replace_once(
    "        if (\n"
    "            offerSpokenQuestion &&\n"
    "            pendingVoiceImageQuestion.isNullOrBlank() &&\n"
    "            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED\n"
    "        ) {\n"
    "            val deferred = kotlinx.coroutines.CompletableDeferred<String?>()\n",
    "        if (\n"
    "            offerSpokenQuestion &&\n"
    "            pendingVoiceImageQuestion.isNullOrBlank() &&\n"
    "            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED\n"
    "        ) {\n"
    "            cancelActiveAssistantTurnForVoice(\"New visual voice input started\")\n"
    "            interruptAssistantPlayback(\"visual voice input\")\n"
    "            val deferred = kotlinx.coroutines.CompletableDeferred<String?>()\n",
    "visual voice barge-in",
)
replace_once(
    "                        val recognitionRequestedAtMs = android.os.SystemClock.elapsedRealtime()\n"
    "                        var speechEndedAtMs = 0L\n"
    "                        recognizer = AndroidAssistantVoiceIo.createRecognizer(this@MainActivity)\n",
    "                        val recognitionRequestedAtMs = android.os.SystemClock.elapsedRealtime()\n"
    "                        lastAssistantSpeechEndAtMs.set(0L)\n"
    "                        var speechEndedAtMs = 0L\n"
    "                        recognizer = AndroidAssistantVoiceIo.createRecognizer(this@MainActivity)\n",
    "image speech metric reset",
)
replace_once(
    "                            override fun onEndOfSpeech() {\n"
    "                                speechEndedAtMs = android.os.SystemClock.elapsedRealtime()\n"
    "                                Log.i(\"AssistantTiming\", \"stage=asr_speech_end surface=image_question\")\n"
    "                            }\n",
    "                            override fun onEndOfSpeech() {\n"
    "                                speechEndedAtMs = android.os.SystemClock.elapsedRealtime()\n"
    "                                lastAssistantSpeechEndAtMs.set(speechEndedAtMs)\n"
    "                                Log.i(\"AssistantTiming\", \"stage=asr_speech_end surface=image_question\")\n"
    "                            }\n",
    "image speech metric capture",
)
replace_once(
    "        val recognitionRequestedAtMs = android.os.SystemClock.elapsedRealtime()\n"
    "        var speechEndedAtMs = 0L\n"
    "        val recognizer = AndroidAssistantVoiceIo.createRecognizer(this)\n",
    "        val recognitionRequestedAtMs = android.os.SystemClock.elapsedRealtime()\n"
    "        lastAssistantSpeechEndAtMs.set(0L)\n"
    "        var speechEndedAtMs = 0L\n"
    "        val recognizer = AndroidAssistantVoiceIo.createRecognizer(this)\n",
    "voice speech metric reset",
)
replace_once(
    "            override fun onEndOfSpeech() {\n"
    "                speechEndedAtMs = android.os.SystemClock.elapsedRealtime()\n"
    "                Log.i(\"AssistantTiming\", \"stage=asr_speech_end surface=voice_query\")\n"
    "            }\n",
    "            override fun onEndOfSpeech() {\n"
    "                speechEndedAtMs = android.os.SystemClock.elapsedRealtime()\n"
    "                lastAssistantSpeechEndAtMs.set(speechEndedAtMs)\n"
    "                Log.i(\"AssistantTiming\", \"stage=asr_speech_end surface=voice_query\")\n"
    "            }\n",
    "voice speech metric capture",
)
replace_once(
    "                    \"Image is ${ageMs / 60000} min old — too old to use.\",\n",
    "                    \"That view is ${ageMs / 1000}s old, so I won’t use it as current visual context.\",\n",
    "stale visual message",
)

path.write_text(text)
print("Applied assistant voice I/O follow-up patch")

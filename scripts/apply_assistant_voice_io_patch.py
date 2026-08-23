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
    "import com.ad_glasses.ai.AiWakeWordPreferences\n",
    "import com.ad_glasses.ai.AiWakeWordPreferences\n"
    "import com.ad_glasses.ai.AndroidAssistantVoiceIo\n",
    "voice IO import",
)
replace_once(
    "import com.ad_glasses.ai.orchestrator.AssistantInputSurface\n"
    "import com.ad_glasses.ai.orchestrator.AssistantOrchestrator\n"
    "import com.ad_glasses.ai.orchestrator.AssistantTurn\n",
    "import com.ad_glasses.ai.orchestrator.AssistantConversationSession\n"
    "import com.ad_glasses.ai.orchestrator.AssistantInputSurface\n"
    "import com.ad_glasses.ai.orchestrator.AssistantOrchestrator\n"
    "import com.ad_glasses.ai.orchestrator.AssistantTurn\n"
    "import com.ad_glasses.ai.orchestrator.AssistantTurnCoordinator\n",
    "assistant coordinator imports",
)
replace_once(
    "    private var tts: TextToSpeech? = null\n"
    "    private var ttsReady = false\n"
    "    private val ttsDoneCallbacks = ConcurrentHashMap<String, () -> Unit>()\n",
    "    private var tts: TextToSpeech? = null\n"
    "    private var ttsReady = false\n"
    "    private val ttsDoneCallbacks = ConcurrentHashMap<String, () -> Unit>()\n"
    "    private val ttsEnqueuedAtMs = ConcurrentHashMap<String, Long>()\n",
    "TTS timing state",
)
replace_once(
    "        if (status == TextToSpeech.SUCCESS) {\n"
    "            tts?.language = Locale.getDefault()\n",
    "        if (status == TextToSpeech.SUCCESS) {\n"
    "            tts?.let { AndroidAssistantVoiceIo.preferOfflineVoice(it, Locale.getDefault()) }\n",
    "offline TTS initialization",
)
replace_once(
    "        languageTag?.takeIf { it.isNotBlank() }?.let { tag ->\n"
    "            val result = engine?.setLanguage(Locale.forLanguageTag(tag))\n"
    "            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {\n"
    "                Log.w(TAG, \"Text-to-speech voice unavailable for $tag\")\n"
    "            }\n"
    "            Log.i(\"ImageQuestionAudio\", \"TTS language tag=$tag result=$result\")\n"
    "        }\n",
    "        languageTag?.takeIf { it.isNotBlank() }?.let { tag ->\n"
    "            val locale = Locale.forLanguageTag(tag)\n"
    "            val offlineVoice = engine?.let { AndroidAssistantVoiceIo.preferOfflineVoice(it, locale) }\n"
    "            Log.i(\n"
    "                \"ImageQuestionAudio\",\n"
    "                \"TTS language tag=$tag offlineVoice=${offlineVoice?.name ?: \"fallback\"}\",\n"
    "            )\n"
    "        }\n",
    "per-language offline TTS preference",
)
replace_once(
    "        AudioSessionCoordinator.markBusy()\n"
    "        val result = runCatching {\n"
    "            engine?.speak(text, TextToSpeech.QUEUE_FLUSH, bundle, id)\n",
    "        AudioSessionCoordinator.markBusy()\n"
    "        ttsEnqueuedAtMs[id] = android.os.SystemClock.elapsedRealtime()\n"
    "        Log.i(\"AssistantTiming\", \"stage=tts_enqueued id=$id chars=${text.length}\")\n"
    "        val result = runCatching {\n"
    "            engine?.speak(text, TextToSpeech.QUEUE_FLUSH, bundle, id)\n",
    "TTS enqueue timing",
)
replace_once(
    "    private fun completeTtsUtterance(utteranceId: String?) {\n"
    "        utteranceId?.let { id -> ttsDoneCallbacks.remove(id)?.invoke() }\n"
    "    }\n\n"
    "    private fun discardTtsUtterance(utteranceId: String?) {\n"
    "        utteranceId?.let(ttsDoneCallbacks::remove)\n"
    "    }\n\n"
    "    private fun resetTtsAudioState() {\n"
    "        ttsDoneCallbacks.clear()\n"
    "        AudioSessionCoordinator.markIdle()\n"
    "    }\n",
    "    private fun completeTtsUtterance(utteranceId: String?) {\n"
    "        utteranceId?.let { id ->\n"
    "            ttsEnqueuedAtMs.remove(id)\n"
    "            ttsDoneCallbacks.remove(id)?.invoke()\n"
    "        }\n"
    "    }\n\n"
    "    private fun discardTtsUtterance(utteranceId: String?) {\n"
    "        utteranceId?.let { id ->\n"
    "            ttsEnqueuedAtMs.remove(id)\n"
    "            ttsDoneCallbacks.remove(id)\n"
    "        }\n"
    "    }\n\n"
    "    private fun resetTtsAudioState() {\n"
    "        ttsDoneCallbacks.clear()\n"
    "        ttsEnqueuedAtMs.clear()\n"
    "        AudioSessionCoordinator.markIdle()\n"
    "    }\n\n"
    "    private fun interruptAssistantPlayback(reason: String) {\n"
    "        val wasActive = tts?.isSpeaking == true || AudioSessionCoordinator.isBusy()\n"
    "        if (!wasActive) return\n"
    "        runCatching { tts?.stop() }\n"
    "            .onFailure { error -> Log.w(\"ImageQuestionAudio\", \"Could not interrupt TTS\", error) }\n"
    "        resetTtsAudioState()\n"
    "        Log.i(\"AssistantTiming\", \"stage=tts_interrupted reason=$reason\")\n"
    "    }\n\n"
    "    private fun cancelActiveAssistantTurnForVoice(reason: String) {\n"
    "        runCatching {\n"
    "            AssistantTurnCoordinator.cancelActive(\n"
    "                AssistantConversationSession.get(this).activeThreadId(),\n"
    "                reason,\n"
    "            )\n"
    "        }.onFailure { error ->\n"
    "            Log.w(\"AssistantTiming\", \"Could not cancel active assistant turn\", error)\n"
    "        }\n"
    "    }\n",
    "TTS cleanup and barge-in helpers",
)
replace_once(
    "        private const val VOICE_CUE_ROUTE_SETTLE_MS = 500L\n"
    "        private const val VOICE_CUE_BLUETOOTH_TAIL_MS = 50L\n"
    "        private const val VOICE_CUE_CALLBACK_TIMEOUT_MS = 3_000L\n",
    "        private const val VOICE_CUE_ROUTE_SETTLE_MS = 250L\n"
    "        private const val VOICE_CUE_BLUETOOTH_TAIL_MS = 50L\n"
    "        private const val VOICE_CUE_CALLBACK_TIMEOUT_MS = 2_000L\n",
    "voice cue latency",
)
replace_once(
    "            override fun onStart(utteranceId: String?) {\n"
    "                if (utteranceId?.startsWith(\"image_question_cue_\") == true) {\n"
    "                    Log.i(\"ImageQuestionAudio\", \"TTS cue started id=$utteranceId\")\n"
    "                }\n"
    "            }\n",
    "            override fun onStart(utteranceId: String?) {\n"
    "                val now = android.os.SystemClock.elapsedRealtime()\n"
    "                val queuedAt = utteranceId?.let(ttsEnqueuedAtMs::get)\n"
    "                Log.i(\n"
    "                    \"AssistantTiming\",\n"
    "                    \"stage=tts_start id=$utteranceId enqueueToStartMs=${queuedAt?.let { now - it } ?: -1}\",\n"
    "                )\n"
    "                if (utteranceId?.startsWith(\"image_question_cue_\") == true) {\n"
    "                    Log.i(\"ImageQuestionAudio\", \"TTS cue started id=$utteranceId\")\n"
    "                }\n"
    "            }\n",
    "TTS start timing",
)
replace_once(
    "            override fun onDone(utteranceId: String?) {\n"
    "                if (utteranceId?.startsWith(\"image_question_cue_\") == true) {\n"
    "                    Log.i(\"ImageQuestionAudio\", \"TTS cue completed id=$utteranceId\")\n"
    "                }\n"
    "                completeTtsUtterance(utteranceId)\n"
    "            }\n",
    "            override fun onDone(utteranceId: String?) {\n"
    "                val now = android.os.SystemClock.elapsedRealtime()\n"
    "                val queuedAt = utteranceId?.let(ttsEnqueuedAtMs::get)\n"
    "                Log.i(\n"
    "                    \"AssistantTiming\",\n"
    "                    \"stage=tts_done id=$utteranceId totalMs=${queuedAt?.let { now - it } ?: -1}\",\n"
    "                )\n"
    "                if (utteranceId?.startsWith(\"image_question_cue_\") == true) {\n"
    "                    Log.i(\"ImageQuestionAudio\", \"TTS cue completed id=$utteranceId\")\n"
    "                }\n"
    "                completeTtsUtterance(utteranceId)\n"
    "            }\n",
    "TTS completion timing",
)
replace_once(
    "                        recognizer = SpeechRecognizer.createSpeechRecognizer(this@MainActivity)\n"
    "                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {\n"
    "                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)\n"
    "                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)\n"
    "                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguageTag())\n"
    "                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, recognitionLanguageTag())\n"
    "                            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)\n"
    "                            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)\n"
    "                            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)\n"
    "                        }\n",
    "                        val recognitionRequestedAtMs = android.os.SystemClock.elapsedRealtime()\n"
    "                        var speechEndedAtMs = 0L\n"
    "                        recognizer = AndroidAssistantVoiceIo.createRecognizer(this@MainActivity)\n"
    "                        val intent = AndroidAssistantVoiceIo.recognitionIntent(recognitionLanguageTag())\n",
    "image-question recognizer policy",
)
replace_once(
    "                            override fun onReadyForSpeech(params: Bundle?) {\n"
    "                                Log.i(\"ImageQuestionAudio\", \"Image-question recognizer ready\")\n"
    "                            }\n",
    "                            override fun onReadyForSpeech(params: Bundle?) {\n"
    "                                Log.i(\n"
    "                                    \"AssistantTiming\",\n"
    "                                    \"stage=asr_ready surface=image_question elapsedMs=${android.os.SystemClock.elapsedRealtime() - recognitionRequestedAtMs}\",\n"
    "                                )\n"
    "                                Log.i(\"ImageQuestionAudio\", \"Image-question recognizer ready\")\n"
    "                            }\n",
    "image ASR ready timing",
)
replace_once(
    "                            override fun onEndOfSpeech() {}\n\n"
    "                            override fun onError(error: Int) {\n"
    "                                Log.i(\"AIHijack\", \"Image question listener ended with error code=$error\")\n",
    "                            override fun onEndOfSpeech() {\n"
    "                                speechEndedAtMs = android.os.SystemClock.elapsedRealtime()\n"
    "                                Log.i(\"AssistantTiming\", \"stage=asr_speech_end surface=image_question\")\n"
    "                            }\n\n"
    "                            override fun onError(error: Int) {\n"
    "                                Log.i(\"AIHijack\", \"Image question listener ended with error code=$error\")\n",
    "image ASR end timing",
)
replace_once(
    "                                Log.i(\"ImageQuestionAudio\", \"Image-question recognizer resultCount=${matches?.size ?: 0}\")\n"
    "                                finish(matches?.firstOrNull())\n",
    "                                val finalAtMs = android.os.SystemClock.elapsedRealtime()\n"
    "                                Log.i(\n"
    "                                    \"AssistantTiming\",\n"
    "                                    \"stage=asr_final surface=image_question afterEndMs=${if (speechEndedAtMs > 0L) finalAtMs - speechEndedAtMs else -1} totalMs=${finalAtMs - recognitionRequestedAtMs}\",\n"
    "                                )\n"
    "                                Log.i(\"ImageQuestionAudio\", \"Image-question recognizer resultCount=${matches?.size ?: 0}\")\n"
    "                                finish(matches?.firstOrNull())\n",
    "image ASR final timing",
)
replace_once(
    "        releaseActiveVoiceRecognition(\"new voice query\")\n"
    "        prepareAiQuestionForLockScreen()\n",
    "        cancelActiveAssistantTurnForVoice(\"New voice input started\")\n"
    "        interruptAssistantPlayback(\"new voice input\")\n"
    "        releaseActiveVoiceRecognition(\"new voice query\")\n"
    "        prepareAiQuestionForLockScreen()\n",
    "voice barge-in",
)
replace_once(
    "        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)\n"
    "        activeVoiceRecognizer = recognizer\n"
    "        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {\n"
    "            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)\n"
    "            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)\n"
    "            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguageTag())\n"
    "            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, recognitionLanguageTag())\n"
    "        }\n",
    "        val recognitionRequestedAtMs = android.os.SystemClock.elapsedRealtime()\n"
    "        var speechEndedAtMs = 0L\n"
    "        val recognizer = AndroidAssistantVoiceIo.createRecognizer(this)\n"
    "        activeVoiceRecognizer = recognizer\n"
    "        val intent = AndroidAssistantVoiceIo.recognitionIntent(recognitionLanguageTag())\n",
    "voice-query recognizer policy",
)
replace_once(
    "            override fun onReadyForSpeech(params: Bundle?) {\n"
    "                Log.i(\"ImageQuestionAudio\", \"Voice-query recognizer ready after listening cue\")\n"
    "            }\n\n"
    "            override fun onBeginningOfSpeech() {}\n"
    "            override fun onRmsChanged(rmsdB: Float) {}\n"
    "            override fun onBufferReceived(buffer: ByteArray?) {}\n"
    "            override fun onEndOfSpeech() {}\n",
    "            override fun onReadyForSpeech(params: Bundle?) {\n"
    "                Log.i(\n"
    "                    \"AssistantTiming\",\n"
    "                    \"stage=asr_ready surface=voice_query elapsedMs=${android.os.SystemClock.elapsedRealtime() - recognitionRequestedAtMs}\",\n"
    "                )\n"
    "                Log.i(\"ImageQuestionAudio\", \"Voice-query recognizer ready after listening cue\")\n"
    "            }\n\n"
    "            override fun onBeginningOfSpeech() {\n"
    "                Log.i(\"AssistantTiming\", \"stage=asr_speech_begin surface=voice_query\")\n"
    "            }\n"
    "            override fun onRmsChanged(rmsdB: Float) {}\n"
    "            override fun onBufferReceived(buffer: ByteArray?) {}\n"
    "            override fun onEndOfSpeech() {\n"
    "                speechEndedAtMs = android.os.SystemClock.elapsedRealtime()\n"
    "                Log.i(\"AssistantTiming\", \"stage=asr_speech_end surface=voice_query\")\n"
    "            }\n",
    "voice ASR timing callbacks",
)
# The normal voice result block has a blank-prompt check immediately after extracting the first result.
needle = """            override fun onResults(results: Bundle?) {\n                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)\n                val prompt = matches?.firstOrNull()?.trim().orEmpty()\n\n                if (prompt.isBlank()) {\n"""
replacement = """            override fun onResults(results: Bundle?) {\n                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)\n                val prompt = matches?.firstOrNull()?.trim().orEmpty()\n                val finalAtMs = android.os.SystemClock.elapsedRealtime()\n                Log.i(\n                    \"AssistantTiming\",\n                    \"stage=asr_final surface=voice_query afterEndMs=${if (speechEndedAtMs > 0L) finalAtMs - speechEndedAtMs else -1} totalMs=${finalAtMs - recognitionRequestedAtMs}\",\n                )\n\n                if (prompt.isBlank()) {\n"""
replace_once(needle, replacement, "voice ASR final timing")
replace_once(
    "        cancelParallelAudioQuestion()\n"
    "        releaseActiveVoiceRecognition(\"activity destroyed\")\n"
    "        finishAiQuestionForegroundWork()\n",
    "        cancelParallelAudioQuestion()\n"
    "        cancelActiveAssistantTurnForVoice(\"Activity destroyed\")\n"
    "        releaseActiveVoiceRecognition(\"activity destroyed\")\n"
    "        finishAiQuestionForegroundWork()\n",
    "destroy assistant cancellation",
)

path.write_text(text)
print("Applied Android assistant voice I/O patch")

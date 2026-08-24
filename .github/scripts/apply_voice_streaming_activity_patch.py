from pathlib import Path

path = Path("android/AD-Glasses/app/src/main/java/com/ad_glasses/MainActivity.kt")
text = path.read_text()

# 1) Normal glasses voice must go straight from finalized transcript to the orchestrator.
start = text.index("    private suspend fun runMemoryAwareChosenProviderQuery(")
end = text.index("    private fun resolveImageQuestionPrompt", start)
new_query_function = '''    private suspend fun runMemoryAwareChosenProviderQuery(
        userPrompt: String,
        providerType: AgentProviderType,
        imagePaths: List<String> = emptyList(),
        audioPath: String? = null,
        onToken: ((String) -> Unit)? = null,
    ): String {
        // Normal glasses Ask is transcript-first: once Moonshine finalizes text, go straight into
        // the orchestrator. Personal-memory/artifact construction is deliberately not on this
        // latency-critical path; the executor already supplies the bounded native chat history.
        if (imagePaths.isEmpty() && audioPath.isNullOrBlank()) {
            val result = AssistantOrchestrator(
                context = this,
                executor = AndroidAssistantCapabilityExecutor(this, onToken = onToken),
            ).handle(
                turn = AssistantTurn(
                    text = userPrompt,
                    surface = AssistantInputSurface.GLASSES_VOICE,
                    contextText = null,
                    webRequested = null,
                ),
                providerType = providerType,
            )
            return if (onToken != null) {
                // Streaming speech consumes the provider deltas, but finalization still needs the
                // complete rich answer so the buffer can safely flush its remaining spoken tail.
                result.richText.trim().ifBlank { result.spokenText.trim() }
            } else {
                result.spokenText.trim().ifBlank { result.richText.trim() }
            }
        }

        // Media requests keep their explicit context path. Normal voice never reaches here.
        val date = todayDateString()
        val languageTag = recognitionLanguageTag()
        val systemPrompt = buildString {
            append(buildCompactMemoryAwareSystemPrompt(queryText = userPrompt, date = date))
            append("\\n\\n")
            append(ImageQuestionDefaults.responseLanguageInstruction(languageTag))
        }
        val messages = listOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to userPrompt),
        )
        return ApiTokenClient.chat(
            context = this,
            messages = messages,
            imagePaths = imagePaths,
            audioPath = audioPath,
        ).getOrElse {
            "Cloud AI unavailable (${it.message ?: it::class.java.simpleName})."
        }.trim()
    }

'''
text = text[:start] + new_query_function + text[end:]

# 2) Streamed segments use QUEUE_ADD. The normal speak() path remains QUEUE_FLUSH so a new user
# action can still interrupt stale speech immediately.
helper_anchor = "    private fun completeTtsUtterance(utteranceId: String?) {\n"
if "    private fun enqueueStreamingTts(" not in text:
    helper = '''    private fun enqueueStreamingTts(
        text: String,
        utteranceId: String,
        onDone: () -> Unit,
    ) {
        val clean = text.trim()
        if (clean.isBlank()) {
            onDone()
            return
        }
        val engine = tts
        val languageTag = recognitionLanguageTag()
        val locale = Locale.forLanguageTag(languageTag)
        engine?.let { AndroidAssistantVoiceIo.preferOfflineVoice(it, locale) }
        ttsDoneCallbacks[utteranceId] = onDone

        val bundle = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        AudioSessionCoordinator.markBusy()
        ttsEnqueuedAtMs[utteranceId] = android.os.SystemClock.elapsedRealtime()
        Log.i(
            "AssistantTiming",
            "stage=tts_stream_enqueued id=$utteranceId chars=${clean.length}",
        )
        val result = runCatching {
            engine?.speak(clean, TextToSpeech.QUEUE_ADD, bundle, utteranceId)
        }.onFailure { error ->
            Log.e("ImageQuestionAudio", "Streaming TTS enqueue threw id=$utteranceId", error)
        }.getOrNull()
        if (result != TextToSpeech.SUCCESS) {
            Log.w("ImageQuestionAudio", "Streaming TTS enqueue failed id=$utteranceId result=$result")
            completeTtsUtterance(utteranceId)
        }
    }

'''
    if helper_anchor not in text:
        raise SystemExit("TTS helper anchor not found")
    text = text.replace(helper_anchor, helper + helper_anchor, 1)

# 3) Replace only the ANSWER_QUESTION branch inside triggerInternalVoiceQuery. Deltas are sanitized
# cumulatively and converted to natural sentence/phrase segments before TTS sees them.
voice_start = text.index("    private fun triggerInternalVoiceQuery(")
answer_start = text.index("                            AssistantIntent.ANSWER_QUESTION -> {", voice_start)
answer_end = text.index("\n\n                            AssistantIntent.ANALYZE_IMAGE", answer_start)
new_answer_branch = '''                            AssistantIntent.ANSWER_QUESTION -> {
                                val speechBuffer = com.ad_glasses.ai.orchestrator.AssistantStreamingSpeechBuffer()
                                val pendingSpeech = java.util.concurrent.atomic.AtomicInteger(0)
                                val generationFinished = AtomicBoolean(false)
                                val cleanupFinished = AtomicBoolean(false)
                                val segmentCounter = java.util.concurrent.atomic.AtomicInteger(0)

                                fun finishWhenDrained() {
                                    if (!generationFinished.get() || pendingSpeech.get() != 0) return
                                    if (!cleanupFinished.compareAndSet(false, true)) return
                                    runOnUiThread {
                                        AudioSessionCoordinator.markIdle()
                                        stopSco()
                                        finishAiQuestionForegroundWork()
                                    }
                                }

                                fun enqueueSegments(segments: List<String>) {
                                    segments.forEach { segment ->
                                        val number = segmentCounter.incrementAndGet()
                                        pendingSpeech.incrementAndGet()
                                        val id = "voice_stream_${System.nanoTime()}_$number"
                                        runOnUiThread {
                                            enqueueStreamingTts(
                                                text = segment,
                                                utteranceId = id,
                                                onDone = {
                                                    pendingSpeech.decrementAndGet()
                                                    finishWhenDrained()
                                                },
                                            )
                                        }
                                    }
                                }

                                val reply = runMemoryAwareChosenProviderQuery(
                                    userPrompt = prompt,
                                    providerType = selectedProvider,
                                    onToken = { delta ->
                                        enqueueSegments(speechBuffer.accept(delta))
                                    },
                                )
                                enqueueSegments(speechBuffer.finish(reply))
                                generationFinished.set(true)
                                finishWhenDrained()
                            }'''
text = text[:answer_start] + new_answer_branch + text[answer_end:]

# 4) Arm the output route immediately after the transcript is final, while Cloud generation begins.
# This overlaps Bluetooth route settling with provider TTFT instead of adding it after generation.
dispatch_anchor = '''                destroyActiveVoiceRecognizer(recognizer)
                lifecycleScope.launch(Dispatchers.IO) {
'''
dispatch_replacement = '''                destroyActiveVoiceRecognizer(recognizer)
                val streamingRouteSelected = AndroidAssistantVoiceIo.prepareSpeechOutputRoute(this@MainActivity)
                Log.i(
                    "AssistantTiming",
                    "stage=voice_transcript_dispatch delayMs=${android.os.SystemClock.elapsedRealtime() - finalAtMs} " +
                        "ttsRouteBluetooth=$streamingRouteSelected",
                )
                lifecycleScope.launch(Dispatchers.IO) {
'''
voice_tail = text[voice_start:]
if dispatch_anchor not in voice_tail:
    raise SystemExit("voice transcript dispatch anchor not found")
voice_tail = voice_tail.replace(dispatch_anchor, dispatch_replacement, 1)
text = text[:voice_start] + voice_tail

path.write_text(text)

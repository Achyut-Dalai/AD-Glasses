from pathlib import Path
import re

PATH = Path("android/AD-Glasses/app/src/main/java/com/ad_glasses/MainActivity.kt")
text = PATH.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    if new in text and old not in text:
        print(f"already applied: {label}")
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    text = text.replace(old, new, 1)
    print(f"applied: {label}")


def sub_once(pattern: str, replacement: str, label: str) -> None:
    global text
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"{label}: expected one regex match, found {count}")
    text = updated
    print(f"applied: {label}")


# Retired external-assistant gallery handoff helpers are dead after the Cloud API/Local migration.
text = text.replace("import android.media.MediaScannerConnection\n", "")
text = text.replace("import android.os.Environment\n", "")
sub_once(
    r'\n    /\*\*\n     \* Copy an image file to DCIM/Camera/ with the Glasses_AI_ naming convention\..*?\n    private suspend fun captureOptionalImageQuestionFromBluetoothMic',
    '\n    private suspend fun captureOptionalImageQuestionFromBluetoothMic',
    'remove retired external-assistant image helpers',
)

# Activity-owned references let onDestroy deterministically release SpeechRecognizer and the
# Bluetooth communication route even if Android never delivers onError/onResults.
replace_once(
    '''    private var activeParallelAudioQuestionDeferred: kotlinx.coroutines.CompletableDeferred<String?>? = null
    private var activeParallelAudioQuestionJob: Job? = null
''',
    '''    private var activeParallelAudioQuestionDeferred: kotlinx.coroutines.CompletableDeferred<String?>? = null
    private var activeParallelAudioQuestionJob: Job? = null
    private var activeVoiceRecognizer: SpeechRecognizer? = null
    private var activeVoiceAudioManager: android.media.AudioManager? = null
''',
    'add activity-owned voice recognition state',
)
replace_once(
    '''        cancelParallelAudioQuestion()
        finishAiQuestionForegroundWork()
''',
    '''        cancelParallelAudioQuestion()
        releaseActiveVoiceRecognition("activity destroyed")
        finishAiQuestionForegroundWork()
''',
    'release voice recognition on Activity destruction',
)

# The optional image-question recognizer used a lifecycleScope cleanup from its cancellation
# handler. During Activity destruction that scope is already cancelled, so SCO/recognizer cleanup
# could be skipped. Make cancellation cleanup post directly to the main thread instead.
sub_once(
    r'    private suspend fun captureOptionalImageQuestionFromBluetoothMic\(timeoutMs: Long\): String\? \{.*?\n    \}\n\n    private suspend fun playImageQuestionTone',
    '''    private suspend fun captureOptionalImageQuestionFromBluetoothMic(timeoutMs: Long): String? {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                Log.i(
                    "ImageQuestionAudio",
                    "Starting image-question microphone timeoutMs=$timeoutMs route=${audioRouteSummary(audioManager)}",
                )
                var recognizer: SpeechRecognizer? = null
                var timeoutJob: Job? = null
                var finished = false
                var heardSpeech = false

                fun cleanup() {
                    runCatching { recognizer?.cancel() }
                    runCatching { recognizer?.destroy() }
                    recognizer = null
                    runCatching { clearVoiceAudioRoute(audioManager) }
                    Log.i(
                        "ImageQuestionAudio",
                        "Image-question microphone route cleared: ${audioRouteSummary(audioManager)}",
                    )
                }

                fun finish(result: String?, playCompletionTone: Boolean = true) {
                    if (finished) return
                    finished = true
                    timeoutJob?.cancel()
                    timeoutJob = null
                    val cleaned = result?.trim()?.takeIf { it.isNotBlank() }
                    Log.i(
                        "ImageQuestionAudio",
                        "Image-question microphone finished heardSpeech=$heardSpeech resultLength=${cleaned?.length ?: 0}",
                    )
                    if (!cont.isActive) {
                        cleanup()
                        return
                    }
                    lifecycleScope.launch {
                        try {
                            if (playCompletionTone) {
                                playImageQuestionTone(android.media.ToneGenerator.TONE_PROP_BEEP2)
                            }
                        } finally {
                            cleanup()
                            if (cont.isActive) cont.resume(cleaned)
                        }
                    }
                }

                startBluetoothMicRoute(audioManager)

                lifecycleScope.launch {
                    try {
                        playImageQuestionTone(android.media.ToneGenerator.TONE_PROP_BEEP)
                        speakImageQuestionCue()
                        if (finished || !cont.isActive) return@launch

                        Log.i("ImageQuestionAudio", "Cue complete; creating speech recognizer")
                        recognizer = SpeechRecognizer.createSpeechRecognizer(this@MainActivity)
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguageTag())
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, recognitionLanguageTag())
                            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)
                            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)
                            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
                        }

                        recognizer?.setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                Log.i("ImageQuestionAudio", "Image-question recognizer ready")
                            }
                            override fun onBeginningOfSpeech() {
                                heardSpeech = true
                                Log.i("ImageQuestionAudio", "Image-question speech detected")
                                timeoutJob?.cancel()
                                timeoutJob = null
                            }
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {}

                            override fun onError(error: Int) {
                                Log.i("AIHijack", "Image question listener ended with error code=$error")
                                finish(null)
                            }

                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                Log.i("ImageQuestionAudio", "Image-question recognizer resultCount=${matches?.size ?: 0}")
                                finish(matches?.firstOrNull())
                            }

                            override fun onPartialResults(partialResults: Bundle?) {}
                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })

                        timeoutJob = lifecycleScope.launch(Dispatchers.Main) {
                            delay(timeoutMs)
                            if (!heardSpeech) finish(null)
                        }
                        recognizer?.startListening(intent)
                    } catch (error: CancellationException) {
                        if (!finished) {
                            finished = true
                            timeoutJob?.cancel()
                            timeoutJob = null
                            cleanup()
                        }
                        throw error
                    } catch (error: Exception) {
                        Log.e("ImageQuestionAudio", "Unable to start image-question recognizer", error)
                        finish(null, playCompletionTone = false)
                    }
                }

                cont.invokeOnCancellation {
                    runOnUiThread {
                        if (!finished) {
                            finished = true
                            timeoutJob?.cancel()
                            timeoutJob = null
                            cleanup()
                        }
                    }
                }
            }
        }
    }

    private suspend fun playImageQuestionTone''',
    'harden optional image-question recognizer cancellation',
)

# Share route cleanup helpers between the main voice recognizer and image-question recognizer.
replace_once(
    '''    private fun triggerInternalVoiceQuery(chosenProviderType: AgentProviderType) {
''',
    '''    private fun clearVoiceAudioRoute(audioManager: android.media.AudioManager) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            audioManager.mode = android.media.AudioManager.MODE_NORMAL
        }.onFailure { error ->
            Log.w("ImageQuestionAudio", "Unable to clear voice audio route", error)
        }
        if (activeVoiceAudioManager === audioManager) activeVoiceAudioManager = null
    }

    private fun destroyActiveVoiceRecognizer(recognizer: SpeechRecognizer? = activeVoiceRecognizer) {
        if (recognizer == null) return
        if (activeVoiceRecognizer === recognizer) activeVoiceRecognizer = null
        runCatching { recognizer.cancel() }
        runCatching { recognizer.destroy() }
    }

    private fun releaseActiveVoiceRecognition(reason: String) {
        val recognizer = activeVoiceRecognizer
        val audioManager = activeVoiceAudioManager
        activeVoiceRecognizer = null
        activeVoiceAudioManager = null
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        audioManager?.let(::clearVoiceAudioRoute)
        if (recognizer != null || audioManager != null) {
            Log.i("ImageQuestionAudio", "Released active voice recognition: $reason")
        }
    }

    private fun triggerInternalVoiceQuery(chosenProviderType: AgentProviderType) {
''',
    'add deterministic voice resource cleanup helpers',
)

# Main voice recognition owns its recognizer/SCO route across asynchronous inference and TTS.
replace_once(
    '''        prepareAiQuestionForLockScreen()
        beginAiQuestionForegroundWork("Listening for glasses voice question", usesPhoneMicrophone = true)
''',
    '''        releaseActiveVoiceRecognition("new voice query")
        prepareAiQuestionForLockScreen()
        beginAiQuestionForegroundWork("Listening for glasses voice question", usesPhoneMicrophone = true)
''',
    'release any prior voice session before listening',
)
replace_once(
    '''        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

        fun stopSco() {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                }
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
                audioManager.mode = android.media.AudioManager.MODE_NORMAL
            }
        }
''',
    '''        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        activeVoiceAudioManager = audioManager

        fun stopSco() {
            clearVoiceAudioRoute(audioManager)
        }
''',
    'track main voice audio route',
)
replace_once(
    '''        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
''',
    '''        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        activeVoiceRecognizer = recognizer
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
''',
    'track main voice recognizer',
)
text = text.replace('                recognizer.destroy()\n                stopSco()\n                finishAiQuestionForegroundWork()', '                destroyActiveVoiceRecognizer(recognizer)\n                stopSco()\n                finishAiQuestionForegroundWork()', 1)
text = text.replace('                    recognizer.destroy()\n                    stopSco()\n                    finishAiQuestionForegroundWork()', '                    destroyActiveVoiceRecognizer(recognizer)\n                    stopSco()\n                    finishAiQuestionForegroundWork()', 1)
replace_once(
    '''                lifecycleScope.launch(Dispatchers.IO) {
''',
    '''                destroyActiveVoiceRecognizer(recognizer)
                lifecycleScope.launch(Dispatchers.IO) {
''',
    'destroy recognizer before async voice inference',
)
# Remove the old unconditional destroy after starting inference; ownership has already been cleared.
text = text.replace('\n                recognizer.destroy()\n            }\n\n            override fun onPartialResults', '\n            }\n\n            override fun onPartialResults', 1)
replace_once(
    '''                Log.i("ImageQuestionAudio", "Starting voice recognizer after listening cue reason=$reason")
                recognizer.startListening(intent)
''',
    '''                if (isFinishing || isDestroyed || activeVoiceRecognizer !== recognizer) return@launch
                Log.i("ImageQuestionAudio", "Starting voice recognizer after listening cue reason=$reason")
                runCatching { recognizer.startListening(intent) }
                    .onFailure { error ->
                        Log.e("ImageQuestionAudio", "Unable to start voice recognizer", error)
                        destroyActiveVoiceRecognizer(recognizer)
                        stopSco()
                        finishAiQuestionForegroundWork()
                    }
''',
    'guard delayed recognizer start against destroyed Activity',
)

PATH.write_text(text, encoding="utf-8")
print("MainActivity phase-3 audit transformer completed")

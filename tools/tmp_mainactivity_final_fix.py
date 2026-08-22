from pathlib import Path

path = Path("android/AD-Glasses/app/src/main/java/com/ad_glasses/MainActivity.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    text = text.replace(old, new, 1)
    print(f"applied: {label}")


replace_once(
    '''    private fun completeTtsUtterance(utteranceId: String?) {
        utteranceId?.let { id -> completeTtsUtterance(id) }
    }
''',
    '''    private fun completeTtsUtterance(utteranceId: String?) {
        utteranceId?.let { id -> ttsDoneCallbacks.remove(id)?.invoke() }
    }
''',
    'fix TTS completion recursion',
)

meta_block = '''            prepareAiQuestionForLockScreen()
            beginAiQuestionForegroundWork(
                "Capturing image from Meta glasses",
                usesPhoneMicrophone = pendingImageQuestionOfferSpokenQuestion &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            )
'''
doubled = meta_block + meta_block
replace_once(doubled, meta_block, 'remove duplicate Meta foreground start')

replace_once(
    '''                fun cleanup() {
                    runCatching { recognizer?.cancel() }
                    runCatching { recognizer?.destroy() }
                    recognizer = null
                    runCatching { clearVoiceAudioRoute(audioManager) }
''',
    '''                var cleanupDone = false
                fun cleanup() {
                    if (cleanupDone) return
                    cleanupDone = true
                    runCatching { recognizer?.cancel() }
                    runCatching { recognizer?.destroy() }
                    recognizer = null
                    clearVoiceAudioRoute(audioManager)
''',
    'make optional recognizer cleanup idempotent',
)

replace_once(
    '''                    lifecycleScope.launch {
                        try {
                            if (playCompletionTone) {
                                playImageQuestionTone(android.media.ToneGenerator.TONE_PROP_BEEP2)
                            }
                        } finally {
                            cleanup()
                            if (cont.isActive) cont.resume(cleaned)
                        }
                    }
''',
    '''                    val completionJob = lifecycleScope.launch {
                        if (playCompletionTone) {
                            playImageQuestionTone(android.media.ToneGenerator.TONE_PROP_BEEP2)
                        }
                    }
                    completionJob.invokeOnCompletion {
                        runOnUiThread {
                            cleanup()
                            if (cont.isActive) cont.resume(cleaned)
                        }
                    }
''',
    'guarantee optional recognizer cleanup when lifecycle cancels',
)

replace_once(
    '''    private fun destroyActiveVoiceRecognizer(recognizer: SpeechRecognizer? = activeVoiceRecognizer) {
        if (recognizer == null) return
        if (activeVoiceRecognizer === recognizer) activeVoiceRecognizer = null
        runCatching { recognizer.cancel() }
        runCatching { recognizer.destroy() }
    }
''',
    '''    private fun destroyActiveVoiceRecognizer(recognizer: SpeechRecognizer? = activeVoiceRecognizer) {
        if (recognizer == null) return
        if (activeVoiceRecognizer === recognizer) activeVoiceRecognizer = null
        runCatching { recognizer.destroy() }
    }
''',
    'avoid cancel callback after successful recognition',
)

path.write_text(text, encoding="utf-8")
print("final MainActivity audit corrections applied")

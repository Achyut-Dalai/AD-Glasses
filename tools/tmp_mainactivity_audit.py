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
    if count == 0:
        print(f"no regex change (possibly already applied): {label}")
        return
    text = updated
    print(f"applied: {label}")


# Rebrand callback correctness: WelcomeActivity/manifest use ad-glasses://.
replace_once(
    '        if (!callbackIntent.data?.scheme.equals("ADGlasses", ignoreCase = true)) return false',
    '        if (!callbackIntent.data?.scheme.equals("ad-glasses", ignoreCase = true)) return false',
    "Meta callback URI scheme",
)

# Remove the retired consumer-assistant mode state. It no longer participates in routing.
text = text.replace(
    '    private var isImageAssistantMode = true // Use assistant vs share intent\n',
    '',
)
sub_once(
    r'\n        binding\.cbImageAsAssistant\.isChecked = isImageAssistantMode\n'
    r'        binding\.cbImageAsAssistant\.text = if \(isImageAssistantMode\) "Direct Assistant" else "App Sharing"\n'
    r'\s*\n'
    r'        binding\.cbImageAsAssistant\.setOnCheckedChangeListener \{ _, isChecked ->\n'
    r'            isImageAssistantMode = isChecked\n'
    r'            val modeName = if \(isChecked\) "Direct Assistant" else "App Sharing"\n'
    r'            binding\.cbImageAsAssistant\.text = modeName\n'
    r'            Toast\.makeText\(this, "Image Hijack: \$modeName", Toast\.LENGTH_SHORT\)\.show\(\)\n'
    r'        \}\n',
    '\n',
    "retired Direct Assistant/App Sharing UI state",
)

# TTS callbacks must be removed exactly once. QUEUE_FLUSH interruption should not retain stale
# follow-up closures; Activity destruction should also clear the global audio-busy state.
text = text.replace(
    'utteranceId?.let { ttsDoneCallbacks.remove(it)?.invoke() }',
    'completeTtsUtterance(utteranceId)',
)
text = text.replace(
    'ttsDoneCallbacks.remove(id)?.invoke()',
    'completeTtsUtterance(id)',
)
if 'override fun onStop(utteranceId: String?, interrupted: Boolean)' not in text:
    pattern = (
        r'(            override fun onError\(utteranceId: String\?, errorCode: Int\) \{\n'
        r'.*?\n            \})\n        \}\)'
    )
    replacement = r'''\1

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                localSpeechSessionManager.speechQueueController.onUtteranceError(utteranceId)
                Log.i("ImageQuestionAudio", "TTS stopped id=$utteranceId interrupted=$interrupted")
                discardTtsUtterance(utteranceId)
            }
        })'''
    sub_once(pattern, replacement, "TTS interruption callback cleanup")

if 'private fun completeTtsUtterance(utteranceId: String?)' not in text:
    marker = '    companion object {\n'
    helpers = '''    private fun completeTtsUtterance(utteranceId: String?) {
        utteranceId?.let { id -> ttsDoneCallbacks.remove(id)?.invoke() }
    }

    private fun discardTtsUtterance(utteranceId: String?) {
        utteranceId?.let(ttsDoneCallbacks::remove)
    }

    private fun resetTtsAudioState() {
        ttsDoneCallbacks.clear()
        AudioSessionCoordinator.markIdle()
    }

'''
    if marker not in text:
        raise SystemExit("TTS helper insertion marker missing")
    text = text.replace(marker, helpers + marker, 1)
    print("applied: TTS helper functions")

replace_once(
    '''        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
''',
    '''        cancelParallelAudioQuestion()
        finishAiQuestionForegroundWork()
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
        resetTtsAudioState()
        super.onDestroy()
    }
''',
    "Activity/TTS terminal cleanup",
)

# UI-owned long-running work should be cancelled with the Activity.
text = text.replace(
    '        batteryPollJob = CoroutineScope(Dispatchers.Main).launch {',
    '        batteryPollJob = lifecycleScope.launch(Dispatchers.Main) {',
)
text = text.replace(
    '        val queryJob = CoroutineScope(Dispatchers.IO).launch {',
    '        val queryJob = lifecycleScope.launch(Dispatchers.IO) {',
)
text = text.replace(
    '            CoroutineScope(Dispatchers.IO).launch {\n                try {\n                    val thumbnailSize',
    '            lifecycleScope.launch(Dispatchers.IO) {\n                try {\n                    val thumbnailSize',
)
text = text.replace(
    '        CoroutineScope(Dispatchers.IO).launch {\n            var thumbnailTransferStarted',
    '        lifecycleScope.launch(Dispatchers.IO) {\n            var thumbnailTransferStarted',
)
text = text.replace(
    '        CoroutineScope(Dispatchers.IO).launch {\n            var initialQuestion',
    '        lifecycleScope.launch(Dispatchers.IO) {\n            var initialQuestion',
)
text = text.replace(
    '                CoroutineScope(Dispatchers.IO).launch {\n                    val selectedProvider = chosenProviderType',
    '                lifecycleScope.launch(Dispatchers.IO) {\n                    val selectedProvider = chosenProviderType',
)

# The foreground host already supports a microphone service type; MainActivity must request it for
# voice capture. Other call sites remain source-compatible through the default argument.
replace_once(
    '''    private fun beginAiQuestionForegroundWork(status: String) {
        AiQuestionForegroundService.start(this, status)
    }
''',
    '''    private fun beginAiQuestionForegroundWork(
        status: String,
        usesPhoneMicrophone: Boolean = false,
    ) {
        AiQuestionForegroundService.start(this, status, usesPhoneMicrophone = usesPhoneMicrophone)
    }
''',
    "foreground microphone service typing",
)
text = text.replace(
    '        beginAiQuestionForegroundWork("Listening for glasses voice question")',
    '        beginAiQuestionForegroundWork("Listening for glasses voice question", usesPhoneMicrophone = true)',
)

# Never send HeyCyan/Oudmon command bytes to Eyevue, Meta, or MYVU.
if 'Stopped Eyevue voice recognition for $source' not in text:
    replace_once(
        '''    private fun stopGlassesAiAudio(source: String) {
        if (isMetaRaybanSelected()) {
            // Meta audio is managed by DAT/Android audio routing; never send Oudmon
            // command bytes to a Meta wearable.
            Log.d("AIHijack", "Skipping HeyCyan AI-audio stop for Meta ($source)")
            return
        }
''',
        '''    private fun stopGlassesAiAudio(source: String) {
        if (isEyevueSelected()) {
            getOrCreateEyevueManager().stopVoiceRecognition()
            Log.d("AIHijack", "Stopped Eyevue voice recognition for $source")
            return
        }
        if (isMetaRaybanSelected() || isMeizuMyvuSelected()) {
            Log.d("AIHijack", "Skipping HeyCyan AI-audio stop for the selected non-HeyCyan device ($source)")
            return
        }
''',
        "device-specific AI audio transport",
    )

# The wake-word handler used to issue one stop command and then triggerInternalVoiceQuery issued a
# second. Voice now lets the actual voice routine own that command; image stops once before capture.
sub_once(
    r'    private fun handleAiWakeWordActivation\(source: String\) \{.*?\n    \}\n\n    private fun triggerAssistantVoiceQuery',
    '''    private fun handleAiWakeWordActivation(source: String) {
        val route = AiWakeWordPreferences.getRoute(this)
        Log.i("AIHijack", "Wake-word activation source=$source route=${route.wireName}")
        runOnUiThread {
            when (route) {
                AiWakeWordRoute.VOICE_QUESTION -> triggerAssistantVoiceQuery()
                AiWakeWordRoute.IMAGE_QUESTION -> {
                    stopGlassesAiAudio("$source wake-word image route")
                    handleGlassesImageButtonPressed(
                        triggerCapture = true,
                        sourceTag = "${source}_wake_word",
                        source = ImageQuestionSourcePolicy.defaultSource(),
                        thumbnailQuality = ImageQuestionSourcePolicy.defaultThumbnailQuality(),
                        offerSpokenQuestion = true,
                    )
                }
            }
        }
    }

    private fun triggerAssistantVoiceQuery''',
    "single-owner wake-word stop command",
)

# MYVU's registered adapter exposes display, battery, microphone and speaker capabilities, but no
# camera transport. Disable image questions instead of falling into HeyCyan camera commands.
if 'Image questions are unavailable for MYVU' not in text:
    replace_once(
        '''    private fun imageQueryUnsupportedReasonForCurrentSelection(): String? {
        if (currentAssistantRoute() != GlassesAssistantRoute.LOCAL) return null
''',
        '''    private fun imageQueryUnsupportedReasonForCurrentSelection(): String? {
        if (isMeizuMyvuSelected()) {
            return "Image questions are unavailable for MYVU because its current transport does not expose camera capture."
        }
        if (currentAssistantRoute() != GlassesAssistantRoute.LOCAL) return null
''',
        "MYVU camera capability gate",
    )

# A timestamp debounce could drop a legitimate new captured image while leaving foreground work
# active. The atomic guard is sufficient. A duplicate must never clear the guard owned by the
# original request.
text = text.replace('    private var lastImageQueryAtMs: Long = 0L\n', '')
sub_once(
    r'    private fun triggerAssistantImageQuery\(\n'
    r'        imagePath: String,\n'
    r'        userQuestion: String\? = null,\n'
    r'        source: ImageQuestionSource = ImageQuestionSourcePolicy\.defaultSource\(\),\n'
    r'        onReplySpoken: \(\(\) -> Unit\)\? = null,\n'
    r'    \) \{.*?\n    \}\n\n    private fun analyzeSyncedCapture',
    '''    private fun triggerAssistantImageQuery(
        imagePath: String,
        userQuestion: String? = null,
        source: ImageQuestionSource = ImageQuestionSourcePolicy.defaultSource(),
        onReplySpoken: (() -> Unit)? = null,
    ) {
        if (!imageQueryInProgress.compareAndSet(false, true)) {
            Log.w("AIHijack", "Image query already in progress; ignoring duplicate request")
            return
        }

        val usesPhoneMicrophone = onReplySpoken != null &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        beginAiQuestionForegroundWork(
            "Analyzing glasses image",
            usesPhoneMicrophone = usesPhoneMicrophone,
        )

        try {
            val resolvedPrompt = resolveImageQuestionPrompt(userQuestion)
            val providerType = when (currentAssistantRoute()) {
                GlassesAssistantRoute.LOCAL -> AgentProviderType.LOCAL_AGENT
                GlassesAssistantRoute.CLOUD -> AgentProviderType.CLOUD_AI
            }
            Log.i("AIHijack", "Starting image query source=${source.wireName} provider=$providerType")
            triggerMemoryAwareImageQuery(
                imagePath = imagePath,
                providerType = providerType,
                resolvedPrompt = resolvedPrompt,
                onReplySpoken = onReplySpoken,
            )
        } catch (error: Exception) {
            imageQueryInProgress.set(false)
            finishAiQuestionForegroundWork()
            Log.e("AIHijack", "Could not start image query", error)
            Toast.makeText(
                this,
                "Could not start image analysis: ${error.message ?: "unknown error"}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun analyzeSyncedCapture''',
    "image query concurrency guard",
)

# General vendor notifications must be long enough before index 6 or deeper fields are read.
if 'Ignoring short device notification' not in text:
    replace_once(
        '''        override fun parseData(cmdType: Int, response: GlassesDeviceNotifyRsp) {
            Log.i(
                "DeviceNotify",
                "cmdType=$cmdType, loadData=${response.loadData.joinToString(separator = ",") { it.toInt().toString() }}"
            )
            if (otaManager.isActive) {
''',
        '''        override fun parseData(cmdType: Int, response: GlassesDeviceNotifyRsp) {
            val load = response.loadData
            Log.i(
                "DeviceNotify",
                "cmdType=$cmdType, loadData=${load.joinToString(separator = ",") { it.toInt().toString() }}"
            )
            if (load.size < 7) {
                Log.w("DeviceNotify", "Ignoring short device notification: cmdType=$cmdType size=${load.size}")
                return
            }
            if (otaManager.isActive) {
''',
        "base device-notification length guard",
    )
    text = text.replace('            when (response.loadData[6].toInt()) {', '            when (load[6].toInt()) {', 1)

if 'Ignoring short battery notification' not in text:
    replace_once(
        '''                0x05 -> {
                    //Current battery
                    val battery = response.loadData[7].toInt()
                    //Is it charging
                    val changing = response.loadData[8].toInt()
''',
        '''                0x05 -> {
                    if (load.size < 9) {
                        Log.w("DeviceNotify", "Ignoring short battery notification: size=${load.size}")
                        return
                    }
                    //Current battery
                    val battery = load[7].toInt()
                    //Is it charging
                    val changing = load[8].toInt()
''',
        "battery notification length guard",
    )

if 'Ignoring short volume notification' not in text:
    text = text.replace(
        '''                0x12 -> {
                    //Music volume
''',
        '''                0x12 -> {
                    if (load.size < 20) {
                        Log.w("DeviceNotify", "Ignoring short volume notification: size=${load.size}")
                        return
                    }
                    //Music volume
''',
        1,
    )
text = text.replace(
    '                    if (response.loadData[7].toInt() == 1) {\n                        //to do\n                    }',
    '                    if (load.size > 7 && load[7].toInt() == 1) {\n                        //to do\n                    }',
)
text = text.replace(
    '                        Log.i("DeviceNotify", "AI Button Pressed - Hijacking to Phone Assistant")',
    '                        Log.i("DeviceNotify", "AI Button Pressed - routing to AD assistant")',
)

# Human-facing branding only; technical identifiers remain ADGlasses where hyphens/spaces are invalid.
text = text.replace('ADGlasses settings first.', 'AD Glasses settings first.')
text = text.replace('ADGlasses accessibility control first.', 'AD Glasses accessibility control first.')
text = text.replace('ADGlasses got stuck before media transfer started.', 'AD Glasses got stuck before media transfer started.')
text = text.replace('ADGlasses found other Wi‑Fi Direct devices', 'AD Glasses found other Wi‑Fi Direct devices')
text = text.replace('preventing ADGlasses from discovering the glasses', 'preventing AD Glasses from discovering the glasses')
text = text.replace('send the logs to the ADGlasses server', 'send the logs to the AD Glasses server')
text = text.replace('ADGlasses has not sent a preview automatically.', 'AD Glasses has not sent a preview automatically.')

PATH.write_text(text, encoding="utf-8")

# Final forward-architecture assertions for this critical file.
final = PATH.read_text(encoding="utf-8")
for forbidden in (
    'CliRelayClient',
    'CloudAiPrefs',
    'ImageQuestionRoute.PRO_RELAY',
    'PHONE_ASSISTANT',
    'isImageAssistantMode',
    'lastImageQueryAtMs',
):
    if forbidden in final:
        raise SystemExit(f"retired MainActivity symbol remains: {forbidden}")
for required in (
    'scheme.equals("ad-glasses"',
    'usesPhoneMicrophone = true',
    'Stopped Eyevue voice recognition for $source',
    'Image query already in progress; ignoring duplicate request',
    'Ignoring short device notification',
):
    if required not in final:
        raise SystemExit(f"required audited invariant missing: {required}")

print("MainActivity audit transformer completed")

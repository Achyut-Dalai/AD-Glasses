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


# Vendor listeners are HeyCyan-specific. MYVU has its own manager/transport just like Meta/Eyevue.
replace_once(
    '        if (!isMetaRaybanSelected() && !isEyevueSelected()) {\n            LargeDataHandler.getInstance().addOutDeviceListener(100, deviceNotifyListener)\n        }',
    '        if (!isMetaRaybanSelected() && !isEyevueSelected() && !isMeizuMyvuSelected()) {\n            LargeDataHandler.getInstance().addOutDeviceListener(100, deviceNotifyListener)\n        }',
    'exclude MYVU from HeyCyan notify listener',
)

# Give one layer ownership of stopping a device microphone. Voice query stops its own source; image
# wake stops before capture. This avoids duplicate HeyCyan commands and duplicate Eyevue stop calls.
sub_once(
    r'    private fun handleAiWakeWordActivation\(source: String\) \{\n'
    r'        if \(!isAiHijackEnabled\) return\n'
    r'        val route = AiWakeWordPreferences\.route\(this\)\n'
    r'        Log\.i\("AIHijack", "AI wake activation source=\$source route=\$route"\)\n'
    r'        runOnUiThread \{.*?\n        \}\n    \}',
    '''    private fun handleAiWakeWordActivation(source: String) {
        if (!isAiHijackEnabled) return
        val route = AiWakeWordPreferences.route(this)
        Log.i("AIHijack", "AI wake activation source=$source route=$route")
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
    }''',
    'single-owner wake-word transport stop',
)

# Centralize image capability validation so hardware wake, voice routing and dashboard taps cannot
# bypass the same selected-device/provider constraints.
replace_once(
    '''        pendingImageQuestionSource = source
        pendingImageThumbnailQuality = thumbnailQuality
        pendingImageCaptureStartedAtMs = System.currentTimeMillis()
        pendingImageQuestionOfferSpokenQuestion = offerSpokenQuestion
        if (isMetaRaybanSelected()) {
''',
    '''        pendingImageQuestionSource = source
        pendingImageThumbnailQuality = thumbnailQuality
        pendingImageCaptureStartedAtMs = System.currentTimeMillis()
        pendingImageQuestionOfferSpokenQuestion = offerSpokenQuestion
        imageQueryUnsupportedReasonForCurrentSelection()?.let { reason ->
            clearPendingVoiceImageQuestion(sourceTag)
            finishAiQuestionForegroundWork()
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            return
        }
        if (isMetaRaybanSelected()) {
''',
    'central image capability gate',
)

# HeyCyan capture should not start bounded foreground work until it owns the vendor command slot.
replace_once(
    '''        if (isGlassesCommandBlocked("AI image capture")) {
            clearPendingVoiceImageQuestion(sourceTag)
            return
        }
        if (!BleOperateManager.getInstance().isConnected) {
            clearPendingVoiceImageQuestion(sourceTag)
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Glasses are not connected. Connect first to use image query.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            return
        }
        prepareAiQuestionForLockScreen()
        beginAiQuestionForegroundWork("Capturing image from glasses")

        if (triggerCapture) {
            val permit = acquireBackgroundGlassesCommand("AI image capture") ?: return
            if (imageThumbnailRequestInProgress.get() ||
                !imageCaptureAwaitingNotification.compareAndSet(false, true)
            ) {
                GlassesSessionCoordinator.releaseBackgroundCommand(permit)
                Log.i("AIHijack", "[$sourceTag] Image capture already in progress")
                return
            }

            pendingImageCaptureSourceTag = sourceTag
''',
    '''        if (isGlassesCommandBlocked("AI image capture")) {
            clearPendingVoiceImageQuestion(sourceTag)
            finishAiQuestionForegroundWork()
            return
        }
        if (!BleOperateManager.getInstance().isConnected) {
            clearPendingVoiceImageQuestion(sourceTag)
            finishAiQuestionForegroundWork()
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Glasses are not connected. Connect first to use image query.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            return
        }

        if (triggerCapture) {
            val permit = acquireBackgroundGlassesCommand("AI image capture") ?: run {
                clearPendingVoiceImageQuestion(sourceTag)
                finishAiQuestionForegroundWork()
                return
            }
            if (imageThumbnailRequestInProgress.get() ||
                !imageCaptureAwaitingNotification.compareAndSet(false, true)
            ) {
                GlassesSessionCoordinator.releaseBackgroundCommand(permit)
                Log.i("AIHijack", "[$sourceTag] Image capture already in progress")
                return
            }

            prepareAiQuestionForLockScreen()
            beginAiQuestionForegroundWork(
                "Capturing image from glasses",
                usesPhoneMicrophone = offerSpokenQuestion &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            )
            pendingImageCaptureSourceTag = sourceTag
''',
    'HeyCyan capture foreground ownership',
)
replace_once(
    '''        } else {
            requestSelectedImageSourceForQuestion(sourceTag)
        }
    }

    private fun requestSelectedImageSourceForQuestion''',
    '''        } else {
            prepareAiQuestionForLockScreen()
            beginAiQuestionForegroundWork(
                "Receiving image from glasses",
                usesPhoneMicrophone = offerSpokenQuestion &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            )
            requestSelectedImageSourceForQuestion(sourceTag)
        }
    }

    private fun requestSelectedImageSourceForQuestion''',
    'hardware-ready image foreground ownership',
)

# Meta and Eyevue do not pass through the generic HeyCyan foreground setup.
replace_once(
    '''            val manager = getOrCreateMetaRaybanManager()
            lifecycleScope.launch(Dispatchers.IO) {
''',
    '''            prepareAiQuestionForLockScreen()
            beginAiQuestionForegroundWork(
                "Capturing image from Meta glasses",
                usesPhoneMicrophone = pendingImageQuestionOfferSpokenQuestion &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            )
            val manager = getOrCreateMetaRaybanManager()
            lifecycleScope.launch(Dispatchers.IO) {
''',
    'Meta image foreground ownership',
)
replace_once(
    '''                }.onFailure { error ->
                    clearPendingVoiceImageQuestion(sourceTag)
                    withContext(Dispatchers.Main) {
''',
    '''                }.onFailure { error ->
                    clearPendingVoiceImageQuestion(sourceTag)
                    finishAiQuestionForegroundWork()
                    withContext(Dispatchers.Main) {
''',
    'Meta image terminal cleanup',
)
replace_once(
    '''        if (!manager.isConnected()) {
            clearPendingVoiceImageQuestion(sourceTag)
            Toast.makeText(this, "Connect Eyevue glasses first.", Toast.LENGTH_SHORT).show()
            return
        }
''',
    '''        if (!manager.isConnected()) {
            clearPendingVoiceImageQuestion(sourceTag)
            finishAiQuestionForegroundWork()
            Toast.makeText(this, "Connect Eyevue glasses first.", Toast.LENGTH_SHORT).show()
            return
        }
''',
    'Eyevue disconnected cleanup',
)
replace_once(
    '        beginAiQuestionForegroundWork("Capturing image from Eyevue glasses")',
    '''        beginAiQuestionForegroundWork(
            "Capturing image from Eyevue glasses",
            usesPhoneMicrophone = offerSpokenQuestion &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )''',
    'Eyevue microphone foreground type',
)

# Thumbnail acquisition terminal paths must release/stop the bounded question state. Do not stop
# an existing transfer merely because a duplicate request arrives.
replace_once(
    '''    private fun requestImageThumbnailForQuestion(sourceTag: String) {
        if (isGlassesCommandBlocked("AI thumbnail request")) return
        val permit = pendingImageCapturePermit.getAndSet(null)
            ?: acquireBackgroundGlassesCommand("AI thumbnail request")
            ?: return
''',
    '''    private fun requestImageThumbnailForQuestion(sourceTag: String) {
        if (isGlassesCommandBlocked("AI thumbnail request")) {
            clearPendingVoiceImageQuestion(sourceTag)
            finishAiQuestionForegroundWork()
            return
        }
        val permit = pendingImageCapturePermit.getAndSet(null)
            ?: acquireBackgroundGlassesCommand("AI thumbnail request")
            ?: run {
                clearPendingVoiceImageQuestion(sourceTag)
                finishAiQuestionForegroundWork()
                return
            }
''',
    'thumbnail early terminal cleanup',
)
replace_once(
    '''        Log.i("AIHijack", "[$sourceTag] Requesting BLE thumbnail")
        if (isGlassesCommandBlocked("AI thumbnail request")) return false
        try {
''',
    '''        Log.i("AIHijack", "[$sourceTag] Requesting BLE thumbnail")
        if (isGlassesCommandBlocked("AI thumbnail request")) {
            GlassesSessionCoordinator.releaseBackgroundCommand(permit)
            return false
        }
        try {
''',
    'thumbnail late-block permit release',
)

# Invalid/stale media is terminal and must cancel the parallel microphone and foreground work.
replace_once(
    '''        if (metrics == null) {
            pendingVoiceImageQuestion = null
            Log.e("AIHijack", "Image file is missing or invalid: $imagePath (${imageFile.length()} bytes)")
''',
    '''        if (metrics == null) {
            clearPendingVoiceImageQuestion("invalid_image")
            pendingVoiceImageQuestion = null
            finishAiQuestionForegroundWork()
            Log.e("AIHijack", "Image file is missing or invalid: $imagePath (${imageFile.length()} bytes)")
''',
    'invalid image terminal cleanup',
)
replace_once(
    '''        if (ageMs > IMAGE_QUESTION_MAX_IMAGE_AGE_MS || ageMs < 0) {
            pendingVoiceImageQuestion = null
            Log.w("AIHijack", "Image too old: age=${ageMs / 1000}s, path=$imagePath")
''',
    '''        if (ageMs > IMAGE_QUESTION_MAX_IMAGE_AGE_MS || ageMs < 0) {
            clearPendingVoiceImageQuestion("stale_image")
            pendingVoiceImageQuestion = null
            finishAiQuestionForegroundWork()
            Log.w("AIHijack", "Image too old: age=${ageMs / 1000}s, path=$imagePath")
''',
    'stale image terminal cleanup',
)

# Preserve structured cancellation. runCatching catches CancellationException, which caused an
# Activity cancellation to become a spoken "vision failed" response and could keep question work alive.
replace_once(
    '''                val outcome = runCatching {
                    AssistantOrchestrator(
                        context = this@MainActivity,
                        executor = AndroidAssistantCapabilityExecutor(this@MainActivity),
                    ).handle(
                        turn = AssistantTurn(
                            text = routePrompt,
                            surface = AssistantInputSurface.GLASSES_VISION,
                            imagePath = imagePath,
                            contextText = systemContext,
                            webRequested = false,
                        ),
                        providerType = providerType,
                    )
                }
                val finalReply = outcome.fold(
                    onSuccess = { it.spokenText.trim().ifBlank { it.richText.trim() } },
                    onFailure = { error ->
                        Log.e("AIHijack", "Image query failed without provider fallback", error)
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Vision error: ${(error.message ?: "unknown error").take(100)}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        "I couldn't analyze that image with the selected route. I did not send it to another provider."
                    },
                )
''',
    '''                val finalReply = try {
                    AssistantOrchestrator(
                        context = this@MainActivity,
                        executor = AndroidAssistantCapabilityExecutor(this@MainActivity),
                    ).handle(
                        turn = AssistantTurn(
                            text = routePrompt,
                            surface = AssistantInputSurface.GLASSES_VISION,
                            imagePath = imagePath,
                            contextText = systemContext,
                            webRequested = false,
                        ),
                        providerType = providerType,
                    ).let { result -> result.spokenText.trim().ifBlank { result.richText.trim() } }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e("AIHijack", "Image query failed without provider fallback", error)
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Vision error: ${(error.message ?: "unknown error").take(100)}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    "I couldn't analyze that image with the selected route. I did not send it to another provider."
                }
''',
    'preserve image coroutine cancellation',
)
replace_once(
    '''                runOnUiThread {
                    if (providerType == AgentProviderType.LOCAL_AGENT) {
                        localSpeechSessionId?.let(localSpeechSessionManager::onModelGenerationCompleted)
                    } else {
                        speakVision(replyToSpeak, onDone = onSpeechCompleted)
                    }
                }
            } finally {
                imageQueryInProgress.set(false)
            }
''',
    '''                runOnUiThread {
                    if (providerType == AgentProviderType.LOCAL_AGENT) {
                        localSpeechSessionId?.let(localSpeechSessionManager::onModelGenerationCompleted)
                    } else {
                        speakVision(replyToSpeak, onDone = onSpeechCompleted)
                    }
                }
            } catch (error: CancellationException) {
                finishAiQuestionForegroundWork()
                throw error
            } catch (error: Exception) {
                Log.e("AIHijack", "Image query pipeline failed", error)
                finishAiQuestionForegroundWork()
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Image question failed: ${error.message ?: "unknown error"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } finally {
                imageQueryInProgress.set(false)
            }
''',
    'image query terminal cleanup',
)

# A missing TTS callback must not leave the cue itself speaking while the recognizer starts.
replace_once(
    '''            lifecycleScope.launch {
                delay(2_000L)
                complete("2s fallback")
            }
            cont.invokeOnCancellation {
                ttsDoneCallbacks.remove(utteranceId)
            }
''',
    '''            lifecycleScope.launch {
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
''',
    'image cue timeout cleanup',
)
replace_once(
    '''            // Do not leave Test Voice unresponsive if a TTS engine never reports completion.
            delay(VOICE_CUE_CALLBACK_TIMEOUT_MS)
            startListeningAfterCue("tts callback timeout")
''',
    '''            // Do not leave Test Voice unresponsive if a TTS engine never reports completion.
            delay(VOICE_CUE_CALLBACK_TIMEOUT_MS)
            if (!listeningStarted.get()) {
                discardTtsUtterance(cueUtteranceId)
                runCatching { tts?.stop() }
                AudioSessionCoordinator.markIdle()
            }
            startListeningAfterCue("tts callback timeout")
''',
    'voice cue timeout cleanup',
)

# Voice inference owns the bounded question service until it either hands off into image capture or
# finishes speaking its terminal result. Exceptions must not strand SCO or foreground state.
old_voice = '''                lifecycleScope.launch(Dispatchers.IO) {
                    val selectedProvider = chosenProviderType
                    val routing = assistantRequestRouter.route(
                        context = this@MainActivity,
                        request = AssistantRequest(
                            text = prompt,
                            source = AssistantRequestSource.GLASSES_VOICE,
                        ),
                        providerType = selectedProvider,
                    )

                    when (routing.intent) {
                        AssistantIntent.ANSWER_QUESTION -> {
                            val reply = runMemoryAwareChosenProviderQuery(
                                userPrompt = prompt,
                                providerType = selectedProvider,
                            )

                            runOnUiThread {
                                speakVision(reply) {
                                    stopSco()
                                    finishAiQuestionForegroundWork()
                                }
                            }
                        }

                        AssistantIntent.ANALYZE_IMAGE -> runOnUiThread {
                            stopSco()
                            val unsupportedReason = imageQueryUnsupportedReasonForCurrentSelection()
                            if (unsupportedReason != null) {
                                speak(unsupportedReason)
                                return@runOnUiThread
                            }
                            pendingVoiceImageQuestion = routing.normalizedGoal ?: prompt
                            speak("Okay. I'll check what you see.")
                            handleGlassesImageButtonPressed(
                                triggerCapture = true,
                                sourceTag = "voice_request",
                            )
                        }

                        AssistantIntent.EXECUTE_UI_TASK -> runOnUiThread {
                            stopSco()
                            if (!AutomationPrefs.isLocalAgentAutomationEnabled(this@MainActivity)) {
                                speak("Enable Local Agent phone control in AD Glasses settings first.")
                                return@runOnUiThread
                            }
                            if (isDeviceLockedForAutomation()) {
                                speak("Unlock your phone before I control it.")
                                return@runOnUiThread
                            }
                            if (!LocalAgentAccessibilityBridge.isConnected()) {
                                speak("Please enable AD Glasses accessibility control first.")
                                return@runOnUiThread
                            }

                            val goal = routing.normalizedGoal ?: prompt
                            val result = LocalAgentController.start(this@MainActivity, goal)
                            if (result.ok) {
                                speak("Okay. I'll do that.")
                            } else {
                                speak("I couldn't start phone control.")
                            }
                        }

                        AssistantIntent.CLARIFY -> runOnUiThread {
                            stopSco()
                            speak(
                                AssistantSpeechPolicy.clarification(routing.clarification)
                            )
                        }
                    }
                }
'''
new_voice = '''                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val selectedProvider = chosenProviderType
                        val routing = assistantRequestRouter.route(
                            context = this@MainActivity,
                            request = AssistantRequest(
                                text = prompt,
                                source = AssistantRequestSource.GLASSES_VOICE,
                            ),
                            providerType = selectedProvider,
                        )

                        when (routing.intent) {
                            AssistantIntent.ANSWER_QUESTION -> {
                                val reply = runMemoryAwareChosenProviderQuery(
                                    userPrompt = prompt,
                                    providerType = selectedProvider,
                                )

                                runOnUiThread {
                                    speakVision(reply) {
                                        stopSco()
                                        finishAiQuestionForegroundWork()
                                    }
                                }
                            }

                            AssistantIntent.ANALYZE_IMAGE -> runOnUiThread {
                                stopSco()
                                val unsupportedReason = imageQueryUnsupportedReasonForCurrentSelection()
                                if (unsupportedReason != null) {
                                    speakVision(unsupportedReason) { finishAiQuestionForegroundWork() }
                                    return@runOnUiThread
                                }
                                pendingVoiceImageQuestion = routing.normalizedGoal ?: prompt
                                speak("Okay. I'll check what you see.")
                                handleGlassesImageButtonPressed(
                                    triggerCapture = true,
                                    sourceTag = "voice_request",
                                )
                            }

                            AssistantIntent.EXECUTE_UI_TASK -> runOnUiThread {
                                stopSco()
                                if (!AutomationPrefs.isLocalAgentAutomationEnabled(this@MainActivity)) {
                                    speakVision("Enable Local Agent phone control in AD Glasses settings first.") {
                                        finishAiQuestionForegroundWork()
                                    }
                                    return@runOnUiThread
                                }
                                if (isDeviceLockedForAutomation()) {
                                    speakVision("Unlock your phone before I control it.") {
                                        finishAiQuestionForegroundWork()
                                    }
                                    return@runOnUiThread
                                }
                                if (!LocalAgentAccessibilityBridge.isConnected()) {
                                    speakVision("Please enable AD Glasses accessibility control first.") {
                                        finishAiQuestionForegroundWork()
                                    }
                                    return@runOnUiThread
                                }

                                val goal = routing.normalizedGoal ?: prompt
                                val result = LocalAgentController.start(this@MainActivity, goal)
                                speakVision(
                                    if (result.ok) "Okay. I'll do that." else "I couldn't start phone control.",
                                ) {
                                    finishAiQuestionForegroundWork()
                                }
                            }

                            AssistantIntent.CLARIFY -> runOnUiThread {
                                stopSco()
                                speakVision(AssistantSpeechPolicy.clarification(routing.clarification)) {
                                    finishAiQuestionForegroundWork()
                                }
                            }
                        }
                    } catch (error: CancellationException) {
                        runOnUiThread { stopSco() }
                        finishAiQuestionForegroundWork()
                        throw error
                    } catch (error: Exception) {
                        Log.e("AIHijack", "Voice request failed", error)
                        runOnUiThread {
                            stopSco()
                            speakVision("I couldn't complete that request with the selected AI route.") {
                                finishAiQuestionForegroundWork()
                            }
                        }
                    }
                }
'''
replace_once(old_voice, new_voice, 'voice routing terminal cleanup')

# High-quality failure is an explicit choice state. Cancellation/destroy is terminal; do not leave
# invisible background work running, and prevent outside/back dismissal that bypasses cleanup.
replace_once(
    '''    ) {
        if (isFinishing || isDestroyed) return
        check(
''',
    '''    ) {
        if (isFinishing || isDestroyed) {
            highQualityImageRequest = null
            clearPendingVoiceImageQuestion(request.sourceTag)
            finishAiQuestionForegroundWork()
            return
        }
        check(
''',
    'high-quality destroyed cleanup',
)
replace_once(
    '''                    highQualityImageRequest = null
                    clearPendingVoiceImageQuestion(request.sourceTag)
                }
            }
            .show()
''',
    '''                    highQualityImageRequest = null
                    clearPendingVoiceImageQuestion(request.sourceTag)
                    finishAiQuestionForegroundWork()
                }
            }
            .setCancelable(false)
            .show()
''',
    'high-quality explicit cancel cleanup',
)

PATH.write_text(text, encoding='utf-8')

final = PATH.read_text(encoding='utf-8')
required = (
    '!isMeizuMyvuSelected()) {',
    'stopGlassesAiAudio("$source wake-word image route")',
    'usesPhoneMicrophone = offerSpokenQuestion &&',
    'finishAiQuestionForegroundWork()\n            return\n        }\n        val permit = pendingImageCapturePermit',
    'catch (error: CancellationException)',
    '.setCancelable(false)',
)
for token in required:
    if token not in final:
        raise SystemExit(f'required phase-two invariant missing: {token}')

print('MainActivity audit phase two completed')

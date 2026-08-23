package com.ad_glasses.ai.orchestrator

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.ad_glasses.ai.AndroidAssistantVoiceIo
import com.ad_glasses.ai.router.AgentInferencePurpose
import com.ad_glasses.ai.router.AgentInferenceRouter
import kotlinx.coroutines.CancellationException

/** Android execution bridge for conversational, vision and explicit AD capability requests. */
class AndroidAssistantCapabilityExecutor(
    context: Context,
    private val onToken: ((String) -> Unit)? = null,
) : AssistantCapabilityExecutor {
    private val appContext = context.applicationContext
    private val capabilities = AndroidCapabilityCommandExecutor(context)

    override suspend fun answer(
        prompt: String,
        context: AssistantExecutionContext,
    ): AssistantResult {
        if (context.surface == AssistantInputSurface.GLASSES_VOICE) {
            clearStaleVoiceRouteWhenHeadsetMissing()
        }
        val result = try {
            AgentInferenceRouter.complete(
                context = appContext,
                purpose = AgentInferencePurpose.UI_PLANNING,
                sessionId = context.threadId,
                systemPrompt = conversationSystemPrompt(context),
                userPrompt = prompt,
                providerType = context.providerType,
                onToken = onToken,
                webRequested = context.useWeb,
                maxTokens = outputTokenLimit(context.surface),
            ).toDisplaylessResult()
        } catch (error: CancellationException) {
            // Latest-turn-wins cancellation must never turn into a spoken/persisted failure from an
            // obsolete request.
            throw error
        } catch (error: Exception) {
            Log.w(
                "AssistantTiming",
                "stage=assistant_provider_failure surface=${context.surface} type=${error::class.java.simpleName}",
                error,
            )
            providerFailureResult(error)
        }
        prepareSpeechOutputRouteIfNeeded(context)
        return result
    }

    override suspend fun analyzeImage(
        prompt: String,
        imagePath: String?,
        context: AssistantExecutionContext,
    ): AssistantResult {
        if (imagePath.isNullOrBlank()) {
            val result = AssistantResult(
                spokenText = "I don’t have a usable frame for that yet.",
                richText = "This visual request has context, but no image frame was supplied to the selected vision engine.",
            )
            prepareSpeechOutputRouteIfNeeded(context)
            return result
        }

        val result = try {
            AgentInferenceRouter.completeUiPlanning(
                context = appContext,
                sessionId = context.threadId,
                systemPrompt = conversationSystemPrompt(context),
                userPrompt = prompt,
                imagePath = imagePath,
                allowRemoteImageUpload = com.ad_glasses.localagent.LocalAgentPrefs
                    .isRemoteScreenshotUploadEnabled(appContext),
                providerType = context.providerType,
                onToken = onToken,
                webRequested = false,
                maxTokens = outputTokenLimit(context.surface),
            ).content.toDisplaylessResult()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(
                "AssistantTiming",
                "stage=assistant_provider_failure surface=${context.surface} type=${error::class.java.simpleName}",
                error,
            )
            providerFailureResult(error)
        }
        prepareSpeechOutputRouteIfNeeded(context)
        return result
    }

    override suspend fun executeCapabilityCommand(
        command: AssistantCapabilityCommand,
        context: AssistantExecutionContext,
    ): AssistantResult = capabilities.execute(command)

    /**
     * Home voice capture asks MainActivity for a Bluetooth communication route before Moonshine
     * starts. Moonshine now releases that input route as soon as it has a final transcript. This is
     * an additional safety net for a disconnected/no-headset device where a legacy SCO request may
     * otherwise leave MODE_IN_COMMUNICATION active and make TTS effectively silent.
     */
    private fun clearStaleVoiceRouteWhenHeadsetMissing() {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val hasBluetoothCommunicationDevice = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.availableCommunicationDevices.any { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
            }
        }.getOrDefault(false)
        if (hasBluetoothCommunicationDevice) return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.i("ImageQuestionAudio", "No Bluetooth communication headset; restored phone audio route before Cloud generation")
        }.onFailure { error ->
            Log.w("ImageQuestionAudio", "Could not restore phone audio route", error)
        }
    }

    /** Re-open the glasses communication output only after inference, immediately before TTS. */
    private fun prepareSpeechOutputRouteIfNeeded(context: AssistantExecutionContext) {
        if (context.surface == AssistantInputSurface.GLASSES_VOICE ||
            context.surface == AssistantInputSurface.GLASSES_VISION
        ) {
            AndroidAssistantVoiceIo.prepareSpeechOutputRoute(appContext)
        }
    }

    /**
     * Convert provider/network failures into a normal assistant result. The orchestrator can then
     * persist the failure beside the user's question and voice can speak it through the same
     * latest-turn guarded path as every successful answer, instead of falling into MainActivity's
     * delayed generic "selected route" exception speech.
     */
    private fun providerFailureResult(error: Throwable): AssistantResult {
        val detail = generateSequence(error) { it.cause }
            .joinToString(" ") { it.message.orEmpty() }
        val spoken = when {
            Regex("(?:API )?HTTP 401", RegexOption.IGNORE_CASE).containsMatchIn(detail) ->
                "Cloud AI authentication failed. Check the API key."
            Regex("(?:API )?HTTP 429", RegexOption.IGNORE_CASE).containsMatchIn(detail) ->
                "Cloud AI is rate limited right now. Try again shortly."
            Regex("(?:API )?HTTP 5\\d\\d", RegexOption.IGNORE_CASE).containsMatchIn(detail) ->
                "Cloud AI is temporarily unavailable. Try again."
            else -> "I couldn't get an answer. Try again."
        }
        return AssistantResult(spokenText = spoken, richText = spoken)
    }

    private fun conversationSystemPrompt(context: AssistantExecutionContext): String = buildString {
        appendLine("You are AD, the conversational assistant for displayless smart glasses.")
        appendLine("Answer naturally and directly. Lead with the useful answer and avoid giant tables.")
        when (context.surface) {
            AssistantInputSurface.GLASSES_VOICE,
            AssistantInputSurface.GLASSES_VISION,
            AssistantInputSurface.PHONE_VOICE -> {
                appendLine("This answer will be spoken. Default to one short sentence, usually under 30 words.")
                appendLine("Use a few short sentences only when safety, ambiguity, or an explicitly requested explanation requires it.")
                appendLine("Do not use introductions, filler, markdown, repeated conclusions, or tell the user to check the phone for the basic answer.")
            }
            AssistantInputSurface.PHONE_TEXT,
            AssistantInputSurface.AUTOMATION -> {
                appendLine("The phone may show richer detail when it is useful, but stay concise unless the request calls for depth.")
            }
        }
        appendLine("Maintain context only from the recent conversation supplied below; do not assume older omitted turns are still active.")
        appendLine("Do not ask the user to operate the phone unless genuinely needed.")
        appendLine("Do not claim to open apps, tap controls, change Android settings, or operate the phone UI. AD no longer exposes UI automation as an AI invocation method.")
        if (context.useWeb) {
            appendLine("Web access was explicitly enabled for this turn. Use the active provider's native search tool when available and ground current claims in those results.")
        }
        context.artifactContext?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("Current artifact context (trusted app context, not a user quote):")
            appendLine(it.take(AssistantInferenceContextPolicy.artifactLimit(context.surface)))
        }
        val prior = AssistantInferenceContextPolicy.priorMessages(context.history)
        if (prior.isNotEmpty()) {
            appendLine()
            appendLine("Recent conversation:")
            prior.forEach { message ->
                append(message.role.name.lowercase())
                append(": ")
                appendLine(message.content.take(AssistantInferenceContextPolicy.MAX_MESSAGE_CHARS))
            }
        }
    }

    private fun outputTokenLimit(surface: AssistantInputSurface): Int = when (surface) {
        AssistantInputSurface.GLASSES_VOICE -> 160
        AssistantInputSurface.GLASSES_VISION -> 192
        AssistantInputSurface.PHONE_VOICE -> 192
        AssistantInputSurface.PHONE_TEXT -> 512
        AssistantInputSurface.AUTOMATION -> 384
    }

    private fun String.toDisplaylessResult(): AssistantResult {
        val rich = trim()
        if (rich.isBlank()) return AssistantResult("I didn’t get a usable answer.")
        return AssistantResult(
            spokenText = AssistantSpokenResponsePolicy.forGlasses(rich),
            richText = rich,
        )
    }
}

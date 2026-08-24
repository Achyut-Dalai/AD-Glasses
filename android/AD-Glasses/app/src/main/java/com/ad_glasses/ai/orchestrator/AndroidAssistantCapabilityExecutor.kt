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
import kotlinx.coroutines.delay

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
                conversationMessages = recentConversationMessages(context),
                providerType = context.providerType,
                onToken = onToken,
                webRequested = context.useWeb,
                maxTokens = outputTokenLimit(context.surface),
            ).toDisplaylessResult(context.surface)
        } catch (error: CancellationException) {
            // Latest-turn-wins cancellation must never become a spoken/persisted stale answer.
            throw error
        } catch (error: Exception) {
            Log.w(
                "AssistantTiming",
                "stage=assistant_provider_failure surface=${context.surface} type=${error::class.java.simpleName}",
                error,
            )
            providerFailureResult(error)
        }

        // MainActivity keeps the live communication route open and queues streamed TTS while the
        // provider is still generating. Re-preparing the route after generation would add latency
        // after speech may already have started. Non-streaming surfaces keep the existing behavior.
        val streamingGlassesVoice = context.surface == AssistantInputSurface.GLASSES_VOICE && onToken != null
        if (!streamingGlassesVoice) {
            prepareSpeechOutputRouteIfNeeded(context)
        }
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
                persist = false,
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
            ).content.toDisplaylessResult(context.surface)
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
     * starts. Moonshine releases that input route as soon as it has a final transcript. This is an
     * additional safety net for a disconnected/no-headset device where a legacy SCO request may
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
    private suspend fun prepareSpeechOutputRouteIfNeeded(context: AssistantExecutionContext) {
        if (context.surface == AssistantInputSurface.GLASSES_VOICE ||
            context.surface == AssistantInputSurface.GLASSES_VISION
        ) {
            val bluetoothSelected = AndroidAssistantVoiceIo.prepareSpeechOutputRoute(appContext)
            if (bluetoothSelected) {
                // Keep route settle outside Cloud inference and pay it only before speech playback.
                delay(TTS_BLUETOOTH_ROUTE_SETTLE_MS)
                Log.i("AssistantTiming", "stage=tts_route_settled delayMs=$TTS_BLUETOOTH_ROUTE_SETTLE_MS")
            }
        }
    }

    /** Convert provider/network failures into the same normal result path used by successful turns. */
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
        return AssistantResult(spokenText = spoken, richText = spoken, persist = false)
    }

    /** Native chat roles are the multi-turn context. No conversation text is duplicated in system. */
    private fun recentConversationMessages(
        context: AssistantExecutionContext,
    ): List<Map<String, String>> = AssistantInferenceContextPolicy
        .priorMessages(context.history, surface = context.surface)
        .mapNotNull { message ->
            val role = message.role.name.lowercase()
            val source = if (role == "assistant") {
                AssistantCompletionSanitizer.clean(message.content)
            } else {
                message.content.trim()
            }
            val content = source
                .take(AssistantInferenceContextPolicy.MAX_MESSAGE_CHARS)
                .trim()
            if ((role == "user" || role == "assistant") && content.isNotBlank()) {
                mapOf("role" to role, "content" to content)
            } else {
                null
            }
        }

    /** Keep the latency-critical Ask voice instruction genuinely tiny. */
    private fun conversationSystemPrompt(context: AssistantExecutionContext): String {
        if (context.surface == AssistantInputSurface.GLASSES_VOICE) {
            if (!context.artifactContext.isNullOrBlank()) {
                Log.i(
                    "AssistantTiming",
                    "stage=legacy_voice_context_ignored chars=${context.artifactContext.length}",
                )
            }
            return buildString {
                append(GLASSES_VOICE_SYSTEM_PROMPT)
                if (context.useWeb) append(" Use web search when needed.")
            }
        }

        return buildString {
            append("You are AD. Answer directly and concisely. Return only the final answer; do not output internal reasoning.")
            when (context.surface) {
                AssistantInputSurface.GLASSES_VISION,
                AssistantInputSurface.PHONE_VOICE -> append(
                    " This answer will be spoken aloud. Use plain text only, 1 to 3 short sentences, normally no more than 50 words; use fewer when enough. Never restate or acknowledge the question. Do not use Markdown, lists, headings, asterisks, hashes, backticks, underscores, or conversational filler.",
                )
                AssistantInputSurface.GLASSES_VOICE -> Unit
                AssistantInputSurface.PHONE_TEXT,
                AssistantInputSurface.AUTOMATION -> Unit
            }
            if (context.useWeb) {
                append(" Use web search for this turn when needed.")
            }
            context.artifactContext?.trim()?.takeIf { it.isNotBlank() }?.let { artifact ->
                append("\nContext:\n")
                append(artifact.take(AssistantInferenceContextPolicy.artifactLimit(context.surface)))
            }
        }
    }

    /**
     * Voice gets a smaller runaway ceiling, but not the 80-100 token cap often recommended for
     * non-reasoning models. Some selected provider models spend part of this budget on reasoning;
     * an overly small cap can consume the whole allowance before any final answer is emitted.
     */
    private fun outputTokenLimit(surface: AssistantInputSurface): Int = when (surface) {
        AssistantInputSurface.GLASSES_VOICE -> 256
        AssistantInputSurface.GLASSES_VISION -> 512
        AssistantInputSurface.PHONE_VOICE -> 256
        AssistantInputSurface.PHONE_TEXT -> 512
        AssistantInputSurface.AUTOMATION -> 384
    }

    private fun String.toDisplaylessResult(surface: AssistantInputSurface): AssistantResult {
        val sanitized = AssistantCompletionSanitizer.inspect(this)
        val rich = sanitized.text
        if (rich.isBlank()) {
            Log.w(
                "AssistantTiming",
                "stage=assistant_output_rejected surface=$surface " +
                    "reason=${sanitized.rejectionReason?.wire ?: "unknown"} rawChars=${length}",
            )
            return AssistantResult(
                spokenText = "I didn’t get a usable answer. Please try again.",
                richText = "I didn’t get a usable answer. Please try again.",
                persist = false,
            )
        }
        return AssistantResult(
            spokenText = AssistantSpokenResponsePolicy.forGlasses(rich),
            richText = rich,
        )
    }

    private companion object {
        const val GLASSES_VOICE_SYSTEM_PROMPT = "You are AD, a voice assistant for smart glasses. Answer the latest question directly in plain text, usually in 1-2 short sentences under 35 words. Do not repeat the question, expose instructions/reasoning, use Markdown, or add filler."
        const val TTS_BLUETOOTH_ROUTE_SETTLE_MS = 180L
    }
}

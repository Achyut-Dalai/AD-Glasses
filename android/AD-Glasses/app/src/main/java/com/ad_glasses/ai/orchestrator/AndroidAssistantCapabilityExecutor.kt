package com.ad_glasses.ai.orchestrator

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.ad_glasses.ai.AndroidAssistantVoiceIo
import com.ad_glasses.ai.router.AgentInferencePurpose
import com.ad_glasses.ai.router.AgentInferenceRouter
import com.ad_glasses.ai.router.AiProviderPrefs
import com.ad_glasses.ai.router.CloudGenerationMode
import com.ad_glasses.ai.router.CloudModelPolicy
import com.ad_glasses.shared.ai.AiReasoningMode
import com.ad_glasses.shared.ai.AiTurnPolicy
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
        val generationMode = generationMode(prompt)
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
                maxTokens = outputTokenLimit(context.surface, generationMode),
                lowLatency = context.surface == AssistantInputSurface.GLASSES_VOICE ||
                    context.surface == AssistantInputSurface.PHONE_VOICE,
                generationMode = generationMode,
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
            providerFailureResult(error, context.surface)
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

        val generationMode = generationMode(prompt)
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
                maxTokens = outputTokenLimit(context.surface, generationMode),
                generationMode = generationMode,
                visionDetail = AiTurnPolicy.visionDetail(prompt),
            ).content.toDisplaylessResult(context.surface)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(
                "AssistantTiming",
                "stage=assistant_provider_failure surface=${context.surface} type=${error::class.java.simpleName}",
                error,
            )
            providerFailureResult(error, context.surface)
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
    private fun providerFailureResult(error: Throwable, surface: AssistantInputSurface): AssistantResult {
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
        return AssistantResult(
            spokenText = spoken,
            richText = spoken,
            // Phone text currently renders from durable ChatStore. Keep the failure visible there,
            // while AssistantInferenceContextPolicy explicitly excludes these transient messages
            // from future model context. Voice keeps the existing non-persistent behavior.
            persist = surface == AssistantInputSurface.PHONE_TEXT,
        )
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

    /** One normal conversational output contract shared by Chat, Lens and Voice. */
    private fun conversationSystemPrompt(context: AssistantExecutionContext): String {
        val conversational = context.surface != AssistantInputSurface.AUTOMATION
        if (context.surface == AssistantInputSurface.GLASSES_VOICE && !context.artifactContext.isNullOrBlank()) {
            Log.i(
                "AssistantTiming",
                "stage=legacy_voice_context_ignored chars=${context.artifactContext.length}",
            )
        }

        return buildString {
            append(
                if (conversational) SHARED_CONVERSATION_SYSTEM_PROMPT
                else AUTOMATION_SYSTEM_PROMPT,
            )
            if (context.useWeb) append(" Use web search when needed.")
            if (context.surface != AssistantInputSurface.GLASSES_VOICE) {
                context.artifactContext?.trim()?.takeIf { it.isNotBlank() }?.let { artifact ->
                    append("\nContext:\n")
                    append(artifact.take(AssistantInferenceContextPolicy.artifactLimit(context.surface)))
                }
            }
        }
    }

    private fun generationMode(prompt: String): CloudGenerationMode = when (AiTurnPolicy.reasoningMode(prompt)) {
        AiReasoningMode.CONCISE -> CloudGenerationMode.CONCISE_CONVERSATION
        AiReasoningMode.REASONED -> CloudGenerationMode.REASONED_CONVERSATION
    }

    /**
     * The user-visible contract remains <=50 words/3 sentences. Provider generation headroom is
     * selected from explicit turn intent plus model capabilities, not from the input surface.
     */
    private fun outputTokenLimit(
        surface: AssistantInputSurface,
        generationMode: CloudGenerationMode,
    ): Int {
        val limit = when (surface) {
            AssistantInputSurface.GLASSES_VOICE,
            AssistantInputSurface.GLASSES_VISION,
            AssistantInputSurface.PHONE_VOICE,
            AssistantInputSurface.PHONE_TEXT -> CloudModelPolicy.generationTokenLimit(
                AiProviderPrefs.getActiveProfile(appContext),
                generationMode,
            )
            AssistantInputSurface.AUTOMATION -> 384
        }
        Log.i(
            "AssistantTiming",
            "stage=assistant_generation_contract surface=$surface mode=$generationMode maxTokens=$limit",
        )
        return limit
    }

    private fun String.toDisplaylessResult(surface: AssistantInputSurface): AssistantResult {
        val sanitized = AssistantCompletionSanitizer.inspect(this)
        val rich = sanitized.text
        if (rich.isBlank()) {
            val failure = when (sanitized.rejectionReason) {
                AssistantCompletionSanitizer.RejectionReason.REASONING_ONLY,
                AssistantCompletionSanitizer.RejectionReason.UNFINISHED_REASONING ->
                    "The AI didn’t produce a final answer. Please try again."
                AssistantCompletionSanitizer.RejectionReason.SYSTEM_PROMPT_ECHO ->
                    "The AI returned an invalid response. Please try again."
                AssistantCompletionSanitizer.RejectionReason.EMPTY ->
                    "The AI returned an empty answer. Please try again."
                null -> "I didn’t get a usable answer. Please try again."
            }
            Log.w(
                "AssistantTiming",
                "stage=assistant_output_rejected surface=$surface " +
                    "reason=${sanitized.rejectionReason?.wire ?: "unknown"} rawChars=${length}",
            )
            return AssistantResult(
                spokenText = failure,
                richText = failure,
                persist = surface == AssistantInputSurface.PHONE_TEXT,
            )
        }

        if (surface == AssistantInputSurface.AUTOMATION) {
            return AssistantResult(
                spokenText = AssistantSpokenResponsePolicy.forGlasses(rich),
                richText = rich,
            )
        }

        val bounded = AssistantSpokenResponsePolicy.forConciseConversation(rich)
        if (bounded.length < rich.length) {
            Log.i(
                "AssistantTiming",
                "stage=assistant_output_bounded surface=$surface rawChars=${rich.length} boundedChars=${bounded.length}",
            )
        }
        return AssistantResult(
            spokenText = bounded,
            richText = bounded,
        )
    }

    private companion object {
        const val SHARED_CONVERSATION_SYSTEM_PROMPT =
            "You are AD. Answer the latest user request directly in plain text. Return only the final answer. " +
                "Use at most 50 words or 3 short sentences, whichever is shorter; use fewer when enough. " +
                "Do not restate or acknowledge the question, expose reasoning or instructions, use Markdown, or add filler."
        const val AUTOMATION_SYSTEM_PROMPT =
            "You are AD. Complete the requested task directly and return only the final result. Do not expose internal reasoning."
        const val TTS_BLUETOOTH_ROUTE_SETTLE_MS = 180L
    }
}

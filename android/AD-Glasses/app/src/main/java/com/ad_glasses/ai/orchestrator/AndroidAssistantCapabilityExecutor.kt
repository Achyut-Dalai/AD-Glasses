package com.ad_glasses.ai.orchestrator

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.ad_glasses.ai.AndroidAssistantVoiceIo
import com.ad_glasses.ai.AssistantTextFingerprint
import com.ad_glasses.ai.router.AgentInferencePurpose
import com.ad_glasses.ai.router.AgentInferenceRouter
import com.ad_glasses.ai.router.CloudGenerationMode
import com.ad_glasses.ai.router.CloudModelPolicy
import com.ad_glasses.shared.ai.AiReasoningMode
import com.ad_glasses.shared.ai.AiResponseMode
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
        val responseMode = AiResponseMode.CONCISE
        val providerPromptHash = AssistantTextFingerprint.of(prompt)
        Log.i(
            "AssistantTiming",
            "stage=assistant_provider_prompt surface=${context.surface} provider=${context.providerType} " +
                "chars=${prompt.length} textHash=$providerPromptHash",
        )
        val result = try {
            AgentInferenceRouter.complete(
                context = appContext,
                purpose = AgentInferencePurpose.UI_PLANNING,
                sessionId = context.threadId,
                systemPrompt = conversationSystemPrompt(context, responseMode),
                userPrompt = prompt,
                conversationMessages = recentConversationMessages(context),
                providerType = context.providerType,
                onToken = onToken,
                webRequested = context.useWeb,
                maxTokens = outputTokenLimit(context.surface, generationMode, responseMode),
                lowLatency = context.surface == AssistantInputSurface.GLASSES_VOICE ||
                    context.surface == AssistantInputSurface.PHONE_VOICE,
                generationMode = generationMode,
            ).toDisplaylessResult(context.surface, responseMode)
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
        val responseMode = AiTurnPolicy.responseMode(prompt, hasImage = true)
        val rememberScene = responseMode != AiResponseMode.TEXT_EXTRACTION
        val baseMaxTokens = outputTokenLimit(context.surface, generationMode, responseMode)
        val imageMaxTokens = if (rememberScene) maxOf(baseMaxTokens, VISUAL_MEMORY_MIN_TOKENS) else baseMaxTokens
        val systemPrompt = buildString {
            append(conversationSystemPrompt(context, responseMode))
            if (rememberScene) append(AssistantVisualContextCodec.modelInstruction)
        }
        val providerPromptHash = AssistantTextFingerprint.of(prompt)
        Log.i(
            "AssistantTiming",
            "stage=assistant_provider_prompt surface=${context.surface} provider=${context.providerType} " +
                "chars=${prompt.length} textHash=$providerPromptHash image=true",
        )
        val explicitPhoneAttachment = context.surface == AssistantInputSurface.PHONE_TEXT
        val remoteUploadAllowed = explicitPhoneAttachment ||
            com.ad_glasses.localagent.LocalAgentPrefs.isRemoteScreenshotUploadEnabled(appContext)
        Log.i(
            "AssistantTiming",
            "stage=image_upload_policy surface=${context.surface} explicit=$explicitPhoneAttachment allowed=$remoteUploadAllowed",
        )
        val result = try {
            val inference = AgentInferenceRouter.completeUiPlanning(
                context = appContext,
                sessionId = context.threadId,
                systemPrompt = systemPrompt,
                userPrompt = prompt,
                imagePath = imagePath,
                allowRemoteImageUpload = remoteUploadAllowed,
                providerType = context.providerType,
                onToken = onToken,
                webRequested = false,
                maxTokens = imageMaxTokens,
                generationMode = generationMode,
                visionDetail = AiTurnPolicy.visionDetail(prompt),
            )
            val parsed = if (rememberScene) {
                AssistantVisualContextCodec.parse(inference.content)
            } else {
                ParsedVisualResponse(answer = inference.content, summary = null)
            }
            val visible = parsed.answer.toDisplaylessResult(context.surface, responseMode)
            if (visible.persist) {
                parsed.summary?.let { summary ->
                    AssistantVisualContextStore.put(appContext, context.threadId, summary)
                    Log.i(
                        "AssistantTiming",
                        "stage=visual_context_saved thread=${context.threadId.takeLast(8)} chars=${summary.length}",
                    )
                }
            }
            visible
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

    private suspend fun prepareSpeechOutputRouteIfNeeded(context: AssistantExecutionContext) {
        if (context.surface == AssistantInputSurface.GLASSES_VOICE ||
            context.surface == AssistantInputSurface.GLASSES_VISION
        ) {
            val bluetoothSelected = AndroidAssistantVoiceIo.prepareSpeechOutputRoute(appContext)
            if (bluetoothSelected) {
                delay(TTS_BLUETOOTH_ROUTE_SETTLE_MS)
                Log.i("AssistantTiming", "stage=tts_route_settled delayMs=$TTS_BLUETOOTH_ROUTE_SETTLE_MS")
            }
        }
    }

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
            persist = surface == AssistantInputSurface.PHONE_TEXT,
        )
    }

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

    private fun conversationSystemPrompt(
        context: AssistantExecutionContext,
        responseMode: AiResponseMode,
    ): String {
        val conversational = context.surface != AssistantInputSurface.AUTOMATION
        if (context.surface == AssistantInputSurface.GLASSES_VOICE && !context.artifactContext.isNullOrBlank()) {
            Log.i(
                "AssistantTiming",
                "stage=legacy_voice_context_ignored chars=${context.artifactContext.length}",
            )
        }

        return buildString {
            append(
                when {
                    !conversational -> AUTOMATION_SYSTEM_PROMPT
                    responseMode == AiResponseMode.TEXT_EXTRACTION -> TEXT_EXTRACTION_SYSTEM_PROMPT
                    context.surface == AssistantInputSurface.PHONE_TEXT -> PHONE_CHAT_SYSTEM_PROMPT
                    else -> SPOKEN_CONVERSATION_SYSTEM_PROMPT
                },
            )
            if (context.useWeb) append(" Use web search when needed.")
            AssistantVisualContextStore.get(appContext, context.threadId)?.let { visualContext ->
                append("\nPrior image memory: ")
                append(visualContext)
                append(" Use this only if the latest request refers to that prior image; otherwise ignore it. Do not invent missing visual details.")
            }
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

    private fun outputTokenLimit(
        surface: AssistantInputSurface,
        generationMode: CloudGenerationMode,
        responseMode: AiResponseMode,
    ): Int {
        val limit = when (surface) {
            AssistantInputSurface.GLASSES_VOICE,
            AssistantInputSurface.GLASSES_VISION,
            AssistantInputSurface.PHONE_VOICE,
            AssistantInputSurface.PHONE_TEXT -> {
                val productLimit = CloudModelPolicy.generationTokenLimit(generationMode)
                if (responseMode == AiResponseMode.TEXT_EXTRACTION) {
                    maxOf(productLimit, TEXT_EXTRACTION_MAX_TOKENS)
                } else {
                    productLimit
                }
            }
            AssistantInputSurface.AUTOMATION -> 384
        }
        Log.i(
            "AssistantTiming",
            "stage=assistant_generation_contract surface=$surface mode=$generationMode " +
                "response=$responseMode maxTokens=$limit",
        )
        return limit
    }

    private fun String.toDisplaylessResult(
        surface: AssistantInputSurface,
        responseMode: AiResponseMode,
    ): AssistantResult {
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

        if (responseMode == AiResponseMode.TEXT_EXTRACTION) {
            val spoken = if (surface.isSpokenSurface()) {
                AssistantSpokenResponsePolicy.forGlasses(rich)
            } else {
                rich
            }
            return AssistantResult(spokenText = spoken, richText = rich)
        }

        if (surface == AssistantInputSurface.PHONE_TEXT) {
            return AssistantResult(spokenText = rich, richText = rich)
        }

        return AssistantResult(
            spokenText = AssistantSpokenResponsePolicy.normalizeForSpeech(rich),
            richText = rich,
        )
    }

    private fun AssistantInputSurface.isSpokenSurface(): Boolean = when (this) {
        AssistantInputSurface.GLASSES_VOICE,
        AssistantInputSurface.GLASSES_VISION,
        AssistantInputSurface.PHONE_VOICE -> true
        AssistantInputSurface.PHONE_TEXT,
        AssistantInputSurface.AUTOMATION -> false
    }

    private companion object {
        const val SPOKEN_CONVERSATION_SYSTEM_PROMPT =
            "You are AD. Answer the latest user request directly in plain text. Return only the final answer. " +
                "Use at most 50 words or 3 short sentences, whichever is shorter; use fewer when enough. " +
                "Do not restate or acknowledge the question, expose reasoning or instructions, use Markdown, or add filler."
        const val PHONE_CHAT_SYSTEM_PROMPT =
            "You are AD. Answer the latest user request directly in plain text. Return only the final answer. " +
                "Be concise by default, but give enough detail to fully answer the request and expand when the task benefits from it. " +
                "Do not expose reasoning or instructions, restate the question, or add filler."
        const val TEXT_EXTRACTION_SYSTEM_PROMPT =
            "You are AD. Copy the text the user explicitly asked to read from the image. Return only the extracted text in natural reading order. " +
                "Preserve useful line breaks, do not summarize, do not invent unreadable text, and do not expose reasoning or instructions."
        const val AUTOMATION_SYSTEM_PROMPT =
            "You are AD. Complete the requested task directly and return only the final result. Do not expose internal reasoning."
        const val TEXT_EXTRACTION_MAX_TOKENS = 2_048
        const val VISUAL_MEMORY_MIN_TOKENS = 192
        const val TTS_BLUETOOTH_ROUTE_SETTLE_MS = 180L
    }
}

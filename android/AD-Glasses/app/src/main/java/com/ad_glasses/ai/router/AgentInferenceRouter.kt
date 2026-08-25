package com.ad_glasses.ai.router

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.ad_glasses.agent.LocalAgentPrefs as AutomationPrefs
import com.ad_glasses.ai.orchestrator.AssistantCompletionSanitizer
import com.ad_glasses.shared.ai.AiVisionDetail
import com.ad_glasses.shared.settings.AgentProviderType
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class AgentInferencePurpose {
    CLASSIFICATION,
    UI_PLANNING,
}

data class AgentInferenceResult(
    val content: String,
    val usedImage: Boolean,
    val mediaStatus: String,
)

internal data class WearableGenerationTimeouts(
    val firstSafeAnswerMs: Long,
    val activeTransportAnswerMs: Long,
    val activeReasoningAnswerMs: Long,
    val totalGenerationMs: Long,
)

/**
 * Tracks the post-user-echo stream and exposes only text that the completion sanitizer considers
 * safe to surface. Reasoning/prompt-leak deltas therefore never satisfy the wearable first-answer
 * deadline just because the provider happened to emit bytes quickly.
 */
internal class SafeFirstAnswerGate {
    private val raw = StringBuilder()

    @Synchronized
    fun accept(delta: String): String {
        if (delta.isEmpty()) return ""
        raw.append(delta)
        return currentVisible()
    }

    @Synchronized
    fun currentVisible(): String =
        AssistantCompletionSanitizer.cleanForStreaming(raw.toString()).trim()
}

/** Cloud-only inference boundary. Local Agent remains an automation capability, not an LLM. */
object AgentInferenceRouter {
    private const val TIMING_TAG = "AssistantTiming"

    // HttpURLConnection's blocking SSE read is not reliably interruptible on every Android build.
    // Keep the wearable user-facing deadline independent from that socket worker: after the voice
    // deadline expires we stop accepting its deltas and return immediately, even if Android takes
    // longer to unwind the underlying connection.
    private val lowLatencyNetworkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun complete(
        context: Context,
        purpose: AgentInferencePurpose,
        sessionId: String,
        systemPrompt: String,
        userPrompt: String,
        conversationMessages: List<Map<String, String>> = emptyList(),
        providerType: AgentProviderType = AutomationPrefs.getProviderType(context),
        onToken: ((String) -> Unit)? = null,
        webRequested: Boolean = false,
        maxTokens: Int? = null,
        lowLatency: Boolean = false,
        generationMode: CloudGenerationMode = CloudGenerationMode.DEFAULT,
    ): String = completeText(
        context = context,
        purpose = purpose,
        sessionId = sessionId,
        systemPrompt = systemPrompt,
        userPrompt = userPrompt,
        conversationMessages = conversationMessages,
        onToken = onToken,
        webRequested = webRequested,
        maxTokensOverride = maxTokens,
        lowLatencyRequest = lowLatency,
        generationMode = generationMode,
    )

    suspend fun completeUiPlanning(
        context: Context,
        sessionId: String,
        systemPrompt: String,
        userPrompt: String,
        imagePath: String?,
        allowRemoteImageUpload: Boolean,
        providerType: AgentProviderType = AutomationPrefs.getProviderType(context),
        onToken: ((String) -> Unit)? = null,
        webRequested: Boolean = false,
        maxTokens: Int = UI_PLANNING_MAX_TOKENS,
        lowLatency: Boolean = false,
        generationMode: CloudGenerationMode = CloudGenerationMode.DEFAULT,
        visionDetail: AiVisionDetail = AiVisionDetail.STANDARD,
    ): AgentInferenceResult {
        val usableImagePath = imagePath?.trim()?.takeIf { File(it).isFile }
        if (!imagePath.isNullOrBlank() && usableImagePath == null) {
            throw IllegalStateException("The requested image file is missing. AD did not fall back to a text-only answer.")
        }
        if (usableImagePath == null) {
            return AgentInferenceResult(
                content = completeText(
                    context = context,
                    purpose = AgentInferencePurpose.UI_PLANNING,
                    sessionId = sessionId,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    onToken = onToken,
                    webRequested = webRequested,
                    maxTokensOverride = maxTokens,
                    lowLatencyRequest = lowLatency,
                    generationMode = generationMode,
                ),
                usedImage = false,
                mediaStatus = "Cloud text inference",
            )
        }
        if (!allowRemoteImageUpload) {
            throw IllegalStateException("Remote image upload is off. The image was not sent.")
        }

        val startedAt = SystemClock.elapsedRealtime()
        val sessionLabel = sessionId.takeLast(8)
        Log.i(
            TIMING_TAG,
            "stage=cloud_image_start thread=$sessionLabel mode=$generationMode detail=$visionDetail",
        )
        val prepared = try {
            CloudVisionImagePreprocessor.prepare(
                context = context,
                sourcePath = usableImagePath,
                detail = visionDetail,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(
                TIMING_TAG,
                "stage=vision_preprocess_failed thread=$sessionLabel type=${error::class.java.simpleName}",
            )
            throw IllegalStateException("The image could not be prepared safely for cloud analysis.", error)
        }

        val imageContent = try {
            withContext(Dispatchers.IO) {
                ApiTokenClient.image(
                    context = context,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    imagePath = prepared.path,
                    maxTokens = maxTokens,
                    generationMode = generationMode,
                    visionDetail = visionDetail,
                ).getOrThrow()
            }
        } catch (error: CancellationException) {
            Log.i(
                TIMING_TAG,
                "stage=cloud_image_cancelled thread=$sessionLabel elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
            )
            throw error
        } catch (error: Exception) {
            Log.w(
                TIMING_TAG,
                "stage=cloud_image_failed thread=$sessionLabel elapsedMs=${SystemClock.elapsedRealtime() - startedAt} type=${error::class.java.simpleName}",
            )
            throw IllegalStateException(
                "The active Cloud AI profile could not analyze this image: " +
                    (error.message ?: error::class.java.simpleName),
                error,
            )
        } finally {
            prepared.cleanup()
        }
        Log.i(
            TIMING_TAG,
            "stage=cloud_image_done thread=$sessionLabel elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
        )

        return AgentInferenceResult(
            content = imageContent,
            usedImage = true,
            mediaStatus = "Image attached to the active Cloud AI profile.",
        )
    }

    fun isRemotePlanner(providerType: AgentProviderType): Boolean = true

    internal fun wearableTimeouts(generationMode: CloudGenerationMode): WearableGenerationTimeouts =
        if (generationMode == CloudGenerationMode.REASONED_CONVERSATION) {
            WearableGenerationTimeouts(
                firstSafeAnswerMs = REASONED_FIRST_SAFE_ANSWER_TIMEOUT_MS,
                activeTransportAnswerMs = REASONED_ACTIVE_TRANSPORT_TIMEOUT_MS,
                activeReasoningAnswerMs = REASONED_ACTIVE_REASONING_TIMEOUT_MS,
                totalGenerationMs = REASONED_TOTAL_GENERATION_TIMEOUT_MS,
            )
        } else {
            WearableGenerationTimeouts(
                firstSafeAnswerMs = CONCISE_FIRST_SAFE_ANSWER_TIMEOUT_MS,
                activeTransportAnswerMs = CONCISE_ACTIVE_TRANSPORT_TIMEOUT_MS,
                activeReasoningAnswerMs = CONCISE_ACTIVE_REASONING_TIMEOUT_MS,
                totalGenerationMs = CONCISE_TOTAL_GENERATION_TIMEOUT_MS,
            )
        }

    /**
     * Decide whether a first-answer deadline may be extended after its current budget expires.
     * Activity extends to a fixed ceiling; it never resets a rolling timer indefinitely.
     */
    internal fun nextFirstAnswerDeadline(
        currentDeadlineMs: Long,
        timeouts: WearableGenerationTimeouts,
        providerActivitySeen: Boolean,
        reasoningActivitySeen: Boolean,
    ): Long? = when {
        reasoningActivitySeen && currentDeadlineMs < timeouts.activeReasoningAnswerMs ->
            timeouts.activeReasoningAnswerMs
        providerActivitySeen && currentDeadlineMs < timeouts.activeTransportAnswerMs ->
            timeouts.activeTransportAnswerMs
        else -> null
    }

    private suspend fun completeText(
        context: Context,
        purpose: AgentInferencePurpose,
        sessionId: String,
        systemPrompt: String,
        userPrompt: String,
        conversationMessages: List<Map<String, String>> = emptyList(),
        onToken: ((String) -> Unit)?,
        webRequested: Boolean,
        maxTokensOverride: Int?,
        lowLatencyRequest: Boolean,
        generationMode: CloudGenerationMode,
    ): String {
        val maxTokens = maxTokensOverride
            ?.coerceIn(32, 2_048)
            ?: if (purpose == AgentInferencePurpose.CLASSIFICATION) 256 else 512
        // Latency behavior is a product/surface decision, not a side effect of the generation
        // ceiling. A fast non-reasoning Chat model may legitimately use only 96 tokens without
        // inheriting voice's short history, prompt ceiling, echo gate, or wearable timeouts.
        val lowLatencyVoiceRequest = lowLatencyRequest &&
            purpose == AgentInferencePurpose.UI_PLANNING &&
            onToken != null
        val wearableTimeouts = wearableTimeouts(generationMode)

        // Ask voice should never inherit a large persona/memory prompt. The dedicated caller uses a
        // compact system instruction; this ceiling is defense in depth for future callers.
        val effectiveSystemPrompt = if (lowLatencyVoiceRequest) {
            systemPrompt.take(LOW_LATENCY_SYSTEM_PROMPT_CHARS).trim()
        } else {
            systemPrompt
        }
        val effectiveConversationMessages = if (lowLatencyVoiceRequest) {
            boundedLowLatencyHistory(conversationMessages)
        } else {
            conversationMessages
        }
        val effectiveHistoryChars = effectiveConversationMessages.sumOf { it["content"].orEmpty().length }

        val startedAt = SystemClock.elapsedRealtime()
        val sessionLabel = sessionId.takeLast(8)
        val acceptingStreaming = AtomicBoolean(true)
        var lowLatencyInFlight: Deferred<String>? = null
        Log.i(
            TIMING_TAG,
            "stage=cloud_text_start thread=$sessionLabel purpose=$purpose mode=$generationMode " +
                "maxTokens=$maxTokens streaming=${onToken != null} lowLatency=$lowLatencyVoiceRequest " +
                "systemChars=${effectiveSystemPrompt.length} historyMessages=${effectiveConversationMessages.size} " +
                "historyChars=$effectiveHistoryChars",
        )
        return try {
            val firstUsefulDelta = CompletableDeferred<Unit>()
            val firstDeltaLogged = AtomicBoolean(false)
            val providerActivitySeen = AtomicBoolean(false)
            val httpReadySeen = AtomicBoolean(false)
            val providerDataSeen = AtomicBoolean(false)
            val reasoningActivitySeen = AtomicBoolean(false)
            val echoGate = UserPromptEchoGate(userPrompt)
            val safeFirstAnswerGate = SafeFirstAnswerGate()
            val streamingCallback = onToken?.let { downstream ->
                { delta: String ->
                    if (acceptingStreaming.get()) {
                        val safeDelta = if (lowLatencyVoiceRequest) echoGate.accept(delta) else delta
                        if (safeDelta.isNotEmpty() && acceptingStreaming.get()) {
                            val visibleAnswer = if (lowLatencyVoiceRequest) {
                                safeFirstAnswerGate.accept(safeDelta)
                            } else {
                                safeDelta
                            }
                            if (visibleAnswer.isNotBlank()) {
                                if (firstDeltaLogged.compareAndSet(false, true)) {
                                    Log.i(
                                        TIMING_TAG,
                                        "stage=cloud_text_first_delta thread=$sessionLabel purpose=$purpose mode=$generationMode " +
                                            "elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                                            "chars=${visibleAnswer.length} sanitized=$lowLatencyVoiceRequest",
                                    )
                                }
                                if (!firstUsefulDelta.isCompleted) firstUsefulDelta.complete(Unit)
                            }
                            // Downstream speech buffering independently sanitizes the cumulative raw
                            // answer before TTS. Keep forwarding post-user-echo deltas so it can emit
                            // the final answer immediately when a reasoning wrapper closes.
                            downstream(safeDelta)
                        }
                    }
                }
            }
            val activityCallback: ((CloudStreamActivity) -> Unit)? = if (lowLatencyVoiceRequest) {
                { activity ->
                    if (acceptingStreaming.get()) {
                        when (activity) {
                            CloudStreamActivity.HTTP_READY -> {
                                providerActivitySeen.set(true)
                                if (httpReadySeen.compareAndSet(false, true)) {
                                    Log.i(
                                        TIMING_TAG,
                                        "stage=cloud_text_http_ready thread=$sessionLabel purpose=$purpose " +
                                            "mode=$generationMode elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
                                    )
                                }
                            }
                            CloudStreamActivity.PROVIDER_DATA -> {
                                providerActivitySeen.set(true)
                                if (providerDataSeen.compareAndSet(false, true)) {
                                    Log.i(
                                        TIMING_TAG,
                                        "stage=cloud_text_provider_active thread=$sessionLabel purpose=$purpose " +
                                            "mode=$generationMode elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
                                    )
                                }
                            }
                            CloudStreamActivity.REASONING -> {
                                providerActivitySeen.set(true)
                                if (reasoningActivitySeen.compareAndSet(false, true)) {
                                    Log.i(
                                        TIMING_TAG,
                                        "stage=cloud_text_reasoning_active thread=$sessionLabel purpose=$purpose " +
                                            "mode=$generationMode elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                null
            }

            val request: suspend () -> String = {
                withContext(Dispatchers.IO) {
                    val result = if (streamingCallback != null) {
                        ApiTokenClient.chatStreaming(
                            context = context,
                            messages = messages(effectiveSystemPrompt, effectiveConversationMessages, userPrompt),
                            maxTokens = maxTokens,
                            webRequested = webRequested,
                            generationMode = generationMode,
                            onToken = streamingCallback,
                            onActivity = activityCallback,
                        )
                    } else {
                        ApiTokenClient.chat(
                            context = context,
                            messages = messages(effectiveSystemPrompt, effectiveConversationMessages, userPrompt),
                            maxTokens = maxTokens,
                            webRequested = webRequested,
                            generationMode = generationMode,
                        )
                    }
                    result.getOrThrow()
                }
            }

            val raw = if (lowLatencyVoiceRequest && streamingCallback != null) {
                val inFlight = lowLatencyNetworkScope.async { request() }
                lowLatencyInFlight = inFlight
                var firstAnswerDeadlineMs = wearableTimeouts.firstSafeAnswerMs
                var startedBeforeDeadline = false

                while (!startedBeforeDeadline) {
                    val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                    val remainingMs = (firstAnswerDeadlineMs - elapsedMs).coerceAtLeast(1L)
                    startedBeforeDeadline = withTimeoutOrNull(remainingMs) {
                        select<Unit> {
                            firstUsefulDelta.onAwait { Unit }
                            inFlight.onAwait { Unit }
                        }
                        true
                    } ?: false
                    if (startedBeforeDeadline) break

                    val nextDeadline = nextFirstAnswerDeadline(
                        currentDeadlineMs = firstAnswerDeadlineMs,
                        timeouts = wearableTimeouts,
                        providerActivitySeen = providerActivitySeen.get(),
                        reasoningActivitySeen = reasoningActivitySeen.get(),
                    ) ?: break
                    val extensionReason = if (reasoningActivitySeen.get()) {
                        "reasoning_activity"
                    } else {
                        "provider_activity"
                    }
                    firstAnswerDeadlineMs = nextDeadline
                    Log.i(
                        TIMING_TAG,
                        "stage=cloud_text_wait_extended thread=$sessionLabel purpose=$purpose mode=$generationMode " +
                            "reason=$extensionReason budgetMs=$firstAnswerDeadlineMs",
                    )
                }

                if (!startedBeforeDeadline) {
                    acceptingStreaming.set(false)
                    Log.w(
                        TIMING_TAG,
                        "stage=cloud_text_timeout thread=$sessionLabel purpose=$purpose mode=$generationMode " +
                            "phase=first_safe_delta elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                            "budgetMs=$firstAnswerDeadlineMs httpReady=${httpReadySeen.get()} " +
                            "providerData=${providerDataSeen.get()} reasoningActive=${reasoningActivitySeen.get()}",
                    )
                    inFlight.cancel(CancellationException("Wearable first safe-answer deadline expired"))
                    throw IllegalStateException(
                        "Cloud AI did not produce a usable answer within ${firstAnswerDeadlineMs}ms",
                    )
                }

                // Once safe speech has started, generation may continue while local TTS is already
                // playing. Reasoned turns deliberately get a longer runway, but neither mode can
                // leave a dead provider socket running indefinitely.
                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                val remainingMs = (wearableTimeouts.totalGenerationMs - elapsedMs).coerceAtLeast(1L)
                val completed = withTimeoutOrNull(remainingMs) { inFlight.await() }
                if (completed == null) {
                    acceptingStreaming.set(false)
                    val partial = safeFirstAnswerGate.currentVisible()
                    Log.w(
                        TIMING_TAG,
                        "stage=cloud_text_timeout thread=$sessionLabel purpose=$purpose mode=$generationMode " +
                            "phase=total_partial elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                            "budgetMs=${wearableTimeouts.totalGenerationMs} partialChars=${partial.length}",
                    )
                    inFlight.cancel(CancellationException("Wearable generation runaway cap expired"))
                    if (partial.isBlank()) {
                        throw IllegalStateException(
                            "Cloud AI exceeded the ${wearableTimeouts.totalGenerationMs}ms wearable generation cap without a usable partial answer",
                        )
                    }
                    partial
                } else {
                    completed
                }
            } else {
                request()
            }

            acceptingStreaming.set(false)
            val cleaned = if (lowLatencyVoiceRequest) {
                echoGate.finish(raw)
            } else {
                raw.trim()
            }
            if (cleaned.isBlank()) {
                throw IllegalStateException("The provider returned only a prompt echo or an empty answer")
            }
            cleaned.also {
                Log.i(
                    TIMING_TAG,
                    "stage=cloud_text_done thread=$sessionLabel purpose=$purpose mode=$generationMode " +
                        "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
                )
            }
        } catch (error: CancellationException) {
            acceptingStreaming.set(false)
            lowLatencyInFlight?.cancel(error)
            Log.i(
                TIMING_TAG,
                "stage=cloud_text_cancelled thread=$sessionLabel purpose=$purpose mode=$generationMode " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
            )
            throw error
        } catch (error: Exception) {
            acceptingStreaming.set(false)
            lowLatencyInFlight?.cancel(CancellationException("Wearable request failed"))
            Log.w(
                TIMING_TAG,
                "stage=cloud_text_failed thread=$sessionLabel purpose=$purpose mode=$generationMode " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt} type=${error::class.java.simpleName}",
            )
            throw error
        }
    }

    /** Keep the old ~720-character low-latency history ceiling as a contiguous recent suffix. */
    internal fun boundedLowLatencyHistory(
        conversationMessages: List<Map<String, String>>,
    ): List<Map<String, String>> {
        val selectedNewestFirst = mutableListOf<Map<String, String>>()
        var usedChars = 0
        for (message in conversationMessages.asReversed()) {
            val role = message["role"]?.trim()?.lowercase().orEmpty()
            val content = message["content"]
                ?.trim()
                .orEmpty()
                .take(LOW_LATENCY_MESSAGE_CHARS)
                .trim()
            if ((role != "user" && role != "assistant") || content.isBlank()) continue
            if (usedChars + content.length > LOW_LATENCY_HISTORY_CHARS) break
            selectedNewestFirst += mapOf("role" to role, "content" to content)
            usedChars += content.length
        }
        return selectedNewestFirst.asReversed()
    }

    /** Hold an answer only while it still looks like the provider may be echoing the user question. */
    private class UserPromptEchoGate(userPrompt: String) {
        private val prompt = userPrompt.trim()
        private val raw = StringBuilder()
        private var emitted = ""

        @Synchronized
        fun accept(delta: String): String {
            if (delta.isEmpty()) return ""
            raw.append(delta)
            val safe = stripUserPromptEcho(raw.toString(), prompt, final = false) ?: return ""
            if (!safe.startsWith(emitted)) return ""
            val fresh = safe.substring(emitted.length)
            emitted = safe
            return fresh
        }

        @Synchronized
        fun finish(finalRaw: String): String =
            stripUserPromptEcho(finalRaw, prompt, final = true).orEmpty().trim()
    }

    private fun stripUserPromptEcho(raw: String, prompt: String, final: Boolean): String? {
        val text = raw.trimStart()
        if (text.isBlank() || prompt.isBlank()) return text

        val candidates = buildList {
            add("User: $prompt")
            add("Question: $prompt")
            val wordCount = prompt.split(Regex("\\s+")).count { it.isNotBlank() }
            if (prompt.length >= 16 || wordCount >= 3 || prompt.endsWith('?')) {
                add(prompt)
            }
        }

        for (candidate in candidates) {
            if (!final && candidate.startsWith(text, ignoreCase = true) && text.length < candidate.length) {
                return null
            }
            if (text.equals(candidate, ignoreCase = true)) {
                return if (final) "" else null
            }
            if (text.startsWith(candidate, ignoreCase = true)) {
                return text.substring(candidate.length)
                    .trimStart(' ', '\t', '\r', '\n', ':', '-', '–', '—')
            }
        }
        return text
    }

    private fun messages(
        systemPrompt: String,
        conversationMessages: List<Map<String, String>>,
        userPrompt: String,
    ): List<Map<String, String>> = buildList {
        if (systemPrompt.isNotBlank()) {
            add(mapOf("role" to "system", "content" to systemPrompt))
        }
        conversationMessages.forEach { message ->
            val role = message["role"]?.trim()?.lowercase().orEmpty()
            val content = message["content"]?.trim().orEmpty()
            if ((role == "user" || role == "assistant") && content.isNotBlank()) {
                add(mapOf("role" to role, "content" to content))
            }
        }
        add(mapOf("role" to "user", "content" to userPrompt))
    }

    private const val UI_PLANNING_MAX_TOKENS = 512
    private const val LOW_LATENCY_SYSTEM_PROMPT_CHARS = 320
    private const val LOW_LATENCY_MESSAGE_CHARS = 360
    private const val LOW_LATENCY_HISTORY_CHARS = 720
    private const val CONCISE_FIRST_SAFE_ANSWER_TIMEOUT_MS = 6_000L
    private const val CONCISE_ACTIVE_TRANSPORT_TIMEOUT_MS = 10_000L
    private const val CONCISE_ACTIVE_REASONING_TIMEOUT_MS = 15_000L
    private const val CONCISE_TOTAL_GENERATION_TIMEOUT_MS = 30_000L
    private const val REASONED_FIRST_SAFE_ANSWER_TIMEOUT_MS = 15_000L
    private const val REASONED_ACTIVE_TRANSPORT_TIMEOUT_MS = 20_000L
    private const val REASONED_ACTIVE_REASONING_TIMEOUT_MS = 30_000L
    private const val REASONED_TOTAL_GENERATION_TIMEOUT_MS = 45_000L
}

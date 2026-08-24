package com.ad_glasses.ai.router

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.ad_glasses.agent.LocalAgentPrefs as AutomationPrefs
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.selects.select

enum class AgentInferencePurpose {
    CLASSIFICATION,
    UI_PLANNING,
}

data class AgentInferenceResult(
    val content: String,
    val usedImage: Boolean,
    val mediaStatus: String,
)

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
        Log.i(TIMING_TAG, "stage=cloud_image_start thread=$sessionLabel")
        val imageContent = try {
            withContext(Dispatchers.IO) {
                ApiTokenClient.image(
                    context = context,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    imagePath = usableImagePath,
                    maxTokens = maxTokens,
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
    ): String {
        val maxTokens = maxTokensOverride
            ?.coerceIn(32, 2_048)
            ?: if (purpose == AgentInferencePurpose.CLASSIFICATION) 256 else 512
        val lowLatencyVoiceRequest = purpose == AgentInferencePurpose.UI_PLANNING &&
            onToken != null &&
            maxTokens <= LOW_LATENCY_VOICE_TOKEN_CEILING

        // The voice system prompt on current main is already small. Keep a hard ceiling anyway so a
        // future caller cannot accidentally put multi-kilobyte memory/persona context back on the
        // wearable hot path. Native chat roles carry only a tiny recent continuation below.
        val effectiveSystemPrompt = if (lowLatencyVoiceRequest) {
            systemPrompt.take(LOW_LATENCY_SYSTEM_PROMPT_CHARS).trim()
        } else {
            systemPrompt
        }
        val effectiveConversationMessages = if (lowLatencyVoiceRequest) {
            conversationMessages
                .takeLast(LOW_LATENCY_PRIOR_MESSAGES)
                .mapNotNull { message ->
                    val role = message["role"]?.trim()?.lowercase().orEmpty()
                    val content = message["content"]
                        ?.trim()
                        .orEmpty()
                        .take(LOW_LATENCY_MESSAGE_CHARS)
                        .trim()
                    if ((role == "user" || role == "assistant") && content.isNotBlank()) {
                        mapOf("role" to role, "content" to content)
                    } else {
                        null
                    }
                }
        } else {
            conversationMessages
        }

        val startedAt = SystemClock.elapsedRealtime()
        val sessionLabel = sessionId.takeLast(8)
        val acceptingStreaming = AtomicBoolean(true)
        var lowLatencyInFlight: Deferred<String>? = null
        Log.i(
            TIMING_TAG,
            "stage=cloud_text_start thread=$sessionLabel purpose=$purpose maxTokens=$maxTokens streaming=${onToken != null} " +
                "lowLatency=$lowLatencyVoiceRequest systemChars=${effectiveSystemPrompt.length} " +
                "historyMessages=${effectiveConversationMessages.size}",
        )
        return try {
            val firstUsefulDelta = CompletableDeferred<Unit>()
            val firstDeltaLogged = AtomicBoolean(false)
            val echoGate = UserPromptEchoGate(userPrompt)
            val streamingCallback = onToken?.let { downstream ->
                { delta: String ->
                    if (acceptingStreaming.get()) {
                        val safeDelta = if (lowLatencyVoiceRequest) echoGate.accept(delta) else delta
                        if (safeDelta.isNotEmpty() && acceptingStreaming.get()) {
                            if (firstDeltaLogged.compareAndSet(false, true)) {
                                Log.i(
                                    TIMING_TAG,
                                    "stage=cloud_text_first_delta thread=$sessionLabel purpose=$purpose " +
                                        "elapsedMs=${SystemClock.elapsedRealtime() - startedAt} chars=${safeDelta.length}",
                                )
                            }
                            if (!firstUsefulDelta.isCompleted) firstUsefulDelta.complete(Unit)
                            downstream(safeDelta)
                        }
                    }
                }
            }

            val request: suspend () -> String = {
                withContext(Dispatchers.IO) {
                    val result = if (streamingCallback != null) {
                        ApiTokenClient.chatStreaming(
                            context = context,
                            messages = messages(effectiveSystemPrompt, effectiveConversationMessages, userPrompt),
                            maxTokens = maxTokens,
                            webRequested = webRequested,
                            onToken = streamingCallback,
                        )
                    } else {
                        ApiTokenClient.chat(
                            context = context,
                            messages = messages(effectiveSystemPrompt, effectiveConversationMessages, userPrompt),
                            maxTokens = maxTokens,
                            webRequested = webRequested,
                        )
                    }
                    result.getOrThrow()
                }
            }

            val raw = if (lowLatencyVoiceRequest && streamingCallback != null) {
                val inFlight = lowLatencyNetworkScope.async { request() }
                lowLatencyInFlight = inFlight
                val startedBeforeDeadline = withTimeoutOrNull(LOW_LATENCY_FIRST_DELTA_TIMEOUT_MS) {
                    select<Unit> {
                        firstUsefulDelta.onAwait { Unit }
                        inFlight.onAwait { Unit }
                    }
                    true
                } ?: false

                if (!startedBeforeDeadline) {
                    acceptingStreaming.set(false)
                    Log.w(
                        TIMING_TAG,
                        "stage=cloud_text_timeout thread=$sessionLabel purpose=$purpose phase=first_delta " +
                            "elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                            "budgetMs=$LOW_LATENCY_FIRST_DELTA_TIMEOUT_MS",
                    )
                    inFlight.cancel(CancellationException("Wearable first-answer deadline expired"))
                    throw IllegalStateException(
                        "Cloud AI did not start answering within ${LOW_LATENCY_FIRST_DELTA_TIMEOUT_MS}ms",
                    )
                }

                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                val remainingMs = (LOW_LATENCY_TOTAL_TIMEOUT_MS - elapsedMs).coerceAtLeast(1L)
                val completed = withTimeoutOrNull(remainingMs) { inFlight.await() }
                if (completed == null) {
                    acceptingStreaming.set(false)
                    Log.w(
                        TIMING_TAG,
                        "stage=cloud_text_timeout thread=$sessionLabel purpose=$purpose phase=total " +
                            "elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                            "budgetMs=$LOW_LATENCY_TOTAL_TIMEOUT_MS",
                    )
                    inFlight.cancel(CancellationException("Wearable total-generation deadline expired"))
                    throw IllegalStateException(
                        "Cloud AI exceeded the ${LOW_LATENCY_TOTAL_TIMEOUT_MS}ms wearable generation budget",
                    )
                }
                completed
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
                    "stage=cloud_text_done thread=$sessionLabel purpose=$purpose elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
                )
            }
        } catch (error: CancellationException) {
            acceptingStreaming.set(false)
            lowLatencyInFlight?.cancel(error)
            Log.i(
                TIMING_TAG,
                "stage=cloud_text_cancelled thread=$sessionLabel purpose=$purpose elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
            )
            throw error
        } catch (error: Exception) {
            acceptingStreaming.set(false)
            lowLatencyInFlight?.cancel(CancellationException("Wearable request failed"))
            Log.w(
                TIMING_TAG,
                "stage=cloud_text_failed thread=$sessionLabel purpose=$purpose elapsedMs=${SystemClock.elapsedRealtime() - startedAt} type=${error::class.java.simpleName}",
            )
            throw error
        }
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
    private const val LOW_LATENCY_VOICE_TOKEN_CEILING = 256
    private const val LOW_LATENCY_SYSTEM_PROMPT_CHARS = 700
    private const val LOW_LATENCY_PRIOR_MESSAGES = 2
    private const val LOW_LATENCY_MESSAGE_CHARS = 360
    private const val LOW_LATENCY_FIRST_DELTA_TIMEOUT_MS = 4_500L
    private const val LOW_LATENCY_TOTAL_TIMEOUT_MS = 8_000L
}

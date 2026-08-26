package com.ad_glasses.ai.orchestrator

import android.content.Context
import android.util.Log
import com.ad_glasses.ai.router.AgentInferencePurpose
import com.ad_glasses.ai.router.AgentInferenceRouter
import com.ad_glasses.ai.router.CloudGenerationMode
import kotlinx.coroutines.CancellationException

/**
 * Rare history-aware rewrite stage used only after the history-free semantic planner explicitly says
 * the current utterance depends on prior context. It never answers the user and never streams/TTS.
 */
class AssistantContextResolver(context: Context) {
    private val appContext = context.applicationContext

    suspend fun resolve(
        currentUtterance: String,
        context: AssistantExecutionContext,
    ): Result<String> = try {
        val prior = AssistantInferenceContextPolicy
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
        if (prior.isEmpty()) {
            return Result.failure(IllegalStateException("The request needs prior context, but no usable prior context is available."))
        }

        val raw = AgentInferenceRouter.complete(
            context = appContext,
            purpose = AgentInferencePurpose.CLASSIFICATION,
            sessionId = "${context.threadId}-context-resolver",
            systemPrompt = SYSTEM_PROMPT,
            userPrompt = currentUtterance.take(MAX_CURRENT_UTTERANCE_CHARS),
            conversationMessages = prior,
            providerType = context.providerType,
            onToken = null,
            webRequested = false,
            maxTokens = MAX_OUTPUT_TOKENS,
            lowLatency = false,
            generationMode = CloudGenerationMode.CONCISE_CONVERSATION,
        )
        val clean = raw
            .replace(Regex("(?is)^```(?:text)?\\s*|\\s*```$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_RESOLVED_CHARS)
        require(clean.isNotBlank()) { "The context resolver returned an empty standalone request." }
        Log.i(TAG, "context_resolved chars=${clean.length} priorMessages=${prior.size}")
        Result.success(clean)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Log.w(TAG, "context_resolution_failed type=${error::class.java.simpleName}")
        Result.failure(error)
    }

    private companion object {
        const val TAG = "AssistantContextResolver"
        const val MAX_CURRENT_UTTERANCE_CHARS = 1_300
        const val MAX_RESOLVED_CHARS = 700
        const val MAX_OUTPUT_TOKENS = 192
        const val SYSTEM_PROMPT =
            "Rewrite ONLY the latest user utterance into one standalone request using the supplied prior conversation messages solely to resolve references such as it/that/the second one. " +
                "Do not answer the request. Do not classify it. Do not add facts, assumptions, preferences, or instructions that were not present in the conversation. Preserve the user's requested action, constraints, names, numbers, and uncertainty. " +
                "Return only the standalone request text, no JSON, labels, explanation, markdown, or quotation marks."
    }
}

package com.ad_glasses.ai.router

import android.content.Context
import com.ad_glasses.shared.settings.AgentProviderType

enum class AssistantIntent {
    ANSWER_QUESTION,
    ANALYZE_IMAGE,
    CLARIFY,
}

enum class AssistantRequestSource {
    GLASSES_VOICE,
    GLASSES_IMAGE,
    CHAT,
    APP_UI,
}

data class AssistantRequest(
    val text: String,
    val source: AssistantRequestSource,
    val imageAttached: Boolean = false,
)

data class AssistantRoutingDecision(
    val intent: AssistantIntent,
    val confidence: Double,
    val normalizedGoal: String? = null,
    val clarification: String? = null,
)

/**
 * Structural entry router only. Natural-language Search/Maps intent belongs to
 * GroundingIntentRouter; this boundary uses no phrase/keyword classifier.
 */
class AssistantRequestRouter {
    @Suppress("UNUSED_PARAMETER")
    suspend fun route(
        context: Context,
        request: AssistantRequest,
        providerType: AgentProviderType,
    ): AssistantRoutingDecision {
        val text = request.text.trim()
        if (text.isBlank()) {
            return AssistantRoutingDecision(
                intent = AssistantIntent.CLARIFY,
                confidence = 1.0,
                clarification = "What would you like to ask?",
            )
        }
        return if (request.imageAttached) {
            AssistantRoutingDecision(
                intent = AssistantIntent.ANALYZE_IMAGE,
                confidence = 1.0,
                normalizedGoal = text,
            )
        } else {
            AssistantRoutingDecision(
                intent = AssistantIntent.ANSWER_QUESTION,
                confidence = 1.0,
            )
        }
    }
}

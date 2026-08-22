package com.fersaiyan.cyanbridge.ai.router

import android.content.Context
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType

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
 * Routes conversational and vision requests only. Phone Accessibility/UI automation is deliberately
 * not an assistant invocation target anymore; explicit capability commands are handled separately.
 */
class AssistantRequestRouter {
    suspend fun route(
        context: Context,
        request: AssistantRequest,
        providerType: AgentProviderType,
    ): AssistantRoutingDecision = classifyHeuristically(request)
        ?: AssistantRoutingDecision(
            intent = AssistantIntent.ANSWER_QUESTION,
            confidence = 0.9,
        )

    internal fun classifyHeuristically(request: AssistantRequest): AssistantRoutingDecision? {
        val text = request.text.trim()
        if (text.isBlank()) {
            return AssistantRoutingDecision(
                intent = AssistantIntent.CLARIFY,
                confidence = 1.0,
                clarification = "What would you like to ask?",
            )
        }

        if (request.imageAttached || IMAGE_REQUEST_REGEX.containsMatchIn(text)) {
            return AssistantRoutingDecision(
                intent = AssistantIntent.ANALYZE_IMAGE,
                confidence = 1.0,
                normalizedGoal = text,
            )
        }

        if (INFORMATIONAL_QUESTION_REGEX.containsMatchIn(text)) {
            return AssistantRoutingDecision(
                intent = AssistantIntent.ANSWER_QUESTION,
                confidence = 0.95,
            )
        }

        return null
    }

    private companion object {
        private val INFORMATIONAL_QUESTION_REGEX = Regex(
            "^(?:what|who|when|where|why|how|explain|describe|define|tell me|is|are|do|does|did)\\b",
            RegexOption.IGNORE_CASE,
        )
        private val IMAGE_REQUEST_REGEX = Regex(
            "\\b(?:what (?:am i|are we) looking at|what do you see|analy[sz]e (?:this )?(?:image|picture|photo)|describe (?:this )?(?:image|picture|photo)|read (?:this )?(?:image|picture|photo))\\b",
            RegexOption.IGNORE_CASE,
        )
    }
}

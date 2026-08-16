package com.fersaiyan.cyanbridge.ai.orchestrator

import android.content.Context
import com.fersaiyan.cyanbridge.ai.router.AssistantIntent
import com.fersaiyan.cyanbridge.ai.router.AssistantRequest
import com.fersaiyan.cyanbridge.ai.router.AssistantRequestRouter
import com.fersaiyan.cyanbridge.ai.router.AssistantRequestSource
import com.fersaiyan.cyanbridge.shared.chat.ChatMessage
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType

enum class AssistantInputSurface {
    GLASSES_VOICE,
    GLASSES_VISION,
    PHONE_TEXT,
    PHONE_VOICE,
    AUTOMATION,
}

data class AssistantTurn(
    val text: String,
    val surface: AssistantInputSurface,
    val imageAttached: Boolean = false,
    val webRequested: Boolean? = null,
)

data class AssistantExecutionContext(
    val threadId: String,
    val history: List<ChatMessage>,
    val useWeb: Boolean,
    val surface: AssistantInputSurface,
)

data class AssistantResult(
    val spokenText: String,
    val richText: String = spokenText,
)

interface AssistantCapabilityExecutor {
    suspend fun answer(
        prompt: String,
        context: AssistantExecutionContext,
    ): AssistantResult

    suspend fun analyzeImage(
        prompt: String,
        context: AssistantExecutionContext,
    ): AssistantResult

    suspend fun executePhoneAction(
        goal: String,
        context: AssistantExecutionContext,
    ): AssistantResult
}

/**
 * Shared control plane for glasses voice, glasses vision and phone continuation.
 * Existing BLE/Wi-Fi/media/Local Agent implementations remain executors outside this class.
 */
class AssistantOrchestrator(
    context: Context,
    private val executor: AssistantCapabilityExecutor,
    private val router: AssistantRequestRouter = AssistantRequestRouter(),
) {
    private val appContext = context.applicationContext
    private val session = AssistantConversationSession.get(appContext)

    suspend fun handle(
        turn: AssistantTurn,
        providerType: AgentProviderType,
    ): AssistantResult {
        val prompt = turn.text.trim()
        require(prompt.isNotBlank()) { "Assistant turn cannot be blank" }

        session.addUserTurn(prompt)
        val threadId = session.activeThreadId()
        val history = session.messages()
        val useWeb = AssistantWebPolicy.shouldUseWeb(prompt, turn.webRequested)

        val decision = router.route(
            context = appContext,
            request = AssistantRequest(
                text = prompt,
                source = turn.surface.toRouterSource(),
                imageAttached = turn.imageAttached || turn.surface == AssistantInputSurface.GLASSES_VISION,
            ),
            providerType = providerType,
        )

        val executionContext = AssistantExecutionContext(
            threadId = threadId,
            history = history,
            useWeb = useWeb,
            surface = turn.surface,
        )

        val result = when (decision.intent) {
            AssistantIntent.ANSWER_QUESTION -> executor.answer(prompt, executionContext)
            AssistantIntent.ANALYZE_IMAGE -> executor.analyzeImage(
                decision.normalizedGoal ?: prompt,
                executionContext,
            )
            AssistantIntent.EXECUTE_UI_TASK -> executor.executePhoneAction(
                decision.normalizedGoal ?: prompt,
                executionContext,
            )
            AssistantIntent.CLARIFY -> AssistantResult(
                spokenText = decision.clarification ?: "What would you like me to do?",
            )
        }

        val persisted = result.richText.trim().ifBlank { result.spokenText.trim() }
        if (persisted.isNotBlank()) session.addAssistantTurn(persisted)
        return result
    }

    fun activeThreadId(): String = session.activeThreadId()

    fun startNewConversation(): String = session.startNewConversation()

    private fun AssistantInputSurface.toRouterSource(): AssistantRequestSource = when (this) {
        AssistantInputSurface.GLASSES_VOICE -> AssistantRequestSource.GLASSES_VOICE
        AssistantInputSurface.GLASSES_VISION -> AssistantRequestSource.GLASSES_IMAGE
        AssistantInputSurface.PHONE_TEXT,
        AssistantInputSurface.PHONE_VOICE,
        AssistantInputSurface.AUTOMATION -> AssistantRequestSource.APP_UI
    }
}

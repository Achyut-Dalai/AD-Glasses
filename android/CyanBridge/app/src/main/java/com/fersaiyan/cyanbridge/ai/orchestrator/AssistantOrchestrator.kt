package com.fersaiyan.cyanbridge.ai.orchestrator

import android.content.Context
import com.fersaiyan.cyanbridge.ai.router.AssistantIntent
import com.fersaiyan.cyanbridge.ai.router.AssistantRequest
import com.fersaiyan.cyanbridge.ai.router.AssistantRequestRouter
import com.fersaiyan.cyanbridge.ai.router.AssistantRequestSource
import com.fersaiyan.cyanbridge.shared.chat.ChatMessage
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType

/** Where a turn entered AD from. */
enum class AssistantInputSurface {
    GLASSES_VOICE,
    GLASSES_VISION,
    PHONE_TEXT,
    PHONE_VOICE,
    AUTOMATION,
}

/** A normalized turn before routing/execution. */
data class AssistantTurn(
    val text: String,
    val surface: AssistantInputSurface,
    val imageAttached: Boolean = false,
    /** null = automatic, true/false = explicit user/UI preference. */
    val webRequested: Boolean? = null,
)

data class AssistantExecutionContext(
    val threadId: String,
    val history: List<ChatMessage>,
    val useWeb: Boolean,
    val surface: AssistantInputSurface,
)

data class AssistantResult(
    /** Concise response suitable for spoken delivery on displayless glasses. */
    val spokenText: String,
    /** Richer/full text persisted for phone review. */
    val richText: String = spokenText,
)

/**
 * Execution boundary around the capabilities that already exist in MainActivity/services.
 * This deliberately keeps BLE/Wi-Fi/media/Local Agent implementations outside the
 * orchestrator. The orchestrator decides; existing subsystems execute.
 */
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

    suspend fun executeModeCommand(
        command: AssistantModeCommand,
        context: AssistantExecutionContext,
    ): AssistantResult
}

/**
 * Single control plane for glasses voice, glasses vision and phone continuation.
 *
 * Every accepted turn is persisted to the same ChatStore-backed session before it is
 * executed, and every assistant response is persisted afterwards. This is what lets
 * "what is this?" -> "how much is it?" -> "find a better one" remain one conversation
 * even as the required capability changes between turns.
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
        val useWeb = AssistantWebPolicy.shouldUseWeb(
            text = prompt,
            requested = turn.webRequested,
            history = history,
        )
        val executionContext = AssistantExecutionContext(
            threadId = threadId,
            history = history,
            useWeb = useWeb,
            surface = turn.surface,
        )

        // Obvious mode commands are deterministic and should not pay an LLM routing cost.
        val modeCommand = AssistantModeCommandRouter.parse(prompt)
        val result = if (modeCommand != null) {
            executor.executeModeCommand(modeCommand, executionContext)
        } else {
            val decision = router.route(
                context = appContext,
                request = AssistantRequest(
                    text = prompt,
                    source = turn.surface.toRouterSource(),
                    imageAttached = turn.imageAttached || turn.surface == AssistantInputSurface.GLASSES_VISION,
                ),
                providerType = providerType,
            )

            when (decision.intent) {
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
        AssistantInputSurface.PHONE_TEXT -> AssistantRequestSource.CHAT
        AssistantInputSurface.PHONE_VOICE,
        AssistantInputSurface.AUTOMATION -> AssistantRequestSource.APP_UI
    }
}

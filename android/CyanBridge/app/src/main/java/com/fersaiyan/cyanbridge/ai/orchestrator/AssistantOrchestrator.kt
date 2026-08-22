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

data class AssistantTurn(
    val text: String,
    val surface: AssistantInputSurface,
    val imagePath: String? = null,
    val contextText: String? = null,
    val webRequested: Boolean? = null,
)

data class AssistantExecutionContext(
    val threadId: String,
    val history: List<ChatMessage>,
    val useWeb: Boolean,
    val providerType: AgentProviderType,
    val surface: AssistantInputSurface,
    val artifactContext: String? = null,
)

data class AssistantResult(
    val spokenText: String,
    val richText: String = spokenText,
)

/** Execution boundary for answer, vision and explicit AD capability commands only. */
interface AssistantCapabilityExecutor {
    suspend fun answer(prompt: String, context: AssistantExecutionContext): AssistantResult

    suspend fun analyzeImage(
        prompt: String,
        imagePath: String?,
        context: AssistantExecutionContext,
    ): AssistantResult

    suspend fun executeCapabilityCommand(
        command: AssistantCapabilityCommand,
        context: AssistantExecutionContext,
    ): AssistantResult
}

/** Single control plane for glasses voice, glasses vision and phone continuation. */
class AssistantOrchestrator(
    context: Context,
    private val executor: AssistantCapabilityExecutor,
    private val router: AssistantRequestRouter = AssistantRequestRouter(),
) {
    private val appContext = context.applicationContext
    private val session = AssistantConversationSession.get(appContext)

    suspend fun handle(turn: AssistantTurn, providerType: AgentProviderType): AssistantResult {
        val prompt = turn.text.trim()
        require(prompt.isNotBlank()) { "Assistant turn cannot be blank" }

        AssistantConversationPolicy.parseCommand(prompt)?.let { command ->
            return when (command) {
                AssistantConversationCommand.START_FRESH -> {
                    session.startNewConversation()
                    AssistantResult(spokenText = "Started a new conversation.")
                }
                AssistantConversationCommand.FORGET_CURRENT -> {
                    session.forgetCurrentConversation()
                    AssistantResult(spokenText = "Forgot that conversation. We can start fresh.")
                }
            }
        }

        val acceptedThreadId = session.activeThreadId()
        return AssistantTurnCoordinator.withThread(acceptedThreadId) {
            handleQueuedTurn(turn, providerType, prompt, acceptedThreadId)
        }
    }

    private suspend fun handleQueuedTurn(
        turn: AssistantTurn,
        providerType: AgentProviderType,
        prompt: String,
        acceptedThreadId: String,
    ): AssistantResult {
        val userMessage = session.addUserTurn(acceptedThreadId, prompt) ?: return AssistantResult(
            spokenText = "That conversation was cleared before I could start. Please ask again.",
        )
        val threadId = userMessage.chatId
        val history = session.messages(threadId)
        val useWeb = AssistantWebPolicy.shouldUseWeb(
            text = prompt,
            requested = turn.webRequested,
            history = history,
        )
        val executionContext = AssistantExecutionContext(
            threadId = threadId,
            history = history,
            useWeb = useWeb,
            providerType = providerType,
            surface = turn.surface,
            artifactContext = turn.contextText?.trim()?.takeIf { it.isNotBlank() },
        )

        val capabilityCommand = AssistantCapabilityCommandRouter.parse(prompt)
        val result = if (capabilityCommand != null) {
            executor.executeCapabilityCommand(capabilityCommand, executionContext)
        } else {
            val decision = router.route(
                context = appContext,
                request = AssistantRequest(
                    text = prompt,
                    source = turn.surface.toRouterSource(),
                    imageAttached = !turn.imagePath.isNullOrBlank() ||
                        turn.surface == AssistantInputSurface.GLASSES_VISION,
                ),
                providerType = providerType,
            )

            when (decision.intent) {
                AssistantIntent.ANSWER_QUESTION -> executor.answer(prompt, executionContext)
                AssistantIntent.ANALYZE_IMAGE -> executor.analyzeImage(
                    prompt = decision.normalizedGoal ?: prompt,
                    imagePath = turn.imagePath,
                    context = executionContext,
                )
                AssistantIntent.CLARIFY -> AssistantResult(
                    spokenText = decision.clarification ?: "What would you like to ask?",
                )
            }
        }

        val persisted = result.richText.trim().ifBlank { result.spokenText.trim() }
        if (persisted.isNotBlank()) session.addAssistantTurn(threadId, persisted)
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

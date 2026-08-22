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
    /** Concrete local image produced by the existing glasses/phone capture pipeline. */
    val imagePath: String? = null,
    /** Hidden artifact description supplied to the model but not persisted as the user's words. */
    val contextText: String? = null,
    /** null = automatic, true/false = explicit user/UI preference. */
    val webRequested: Boolean? = null,
)

data class AssistantExecutionContext(
    val threadId: String,
    val history: List<ChatMessage>,
    val useWeb: Boolean,
    /** Immutable provider selected when the turn was accepted; executors must not reread prefs. */
    val providerType: AgentProviderType,
    val surface: AssistantInputSurface,
    val artifactContext: String? = null,
)

data class AssistantResult(
    /** Concise response suitable for spoken delivery on displayless glasses. */
    val spokenText: String,
    /** Richer/full text persisted for phone review. */
    val richText: String = spokenText,
)

/**
 * Execution boundary around capabilities that already exist in MainActivity/services.
 * The orchestrator decides; existing BLE/Wi-Fi/media/Automation subsystems execute.
 */
interface AssistantCapabilityExecutor {
    suspend fun answer(
        prompt: String,
        context: AssistantExecutionContext,
    ): AssistantResult

    suspend fun analyzeImage(
        prompt: String,
        imagePath: String?,
        context: AssistantExecutionContext,
    ): AssistantResult

    suspend fun executePhoneAction(
        goal: String,
        context: AssistantExecutionContext,
    ): AssistantResult

    suspend fun executeCapabilityCommand(
        command: AssistantCapabilityCommand,
        context: AssistantExecutionContext,
    ): AssistantResult
}

/**
 * Single control plane for glasses voice, glasses vision and phone continuation.
 *
 * Every accepted user turn is persisted exactly as spoken/typed, except explicit conversation
 * controls such as "new topic" and "forget this conversation". Those controls are handled
 * before persistence or model routing. Hidden artifact context is carried separately so a photo
 * transcript/note can inform the model without masquerading as something the user said.
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

        // Capture the topic at acceptance, then serialize its normal turns. A second question
        // waits for the first answer so history remains user→assistant→user→assistant. Explicit
        // new/forget controls above stay immediate and are never trapped behind inference.
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

        // The message's chatId is the atomic turn capture. A user may start/forget another
        // conversation while inference is suspended; this result must never leak into it.
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

        // Obvious capability commands are deterministic and should not pay an LLM routing cost.
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

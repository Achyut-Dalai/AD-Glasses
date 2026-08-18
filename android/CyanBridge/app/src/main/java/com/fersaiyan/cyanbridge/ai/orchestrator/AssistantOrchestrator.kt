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
    /** null = automatic/global preference, true/false = explicit turn preference. */
    val webRequested: Boolean? = null,
)

data class AssistantExecutionContext(
    val threadId: String,
    val history: List<ChatMessage>,
    val useWeb: Boolean,
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
 * Every accepted user turn is persisted exactly as spoken/typed. Hidden artifact context is
 * carried separately so a photo transcript/note can inform the model without masquerading as
 * something the user said.
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
        val requestedWeb = turn.webRequested ?: AssistantWebModePreferences.explicitOverride(appContext)
        val useWeb = AssistantWebPolicy.shouldUseWeb(
            text = prompt,
            requested = requestedWeb,
            history = history,
        )
        val executionContext = AssistantExecutionContext(
            threadId = threadId,
            history = history,
            useWeb = useWeb,
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

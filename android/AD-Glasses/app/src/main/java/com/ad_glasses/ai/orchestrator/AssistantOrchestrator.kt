package com.ad_glasses.ai.orchestrator

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.ad_glasses.ai.AssistantTextFingerprint
import com.ad_glasses.ai.router.AssistantIntent
import com.ad_glasses.ai.router.AssistantRequest
import com.ad_glasses.ai.router.AssistantRequestRouter
import com.ad_glasses.ai.router.AssistantRequestSource
import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.settings.AgentProviderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
    /** False for transient/provider failures that should be spoken but never become chat context. */
    val persist: Boolean = true,
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
        val inputHash = AssistantTextFingerprint.of(prompt)
        Log.i(
            TIMING_TAG,
            "stage=assistant_input surface=${turn.surface} chars=${prompt.length} textHash=$inputHash",
        )

        AssistantConversationPolicy.parseCommand(prompt)?.let { command ->
            val currentThreadId = session.activeThreadId()
            AssistantTurnCoordinator.cancelActive(currentThreadId, "Conversation command received")
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

        val turnStartedAt = SystemClock.elapsedRealtime()
        val acceptedThreadId = session.activeThreadId()
        val lease = if (turn.surface.isInteractive()) {
            currentCoroutineContext()[Job]?.let { job ->
                AssistantTurnCoordinator.beginInteractiveTurn(acceptedThreadId, job)
            }
        } else {
            null
        }

        return try {
            handleTurn(
                turn = turn,
                providerType = providerType,
                prompt = prompt,
                inputHash = inputHash,
                acceptedThreadId = acceptedThreadId,
                turnStartedAt = turnStartedAt,
                lease = lease,
            )
        } catch (cancelled: CancellationException) {
            Log.i(
                TIMING_TAG,
                "turn_cancelled surface=${turn.surface} totalMs=${SystemClock.elapsedRealtime() - turnStartedAt}",
            )
            throw cancelled
        } catch (error: Throwable) {
            val totalMs = SystemClock.elapsedRealtime() - turnStartedAt
            Log.e(
                TIMING_TAG,
                "turn_failed surface=${turn.surface} totalMs=$totalMs error=${error.javaClass.simpleName}",
                error,
            )
            throw error
        } finally {
            lease?.let(AssistantTurnCoordinator::finishInteractiveTurn)
        }
    }

    private suspend fun handleTurn(
        turn: AssistantTurn,
        providerType: AgentProviderType,
        prompt: String,
        inputHash: String,
        acceptedThreadId: String,
        turnStartedAt: Long,
        lease: AssistantTurnCoordinator.InteractiveLease?,
    ): AssistantResult {
        val stateStartedAt = SystemClock.elapsedRealtime()
        val accepted = AssistantTurnCoordinator.withThreadState(acceptedThreadId) {
            val persistUserStartedAt = SystemClock.elapsedRealtime()
            val userMessage = session.addUserTurn(acceptedThreadId, prompt) ?: return@withThreadState null
            Log.i(
                TIMING_TAG,
                "user_persisted surface=${turn.surface} textHash=$inputHash stageMs=${SystemClock.elapsedRealtime() - persistUserStartedAt}",
            )

            val historyStartedAt = SystemClock.elapsedRealtime()
            val history = session.messages(userMessage.chatId)
            Log.i(
                TIMING_TAG,
                "history_loaded surface=${turn.surface} messages=${history.size} stageMs=${SystemClock.elapsedRealtime() - historyStartedAt}",
            )
            AcceptedTurn(userMessage.chatId, history)
        } ?: return AssistantResult(
            spokenText = "That conversation was cleared before I could start. Please ask again.",
            persist = false,
        )
        Log.i(
            TIMING_TAG,
            "state_ready surface=${turn.surface} stageMs=${SystemClock.elapsedRealtime() - stateStartedAt}",
        )

        currentCoroutineContext().ensureActive()
        ensureCurrent(lease)

        val useWeb = AssistantWebPolicy.shouldUseWeb(
            text = prompt,
            requested = turn.webRequested,
            history = accepted.history,
        )
        val executionContext = AssistantExecutionContext(
            threadId = accepted.threadId,
            history = accepted.history,
            useWeb = useWeb,
            providerType = providerType,
            surface = turn.surface,
            artifactContext = turn.contextText?.trim()?.takeIf { it.isNotBlank() },
        )

        val inferenceStartedAt = SystemClock.elapsedRealtime()
        Log.i(
            TIMING_TAG,
            "inference_start surface=${turn.surface} provider=$providerType textHash=$inputHash",
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
        Log.i(
            TIMING_TAG,
            "inference_done surface=${turn.surface} stageMs=${SystemClock.elapsedRealtime() - inferenceStartedAt}",
        )

        currentCoroutineContext().ensureActive()
        ensureCurrent(lease)

        if (result.persist) {
            val persisted = result.richText.trim().ifBlank { result.spokenText.trim() }
            if (persisted.isNotBlank()) {
                val persistAssistantStartedAt = SystemClock.elapsedRealtime()
                AssistantTurnCoordinator.withThreadState(accepted.threadId) {
                    ensureCurrent(lease)
                    session.addAssistantTurn(accepted.threadId, persisted)
                }
                Log.i(
                    TIMING_TAG,
                    "assistant_persisted surface=${turn.surface} stageMs=${SystemClock.elapsedRealtime() - persistAssistantStartedAt}",
                )
            }
        } else {
            Log.i(TIMING_TAG, "assistant_not_persisted surface=${turn.surface}")
        }
        Log.i(
            TIMING_TAG,
            "turn_done surface=${turn.surface} totalMs=${SystemClock.elapsedRealtime() - turnStartedAt}",
        )
        return result
    }

    fun activeThreadId(): String = session.activeThreadId()

    fun startNewConversation(): String {
        AssistantTurnCoordinator.cancelActive(session.activeThreadId(), "New conversation started")
        return session.startNewConversation()
    }

    fun cancelActiveTurn(threadId: String = session.activeThreadId()) {
        AssistantTurnCoordinator.cancelActive(threadId, "Stopped by user")
    }

    private fun ensureCurrent(lease: AssistantTurnCoordinator.InteractiveLease?) {
        if (lease != null && !AssistantTurnCoordinator.isCurrent(lease)) {
            throw CancellationException("Assistant turn was superseded")
        }
    }

    private fun AssistantInputSurface.isInteractive(): Boolean = this != AssistantInputSurface.AUTOMATION

    private fun AssistantInputSurface.toRouterSource(): AssistantRequestSource = when (this) {
        AssistantInputSurface.GLASSES_VOICE -> AssistantRequestSource.GLASSES_VOICE
        AssistantInputSurface.GLASSES_VISION -> AssistantRequestSource.GLASSES_IMAGE
        AssistantInputSurface.PHONE_TEXT -> AssistantRequestSource.CHAT
        AssistantInputSurface.PHONE_VOICE,
        AssistantInputSurface.AUTOMATION -> AssistantRequestSource.APP_UI
    }

    private data class AcceptedTurn(
        val threadId: String,
        val history: List<ChatMessage>,
    )

    private companion object {
        const val TIMING_TAG = "AssistantTiming"
    }
}

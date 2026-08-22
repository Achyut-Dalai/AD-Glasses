package com.ad_glasses.localagent

import android.content.Context
import android.util.Log
import com.ad_glasses.localagent.actions.LocalAgentActionManager
import kotlinx.coroutines.delay

class LocalAgentStepEngine(
    private val context: Context,
    private val executor: LocalAgentActionExecutor,
) {
    data class ExecutionSummary(
        val actionResults: List<String>,
        val haltedForApproval: Boolean,
        val finished: Boolean,
        val haltedForDeviceState: Boolean = false,
        val deviceAvailability: LocalAgentDeviceState.Availability? = null,
    )

    /**
     * Executes a list of actions sequentially.
     * Some actions may be enqueued for approval instead of being executed immediately.
     */
    suspend fun execute(actions: List<LocalAgentAction>): ExecutionSummary {
        val results = mutableListOf<String>()
        for ((index, a) in actions.withIndex()) {
            executor.ensureNotCancelled()

            LocalAgentDeviceState.availability(context)
                .takeIf { it != LocalAgentDeviceState.Availability.READY }
                ?.let { availability ->
                    Log.i(TAG, "Stopping action execution: ${availability.errorCode}")
                    results += "${a.javaClass.simpleName}: blocked_device_state"
                    return ExecutionSummary(
                        actionResults = results,
                        haltedForApproval = false,
                        finished = false,
                        haltedForDeviceState = true,
                        deviceAvailability = availability,
                    )
                }

            if (a is LocalAgentAction.Finish) {
                results += a.message?.takeIf { it.isNotBlank() } ?: "Task marked complete"
                return ExecutionSummary(results, haltedForApproval = false, finished = true)
            }

            if (a is LocalAgentAction.Wait) {
                delay(a.ms)
                continue
            }

            val risk = LocalAgentActionManager.classifyRisk(a)
            val requireConfirm = LocalAgentPrefs.isRequireActionConfirmationEnabled(context)

            // Consequential-only approval: auto-execute LOW and MEDIUM (navigation, typing,
            // clicking), only require approval for HIGH-risk actions (send email, call, SMS,
            // set alarm, read screen aloud).
            val shouldAutoExecute = !requireConfirm || risk != LocalAgentActionManager.Risk.HIGH

            if (shouldAutoExecute) {
                // Read-aloud must run in the active agent service so it can report whether
                // visible text was actually available before the plan advances.
                val intentOk = if (a == LocalAgentAction.ReadScreenAloud) {
                    false
                } else {
                    LocalAgentActionManager.executeNow(context, a)
                }
                if (intentOk) {
                    Log.i(TAG, "action=${a.javaClass.simpleName} executed as system intent")
                    results += "${a.javaClass.simpleName}: ok(system)"
                } else {
                    // Fallback to accessibility
                    val ok = executor.execute(a)
                    Log.i(TAG, "action=${a.javaClass.simpleName} executed via a11y ok=$ok")
                    results += "${a.javaClass.simpleName}: ${if (ok) "ok" else "failed"}"
                }

                // Saved navigation skills may contain several actions. Give the target app
                // the same rendering time that the normal observe-act loop would provide.
                if (index < actions.lastIndex) {
                    delay(LocalAgentRuntimePolicy.settleDelayMs(a))
                }
            } else {
                // Enqueue for manual approval.
                Log.i(TAG, "action=${a.javaClass.simpleName} requires approval, enqueuing.")
                LocalAgentActionManager.processPlannedAction(context, a)
                // When an action is pending, we typically want to stop the current plan
                // until the user approves it.
                results += "${a.javaClass.simpleName}: queued_for_approval"
                return ExecutionSummary(results, haltedForApproval = true, finished = false)
            }
        }

        return ExecutionSummary(results, haltedForApproval = false, finished = false)
    }

    interface LocalAgentActionExecutor {
        suspend fun execute(action: LocalAgentAction): Boolean
        fun ensureNotCancelled()
    }

    private companion object {
        private const val TAG = "LocalAgentSteps"
    }
}

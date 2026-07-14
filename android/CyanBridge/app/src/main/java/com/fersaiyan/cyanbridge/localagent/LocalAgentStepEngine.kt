package com.fersaiyan.cyanbridge.localagent

import android.content.Context
import android.util.Log
import com.fersaiyan.cyanbridge.localagent.actions.LocalAgentActionManager
import kotlinx.coroutines.delay

class LocalAgentStepEngine(
    private val context: Context,
    private val executor: LocalAgentActionExecutor,
) {
    data class ExecutionSummary(
        val actionResults: List<String>,
        val haltedForApproval: Boolean,
        val finished: Boolean,
    )

    /**
     * Executes a list of actions sequentially.
     * Some actions may be enqueued for approval instead of being executed immediately.
     */
    suspend fun execute(actions: List<LocalAgentAction>): ExecutionSummary {
        val results = mutableListOf<String>()
        for (a in actions) {
            executor.ensureNotCancelled()

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
            val autoLowRisk = LocalAgentPrefs.isAutoExecuteLowRiskEnabled(context)

            val shouldAutoExecute = !requireConfirm || (risk == LocalAgentActionManager.Risk.LOW && autoLowRisk)

            if (shouldAutoExecute) {
                // Try executing as a system intent first (e.g. Email)
                val intentOk = LocalAgentActionManager.executeNow(context, a)
                if (intentOk) {
                    Log.i(TAG, "action=${a.javaClass.simpleName} executed as system intent")
                    results += "${a.javaClass.simpleName}: ok(system)"
                } else {
                    // Fallback to accessibility
                    val ok = executor.execute(a)
                    Log.i(TAG, "action=${a.javaClass.simpleName} executed via a11y ok=$ok")
                    results += "${a.javaClass.simpleName}: ${if (ok) "ok" else "failed"}"
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

package com.ad_glasses.localagent

data class LocalAgentTaskState(
    val goal: String,
    val maxSteps: Int,
    val startedAtMs: Long,
    val stepIndex: Int = 1,
    val previousActionResult: String? = null,
    val consecutiveFailures: Int = 0,
    val previousActionSignature: String? = null,
    val identicalActionCount: Int = 0,
) {
    fun hasReachedRepeatLimit(action: LocalAgentAction): Boolean {
        return previousActionSignature == LocalAgentRuntimePolicy.actionSignature(action) &&
            identicalActionCount >= LocalAgentRuntimePolicy.maxIdenticalExecutions(action)
    }

    fun nextStep(
        previousActionResult: String?,
        failed: Boolean,
        action: LocalAgentAction? = null,
    ): LocalAgentTaskState {
        val signature = action?.let(LocalAgentRuntimePolicy::actionSignature)
        val identicalCount = when {
            signature == null -> identicalActionCount
            signature == previousActionSignature -> identicalActionCount + 1
            else -> 1
        }
        return copy(
            stepIndex = stepIndex + 1,
            previousActionResult = previousActionResult,
            consecutiveFailures = if (failed) consecutiveFailures + 1 else 0,
            previousActionSignature = signature ?: previousActionSignature,
            identicalActionCount = identicalCount,
        )
    }
}

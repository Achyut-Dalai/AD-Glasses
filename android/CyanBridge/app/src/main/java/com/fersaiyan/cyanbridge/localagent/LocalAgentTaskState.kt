package com.fersaiyan.cyanbridge.localagent

data class LocalAgentTaskState(
    val goal: String,
    val maxSteps: Int,
    val startedAtMs: Long,
    val stepIndex: Int = 1,
    val previousActionResult: String? = null,
    val consecutiveFailures: Int = 0,
) {
    fun nextStep(previousActionResult: String?, failed: Boolean): LocalAgentTaskState {
        return copy(
            stepIndex = stepIndex + 1,
            previousActionResult = previousActionResult,
            consecutiveFailures = if (failed) consecutiveFailures + 1 else 0,
        )
    }
}

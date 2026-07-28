package com.fersaiyan.cyanbridge.localagent

object LocalAgentObserver {
    fun observe(): LocalAgentObservation {
        val snapshot = LocalAgentAccessibilityBridge.snapshotScreen()
        return LocalAgentObservation(
            createdAtMs = System.currentTimeMillis(),
            packageName = snapshot?.packageName,
            screenText = snapshot?.textSummary,
            screenSnapshot = snapshot,
        )
    }
}

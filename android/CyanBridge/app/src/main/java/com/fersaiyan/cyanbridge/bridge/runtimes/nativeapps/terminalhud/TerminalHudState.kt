package com.fersaiyan.cyanbridge.bridge.runtimes.nativeapps.terminalhud

/**
 * State model for the Terminal HUD display.
 * Represents what the agent wants to show on the glasses.
 */
data class TerminalHudState(
    val provider: AgentProvider = AgentProvider.UNKNOWN,
    val repoName: String = "",
    val status: AgentStatus = AgentStatus.IDLE,
    val recentLines: List<String> = emptyList(),
    val pendingPermission: PermissionRequest? = null,
)

enum class AgentProvider(val label: String) {
    CLAUDE("Claude"),
    CODEX("Codex"),
    OPENCODE("OpenCode"),
    UNKNOWN("Agent"),
}

enum class AgentStatus(val label: String) {
    IDLE("Idle"),
    THINKING("Thinking"),
    WORKING("Working"),
    WAITING_PERMISSION("Permission needed"),
    ERROR("Error"),
    COMPLETED("Done"),
}

data class PermissionRequest(
    val description: String,
    val allowLabel: String = "ALLOW",
    val denyLabel: String = "DENY",
)

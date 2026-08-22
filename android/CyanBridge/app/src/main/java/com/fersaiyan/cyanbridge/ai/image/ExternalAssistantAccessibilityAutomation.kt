package com.fersaiyan.cyanbridge.ai.image

import com.fersaiyan.cyanbridge.localagent.LocalAgentAccessibilityBridge
import kotlinx.coroutines.delay

/** Uses AD Glasses' own accessibility service after a direct image share. */
object ExternalAssistantAccessibilityAutomation {
    private const val POLL_MS = 250L
    private const val DEFAULT_TIMEOUT_MS = 12_000L

    suspend fun fillAndSend(
        target: ImageAutomationTarget,
        targetPackage: String,
        question: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Result<Unit> = runCatching {
        check(LocalAgentAccessibilityBridge.isConnected()) {
            "Enable AD Glasses accessibility access for assistant handoff."
        }

        waitForTargetPackage(targetPackage, timeoutMs)

        val fieldHints = when (target) {
            ImageAutomationTarget.GEMINI -> listOf("assistant_robin_floaty_single_line_query", "Ask Gemini", "message")
            ImageAutomationTarget.CHATGPT -> listOf("composer_edit_text", "Message", "message")
            ImageAutomationTarget.NONE -> emptyList()
        }
        val typed = fieldHints.any { LocalAgentAccessibilityBridge.typeText(question, it) } ||
            LocalAgentAccessibilityBridge.typeText(question)
        check(typed) { "Could not find the ${target.label} message field." }

        delay(300L)
        val viewIds = when (target) {
            ImageAutomationTarget.GEMINI -> listOf(
                "com.google.android.googlequicksearchbox:id/assistant_robin_floaty_single_line_send_button",
            )
            ImageAutomationTarget.CHATGPT -> listOf("com.openai.chatgpt:id/send_button")
            ImageAutomationTarget.NONE -> emptyList()
        }
        val clicked = viewIds.any(LocalAgentAccessibilityBridge::clickByViewId) ||
            listOf("Send", "Submit").any(LocalAgentAccessibilityBridge::clickByText) ||
            LocalAgentAccessibilityBridge.pressEnter()
        check(clicked) { "Could not submit the question in ${target.label}." }
    }

    private suspend fun waitForTargetPackage(targetPackage: String, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (LocalAgentAccessibilityBridge.activeWindowPackageName() == targetPackage) return
            delay(POLL_MS)
        }
        error("${targetPackage.substringAfterLast('.')} did not become ready in time.")
    }
}

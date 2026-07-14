package com.fersaiyan.cyanbridge.localagent

data class LocalAgentObservation(
    val createdAtMs: Long,
    val packageName: String?,
    val screenText: String?,
    val screenSnapshot: LocalAgentScreenSnapshot?,
)

data class LocalAgentScreenSnapshot(
    val packageName: String?,
    val textSummary: String?,
    val nodes: List<LocalAgentScreenNode>,
) {
    fun toPromptText(
        maxNodes: Int = 80,
        maxChars: Int = 12_000,
    ): String {
        val body = buildString {
            val pkg = packageName?.trim().orEmpty()
            if (pkg.isNotEmpty()) {
                appendLine("Current app: $pkg")
            }

            val summary = textSummary?.trim().orEmpty()
            if (summary.isNotEmpty()) {
                appendLine("Visible text summary:")
                appendLine(summary)
            }

            if (nodes.isNotEmpty()) {
                appendLine("Structured nodes:")
                nodes.take(maxNodes).forEach { node ->
                    appendLine(node.toPromptLine())
                }
            }
        }.trim()

        return if (body.length <= maxChars) body else body.take(maxChars)
    }
}

data class LocalAgentScreenNode(
    val index: Int,
    val depth: Int,
    val text: String,
    val contentDescription: String,
    val className: String,
    val viewId: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val bounds: LocalAgentNodeBounds,
    val isPassword: Boolean = false,
) {
    fun toPromptLine(): String {
        val label = when {
            isPassword -> "(password field redacted)"
            text.isNotBlank() -> text
            contentDescription.isNotBlank() -> contentDescription
            else -> "(no text)"
        }
        val attrs = buildList {
            if (isClickable) add("clickable")
            if (isEditable) add("editable")
            if (isScrollable) add("scrollable")
            if (viewId.isNotBlank()) add("viewId=$viewId")
        }.joinToString(",")

        val type = className.ifBlank { "Node" }
        val attrSuffix = if (attrs.isNotBlank()) " {$attrs}" else ""

        return "[$index] [$type] \"${label.sanitizeForPrompt()}\"$attrSuffix bounds:[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}] center:(${bounds.centerX},${bounds.centerY})"
    }
}

data class LocalAgentNodeBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

private fun String.sanitizeForPrompt(): String =
    replace("\n", " ")
        .replace("\r", " ")
        .trim()

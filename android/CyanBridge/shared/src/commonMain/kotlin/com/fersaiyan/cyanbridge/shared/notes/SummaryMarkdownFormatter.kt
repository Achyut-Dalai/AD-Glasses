package com.fersaiyan.cyanbridge.shared.notes

/**
 * Stable, structured summary formatter.
 *
 * This intentionally emits a fixed set of headings in a fixed order so saved
 * notes have the same representation across Android and iOS.
 */
object SummaryMarkdownFormatter {
    private const val NONE = "(none)"

    fun format(summary: StructuredSummary): String {
        val title = summary.title.trim().ifBlank { "Meeting Notes" }
        return buildString {
            appendLine("# $title")
            appendLine()

            section("Summary", coerceBullets(summary.summaryBullets))
            appendLine()
            section("Action items", summary.actionItems)
            appendLine()
            section("Key decisions", summary.keyDecisions)
            appendLine()
            section("Open questions", summary.openQuestions)
            appendLine()
            section("Timeline highlights", summary.timelineHighlights)
        }.trimEnd()
    }

    private fun StringBuilder.section(heading: String, bullets: List<String>) {
        appendLine("## $heading")
        val cleaned = bullets.mapNotNull { it.cleanBulletOrNull() }
        if (cleaned.isEmpty()) {
            appendLine("- $NONE")
            return
        }
        cleaned.forEach { appendLine("- $it") }
    }

    private fun String.cleanBulletOrNull(): String? {
        val trimmed = trim().removePrefix("-").trim()
        if (trimmed.isBlank()) return null
        return trimmed.replace(Regex("\\s+"), " ")
    }

    private fun coerceBullets(bullets: List<String>): List<String> {
        val cleaned = bullets.mapNotNull { it.cleanBulletOrNull() }
        if (cleaned.isEmpty()) return emptyList()
        return if (cleaned.size <= 10) cleaned else cleaned.take(10)
    }
}

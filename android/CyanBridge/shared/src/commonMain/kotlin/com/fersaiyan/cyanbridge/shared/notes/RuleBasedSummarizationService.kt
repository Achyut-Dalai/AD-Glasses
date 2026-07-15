package com.fersaiyan.cyanbridge.shared.notes

/** Offline fallback summarizer that does not require a platform service or network access. */
class RuleBasedSummarizationService : SummarizationService {
    override suspend fun summarize(request: SummarizationRequest): StructuredSummary {
        val transcript = request.transcript.trim()
        val title = request.hintTitle?.trim().takeUnless { it.isNullOrBlank() }
            ?: guessTitle(transcript)

        val sentences = splitSentences(transcript)
        val summaryBullets = sentences
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(request.maxSummaryBullets)
            .toList()
            .let { ensureMinBullets(it, request.minSummaryBullets) }

        return StructuredSummary(
            title = title,
            summaryBullets = summaryBullets,
            actionItems = sentences.filter {
                it.contains("todo", ignoreCase = true) || it.contains("action", ignoreCase = true)
            }.take(10),
            keyDecisions = sentences.filter {
                it.contains("decid", ignoreCase = true) || it.contains("agree", ignoreCase = true)
            }.take(10),
            openQuestions = sentences.filter { it.contains("?", ignoreCase = false) }.take(10),
        )
    }

    private fun guessTitle(transcript: String): String {
        if (transcript.isBlank()) return "Meeting Notes"
        val firstLine = transcript.lineSequence().firstOrNull()?.trim().orEmpty()
        val candidate = firstLine.ifBlank { transcript.take(80) }
        return candidate.take(60).trim().ifBlank { "Meeting Notes" }
    }

    private fun splitSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size >= 3) return lines

        return text
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun ensureMinBullets(bullets: List<String>, min: Int): List<String> {
        if (bullets.size >= min) return bullets
        if (bullets.isEmpty()) return (1..min).map { "(no transcript content)" }

        val padded = bullets.toMutableList()
        while (padded.size < min) padded.add(bullets.last())
        return padded
    }
}

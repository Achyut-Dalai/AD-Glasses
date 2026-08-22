package com.ad_glasses.shared.notes

/** Platform-neutral input for turning a transcript into structured meeting notes. */
data class SummarizationRequest(
    val transcript: String,
    val hintTitle: String? = null,
    val maxSummaryBullets: Int = 10,
    val minSummaryBullets: Int = 5,
)

/** Provider-agnostic meeting notes that can be rendered consistently on every host. */
data class StructuredSummary(
    val title: String,
    val summaryBullets: List<String>,
    val actionItems: List<String>,
    val keyDecisions: List<String>,
    val openQuestions: List<String>,
    val timelineHighlights: List<String> = emptyList(),
)

interface SummarizationService {
    suspend fun summarize(request: SummarizationRequest): StructuredSummary
}

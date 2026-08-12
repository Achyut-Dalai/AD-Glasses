package com.achyut.adglasses.plugins.meetingsparknotes

data class MeetingRecord(
    val timestampMs: Long,
    val title: String,
    val summary: String,
    val actionItems: List<String>,
    val participants: List<String>,
    val durationMinutes: Int,
)

data class MeetingSummary(
    val id: String,
    val timestampMs: Long,
    val title: String,
    val summary: String,
    val actionItems: List<String>,
    val participants: List<String>,
    val durationMinutes: Int,
    val audioPath: String?,
)

enum class SummaryStyle {
    CONCISE,
    DETAILED,
    ACTION_FOCUSED,
}

package com.ad_glasses.ai.orchestrator

import android.content.Context

internal data class ParsedVisualResponse(
    val answer: String,
    val summary: String?,
)

/**
 * One-shot image turns may return a compact machine-only scene memory after the visible answer.
 * The raw image is never stored in ChatStore or reused as conversational history.
 */
internal object AssistantVisualContextCodec {
    private const val OPEN_TAG = "<AD_VISUAL_CONTEXT>"
    private const val CLOSE_TAG = "</AD_VISUAL_CONTEXT>"
    const val MAX_SUMMARY_CHARS = 360

    val modelInstruction: String =
        " After the user-facing answer, append exactly one machine-only line in the form " +
            "$OPEN_TAG...$CLOSE_TAG. Keep the text inside to at most 45 words and make it factual: " +
            "include stable objects, people, colors, spatial relationships, and important visible text " +
            "that could help answer a later question about this image. Do not mention this memory line in the answer."

    fun parse(raw: String): ParsedVisualResponse {
        val clean = raw.trim()
        val start = clean.lastIndexOf(OPEN_TAG)
        if (start < 0) return ParsedVisualResponse(answer = clean, summary = null)
        val end = clean.indexOf(CLOSE_TAG, startIndex = start + OPEN_TAG.length)
        if (end < 0) return ParsedVisualResponse(answer = clean, summary = null)

        val summary = normalizeSummary(clean.substring(start + OPEN_TAG.length, end))
        val answer = (clean.substring(0, start) + clean.substring(end + CLOSE_TAG.length))
            .trim()
            .ifBlank { clean }
        return ParsedVisualResponse(
            answer = answer,
            summary = summary.takeIf { it.isNotBlank() },
        )
    }

    fun normalizeSummary(summary: String): String {
        val normalized = summary
            .replace(OPEN_TAG, " ")
            .replace(CLOSE_TAG, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.length <= MAX_SUMMARY_CHARS) return normalized
        val clipped = normalized.take(MAX_SUMMARY_CHARS - 1)
        val wordBoundary = clipped.lastIndexOf(' ')
        return (if (wordBoundary >= MAX_SUMMARY_CHARS / 2) clipped.take(wordBoundary) else clipped).trimEnd() + "…"
    }
}

/**
 * Keeps only the latest compact visual memory per conversation. This is deliberately separate from
 * durable ChatStore messages so old image bytes can never be serialized into later provider turns.
 */
internal object AssistantVisualContextStore {
    private const val PREFS = "ad_assistant_visual_context"

    fun get(context: Context, threadId: String): String? = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(threadId, null)
        ?.let(AssistantVisualContextCodec::normalizeSummary)
        ?.takeIf { it.isNotBlank() }

    fun put(context: Context, threadId: String, summary: String) {
        val clean = AssistantVisualContextCodec.normalizeSummary(summary)
        if (clean.isBlank()) return
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(threadId, clean)
            .apply()
    }

    fun clear(context: Context, threadId: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(threadId)
            .apply()
    }

    fun clearAll(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}

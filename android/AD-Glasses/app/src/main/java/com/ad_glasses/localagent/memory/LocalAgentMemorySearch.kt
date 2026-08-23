package com.ad_glasses.localagent.memory

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.ad_glasses.memoryvault.MemorySearchOrchestrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Backward-compatible entry point for local memory retrieval.
 *
 * Existing callers keep using this API while internals are orchestrated by
 * the privacy-aware memory vault search layer.
 */
object LocalAgentMemorySearch {
    private const val DEFAULT_FACT_LOOKBACK_DAYS: Int = 7
    private const val DEFAULT_TOP_FACTS: Int = 6
    private const val DEFAULT_TOP_SUMMARY_LINES: Int = 5
    private const val ASSISTANT_MEMORY_SEARCH_TIMEOUT_MS: Long = 450L
    private const val TIMING_TAG = "AssistantTiming"

    fun buildRelevantMemoryBlock(
        context: Context,
        queryText: String,
        date: String,
        lookbackDaysFacts: Int = DEFAULT_FACT_LOOKBACK_DAYS,
        topFacts: Int = DEFAULT_TOP_FACTS,
        topSummaryLines: Int = DEFAULT_TOP_SUMMARY_LINES,
        maxChars: Int = 1400,
    ): String {
        val startedAt = SystemClock.elapsedRealtime()
        val result = runCatching {
            runBlocking(Dispatchers.IO) {
                // Personal memory is optional enrichment. A slow Room/FTS/policy lookup must
                // never hold an interactive Ask turn hostage; fail open and answer without it.
                withTimeoutOrNull(ASSISTANT_MEMORY_SEARCH_TIMEOUT_MS) {
                    MemorySearchOrchestrator.buildRelevantMemoryBlock(
                        context = context,
                        queryText = queryText,
                        date = date,
                        params = MemorySearchOrchestrator.SearchParams(
                            lookbackDaysFacts = lookbackDaysFacts,
                            topFacts = topFacts,
                            topSummaryLines = topSummaryLines,
                            topScreenHits = 3,
                            maxChars = maxChars,
                        ),
                    )
                }.orEmpty()
            }
        }.getOrDefault("")
        Log.i(
            TIMING_TAG,
            "stage=memory_search elapsedMs=${SystemClock.elapsedRealtime() - startedAt} hit=${result.isNotBlank()}",
        )
        return result
    }
}

package com.ad_glasses.localagent.memory

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.ad_glasses.memoryvault.MemorySearchOrchestrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Backward-compatible entry point for local memory retrieval.
 *
 * Interactive assistant turns must never wait for Room/FTS/vault work. Memory search therefore
 * uses stale-while-revalidate semantics: return a cached result immediately (or no enrichment on a
 * cold miss) and refresh on a dedicated background worker. If the underlying blocking storage
 * stack stalls, it can no longer stall Ask.
 */
object LocalAgentMemorySearch {
    private const val DEFAULT_FACT_LOOKBACK_DAYS: Int = 7
    private const val DEFAULT_TOP_FACTS: Int = 6
    private const val DEFAULT_TOP_SUMMARY_LINES: Int = 5
    private const val CACHE_TTL_MS: Long = 60_000L
    private const val MAX_CACHE_ENTRIES: Int = 32
    private const val TIMING_TAG = "AssistantTiming"

    private data class CacheEntry(
        val value: String,
        val updatedAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val refreshBusy = AtomicBoolean(false)
    private val refreshExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ad-memory-search").apply { isDaemon = true }
    }

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
        val now = startedAt
        val cacheKey = buildString {
            append(date.trim())
            append('|').append(lookbackDaysFacts)
            append('|').append(topFacts)
            append('|').append(topSummaryLines)
            append('|').append(maxChars)
            append('|').append(queryText.trim().lowercase().take(320))
        }
        val cached = cache[cacheKey]
        val cacheAgeMs = cached?.let { (now - it.updatedAtMs).coerceAtLeast(0L) }
        val fresh = cached != null && cacheAgeMs != null && cacheAgeMs <= CACHE_TTL_MS

        var refreshQueued = false
        if (!fresh && refreshBusy.compareAndSet(false, true)) {
            refreshQueued = true
            val appContext = context.applicationContext
            val query = queryText
            val searchDate = date
            val params = MemorySearchOrchestrator.SearchParams(
                lookbackDaysFacts = lookbackDaysFacts,
                topFacts = topFacts,
                topSummaryLines = topSummaryLines,
                topScreenHits = 3,
                maxChars = maxChars,
            )
            refreshExecutor.execute {
                val refreshStartedAt = SystemClock.elapsedRealtime()
                try {
                    val refreshed = runCatching {
                        runBlocking(Dispatchers.IO) {
                            MemorySearchOrchestrator.buildRelevantMemoryBlock(
                                context = appContext,
                                queryText = query,
                                date = searchDate,
                                params = params,
                            )
                        }
                    }.getOrDefault("")
                    cache[cacheKey] = CacheEntry(
                        value = refreshed,
                        updatedAtMs = SystemClock.elapsedRealtime(),
                    )
                    if (cache.size > MAX_CACHE_ENTRIES) {
                        cache.entries.minByOrNull { it.value.updatedAtMs }?.let { oldest ->
                            cache.remove(oldest.key, oldest.value)
                        }
                    }
                    Log.i(
                        TIMING_TAG,
                        "stage=memory_search_refresh elapsedMs=${SystemClock.elapsedRealtime() - refreshStartedAt} " +
                            "hit=${refreshed.isNotBlank()}",
                    )
                } finally {
                    refreshBusy.set(false)
                }
            }
        }

        val result = cached?.value.orEmpty()
        val source = when {
            fresh -> "cache_fresh"
            cached != null -> "cache_stale"
            else -> "cache_miss"
        }
        Log.i(
            TIMING_TAG,
            "stage=memory_search elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                "hit=${result.isNotBlank()} source=$source refreshQueued=$refreshQueued",
        )
        return result
    }
}

package com.ad_glasses.ai.grounding

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

enum class TavilySearchDepth(val wire: String) {
    FAST("fast"),
    BASIC("basic"),
    ADVANCED("advanced"),
}

enum class TavilyExtractDepth(val wire: String) {
    BASIC("basic"),
    ADVANCED("advanced"),
}

data class TavilySearchResult(
    val title: String,
    val url: String,
    val content: String,
    val score: Double,
)

/**
 * [answer] is retained only for source compatibility with existing callers. Search parsing always
 * returns null because AD treats Tavily strictly as retrieval evidence, never as an answering LLM.
 */
data class TavilySearchResponse(
    val answer: String?,
    val results: List<TavilySearchResult>,
)

data class TavilyExtractResult(
    val url: String,
    val rawContent: String,
)

/**
 * Small Android-native Tavily retrieval client.
 *
 * Tavily is allowed to search and extract public evidence. Tavily-generated answers are disabled at
 * the request boundary and ignored defensively if the service returns one anyway. The configured AD
 * model remains the only model that may synthesize Tavily search evidence into a user-facing answer.
 */
class TavilySearchClient(
    context: Context,
    private val client: OkHttpClient = defaultClient(),
) {
    private val appContext = context.applicationContext

    fun isConfigured(): Boolean {
        val config = GroundingPrefs.getConfig(appContext)
        return config.tavilyEnabled && GroundingPrefs.hasTavilyApiKey(appContext)
    }

    suspend fun search(
        query: String,
        depth: TavilySearchDepth = TavilySearchDepth.FAST,
        maxResults: Int = DEFAULT_MAX_RESULTS,
        topic: TavilySearchTopic = TavilySearchTopic.GENERAL,
        timeRange: TavilyTimeRange? = null,
        @Suppress("UNUSED_PARAMETER") includeAnswer: Boolean = false,
        includeDomains: List<String> = emptyList(),
    ): Result<TavilySearchResponse> = try {
        val key = requireApiKey()
        val payload = buildPayload(
            query = query,
            depth = depth,
            maxResults = maxResults,
            topic = topic,
            timeRange = timeRange,
            includeAnswer = false,
            includeDomains = includeDomains,
        )
        val request = Request.Builder()
            .url(SEARCH_URL)
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .post(payload.toString().toRequestBody(JSON))
            .build()

        val startedAt = SystemClock.elapsedRealtime()
        val call = client.newCall(request)
        call.timeout().timeout(
            if (depth == TavilySearchDepth.ADVANCED) ADVANCED_CALL_TIMEOUT_SECONDS else STANDARD_CALL_TIMEOUT_SECONDS,
            TimeUnit.SECONDS,
        )
        val parsed = call.awaitResponse().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Tavily HTTP ${response.code}: ${safeError(responseBody)}")
            }
            parse(responseBody)
        }
        Log.i(
            TAG,
            "search_done depth=${depth.wire} topic=${topic.wire} freshness=${timeRange?.wire ?: "none"} " +
                "domains=${includeDomains.size} answerRequested=false results=${parsed.results.size} " +
                "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
        )
        Result.success(parsed)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Log.w(TAG, "search_failed type=${error::class.java.simpleName} message=${error.message?.take(180)}")
        Result.failure(error)
    }

    /** Extracts only explicitly supplied pages. Keep this out of generic web search paths. */
    suspend fun extract(
        urls: List<String>,
        depth: TavilyExtractDepth = TavilyExtractDepth.BASIC,
    ): Result<List<TavilyExtractResult>> = try {
        val key = requireApiKey()
        val cleanUrls = urls.asSequence()
            .map(String::trim)
            .mapNotNull { it.take(MAX_URL_CHARS).toHttpUrlOrNull()?.toString() }
            .distinct()
            .take(MAX_EXTRACT_URLS)
            .toList()
        require(cleanUrls.isNotEmpty()) { "Tavily extract requires at least one valid URL." }

        val payload = JSONObject()
            .put("urls", JSONArray(cleanUrls))
            .put("extract_depth", depth.wire)
            .put("include_images", false)
        val request = Request.Builder()
            .url(EXTRACT_URL)
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .post(payload.toString().toRequestBody(JSON))
            .build()

        val startedAt = SystemClock.elapsedRealtime()
        val call = client.newCall(request)
        call.timeout().timeout(EXTRACT_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val parsed = call.awaitResponse().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Tavily extract HTTP ${response.code}: ${safeError(responseBody)}")
            }
            parseExtract(responseBody)
        }
        Log.i(
            TAG,
            "extract_done depth=${depth.wire} requested=${cleanUrls.size} results=${parsed.size} " +
                "chars=${parsed.sumOf { it.rawContent.length }} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
        )
        Result.success(parsed)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Log.w(TAG, "extract_failed type=${error::class.java.simpleName} message=${error.message?.take(180)}")
        Result.failure(error)
    }

    private fun requireApiKey(): String {
        val config = GroundingPrefs.getConfig(appContext)
        check(config.tavilyEnabled) { "Tavily search is disabled." }
        return GroundingPrefs.getTavilyApiKey(appContext)
            .also { check(it.isNotBlank()) { "Tavily API key is not configured." } }
    }

    internal fun buildPayload(
        query: String,
        depth: TavilySearchDepth,
        maxResults: Int,
        topic: TavilySearchTopic,
        timeRange: TavilyTimeRange?,
        @Suppress("UNUSED_PARAMETER") includeAnswer: Boolean,
        includeDomains: List<String> = emptyList(),
    ): JSONObject {
        val cleanQuery = query.replace(Regex("\\s+"), " ").trim().take(MAX_USER_QUERY_CHARS)
        require(cleanQuery.isNotBlank()) { "Tavily query cannot be blank." }
        return JSONObject()
            .put("query", cleanQuery.take(MAX_QUERY_CHARS))
            .put("search_depth", depth.wire)
            .put("chunks_per_source", CHUNKS_PER_SOURCE)
            .put("topic", topic.wire)
            // Never invoke Tavily answer generation. Tavily is retrieval-only in AD.
            .put("include_answer", false)
            .put("include_raw_content", false)
            .put("max_results", maxResults.coerceIn(1, MAX_RESULTS))
            .also { payload ->
                timeRange?.let { payload.put("time_range", it.wire) }
                val domains = includeDomains.map(String::trim).filter(String::isNotBlank).distinct().take(MAX_DOMAINS)
                if (domains.isNotEmpty()) payload.put("include_domains", JSONArray(domains))
            }
    }

    internal fun parse(payload: String): TavilySearchResponse {
        val root = JSONObject(payload)
        val items = root.optJSONArray("results")
        val results = buildList {
            if (items != null) {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val url = item.optString("url")
                        .trim()
                        .take(MAX_URL_CHARS)
                        .toHttpUrlOrNull()
                        ?.toString()
                        ?: continue
                    add(
                        TavilySearchResult(
                            title = item.optString("title").replace(Regex("\\s+"), " ").trim().take(180),
                            url = url,
                            content = item.optString("content")
                                .replace(Regex("\\s+"), " ")
                                .trim()
                                .take(MAX_SNIPPET_CHARS),
                            score = item.optDouble("score", 0.0),
                        ),
                    )
                }
            }
        }.distinctBy { it.url }

        // Ignore any legacy/unexpected `answer` field defensively. It must never enter AD context.
        return TavilySearchResponse(answer = null, results = results)
    }

    internal fun parseExtract(payload: String): List<TavilyExtractResult> {
        val items = JSONObject(payload).optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val url = item.optString("url")
                    .trim()
                    .take(MAX_URL_CHARS)
                    .toHttpUrlOrNull()
                    ?.toString()
                    ?: continue
                val raw = item.optString("raw_content")
                    .replace("\u0000", "")
                    .trim()
                    .take(MAX_EXTRACT_CHARS_PER_URL)
                if (raw.isNotBlank()) add(TavilyExtractResult(url = url, rawContent = raw))
            }
        }.distinctBy { it.url }
    }

    private fun safeError(payload: String): String = runCatching {
        val json = JSONObject(payload)
        json.optString("detail").ifBlank { json.optString("message") }
    }.getOrDefault("").ifBlank { "request failed" }.take(240)

    companion object {
        private const val TAG = "AssistantGrounding"
        private const val SEARCH_URL = "https://api.tavily.com/search"
        private const val EXTRACT_URL = "https://api.tavily.com/extract"
        private const val USER_AGENT = "AD-Glasses Android Tavily retrieval client"
        private const val MAX_USER_QUERY_CHARS = 1_300
        private const val MAX_QUERY_CHARS = 1_500
        private const val MAX_RESULTS = 8
        private const val DEFAULT_MAX_RESULTS = 3
        private const val MAX_DOMAINS = 4
        private const val MAX_URL_CHARS = 1_000
        private const val MAX_SNIPPET_CHARS = 1_600
        private const val MAX_EXTRACT_URLS = 4
        private const val MAX_EXTRACT_CHARS_PER_URL = 6_000
        private const val CHUNKS_PER_SOURCE = 3
        private const val STANDARD_CALL_TIMEOUT_SECONDS = 6L
        private const val ADVANCED_CALL_TIMEOUT_SECONDS = 8L
        private const val EXTRACT_CALL_TIMEOUT_SECONDS = 8L
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .callTimeout(9, TimeUnit.SECONDS)
            .build()

        private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, resource, _ -> resource.close() }
                }
            })
        }
    }
}

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
import org.json.JSONObject

enum class TavilySearchDepth(val wire: String) {
    FAST("fast"),
    BASIC("basic"),
    ADVANCED("advanced"),
}

data class TavilySearchResult(
    val title: String,
    val url: String,
    val content: String,
    val score: Double,
)

data class TavilySearchResponse(
    val answer: String?,
    val results: List<TavilySearchResult>,
)

/**
 * Small Android-native Tavily client.
 *
 * The semantic router chooses topic/freshness. Search depth stays FAST and raw page bodies stay off
 * in the assistant path. Tavily's own basic LLM answer is requested so straightforward web turns do
 * not need a second AD-model synthesis pass.
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
        includeAnswer: Boolean = true,
    ): Result<TavilySearchResponse> = try {
        val config = GroundingPrefs.getConfig(appContext)
        check(config.tavilyEnabled) { "Tavily search is disabled." }
        val key = GroundingPrefs.getTavilyApiKey(appContext)
        check(key.isNotBlank()) { "Tavily API key is not configured." }

        val payload = buildPayload(
            query = query,
            depth = depth,
            maxResults = maxResults,
            topic = topic,
            timeRange = timeRange,
            includeAnswer = includeAnswer,
        )
        val body = payload.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url(SEARCH_URL)
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .post(body)
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
                "answer=${!parsed.answer.isNullOrBlank()} results=${parsed.results.size} " +
                "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
        )
        Result.success(parsed)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    internal fun buildPayload(
        query: String,
        depth: TavilySearchDepth,
        maxResults: Int,
        topic: TavilySearchTopic,
        timeRange: TavilyTimeRange?,
        includeAnswer: Boolean,
    ): JSONObject {
        val cleanQuery = query.replace(Regex("\\s+"), " ").trim().take(MAX_USER_QUERY_CHARS)
        require(cleanQuery.isNotBlank()) { "Tavily query cannot be blank." }
        return JSONObject()
            .put("query", cleanQuery.take(MAX_QUERY_CHARS))
            .put("search_depth", depth.wire)
            .put("chunks_per_source", CHUNKS_PER_SOURCE)
            .put("topic", topic.wire)
            .put("include_answer", if (includeAnswer) "basic" else false)
            .put("include_raw_content", false)
            .put("max_results", maxResults.coerceIn(1, MAX_RESULTS))
            .also { payload -> timeRange?.let { payload.put("time_range", it.wire) } }
    }

    internal fun parse(payload: String): TavilySearchResponse {
        val root = JSONObject(payload)
        val answer = root.optString("answer").replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() }
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
        return TavilySearchResponse(answer = answer?.take(MAX_ANSWER_CHARS), results = results)
    }

    private fun safeError(payload: String): String = runCatching {
        val json = JSONObject(payload)
        json.optString("detail").ifBlank { json.optString("message") }
    }.getOrDefault("").ifBlank { "request failed" }.take(240)

    companion object {
        private const val TAG = "AssistantGrounding"
        private const val SEARCH_URL = "https://api.tavily.com/search"
        private const val USER_AGENT = "AD-Glasses Android Tavily client"
        private const val MAX_USER_QUERY_CHARS = 1_300
        private const val MAX_QUERY_CHARS = 1_500
        private const val MAX_RESULTS = 8
        private const val DEFAULT_MAX_RESULTS = 3
        private const val MAX_URL_CHARS = 1_000
        private const val MAX_SNIPPET_CHARS = 520
        private const val MAX_ANSWER_CHARS = 2_000
        private const val CHUNKS_PER_SOURCE = 1
        private const val STANDARD_CALL_TIMEOUT_SECONDS = 6L
        private const val ADVANCED_CALL_TIMEOUT_SECONDS = 8L
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

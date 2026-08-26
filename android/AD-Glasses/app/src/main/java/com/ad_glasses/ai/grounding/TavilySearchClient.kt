package com.ad_glasses.ai.grounding

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
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

/** Small Android-native Tavily client; raw page bodies are intentionally never requested. */
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
        maxResults: Int = 5,
    ): Result<TavilySearchResponse> = try {
        val config = GroundingPrefs.getConfig(appContext)
        check(config.tavilyEnabled) { "Tavily search is disabled." }
        val key = GroundingPrefs.getTavilyApiKey(appContext)
        check(key.isNotBlank()) { "Tavily API key is not configured." }

        val cleanQuery = query.replace(Regex("\\s+"), " ").trim().take(MAX_QUERY_CHARS)
        require(cleanQuery.isNotBlank()) { "Tavily query cannot be blank." }
        val body = JSONObject()
            .put("query", cleanQuery)
            .put("search_depth", depth.wire)
            .put("include_answer", true)
            .put("include_raw_content", false)
            .put("max_results", maxResults.coerceIn(1, MAX_RESULTS))
            .toString()
            .toRequestBody(JSON)

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
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Tavily HTTP ${response.code}: ${safeError(payload)}")
            }
            parse(payload)
        }
        Log.i(
            TAG,
            "search_done depth=${depth.wire} results=${parsed.results.size} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
        )
        Result.success(parsed)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    internal fun parse(payload: String): TavilySearchResponse {
        val root = JSONObject(payload)
        val answer = root.optString("answer").trim().takeIf { it.isNotBlank() }
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
        private const val MAX_QUERY_CHARS = 1_500
        private const val MAX_RESULTS = 8
        private const val MAX_URL_CHARS = 1_000
        private const val MAX_SNIPPET_CHARS = 1_200
        private const val MAX_ANSWER_CHARS = 1_500
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
                    val token = continuation.tryResumeWithException(error)
                    if (token != null) continuation.completeResume(token)
                }

                override fun onResponse(call: Call, response: Response) {
                    val token = continuation.tryResume(response)
                    if (token != null) {
                        continuation.completeResume(token)
                    } else {
                        response.close()
                    }
                }
            })
        }
    }
}

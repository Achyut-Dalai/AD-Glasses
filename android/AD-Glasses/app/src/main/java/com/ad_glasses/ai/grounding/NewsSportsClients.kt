package com.ad_glasses.ai.grounding

import java.io.IOException
import java.io.StringReader
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

internal data class SyndicatedHeadline(
    val title: String,
    val link: String,
    val source: String?,
    val publishedAt: String?,
)

class GoogleNewsRssClient(
    private val client: OkHttpClient = defaultSyndicationClient(),
) {
    suspend fun lookup(query: String? = null): Result<StructuredKnowledgeResult> = try {
        val cleanQuery = query?.cleanFeedText(MAX_QUERY_CHARS)?.takeIf(String::isNotBlank)
        val locale = Locale.getDefault()
        val country = locale.country.uppercase(Locale.US).takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "IN"
        val language = locale.language.lowercase(Locale.US).takeIf { it.matches(Regex("[a-z]{2,3}")) } ?: "en"
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("news.google.com")
            .addPathSegments(if (cleanQuery == null) "rss" else "rss/search")
            .apply { if (cleanQuery != null) addQueryParameter("q", cleanQuery) }
            .addQueryParameter("hl", "$language-$country")
            .addQueryParameter("gl", country)
            .addQueryParameter("ceid", "$country:$language")
            .build()
        val items = parseRssItems(client.fetchFeed(url, "Google News")).take(MAX_ITEMS)
        if (items.isEmpty()) error("Google News RSS returned no headlines.")
        Result.success(items.toNewsResult(cleanQuery))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    internal fun parse(xml: String): List<SyndicatedHeadline> = parseRssItems(xml)

    private fun List<SyndicatedHeadline>.toNewsResult(query: String?): StructuredKnowledgeResult {
        val shown = take(MAX_SPOKEN_ITEMS)
        val answer = buildString {
            append(if (query == null) "Top Google News headlines: " else "Google News results for $query: ")
            append(shown.joinToString("; ") { item ->
                if (item.source.isNullOrBlank()) item.title else "${item.title} — ${item.source}"
            })
            append('.')
        }.take(MAX_ANSWER_CHARS)
        val context = buildString {
            appendLine(if (query == null) "Google News RSS top headlines:" else "Google News RSS results for: $query")
            this@toNewsResult.forEachIndexed { index, item ->
                append("[${index + 1}] ${item.title}")
                item.source?.takeIf(String::isNotBlank)?.let { append("; publisher=$it") }
                item.publishedAt?.takeIf(String::isNotBlank)?.let { append("; published=$it") }
                appendLine()
            }
            append("Headline records only; do not invent article-body details.")
        }.take(MAX_CONTEXT_CHARS)
        return StructuredKnowledgeResult(
            answer = answer,
            context = context,
            sources = map { GroundingSource(it.source?.takeIf(String::isNotBlank) ?: "Google News", it.link) }
                .distinctBy(GroundingSource::url)
                .take(MAX_ITEMS),
        )
    }

    private companion object {
        const val MAX_QUERY_CHARS = 420
        const val MAX_ITEMS = 6
        const val MAX_SPOKEN_ITEMS = 4
        const val MAX_ANSWER_CHARS = 1_800
        const val MAX_CONTEXT_CHARS = 2_000
    }
}

class EspnSportsClient(
    private val client: OkHttpClient = defaultSyndicationClient(),
) {
    suspend fun lookup(query: String? = null): Result<StructuredKnowledgeResult> = try {
        val cleanQuery = query?.cleanFeedText(MAX_QUERY_CHARS)?.takeIf(String::isNotBlank)
        val all = parseRssItems(client.fetchFeed(TOP_HEADLINES_URL, "ESPN")).take(MAX_ITEMS)
        if (all.isEmpty()) error("ESPN RSS returned no sports headlines.")
        val chosen = if (cleanQuery == null) all else selectRelevant(cleanQuery, all)
        if (chosen.isEmpty()) error("ESPN RSS did not contain enough evidence for the specific sports query.")
        Result.success(chosen.toSportsResult(cleanQuery))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    internal fun parse(xml: String): List<SyndicatedHeadline> = parseRssItems(xml)

    internal fun selectRelevant(query: String, items: List<SyndicatedHeadline>): List<SyndicatedHeadline> {
        val queryTokens = semanticTokens(query)
        if (queryTokens.isEmpty()) return items.take(MAX_MATCHES)
        val minimumOverlap = if (queryTokens.size >= 3) 2 else 1
        return items
            .map { item ->
                val itemTokens = semanticTokens(item.title + " " + item.source.orEmpty())
                item to queryTokens.count(itemTokens::contains)
            }
            .sortedByDescending { it.second }
            .filter { it.second >= minimumOverlap }
            .map { it.first }
            .take(MAX_MATCHES)
    }

    private fun List<SyndicatedHeadline>.toSportsResult(query: String?): StructuredKnowledgeResult {
        val shown = take(MAX_SPOKEN_ITEMS)
        val answer = buildString {
            append(if (query == null) "ESPN sports headlines: " else "ESPN headlines relevant to $query: ")
            append(shown.joinToString("; ") { item -> item.title })
            append('.')
        }.take(MAX_ANSWER_CHARS)
        val context = buildString {
            appendLine(if (query == null) "ESPN general sports RSS headlines:" else "ESPN RSS evidence for: $query")
            this@toSportsResult.forEachIndexed { index, item ->
                append("[${index + 1}] ${item.title}")
                item.publishedAt?.takeIf(String::isNotBlank)?.let { append("; published=$it") }
                appendLine()
            }
            append("Headline evidence only; do not infer a live score/result unless a headline states it.")
        }.take(MAX_CONTEXT_CHARS)
        return StructuredKnowledgeResult(
            answer = answer,
            context = context,
            sources = map { GroundingSource("ESPN — ${it.title.take(100)}", it.link) }
                .distinctBy(GroundingSource::url)
                .take(MAX_MATCHES),
        )
    }

    private companion object {
        val TOP_HEADLINES_URL: HttpUrl = HttpUrl.Builder()
            .scheme("https")
            .host("www.espn.com")
            .addPathSegments("espn/rss/news")
            .build()
        const val MAX_QUERY_CHARS = 420
        const val MAX_ITEMS = 30
        const val MAX_MATCHES = 6
        const val MAX_SPOKEN_ITEMS = 4
        const val MAX_ANSWER_CHARS = 1_800
        const val MAX_CONTEXT_CHARS = 2_000
    }
}

private fun parseRssItems(xml: String): List<SyndicatedHeadline> {
    if (xml.isBlank()) return emptyList()
    val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(StringReader(xml))
    }
    val items = mutableListOf<SyndicatedHeadline>()
    var event = parser.eventType
    var insideItem = false
    var title: String? = null
    var link: String? = null
    var source: String? = null
    var published: String? = null
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name?.lowercase(Locale.US)) {
                "item" -> {
                    insideItem = true
                    title = null
                    link = null
                    source = null
                    published = null
                }
                "title" -> if (insideItem) title = runCatching { parser.nextText() }.getOrNull()?.cleanFeedText(500)
                "link" -> if (insideItem) link = runCatching { parser.nextText() }.getOrNull()?.trim()
                "source" -> if (insideItem) source = runCatching { parser.nextText() }.getOrNull()?.cleanFeedText(160)
                "pubdate", "published", "updated" -> if (insideItem) {
                    published = runCatching { parser.nextText() }.getOrNull()?.cleanFeedText(160)
                }
            }
            XmlPullParser.END_TAG -> if (insideItem && parser.name.equals("item", ignoreCase = true)) {
                val safeTitle = title?.takeIf(String::isNotBlank)
                val safeLink = link?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                if (safeTitle != null && safeLink != null) items += SyndicatedHeadline(safeTitle, safeLink, source, published)
                insideItem = false
            }
        }
        event = parser.next()
    }
    return items
}

private fun semanticTokens(value: String): Set<String> = TOKEN.findAll(value.lowercase(Locale.US))
    .map(MatchResult::value)
    .filter { it.length >= 2 && it !in GENERIC_STOPWORDS }
    .toSet()

private val TOKEN = Regex("[a-z0-9]+")
private val GENERIC_STOPWORDS = setOf(
    "a", "an", "and", "are", "at", "be", "for", "from", "how", "in", "is", "it", "latest", "live",
    "me", "of", "on", "please", "score", "scores", "result", "results", "the", "today", "what", "when",
    "who", "with", "won",
)

private fun String.cleanFeedText(maxChars: Int): String = replace(Regex("\\s+"), " ").trim().take(maxChars)

private fun defaultSyndicationClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(3, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .callTimeout(6, TimeUnit.SECONDS)
    .build()

private suspend fun OkHttpClient.fetchFeed(url: HttpUrl, label: String): String {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "AD-Glasses/alpha")
        .header("Accept", "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.5")
        .get()
        .build()
    return newCall(request).awaitFeedBody(label)
}

private suspend fun Call.awaitFeedBody(label: String): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, error: IOException) {
            continuation.resumeWithException(error)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    continuation.resumeWithException(IOException("$label HTTP ${it.code}"))
                    return
                }
                continuation.resume(body) { _, _, _ -> }
            }
        }
    })
}

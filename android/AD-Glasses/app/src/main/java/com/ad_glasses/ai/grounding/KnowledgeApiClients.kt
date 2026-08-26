package com.ad_glasses.ai.grounding

import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/** Small, bounded result returned by a structured public-data capability. */
data class StructuredKnowledgeResult(
    val answer: String,
    val context: String,
    val sources: List<GroundingSource>,
)

class WikipediaKnowledgeClient(
    private val client: OkHttpClient = defaultKnowledgeClient(),
) {
    suspend fun lookup(query: String, language: String = "en"): Result<StructuredKnowledgeResult> = try {
        val cleanQuery = cleanText(query, 300)
        require(cleanQuery.isNotBlank()) { "Wikipedia query cannot be blank." }
        val lang = sanitizeWikiLanguage(language)
        val searchUrl = HttpUrl.Builder()
            .scheme("https")
            .host("$lang.wikipedia.org")
            .addPathSegments("w/rest.php/v1/search/page")
            .addQueryParameter("q", cleanQuery)
            .addQueryParameter("limit", "1")
            .build()
        val search = getJson(searchUrl)
        val page = search.optJSONArray("pages")?.optJSONObject(0)
            ?: throw IllegalStateException("Wikipedia returned no matching page.")
        val title = cleanText(page.optString("title"), 240)
        require(title.isNotBlank()) { "Wikipedia returned a page without a title." }

        val summaryUrl = HttpUrl.Builder()
            .scheme("https")
            .host("$lang.wikipedia.org")
            .addPathSegments("api/rest_v1/page/summary")
            .addPathSegment(title)
            .build()
        val summary = getJson(summaryUrl)
        val resolvedTitle = cleanText(summary.optString("title").ifBlank { title }, 240)
        val description = cleanText(summary.optString("description"), 300)
        val extract = cleanText(summary.optString("extract"), MAX_WIKI_EXTRACT_CHARS)
        if (extract.isBlank()) throw IllegalStateException("Wikipedia returned no usable summary.")
        val pageUrl = summary.optJSONObject("content_urls")
            ?.optJSONObject("desktop")
            ?.optString("page")
            ?.takeIf { it.startsWith("https://") }
            ?: "https://$lang.wikipedia.org/wiki/${title.replace(' ', '_')}"
        val answer = buildString {
            append(resolvedTitle)
            if (description.isNotBlank()) append(" — $description")
            append(". ")
            append(extract)
        }.trim().take(MAX_STRUCTURED_ANSWER_CHARS)
        Result.success(
            StructuredKnowledgeResult(
                answer = answer,
                context = "Wikipedia article: $resolvedTitle. ${description.take(220)}. ${extract.take(MAX_CONTEXT_CHARS)}"
                    .trim()
                    .take(MAX_CONTEXT_CHARS),
                sources = listOf(GroundingSource("Wikipedia — $resolvedTitle", pageUrl)),
            ),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun getJson(url: HttpUrl): JSONObject {
        val response = get(url)
        return JSONObject(response)
    }

    private suspend fun get(url: HttpUrl): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Api-User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        return client.newCall(request).awaitBody("Wikipedia")
    }

    private companion object {
        const val USER_AGENT = "AD-Glasses/alpha (https://github.com/Achyut-Dalai/AD-Glasses)"
        const val MAX_WIKI_EXTRACT_CHARS = 1_400
        const val MAX_CONTEXT_CHARS = 1_500
        const val MAX_STRUCTURED_ANSWER_CHARS = 1_800
    }
}

class FreeDictionaryClient(
    private val client: OkHttpClient = defaultKnowledgeClient(),
) {
    suspend fun lookup(word: String, language: String = "en"): Result<StructuredKnowledgeResult> = try {
        val cleanWord = cleanText(word, 120)
        require(cleanWord.isNotBlank()) { "Dictionary word cannot be blank." }
        val lang = sanitizeDictionaryLanguage(language)
        val url = "https://api.dictionaryapi.dev/api/v2/entries/$lang/$cleanWord".toHttpUrl()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        val body = client.newCall(request).awaitBody("Dictionary")
        val entries = JSONArray(body)
        val entry = entries.optJSONObject(0) ?: throw IllegalStateException("Dictionary returned no entry.")
        val resolvedWord = cleanText(entry.optString("word").ifBlank { cleanWord }, 120)
        val phonetic = cleanText(entry.optString("phonetic"), 100).ifBlank {
            entry.optJSONArray("phonetics")?.firstNonBlankString("text", 100).orEmpty()
        }
        val definitions = mutableListOf<String>()
        val meanings = entry.optJSONArray("meanings")
        if (meanings != null) {
            for (i in 0 until meanings.length()) {
                val meaning = meanings.optJSONObject(i) ?: continue
                val part = cleanText(meaning.optString("partOfSpeech"), 50)
                val defs = meaning.optJSONArray("definitions") ?: continue
                for (j in 0 until defs.length()) {
                    val definition = cleanText(defs.optJSONObject(j)?.optString("definition").orEmpty(), 420)
                    if (definition.isNotBlank()) {
                        definitions += if (part.isBlank()) definition else "$part: $definition"
                    }
                    if (definitions.size >= 3) break
                }
                if (definitions.size >= 3) break
            }
        }
        if (definitions.isEmpty()) throw IllegalStateException("Dictionary returned no usable definition.")
        val answer = buildString {
            append(resolvedWord)
            if (phonetic.isNotBlank()) append(" ($phonetic)")
            append(": ")
            append(definitions.joinToString(" "))
        }.take(MAX_STRUCTURED_ANSWER_CHARS)
        Result.success(
            StructuredKnowledgeResult(
                answer = answer,
                context = "Dictionary entry for $resolvedWord. ${definitions.joinToString(" ").take(MAX_CONTEXT_CHARS)}",
                sources = listOf(GroundingSource("Free Dictionary API — $resolvedWord", "https://dictionaryapi.dev/")),
            ),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private companion object {
        const val USER_AGENT = "AD-Glasses Android dictionary client"
        const val MAX_CONTEXT_CHARS = 1_300
        const val MAX_STRUCTURED_ANSWER_CHARS = 1_500
    }
}

data class CurrencyQuote(
    val base: String,
    val quote: String,
    val rate: Double,
    val date: String?,
)

class FrankfurterCurrencyClient(
    private val client: OkHttpClient = defaultKnowledgeClient(),
) {
    suspend fun convert(amount: Double, base: String, quote: String): Result<StructuredKnowledgeResult> = try {
        require(amount.isFinite()) { "Currency amount is invalid." }
        val from = sanitizeCurrencyCode(base)
        val to = sanitizeCurrencyCode(quote)
        require(from != to) { "Source and target currencies must differ." }
        val rateUrl = "https://api.frankfurter.dev/v2/rate/$from/$to".toHttpUrl()
        val request = Request.Builder()
            .url(rateUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        val body = client.newCall(request).awaitBody("Frankfurter")
        val parsed = parseQuote(body, from, to)
        val converted = amount * parsed.rate
        val amountText = formatDecimal(amount)
        val convertedText = formatDecimal(converted)
        val rateText = formatDecimal(parsed.rate)
        val dateText = parsed.date?.takeIf { it.isNotBlank() }?.let { " Reference date $it." }.orEmpty()
        val answer = "$amountText $from is about $convertedText $to at a reference rate of 1 $from = $rateText $to.$dateText"
        Result.success(
            StructuredKnowledgeResult(
                answer = answer,
                context = "Frankfurter reference exchange rate: base=$from quote=$to rate=$rateText amount=$amountText converted=$convertedText date=${parsed.date ?: "latest"}. " +
                    "These are reference exchange rates, not a guaranteed card/cash/real-time trading quote.",
                sources = listOf(GroundingSource("Frankfurter exchange rates", "https://frankfurter.dev/")),
            ),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    internal fun parseQuote(body: String, expectedBase: String, expectedQuote: String): CurrencyQuote {
        val trimmed = body.trim()
        val root = if (trimmed.startsWith("[")) {
            JSONArray(trimmed).optJSONObject(0) ?: throw IllegalStateException("Frankfurter returned no quote.")
        } else {
            JSONObject(trimmed)
        }
        val rate = when {
            root.has("rate") -> root.optDouble("rate", Double.NaN)
            root.optJSONObject("rates")?.has(expectedQuote) == true ->
                root.optJSONObject("rates")!!.optDouble(expectedQuote, Double.NaN)
            else -> Double.NaN
        }
        if (!rate.isFinite() || rate <= 0.0) throw IllegalStateException("Frankfurter returned an invalid rate.")
        val base = root.optString("base").trim().uppercase(Locale.US).ifBlank { expectedBase }
        val quote = root.optString("quote").trim().uppercase(Locale.US).ifBlank { expectedQuote }
        val date = root.optString("date").trim().takeIf(String::isNotBlank)
        return CurrencyQuote(base = base, quote = quote, rate = rate, date = date)
    }

    private companion object {
        const val USER_AGENT = "AD-Glasses Android currency client"
    }
}

class OpenLibraryKnowledgeClient(
    private val client: OkHttpClient = defaultKnowledgeClient(),
) {
    suspend fun lookup(query: String): Result<StructuredKnowledgeResult> = try {
        val cleanQuery = cleanText(query, 320)
        require(cleanQuery.isNotBlank()) { "Book query cannot be blank." }
        val url = "https://openlibrary.org/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("q", cleanQuery)
            .addQueryParameter("limit", "3")
            .addQueryParameter("fields", "key,title,author_name,first_publish_year,edition_count")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        val body = client.newCall(request).awaitBody("Open Library")
        val docs = JSONObject(body).optJSONArray("docs") ?: JSONArray()
        val books = buildList {
            for (i in 0 until minOf(docs.length(), 3)) {
                val item = docs.optJSONObject(i) ?: continue
                val title = cleanText(item.optString("title"), 240)
                if (title.isBlank()) continue
                val authors = item.optJSONArray("author_name")?.toStringList(3).orEmpty()
                val year = item.optInt("first_publish_year", 0).takeIf { it > 0 }
                val editions = item.optInt("edition_count", 0).takeIf { it > 0 }
                val key = item.optString("key").trim().takeIf { it.startsWith("/works/") }
                add(BookLookup(title, authors, year, editions, key))
            }
        }
        if (books.isEmpty()) throw IllegalStateException("Open Library returned no matching books.")
        val first = books.first()
        val answer = buildString {
            append(first.title)
            if (first.authors.isNotEmpty()) append(" by ${first.authors.joinToString(", ")}")
            first.firstPublishYear?.let { append(", first published in $it") }
            append('.')
            if (books.size > 1) {
                append(" Other close matches: ")
                append(books.drop(1).joinToString("; ") { it.title })
                append('.')
            }
        }.take(MAX_STRUCTURED_ANSWER_CHARS)
        val context = books.joinToString("\n") { book ->
            buildString {
                append("Book: ${book.title}")
                if (book.authors.isNotEmpty()) append("; authors=${book.authors.joinToString(", ")}")
                book.firstPublishYear?.let { append("; first_publish_year=$it") }
                book.editionCount?.let { append("; edition_count=$it") }
            }
        }.take(MAX_CONTEXT_CHARS)
        val sourceUrl = first.key?.let { "https://openlibrary.org$it" }
            ?: "https://openlibrary.org/search?q=${cleanQuery.replace(' ', '+')}"
        Result.success(
            StructuredKnowledgeResult(
                answer = answer,
                context = context,
                sources = listOf(GroundingSource("Open Library — ${first.title}", sourceUrl)),
            ),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private data class BookLookup(
        val title: String,
        val authors: List<String>,
        val firstPublishYear: Int?,
        val editionCount: Int?,
        val key: String?,
    )

    private companion object {
        const val USER_AGENT = "AD-Glasses/alpha (https://github.com/Achyut-Dalai/AD-Glasses)"
        const val MAX_CONTEXT_CHARS = 1_500
        const val MAX_STRUCTURED_ANSWER_CHARS = 1_500
    }
}

private fun defaultKnowledgeClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(3, TimeUnit.SECONDS)
    .readTimeout(6, TimeUnit.SECONDS)
    .callTimeout(7, TimeUnit.SECONDS)
    .build()

private suspend fun Call.awaitBody(label: String): String = suspendCancellableCoroutine { continuation ->
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

private fun cleanText(value: String, maxChars: Int): String = value
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(maxChars)

private fun sanitizeWikiLanguage(value: String): String {
    val lang = value.trim().lowercase(Locale.US).substringBefore('-')
    return lang.takeIf { Regex("[a-z]{2,12}").matches(it) } ?: "en"
}

private fun sanitizeDictionaryLanguage(value: String): String {
    val lang = value.trim().lowercase(Locale.US).substringBefore('-')
    return lang.takeIf { Regex("[a-z]{2,8}").matches(it) } ?: "en"
}

private fun sanitizeCurrencyCode(value: String): String {
    val code = value.trim().uppercase(Locale.US)
    require(Regex("[A-Z]{3}").matches(code)) { "Currency code must be a 3-letter ISO code." }
    return code
}

private fun formatDecimal(value: Double): String = when {
    !value.isFinite() -> "0"
    kotlin.math.abs(value) >= 100 -> String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    kotlin.math.abs(value) >= 1 -> String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
    else -> String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
}

private fun JSONArray.firstNonBlankString(key: String, maxChars: Int): String? {
    for (i in 0 until length()) {
        val value = cleanText(optJSONObject(i)?.optString(key).orEmpty(), maxChars)
        if (value.isNotBlank()) return value
    }
    return null
}

private fun JSONArray.toStringList(limit: Int): List<String> = buildList {
    for (i in 0 until minOf(length(), limit)) {
        cleanText(optString(i), 160).takeIf(String::isNotBlank)?.let(::add)
    }
}

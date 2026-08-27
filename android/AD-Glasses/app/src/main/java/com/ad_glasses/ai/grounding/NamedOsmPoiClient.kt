package com.ad_glasses.ai.grounding

import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

/**
 * Bounded text/brand POI lookup around a known point.
 *
 * Nominatim's viewbox is a ranking hint unless a bounded search is requested, so it is a poor
 * primitive for "KFC near me" or "navigate to the nearest KFC". This client asks Overpass for
 * named OSM features inside an explicit radius and ranks the returned candidates locally.
 */
internal class NamedOsmPoiClient(
    private val configProvider: () -> GroundingServiceConfig,
    private val client: OkHttpClient = defaultClient(),
) {
    constructor(configProvider: () -> GroundingServiceConfig) : this(
        configProvider = configProvider,
        client = defaultClient(),
    )

    suspend fun nearby(
        origin: GeoPoint,
        query: String,
        radiusMeters: Int,
        limit: Int = 6,
    ): Result<List<OsmPlace>> = try {
        val clean = sanitizeQuery(query)
        require(clean.isNotBlank()) { "Nearby place query cannot be blank." }
        val radius = radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        val outputLimit = limit.coerceIn(1, MAX_RESULTS)
        val valuePattern = escapeOverpassRegex(clean)
        val overpassQuery = buildString {
            append("[out:json][timeout:6];(")
            SEARCH_TAGS.forEach { key ->
                append("nwr(around:$radius,${origin.latitude},${origin.longitude})")
                append("[\"").append(key).append("\"~\"").append(valuePattern).append("\",i];")
            }
            append(");out body center ").append(MAX_RAW_RESULTS).append(';')
        }

        val config = configProvider()
        val request = Request.Builder()
            .url(config.overpassEndpoint)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(FormBody.Builder().add("data", overpassQuery).build())
            .build()
        val call = client.newCall(request)
        call.timeout().timeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val places = call.awaitResponse().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Overpass named POI HTTP ${response.code}")
            parse(payload, origin, clean, outputLimit)
        }
        Result.success(places)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    internal fun parse(payload: String, origin: GeoPoint, query: String, limit: Int): List<OsmPlace> {
        val elements = runCatching { JSONObject(payload).optJSONArray("elements") }.getOrNull() ?: return emptyList()
        val candidates = buildList {
            for (index in 0 until elements.length()) {
                val item = elements.optJSONObject(index) ?: continue
                val tags = item.optJSONObject("tags") ?: JSONObject()
                val center = item.optJSONObject("center")
                val lat = when {
                    item.has("lat") -> item.optDouble("lat")
                    center != null && center.has("lat") -> center.optDouble("lat")
                    else -> Double.NaN
                }
                val lon = when {
                    item.has("lon") -> item.optDouble("lon")
                    center != null && center.has("lon") -> center.optDouble("lon")
                    else -> Double.NaN
                }
                if (!lat.isFinite() || !lon.isFinite()) continue
                val point = GeoPoint(lat, lon)
                val name = firstTag(tags, "name", "brand", "operator", "short_name") ?: continue
                add(
                    RankedPlace(
                        place = OsmPlace(
                            name = name.take(MAX_NAME_CHARS),
                            category = descriptor(tags),
                            point = point,
                            distanceMeters = OsmServiceClient.haversineMeters(origin, point).toInt(),
                        ),
                        textRank = textRank(query, tags),
                    ),
                )
            }
        }
        return candidates
            .sortedWith(compareBy<RankedPlace> { it.textRank }.thenBy { it.place.distanceMeters })
            .distinctBy { ranked -> ranked.place.name.lowercase(Locale.US) to roundedPoint(ranked.place.point) }
            .take(limit.coerceIn(1, MAX_RESULTS))
            .map(RankedPlace::place)
    }

    private fun textRank(query: String, tags: JSONObject): Int {
        val target = normalize(query)
        val values = SEARCH_TAGS.mapNotNull { firstTag(tags, it) }.map(::normalize)
        return when {
            values.any { it == target } -> 0
            values.any { it.startsWith(target) || target.startsWith(it) } -> 1
            values.any { it.contains(target) || target.contains(it) } -> 2
            else -> {
                val queryTokens = semanticTokens(target)
                val bestOverlap = values.maxOfOrNull { semanticTokens(it).count(queryTokens::contains) } ?: 0
                if (bestOverlap > 0) 3 else 4
            }
        }
    }

    private fun descriptor(tags: JSONObject): String {
        val category = firstTag(tags, "amenity", "shop", "tourism", "leisure", "healthcare", "office")
            ?.replace('_', ' ') ?: "place"
        val parts = mutableListOf(category)
        firstTag(tags, "brand")?.takeIf { !it.equals(firstTag(tags, "name"), ignoreCase = true) }?.let {
            parts += "brand: ${it.take(80)}"
        }
        firstTag(tags, "opening_hours")?.let { parts += "hours: ${it.take(120)}" }
        val street = firstTag(tags, "addr:street")
        val house = firstTag(tags, "addr:housenumber")
        val locality = firstTag(tags, "addr:city", "addr:suburb", "addr:place")
        listOfNotNull(listOfNotNull(house, street).joinToString(" ").takeIf(String::isNotBlank), locality)
            .joinToString(", ")
            .takeIf(String::isNotBlank)
            ?.let { parts += "address: ${it.take(180)}" }
        return parts.joinToString("; ").take(MAX_DESCRIPTOR_CHARS)
    }

    private fun sanitizeQuery(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .trim()
        .filter { it.isLetterOrDigit() || it == ' ' || it in SAFE_PUNCTUATION }
        .take(MAX_QUERY_CHARS)

    private fun escapeOverpassRegex(value: String): String = buildString {
        value.forEach { ch ->
            if (ch in REGEX_META || ch == '\\' || ch == '"') append('\\')
            append(ch)
        }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun semanticTokens(value: String): Set<String> = normalize(value).split(' ')
        .filter { it.length >= 2 }
        .toSet()

    private fun firstTag(tags: JSONObject, vararg keys: String): String? = keys.asSequence()
        .map { key -> tags.optString(key).replace(Regex("\\s+"), " ").trim() }
        .firstOrNull(String::isNotBlank)

    private fun roundedPoint(point: GeoPoint): Pair<Int, Int> =
        (point.latitude * 100_000).toInt() to (point.longitude * 100_000).toInt()

    private data class RankedPlace(val place: OsmPlace, val textRank: Int)

    private companion object {
        const val USER_AGENT = "AD-Glasses/alpha (https://github.com/Achyut-Dalai/AD-Glasses)"
        const val CALL_TIMEOUT_SECONDS = 7L
        const val MIN_RADIUS_METERS = 50
        const val MAX_RADIUS_METERS = 25_000
        const val MAX_QUERY_CHARS = 96
        const val MAX_RESULTS = 12
        const val MAX_RAW_RESULTS = 40
        const val MAX_NAME_CHARS = 180
        const val MAX_DESCRIPTOR_CHARS = 420
        val SEARCH_TAGS = listOf("name", "brand", "operator", "short_name")
        val SAFE_PUNCTUATION = setOf('&', '\'', '-', '.', '(', ')', '/')
        val REGEX_META = setOf('.', '^', '$', '|', '?', '*', '+', '(', ')', '[', ']', '{', '}')

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}

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

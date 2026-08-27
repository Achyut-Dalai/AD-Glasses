package com.ad_glasses.ai.grounding

import android.os.SystemClock
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
 * Fast, bounded OSM discovery for nearby public-transport infrastructure.
 *
 * OSM is intentionally used only for physical stop/station discovery. Realtime predictions belong
 * to [TransitRealtimeClient]. Results are cached briefly because the physical stop set does not
 * change between consecutive voice turns and public Overpass instances can be bursty.
 */
class OsmTransitClient(
    private val configProvider: () -> GroundingServiceConfig,
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun nearby(
        origin: GeoPoint,
        radiusMeters: Int = DEFAULT_RADIUS_METERS,
        limit: Int = DEFAULT_LIMIT,
    ): Result<List<OsmPlace>> = try {
        val radius = radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        val outputLimit = limit.coerceIn(1, MAX_RESULTS)
        val config = configProvider()
        val cacheKey = cacheKey(config.overpassEndpoint, origin, radius, outputLimit)
        synchronized(cache) {
            cache[cacheKey]?.takeIf { SystemClock.elapsedRealtime() - it.createdAtMs <= CACHE_TTL_MS }
        }?.let { return Result.success(it.places) }

        val query = buildNearbyQuery(origin, radius)
        val request = Request.Builder()
            .url(config.overpassEndpoint)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(FormBody.Builder().add("data", query).build())
            .build()
        val call = client.newCall(request)
        call.timeout().timeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val places = call.awaitTransitResponse().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Overpass transit HTTP ${response.code}")
            parse(payload, origin, radius, outputLimit)
        }
        synchronized(cache) {
            if (cache.size >= CACHE_LIMIT) cache.keys.firstOrNull()?.let(cache::remove)
            cache[cacheKey] = CachedPlaces(SystemClock.elapsedRealtime(), places)
        }
        Result.success(places)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    internal fun buildNearbyQuery(origin: GeoPoint, radiusMeters: Int): String {
        val radius = radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        val around = "around:$radius,${origin.latitude},${origin.longitude}"
        return buildString {
            append("[out:json][timeout:$OVERPASS_QUERY_TIMEOUT_SECONDS];(")
            append("nwr($around)[\"highway\"=\"bus_stop\"];")
            append("nwr($around)[\"public_transport\"~\"^(platform|station|stop_position)$\"];")
            append("nwr($around)[\"railway\"~\"^(station|halt|subway_entrance|tram_stop)$\"];")
            append(");out body center $MAX_RAW_RESULTS;")
        }
    }

    internal fun parse(
        payload: String,
        origin: GeoPoint,
        radiusMeters: Int,
        limit: Int,
    ): List<OsmPlace> {
        val elements = runCatching { JSONObject(payload).optJSONArray("elements") }.getOrNull() ?: return emptyList()
        return buildList {
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
                val distance = OsmServiceClient.haversineMeters(origin, point).toInt()
                if (distance > radiusMeters) continue
                val category = transitCategory(tags)
                val name = firstTag(tags, "name", "local_ref", "ref", "operator")
                    ?: defaultName(category)
                add(
                    OsmPlace(
                        name = name.take(MAX_NAME_CHARS),
                        category = transitDescriptor(tags, category),
                        point = point,
                        distanceMeters = distance,
                    ),
                )
            }
        }.sortedBy(OsmPlace::distanceMeters)
            .distinctBy { place -> place.name.lowercase(Locale.US) to roundedPoint(place.point) }
            .take(limit.coerceIn(1, MAX_RESULTS))
    }

    private fun transitCategory(tags: JSONObject): String = when {
        tags.optString("railway") == "subway_entrance" -> "subway entrance"
        tags.optString("railway") == "tram_stop" -> "tram stop"
        tags.optString("railway") == "halt" -> "rail halt"
        tags.optString("railway") == "station" -> "rail station"
        tags.optString("highway") == "bus_stop" -> "bus stop"
        tags.optString("public_transport") == "station" -> "transit station"
        tags.optString("public_transport") == "stop_position" -> "transit stop"
        tags.optString("public_transport") == "platform" -> "transit platform"
        else -> "transit stop"
    }

    private fun transitDescriptor(tags: JSONObject, category: String): String {
        val parts = mutableListOf(category)
        firstTag(tags, "network")?.let { parts += "network: ${it.take(100)}" }
        firstTag(tags, "operator")?.let { parts += "operator: ${it.take(100)}" }
        firstTag(tags, "route_ref", "ref")?.let { parts += "routes/ref: ${it.take(100)}" }
        firstTag(tags, "bus")?.takeIf { it == "yes" }?.let { parts += "bus" }
        firstTag(tags, "subway")?.takeIf { it == "yes" }?.let { parts += "subway" }
        firstTag(tags, "tram")?.takeIf { it == "yes" }?.let { parts += "tram" }
        firstTag(tags, "train")?.takeIf { it == "yes" }?.let { parts += "train" }
        firstTag(tags, "wheelchair")?.let { parts += "wheelchair: ${it.take(30)}" }
        return parts.distinct().joinToString("; ").take(MAX_DESCRIPTOR_CHARS)
    }

    private fun defaultName(category: String): String = category.replaceFirstChar { it.uppercase() }

    private fun firstTag(tags: JSONObject, vararg keys: String): String? = keys.asSequence()
        .map { key -> tags.optString(key).replace(Regex("\\s+"), " ").trim() }
        .firstOrNull(String::isNotBlank)

    private fun roundedPoint(point: GeoPoint): Pair<Int, Int> =
        (point.latitude * 100_000).toInt() to (point.longitude * 100_000).toInt()

    private fun cacheKey(endpoint: String, point: GeoPoint, radius: Int, limit: Int): String =
        "$endpoint|${(point.latitude * 10_000).toInt()}|${(point.longitude * 10_000).toInt()}|$radius|$limit"

    private data class CachedPlaces(val createdAtMs: Long, val places: List<OsmPlace>)

    private companion object {
        const val USER_AGENT = "AD-Glasses/alpha OSM transit client"
        const val DEFAULT_RADIUS_METERS = 1_500
        const val DEFAULT_LIMIT = 8
        const val MIN_RADIUS_METERS = 100
        const val MAX_RADIUS_METERS = 5_000
        const val MAX_RESULTS = 16
        const val MAX_RAW_RESULTS = 40
        const val MAX_NAME_CHARS = 180
        const val MAX_DESCRIPTOR_CHARS = 420
        const val OVERPASS_QUERY_TIMEOUT_SECONDS = 4
        const val CALL_TIMEOUT_SECONDS = 5L
        const val CACHE_TTL_MS = 45_000L
        const val CACHE_LIMIT = 48
        val cache = LinkedHashMap<String, CachedPlaces>()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}

private suspend fun Call.awaitTransitResponse(): Response = suspendCancellableCoroutine { continuation ->
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

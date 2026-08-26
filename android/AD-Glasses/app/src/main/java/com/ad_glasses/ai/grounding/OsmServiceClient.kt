package com.ad_glasses.ai.grounding

import android.os.SystemClock
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

data class GeoPoint(val latitude: Double, val longitude: Double)

data class OsmAddress(
    val displayName: String,
    val road: String?,
    val neighbourhood: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    val point: GeoPoint,
)

data class OsmPlace(
    val name: String,
    val category: String,
    val point: GeoPoint,
    val distanceMeters: Int,
)

enum class RouteMode(val profile: String) {
    DRIVING("driving"),
    WALKING("foot"),
    CYCLING("bike"),
}

data class RouteStep(
    val instruction: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
)

data class OsmRoute(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val steps: List<RouteStep>,
)

data class OverpassTagFilter(val key: String, val value: String?)

/**
 * User-triggered OSM-family client.
 *
 * Public Nominatim calls are globally serialized, cached, and kept at <= 1 request/sec. Overpass
 * calls are also serialized. The default FOSSGIS OSRM service is rate-limited to <= 1 request/sec.
 * All requests are coroutine-cancellable so an assistant grounding deadline cancels the actual
 * socket instead of merely abandoning a blocking IO worker.
 */
class OsmServiceClient(
    private val configProvider: () -> GroundingServiceConfig,
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun reverse(point: GeoPoint): Result<OsmAddress> = try {
        val cacheKey = "${roundCoordinate(point.latitude)},${roundCoordinate(point.longitude)}"
        val cached = synchronized(reverseCache) { reverseCache[cacheKey] }
        if (cached != null) {
            Result.success(cached)
        } else {
            val config = configProvider()
            val url = (config.nominatimBaseUrl + "/reverse").toHttpUrl().newBuilder()
                .addQueryParameter("format", "jsonv2")
                .addQueryParameter("lat", point.latitude.toString())
                .addQueryParameter("lon", point.longitude.toString())
                .addQueryParameter("zoom", "18")
                .addQueryParameter("addressdetails", "1")
                .build()
            val payload = nominatimGet(url.toString())
            val root = JSONObject(payload)
            val address = root.optJSONObject("address") ?: JSONObject()
            val parsed = OsmAddress(
                displayName = root.optString("display_name").replace(Regex("\\s+"), " ").trim().take(500),
                road = firstNonBlank(address, "road", "pedestrian", "footway"),
                neighbourhood = firstNonBlank(address, "neighbourhood", "suburb", "quarter"),
                city = firstNonBlank(address, "city", "town", "village", "municipality"),
                state = firstNonBlank(address, "state", "state_district", "region"),
                country = address.optString("country").trim().takeIf { it.isNotBlank() },
                point = point,
            )
            synchronized(reverseCache) {
                if (reverseCache.size >= CACHE_LIMIT) reverseCache.keys.firstOrNull()?.let(reverseCache::remove)
                reverseCache[cacheKey] = parsed
            }
            Result.success(parsed)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    /** One-shot forward geocoding for an explicit user destination; never used for autocomplete. */
    suspend fun geocode(query: String, near: GeoPoint? = null): Result<OsmPlace?> = try {
        val clean = query.replace(Regex("\\s+"), " ").trim().take(300)
        require(clean.isNotBlank()) { "Destination cannot be blank." }
        val config = configProvider()
        val builder = (config.nominatimBaseUrl + "/search").toHttpUrl().newBuilder()
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("q", clean)
            .addQueryParameter("limit", "5")
            .addQueryParameter("addressdetails", "1")
        if (near != null) {
            val delta = 0.18
            builder.addQueryParameter(
                "viewbox",
                "${near.longitude - delta},${near.latitude + delta},${near.longitude + delta},${near.latitude - delta}",
            )
        }
        val payload = nominatimGet(builder.build().toString())
        val items = org.json.JSONArray(payload)
        val candidates = buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val lat = item.optString("lat").toDoubleOrNull() ?: continue
                val lon = item.optString("lon").toDoubleOrNull() ?: continue
                val itemPoint = GeoPoint(lat, lon)
                add(
                    OsmPlace(
                        name = item.optString("display_name").replace(Regex("\\s+"), " ").trim().take(400),
                        category = item.optString("type").ifBlank { item.optString("category") }.take(80),
                        point = itemPoint,
                        distanceMeters = near?.let { haversineMeters(it, itemPoint).toInt() } ?: 0,
                    ),
                )
            }
        }
        Result.success(if (near == null) candidates.firstOrNull() else candidates.minByOrNull { it.distanceMeters })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    suspend fun nearby(
        origin: GeoPoint,
        filters: List<OverpassTagFilter>,
        radiusMeters: Int,
        limit: Int = 8,
    ): Result<List<OsmPlace>> = try {
        require(filters.isNotEmpty()) { "At least one POI filter is required." }
        filters.forEach { filter ->
            require(SAFE_TAG.matches(filter.key)) { "Unsupported OSM tag key." }
            filter.value?.let { require(SAFE_TAG_VALUE.matches(it)) { "Unsupported OSM tag value." } }
        }
        val radius = radiusMeters.coerceIn(50, 5_000)
        val outputLimit = limit.coerceIn(1, 20)
        val query = buildString {
            append("[out:json][timeout:6];(")
            filters.forEach { filter ->
                append("nwr(around:$radius,${origin.latitude},${origin.longitude})")
                if (filter.value == null) append("[\"${filter.key}\"]")
                else append("[\"${filter.key}\"=\"${filter.value}\"]")
                append(';')
            }
            // body keeps node lat/lon; center adds a usable point for ways and relations.
            append(");out body center $outputLimit;")
        }
        overpassMutex.withLock {
            val config = configProvider()
            val request = Request.Builder()
                .url(config.overpassEndpoint)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .post(FormBody.Builder().add("data", query).build())
                .build()
            val call = client.newCall(request)
            call.timeout().timeout(OVERPASS_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val places = call.awaitResponse().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("Overpass HTTP ${response.code}")
                parseOverpass(payload, origin, outputLimit)
            }
            Result.success(places)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    suspend fun route(
        origin: GeoPoint,
        destination: GeoPoint,
        mode: RouteMode = RouteMode.DRIVING,
    ): Result<OsmRoute> = try {
        val config = configProvider()
        val endpoint = routingEndpoint(config.osrmBaseUrl, mode)
        val url = routeRequestUrl(origin, destination, mode, config.osrmBaseUrl)
        val requestBlock: suspend () -> OsmRoute = {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()
            val call = client.newCall(request)
            call.timeout().timeout(OSRM_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            call.awaitResponse().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("OSRM HTTP ${response.code}")
                parseRoute(payload)
            }
        }
        val parsed = if (endpoint.publicFossgis) {
            osrmMutex.withLock {
                val now = SystemClock.elapsedRealtime()
                val remaining = PUBLIC_OSRM_MIN_INTERVAL_MS - (now - lastOsrmRequestAtMs)
                if (remaining > 0) delay(remaining)
                lastOsrmRequestAtMs = SystemClock.elapsedRealtime()
                requestBlock()
            }
        } else {
            requestBlock()
        }
        Result.success(parsed)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    internal fun routeRequestUrl(
        origin: GeoPoint,
        destination: GeoPoint,
        mode: RouteMode,
        configuredBaseUrl: String = configProvider().osrmBaseUrl,
    ): HttpUrl {
        val endpoint = routingEndpoint(configuredBaseUrl, mode)
        val coordinates = "${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}"
        return (endpoint.baseUrl + "/route/v1/${endpoint.profile}/$coordinates").toHttpUrl().newBuilder()
            .addQueryParameter("overview", "false")
            .addQueryParameter("steps", "true")
            .addQueryParameter("alternatives", "false")
            .addQueryParameter("generate_hints", "false")
            .build()
    }

    private suspend fun nominatimGet(url: String): String = nominatimMutex.withLock {
        val now = SystemClock.elapsedRealtime()
        val remaining = NOMINATIM_MIN_INTERVAL_MS - (now - lastNominatimRequestAtMs)
        if (remaining > 0) delay(remaining)
        lastNominatimRequestAtMs = SystemClock.elapsedRealtime()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .get()
            .build()
        val call = client.newCall(request)
        call.timeout().timeout(NOMINATIM_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        call.awaitResponse().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Nominatim HTTP ${response.code}")
            payload
        }
    }

    internal fun parseOverpass(payload: String, origin: GeoPoint, limit: Int): List<OsmPlace> {
        val elements = JSONObject(payload).optJSONArray("elements") ?: return emptyList()
        return buildList {
            for (index in 0 until elements.length()) {
                val item = elements.optJSONObject(index) ?: continue
                val tags = item.optJSONObject("tags") ?: JSONObject()
                val center = item.optJSONObject("center")
                val lat = if (item.has("lat")) item.optDouble("lat") else center?.optDouble("lat")
                val lon = if (item.has("lon")) item.optDouble("lon") else center?.optDouble("lon")
                if (lat == null || lon == null || lat.isNaN() || lon.isNaN()) continue
                val point = GeoPoint(lat, lon)
                val name = tags.optString("name").trim().ifBlank {
                    tags.optString("brand").trim().ifBlank { humanCategory(tags) }
                }
                add(
                    OsmPlace(
                        name = name.take(180),
                        category = humanCategory(tags).take(100),
                        point = point,
                        distanceMeters = haversineMeters(origin, point).toInt(),
                    ),
                )
            }
        }.sortedBy { it.distanceMeters }
            .distinctBy { it.name.lowercase(Locale.US) to it.point }
            .take(limit.coerceIn(1, 20))
    }

    internal fun parseRoute(payload: String): OsmRoute {
        val root = JSONObject(payload)
        val code = root.optString("code")
        if (code != "Ok") throw IOException("OSRM route failed: ${root.optString("message").ifBlank { code }}")
        val route = root.optJSONArray("routes")?.optJSONObject(0) ?: throw IOException("No route found.")
        val steps = buildList {
            val legs = route.optJSONArray("legs") ?: return@buildList
            for (legIndex in 0 until legs.length()) {
                val legSteps = legs.optJSONObject(legIndex)?.optJSONArray("steps") ?: continue
                for (stepIndex in 0 until legSteps.length()) {
                    val step = legSteps.optJSONObject(stepIndex) ?: continue
                    val maneuver = step.optJSONObject("maneuver") ?: JSONObject()
                    val instruction = instruction(
                        type = maneuver.optString("type"),
                        modifier = maneuver.optString("modifier"),
                        roadName = step.optString("name"),
                    )
                    if (instruction.isNotBlank()) {
                        add(
                            RouteStep(
                                instruction = instruction,
                                distanceMeters = step.optDouble("distance", 0.0).toInt(),
                                durationSeconds = step.optDouble("duration", 0.0).toInt(),
                            ),
                        )
                    }
                }
            }
        }
        return OsmRoute(
            distanceMeters = route.optDouble("distance", 0.0).toInt(),
            durationSeconds = route.optDouble("duration", 0.0).toInt(),
            steps = steps.take(16),
        )
    }

    private fun humanCategory(tags: JSONObject): String = sequenceOf(
        "amenity", "shop", "tourism", "historic", "leisure", "highway", "railway", "building",
    ).mapNotNull { key -> tags.optString(key).trim().takeIf { it.isNotBlank() } }
        .firstOrNull()
        ?.replace('_', ' ')
        ?: "place"

    private fun instruction(type: String, modifier: String, roadName: String): String {
        val action = when (type) {
            "depart" -> "Start"
            "arrive" -> "Arrive at the destination"
            "turn" -> if (modifier.isBlank()) "Turn" else "Turn ${modifier.replace('_', ' ')}"
            "continue" -> "Continue${modifier.takeIf { it.isNotBlank() }?.let { " ${it.replace('_', ' ')}" }.orEmpty()}"
            "new name" -> "Continue"
            "merge" -> "Merge${modifier.takeIf { it.isNotBlank() }?.let { " ${it.replace('_', ' ')}" }.orEmpty()}"
            "on ramp" -> "Take the ramp${modifier.takeIf { it.isNotBlank() }?.let { " ${it.replace('_', ' ')}" }.orEmpty()}"
            "off ramp" -> "Take the exit${modifier.takeIf { it.isNotBlank() }?.let { " ${it.replace('_', ' ')}" }.orEmpty()}"
            "fork" -> "Keep${modifier.takeIf { it.isNotBlank() }?.let { " ${it.replace('_', ' ')}" }.orEmpty()}"
            "roundabout", "rotary" -> "Enter the roundabout"
            else -> type.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
        if (type == "arrive" || roadName.isBlank()) return action
        return "$action onto ${roadName.trim()}"
    }

    private fun firstNonBlank(json: JSONObject, vararg keys: String): String? = keys.asSequence()
        .map { json.optString(it).trim() }
        .firstOrNull { it.isNotBlank() }

    private data class RoutingEndpoint(
        val baseUrl: String,
        val profile: String,
        val publicFossgis: Boolean,
    )

    private fun routingEndpoint(configuredBaseUrl: String, mode: RouteMode): RoutingEndpoint {
        val base = configuredBaseUrl.toHttpUrl()
        val isFossgisRoot = base.host == FOSSGIS_ROUTING_HOST && base.encodedPath.trim('/') == ""
        if (!isFossgisRoot) {
            return RoutingEndpoint(
                baseUrl = configuredBaseUrl.trimEnd('/'),
                profile = mode.profile,
                publicFossgis = false,
            )
        }
        val prefix = when (mode) {
            RouteMode.DRIVING -> "routed-car"
            RouteMode.WALKING -> "routed-foot"
            RouteMode.CYCLING -> "routed-bike"
        }
        // FOSSGIS runs one statically prepared OSRM graph per prefix; its path profile remains
        // `driving` even for the foot/bike instances.
        return RoutingEndpoint(
            baseUrl = configuredBaseUrl.trimEnd('/') + "/$prefix",
            profile = "driving",
            publicFossgis = true,
        )
    }

    companion object {
        const val OSM_ATTRIBUTION =
            "Map data © OpenStreetMap contributors · Routing by OSRM · Fix the map: https://www.openstreetmap.org/fixthemap"
        private const val USER_AGENT = "AD-Glasses/alpha (https://github.com/Achyut-Dalai/AD-Glasses)"
        private const val FOSSGIS_ROUTING_HOST = "routing.openstreetmap.de"
        private const val NOMINATIM_MIN_INTERVAL_MS = 1_050L
        private const val PUBLIC_OSRM_MIN_INTERVAL_MS = 1_050L
        private const val NOMINATIM_CALL_TIMEOUT_SECONDS = 4L
        private const val OVERPASS_CALL_TIMEOUT_SECONDS = 6L
        private const val OSRM_CALL_TIMEOUT_SECONDS = 5L
        private const val CACHE_LIMIT = 96
        private val SAFE_TAG = Regex("[a-zA-Z0-9_:.-]{1,64}")
        private val SAFE_TAG_VALUE = Regex("[a-zA-Z0-9_ :.'()-]{1,96}")
        private val nominatimMutex = Mutex()
        private val overpassMutex = Mutex()
        private val osrmMutex = Mutex()
        @Volatile private var lastNominatimRequestAtMs: Long = 0L
        @Volatile private var lastOsrmRequestAtMs: Long = 0L
        private val reverseCache = LinkedHashMap<String, OsmAddress>()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
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

        private fun roundCoordinate(value: Double): String = String.format(Locale.US, "%.5f", value)

        fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
            val earthRadius = 6_371_000.0
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
            return 2 * earthRadius * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
    }
}

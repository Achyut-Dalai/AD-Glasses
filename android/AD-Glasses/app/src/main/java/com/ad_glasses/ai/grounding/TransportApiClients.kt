package com.ad_glasses.ai.grounding

import android.content.Context
import com.google.transit.realtime.GtfsRealtime
import java.io.IOException
import java.net.URI
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

/** Structured snapshot of a realtime public-transit vehicle close to a known point. */
data class TransitVehicleSnapshot(
    val feedId: String,
    val feedLabel: String,
    val routeId: String?,
    val tripId: String?,
    val vehicleId: String?,
    val point: GeoPoint,
    val distanceMeters: Int,
    val stopId: String?,
    val timestampEpochSeconds: Long?,
)

/**
 * Indian Railways status client backed by the configurable RapidAPI IRCTC provider.
 *
 * The default host follows the current IRCTCAPI listing, but the host is configurable because
 * RapidAPI products and hosts can change independently of the app. Secrets never enter logs or
 * returned grounding context.
 */
class RailStatusClient(
    context: Context,
    private val client: OkHttpClient = defaultTransportClient(),
) {
    private val appContext = context.applicationContext

    fun isConfigured(): Boolean = GroundingPrefs.hasRailRapidApiKey(appContext)

    suspend fun liveStatus(trainNumber: String, startDay: Int = 0): Result<StructuredKnowledgeResult> = try {
        val cleanTrain = trainNumber.filter(Char::isDigit).take(MAX_TRAIN_NUMBER_CHARS)
        require(cleanTrain.length >= 4) { "Train number is invalid." }
        val request = rapidApiRequest(liveStatusUrl(cleanTrain, startDay))
        val payload = client.newCall(request).awaitTransportBody("Rail live status")
        Result.success(TransportApiParsers.parseRailLiveStatus(payload, cleanTrain))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    suspend fun pnrStatus(pnrNumber: String): Result<StructuredKnowledgeResult> = try {
        val cleanPnr = pnrNumber.filter(Char::isDigit).take(MAX_PNR_CHARS)
        require(cleanPnr.length == MAX_PNR_CHARS) { "PNR must contain 10 digits." }
        val request = rapidApiRequest(pnrStatusUrl(cleanPnr))
        val payload = client.newCall(request).awaitTransportBody("Rail PNR status")
        Result.success(TransportApiParsers.parsePnrStatus(payload, cleanPnr))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    internal fun liveStatusUrl(trainNumber: String, startDay: Int = 0): HttpUrl = HttpUrl.Builder()
        .scheme("https")
        .host(GroundingPrefs.getRailRapidApiHost(appContext))
        .addPathSegments("api/v1/liveTrainStatus")
        .addQueryParameter("trainNo", trainNumber)
        .addQueryParameter("startDay", startDay.coerceIn(0, 4).toString())
        .build()

    internal fun pnrStatusUrl(pnrNumber: String): HttpUrl = HttpUrl.Builder()
        .scheme("https")
        .host(GroundingPrefs.getRailRapidApiHost(appContext))
        .addPathSegments("api/v3/getPNRStatus")
        .addQueryParameter("pnrNumber", pnrNumber)
        .build()

    private fun rapidApiRequest(url: HttpUrl): Request {
        val key = GroundingPrefs.getRailRapidApiKey(appContext)
        check(key.isNotBlank()) { "Rail RapidAPI key is not configured." }
        val host = GroundingPrefs.getRailRapidApiHost(appContext)
        return Request.Builder()
            .url(url)
            .header("X-RapidAPI-Key", key)
            .header("X-RapidAPI-Host", host)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
    }

    private companion object {
        const val USER_AGENT = "AD-Glasses/alpha rail client"
        const val MAX_TRAIN_NUMBER_CHARS = 6
        const val MAX_PNR_CHARS = 10
    }
}

/** Real-time flight status via AviationStack's bounded /flights endpoint. */
class FlightStatusClient(
    context: Context,
    private val client: OkHttpClient = defaultTransportClient(),
) {
    private val appContext = context.applicationContext

    fun isConfigured(): Boolean = GroundingPrefs.hasAviationStackKey(appContext)

    suspend fun status(flightIata: String): Result<StructuredKnowledgeResult> = try {
        val cleanFlight = flightIata
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]"), "")
            .take(MAX_FLIGHT_IATA_CHARS)
        require(FLIGHT_IATA.matches(cleanFlight)) { "Flight number is invalid." }
        val url = statusUrl(cleanFlight)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val payload = client.newCall(request).awaitTransportBody("AviationStack")
        Result.success(TransportApiParsers.parseFlightStatus(payload, cleanFlight))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    internal fun statusUrl(flightIata: String): HttpUrl {
        val key = GroundingPrefs.getAviationStackKey(appContext)
        check(key.isNotBlank()) { "AviationStack access key is not configured." }
        return (GroundingPrefs.getAviationStackBaseUrl(appContext).trimEnd('/') + "/flights")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("access_key", key)
            .addQueryParameter("flight_iata", flightIata)
            .addQueryParameter("limit", "3")
            .build()
    }

    private companion object {
        const val USER_AGENT = "AD-Glasses/alpha flight client"
        const val MAX_FLIGHT_IATA_CHARS = 8
        val FLIGHT_IATA = Regex("[A-Z0-9]{2,3}[0-9]{1,4}[A-Z]?")
    }
}

/**
 * Agency-configured GTFS-Realtime client.
 *
 * OSM remains the discovery layer for nearby stops/stations. This client handles the realtime half:
 * trip updates, service alerts, and vehicle positions from feeds explicitly configured by the user.
 * A missing trip update is reported as unavailable data, never as an on-time claim.
 */
class TransitRealtimeClient(
    private val client: OkHttpClient = defaultTransportClient(),
) {
    suspend fun status(
        feed: GtfsRealtimeFeedConfig,
        stopId: String? = null,
        routeId: String? = null,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1_000L,
    ): Result<StructuredKnowledgeResult> = try {
        val message = fetch(feed)
        Result.success(parseStatus(feed, message, stopId, routeId, nowEpochSeconds))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    suspend fun nearbyVehicles(
        feed: GtfsRealtimeFeedConfig,
        origin: GeoPoint,
        radiusMeters: Int = 3_000,
        routeId: String? = null,
        limit: Int = 8,
    ): Result<List<TransitVehicleSnapshot>> = try {
        val message = fetch(feed)
        val radius = radiusMeters.coerceIn(100, 25_000)
        val wantedRoute = routeId?.trim()?.takeIf(String::isNotBlank)
        val snapshots = message.entityList.asSequence()
            .filter(GtfsRealtime.FeedEntity::hasVehicle)
            .map(GtfsRealtime.FeedEntity::getVehicle)
            .filter(GtfsRealtime.VehiclePosition::hasPosition)
            .filter { vehicle -> wantedRoute == null || vehicle.trip.routeId == wantedRoute }
            .mapNotNull { vehicle ->
                val lat = vehicle.position.latitude.toDouble()
                val lon = vehicle.position.longitude.toDouble()
                if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                    return@mapNotNull null
                }
                val point = GeoPoint(lat, lon)
                val distance = OsmServiceClient.haversineMeters(origin, point).toInt()
                if (distance > radius) return@mapNotNull null
                TransitVehicleSnapshot(
                    feedId = feed.id,
                    feedLabel = feed.label,
                    routeId = vehicle.trip.routeId.takeIf(String::isNotBlank),
                    tripId = vehicle.trip.tripId.takeIf(String::isNotBlank),
                    vehicleId = vehicle.vehicle.id.takeIf(String::isNotBlank),
                    point = point,
                    distanceMeters = distance,
                    stopId = vehicle.stopId.takeIf(String::isNotBlank),
                    timestampEpochSeconds = vehicle.timestamp.takeIf { it > 0L },
                )
            }
            .sortedBy(TransitVehicleSnapshot::distanceMeters)
            .take(limit.coerceIn(1, 20))
            .toList()
        Result.success(snapshots)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    internal fun parseStatus(
        feed: GtfsRealtimeFeedConfig,
        message: GtfsRealtime.FeedMessage,
        stopId: String?,
        routeId: String?,
        nowEpochSeconds: Long,
    ): StructuredKnowledgeResult {
        val wantedStop = stopId?.trim()?.takeIf(String::isNotBlank)
        val wantedRoute = routeId?.trim()?.takeIf(String::isNotBlank)
        val arrivals = mutableListOf<RealtimeArrival>()
        val alerts = mutableListOf<String>()

        message.entityList.forEach entityLoop@ { entity ->
            if (entity.hasTripUpdate()) {
                val update = entity.tripUpdate
                if (wantedRoute != null && update.trip.routeId != wantedRoute) return@entityLoop
                update.stopTimeUpdateList.forEach stopLoop@ { stopUpdate ->
                    if (wantedStop != null && stopUpdate.stopId != wantedStop) return@stopLoop
                    val event = when {
                        stopUpdate.hasArrival() && stopUpdate.arrival.hasTime() -> stopUpdate.arrival
                        stopUpdate.hasDeparture() && stopUpdate.departure.hasTime() -> stopUpdate.departure
                        else -> null
                    } ?: return@stopLoop
                    if (event.time < nowEpochSeconds - PAST_EVENT_GRACE_SECONDS) return@stopLoop
                    val delay = when {
                        event.hasDelay() -> event.delay
                        stopUpdate.hasArrival() && stopUpdate.arrival.hasDelay() -> stopUpdate.arrival.delay
                        stopUpdate.hasDeparture() && stopUpdate.departure.hasDelay() -> stopUpdate.departure.delay
                        else -> null
                    }
                    arrivals += RealtimeArrival(
                        routeId = update.trip.routeId.takeIf(String::isNotBlank),
                        tripId = update.trip.tripId.takeIf(String::isNotBlank),
                        stopId = stopUpdate.stopId.takeIf(String::isNotBlank),
                        eventEpochSeconds = event.time,
                        delaySeconds = delay,
                    )
                }
            }
            if (entity.hasAlert()) {
                val alert = entity.alert
                val affectsRequested = alert.informedEntityList.isEmpty() || alert.informedEntityList.any { selector ->
                    (wantedRoute == null || selector.routeId.isBlank() || selector.routeId == wantedRoute) &&
                        (wantedStop == null || selector.stopId.isBlank() || selector.stopId == wantedStop)
                }
                if (affectsRequested) {
                    translatedText(alert.headerText)?.let(alerts::add)
                    translatedText(alert.descriptionText)?.let(alerts::add)
                }
            }
        }

        val upcoming = arrivals
            .distinctBy { Triple(it.routeId, it.tripId, it.eventEpochSeconds) }
            .sortedBy(RealtimeArrival::eventEpochSeconds)
            .take(MAX_REALTIME_EVENTS)
        val conciseAlerts = alerts.map { cleanTransportText(it, 300) }.filter(String::isNotBlank).distinct().take(3)
        val target = buildString {
            wantedRoute?.let { append(" route $it") }
            wantedStop?.let { append(" stop $it") }
        }.trim().ifBlank { "the configured feed" }
        val answer = when {
            upcoming.isNotEmpty() -> buildString {
                append("Realtime transit updates for $target: ")
                append(upcoming.joinToString("; ") { item ->
                    val minutes = ((item.eventEpochSeconds - nowEpochSeconds) / 60.0).toInt().coerceAtLeast(0)
                    val route = item.routeId?.let { "route $it " }.orEmpty()
                    val delay = item.delaySeconds?.let { seconds ->
                        val mins = kotlin.math.abs(seconds) / 60
                        when {
                            seconds > 30 -> ", about $mins min late"
                            seconds < -30 -> ", about $mins min early"
                            else -> ", near schedule"
                        }
                    }.orEmpty()
                    "$route in about $minutes min$delay"
                })
                if (conciseAlerts.isNotEmpty()) append(". Alert: ${conciseAlerts.first()}")
                append('.')
            }
            conciseAlerts.isNotEmpty() -> "No matching realtime arrival prediction is available for $target. Service alert: ${conciseAlerts.first()}"
            else -> "No matching GTFS realtime arrival prediction is available for $target. This does not mean service is on time or not running."
        }.take(MAX_STRUCTURED_ANSWER_CHARS)
        val context = buildString {
            appendLine("GTFS-Realtime feed: ${feed.label}")
            appendLine("Filter route=${wantedRoute ?: "any"}; stop=${wantedStop ?: "any"}.")
            if (upcoming.isEmpty()) {
                appendLine("No matching TripUpdate prediction was present. Do not infer on-time status from absence.")
            } else {
                appendLine("Matching TripUpdates:")
                upcoming.forEachIndexed { index, item ->
                    appendLine(
                        "[${index + 1}] route=${item.routeId ?: "unknown"}; trip=${item.tripId ?: "unknown"}; " +
                            "stop=${item.stopId ?: "unknown"}; epoch=${item.eventEpochSeconds}; delaySeconds=${item.delaySeconds ?: "unknown"}",
                    )
                }
            }
            if (conciseAlerts.isNotEmpty()) {
                appendLine("Service alerts:")
                conciseAlerts.forEach { appendLine("- $it") }
            }
        }.take(MAX_REALTIME_CONTEXT_CHARS)
        return StructuredKnowledgeResult(
            answer = answer,
            context = context,
            sources = listOf(GroundingSource("${feed.label} GTFS-Realtime", publicFeedUrl(feed.url))),
        )
    }

    private suspend fun fetch(feed: GtfsRealtimeFeedConfig): GtfsRealtime.FeedMessage {
        val url = GroundingPrefs.validatedHttpsUrl(feed.url)
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/x-protobuf, application/octet-stream;q=0.9, */*;q=0.2")
            .header("User-Agent", USER_AGENT)
        if (!feed.headerName.isNullOrBlank() && !feed.headerValue.isNullOrBlank()) {
            requestBuilder.header(feed.headerName, feed.headerValue)
        }
        val request = requestBuilder.get().build()
        val call = client.newCall(request)
        call.timeout().timeout(GTFS_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val bytes = call.awaitTransportBytes("GTFS realtime")
        if (bytes.size > MAX_GTFS_BYTES) throw IOException("GTFS realtime feed exceeds the bounded response size.")
        return GtfsRealtime.FeedMessage.parseFrom(bytes)
    }

    private fun translatedText(value: GtfsRealtime.TranslatedString): String? = value.translationList
        .asSequence()
        .map { it.text.trim() }
        .firstOrNull(String::isNotBlank)

    private data class RealtimeArrival(
        val routeId: String?,
        val tripId: String?,
        val stopId: String?,
        val eventEpochSeconds: Long,
        val delaySeconds: Int?,
    )

    private companion object {
        const val USER_AGENT = "AD-Glasses/alpha GTFS realtime client"
        const val GTFS_CALL_TIMEOUT_SECONDS = 5L
        const val MAX_GTFS_BYTES = 3 * 1024 * 1024
        const val MAX_REALTIME_EVENTS = 8
        const val MAX_REALTIME_CONTEXT_CHARS = 4_000
        const val MAX_STRUCTURED_ANSWER_CHARS = 1_800
        const val PAST_EVENT_GRACE_SECONDS = 60L
    }
}

/** Pure parsers are kept separate so provider schema fixtures can be tested without network calls. */
internal object TransportApiParsers {
    fun parseRailLiveStatus(payload: String, requestedTrain: String): StructuredKnowledgeResult {
        val root = JSONObject(payload)
        ensureProviderSuccess(root, "Rail live status")
        val data = root.optJSONObject("data") ?: root
        val trainNumber = firstString(data, "train_number", "train_no", "trainNo", "trainNumber") ?: requestedTrain
        val trainName = firstString(data, "train_name", "trainName", "name")
        val current = firstString(
            data,
            "current_station_name",
            "current_station",
            "cur_stn",
            "currentStation",
            "station_name",
        ) ?: nestedName(data, "current_station")
        val next = firstString(data, "next_station_name", "next_station", "nextStation") ?: nestedName(data, "next_station")
        val delay = firstString(data, "delay", "delay_minutes", "delay_in_minutes", "lateMins", "late_minutes")
        val updated = firstString(data, "status_as_of", "last_updated", "updated_at", "updatedAt")
        val runningStatus = firstString(data, "running_status", "status", "train_status")
        val answer = buildString {
            append("Train $trainNumber")
            trainName?.let { append(" ($it)") }
            current?.let { append(" is at/near $it") }
            next?.let { append("; next $it") }
            delay?.let { append("; reported delay $it") }
            runningStatus?.takeIf { !it.equals("success", true) }?.let { append("; $it") }
            updated?.let { append("; updated $it") }
            append('.')
        }.take(MAX_ANSWER_CHARS)
        return StructuredKnowledgeResult(
            answer = answer,
            context = "Indian Railways live-status provider record for train $requestedTrain: ${boundedJson(data)}",
            sources = listOf(GroundingSource("IRCTC API on RapidAPI", RAIL_SOURCE_URL)),
        )
    }

    fun parsePnrStatus(payload: String, requestedPnr: String): StructuredKnowledgeResult {
        val root = JSONObject(payload)
        ensureProviderSuccess(root, "PNR status")
        val data = root.optJSONObject("data") ?: root
        val pnr = firstString(data, "pnrNumber", "pnr_number", "pnr") ?: requestedPnr
        val trainNumber = firstString(data, "trainNumber", "train_number", "train_no")
        val trainName = firstString(data, "trainName", "train_name")
        val journeyDate = firstString(data, "dateOfJourney", "journey_date", "journeyDate")
        val from = firstString(data, "boardingPoint", "boarding_station", "from", "sourceStation")
        val to = firstString(data, "reservationUpto", "destination_station", "to", "destinationStation")
        val chart = firstString(data, "chartStatus", "chart_status", "chartPrepared")
        val passengers = firstArray(data, "passengerList", "passengers", "passenger_status")
        val passengerSummary = passengers?.let(::pnrPassengerSummary).orEmpty()
        val answer = buildString {
            append("PNR $pnr")
            trainNumber?.let { append("; train $it") }
            trainName?.let { append(" $it") }
            if (from != null || to != null) append("; ${from ?: "origin unknown"} to ${to ?: "destination unknown"}")
            journeyDate?.let { append(" on $it") }
            if (passengerSummary.isNotBlank()) append("; $passengerSummary")
            chart?.let { append("; chart $it") }
            append('.')
        }.take(MAX_ANSWER_CHARS)
        return StructuredKnowledgeResult(
            answer = answer,
            context = "Indian Railways PNR provider record for $requestedPnr: ${boundedJson(data)}",
            sources = listOf(GroundingSource("IRCTC API on RapidAPI", RAIL_SOURCE_URL)),
        )
    }

    fun parseFlightStatus(payload: String, requestedFlight: String): StructuredKnowledgeResult {
        val root = JSONObject(payload)
        root.optJSONObject("error")?.let { error ->
            val message = firstString(error, "message", "info", "code") ?: "AviationStack request failed."
            throw IllegalStateException(message)
        }
        val data = root.optJSONArray("data") ?: JSONArray()
        if (data.length() == 0) throw IllegalStateException("AviationStack returned no matching flight record.")
        val flight = data.optJSONObject(0) ?: throw IllegalStateException("AviationStack returned an invalid flight record.")
        val identity = flight.optJSONObject("flight")
        val airline = flight.optJSONObject("airline")
        val departure = flight.optJSONObject("departure") ?: JSONObject()
        val arrival = flight.optJSONObject("arrival") ?: JSONObject()
        val number = firstString(identity, "iata", "icao", "number") ?: requestedFlight
        val airlineName = firstString(airline, "name")
        val status = firstString(flight, "flight_status") ?: "status unavailable"
        val depAirport = firstString(departure, "airport", "iata")
        val arrAirport = firstString(arrival, "airport", "iata")
        val depTerminal = firstString(departure, "terminal")
        val depGate = firstString(departure, "gate")
        val arrTerminal = firstString(arrival, "terminal")
        val arrGate = firstString(arrival, "gate")
        val depDelay = firstString(departure, "delay")
        val arrDelay = firstString(arrival, "delay")
        val depEstimate = firstString(departure, "estimated", "actual", "scheduled")
        val arrEstimate = firstString(arrival, "estimated", "actual", "scheduled")
        val answer = buildString {
            append("Flight $number")
            airlineName?.let { append(" ($it)") }
            append(" is $status")
            if (depAirport != null || arrAirport != null) append("; ${depAirport ?: "origin unknown"} to ${arrAirport ?: "destination unknown"}")
            depTerminal?.let { append("; departure terminal $it") }
            depGate?.let { append(" gate $it") }
            depDelay?.let { append("; departure delay $it min") }
            depEstimate?.let { append("; departure $it") }
            arrTerminal?.let { append("; arrival terminal $it") }
            arrGate?.let { append(" gate $it") }
            arrDelay?.let { append("; arrival delay $it min") }
            arrEstimate?.let { append("; arrival $it") }
            append('.')
        }.take(MAX_ANSWER_CHARS)
        return StructuredKnowledgeResult(
            answer = answer,
            context = "AviationStack realtime flight record for $requestedFlight: ${boundedJson(flight)}",
            sources = listOf(GroundingSource("AviationStack", AVIATION_SOURCE_URL)),
        )
    }

    private fun pnrPassengerSummary(array: JSONArray): String = buildList {
        for (index in 0 until minOf(array.length(), 6)) {
            val passenger = array.optJSONObject(index) ?: continue
            val number = firstString(passenger, "number", "passengerNumber", "passenger_no") ?: (index + 1).toString()
            val current = firstString(passenger, "currentStatus", "current_status", "currentStatusDetails")
            val booking = firstString(passenger, "bookingStatus", "booking_status")
            val status = current ?: booking ?: continue
            add("passenger $number $status")
        }
    }.joinToString(", ")

    private fun ensureProviderSuccess(root: JSONObject, label: String) {
        if (root.has("status") && !root.optBoolean("status", true)) {
            throw IllegalStateException(firstString(root, "message", "error") ?: "$label provider returned failure.")
        }
        root.optJSONObject("error")?.let { error ->
            throw IllegalStateException(firstString(error, "message", "error", "code") ?: "$label provider returned an error.")
        }
    }

    private fun nestedName(json: JSONObject, key: String): String? = json.optJSONObject(key)?.let { nested ->
        firstString(nested, "name", "station_name", "stationName", "code")
    }

    private fun firstArray(json: JSONObject, vararg keys: String): JSONArray? = keys.asSequence()
        .mapNotNull(json::optJSONArray)
        .firstOrNull()

    private fun firstString(json: JSONObject?, vararg keys: String): String? {
        if (json == null) return null
        return keys.asSequence().mapNotNull { key ->
            if (!json.has(key) || json.isNull(key)) return@mapNotNull null
            cleanTransportText(json.opt(key)?.toString().orEmpty(), 300)
                .takeIf { it.isNotBlank() && !it.equals("null", true) }
        }.firstOrNull()
    }

    private fun boundedJson(json: JSONObject): String = json.toString().take(MAX_CONTEXT_JSON_CHARS)

    private const val MAX_ANSWER_CHARS = 1_800
    private const val MAX_CONTEXT_JSON_CHARS = 4_500
    private const val RAIL_SOURCE_URL = "https://rapidapi.com/IRCTCAPI/api/irctc1"
    private const val AVIATION_SOURCE_URL = "https://aviationstack.com/"
}

private fun cleanTransportText(value: String, maxChars: Int): String = value
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(maxChars)

private fun publicFeedUrl(value: String): String = runCatching {
    val uri = URI(value)
    URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toASCIIString()
}.getOrDefault("https://gtfs.org/realtime/")

private fun defaultTransportClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(3, TimeUnit.SECONDS)
    .readTimeout(6, TimeUnit.SECONDS)
    .writeTimeout(3, TimeUnit.SECONDS)
    .callTimeout(7, TimeUnit.SECONDS)
    .build()

private suspend fun Call.awaitTransportBody(label: String): String = awaitTransportResponse(label) { response ->
    response.body?.string().orEmpty().also { body ->
        if (body.length > MAX_TRANSPORT_TEXT_CHARS) throw IOException("$label response exceeds bounded size.")
    }
}

private suspend fun Call.awaitTransportBytes(label: String): ByteArray = awaitTransportResponse(label) { response ->
    val length = response.body?.contentLength() ?: -1L
    if (length > MAX_TRANSPORT_BINARY_BYTES) throw IOException("$label response exceeds bounded size.")
    response.body?.bytes() ?: ByteArray(0)
}

private suspend fun <T> Call.awaitTransportResponse(label: String, parser: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        continuation.resumeWithException(IOException("$label HTTP ${it.code}"))
                        return
                    }
                    try {
                        val parsed = parser(it)
                        continuation.resume(parsed) { _, _, _ -> }
                    } catch (error: Throwable) {
                        continuation.resumeWithException(error)
                    }
                }
            }
        })
    }

private const val MAX_TRANSPORT_TEXT_CHARS = 120_000
private const val MAX_TRANSPORT_BINARY_BYTES = 3L * 1024L * 1024L

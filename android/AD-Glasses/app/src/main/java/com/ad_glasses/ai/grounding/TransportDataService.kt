package com.ad_glasses.ai.grounding

import android.content.Context
import kotlinx.coroutines.CancellationException

/**
 * Router-independent façade for transport capabilities.
 *
 * The semantic router can bind to this service later without duplicating provider logic. Keeping
 * this layer independent also makes the transport clients usable from UI/debug surfaces while the
 * routing contract is being iterated separately.
 */
class TransportDataService(context: Context) {
    private val appContext = context.applicationContext
    private val location = AndroidLocationProvider(appContext)
    private val rail = RailStatusClient(appContext)
    private val flight = FlightStatusClient(appContext)
    private val osmTransit = OsmTransitClient(configProvider = { GroundingPrefs.getConfig(appContext) })
    private val realtime = TransitRealtimeClient()

    fun railConfigured(): Boolean = rail.isConfigured()

    fun flightConfigured(): Boolean = flight.isConfigured()

    fun realtimeFeeds(): List<GtfsRealtimeFeedConfig> = GroundingPrefs.getGtfsRealtimeFeeds(appContext)

    suspend fun railLiveStatus(trainNumber: String, startDay: Int = 0): Result<StructuredKnowledgeResult> =
        rail.liveStatus(trainNumber, startDay)

    suspend fun pnrStatus(pnrNumber: String): Result<StructuredKnowledgeResult> = rail.pnrStatus(pnrNumber)

    suspend fun flightStatus(flightIata: String): Result<StructuredKnowledgeResult> = flight.status(flightIata)

    suspend fun nearbyTransitStops(
        origin: GeoPoint? = null,
        radiusMeters: Int = DEFAULT_TRANSIT_RADIUS_METERS,
        limit: Int = DEFAULT_TRANSIT_LIMIT,
    ): Result<StructuredKnowledgeResult> = try {
        val point = origin ?: requireCurrentPoint()
        val places = osmTransit.nearby(point, radiusMeters, limit).getOrThrow()
        val shown = places.take(limit.coerceIn(1, MAX_TRANSIT_RESULTS))
        val answer = if (shown.isEmpty()) {
            "OpenStreetMap found no mapped bus, metro, tram, or rail stop within the requested radius."
        } else {
            buildString {
                append("Nearby public transport: ")
                append(shown.joinToString("; ") { place ->
                    "${place.name}, about ${spokenDistance(place.distanceMeters)}, ${place.category}"
                })
                append('.')
            }.take(MAX_ANSWER_CHARS)
        }
        val context = buildString {
            appendLine("OpenStreetMap nearby public-transport infrastructure:")
            if (shown.isEmpty()) {
                append("No matching mapped stop/station was returned in the bounded radius.")
            } else {
                shown.forEachIndexed { index, place ->
                    appendLine(
                        "[${index + 1}] ${place.name}; distanceMeters=${place.distanceMeters}; ${place.category}",
                    )
                }
            }
            append("These are physical mapped stops/stations, not realtime arrival predictions.")
        }.take(MAX_CONTEXT_CHARS)
        Result.success(
            StructuredKnowledgeResult(
                answer = answer,
                context = context,
                sources = listOf(GroundingSource("OpenStreetMap contributors", OSM_SOURCE_URL)),
            ),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    suspend fun realtimeStatus(
        feedId: String,
        stopId: String? = null,
        routeId: String? = null,
    ): Result<StructuredKnowledgeResult> = try {
        val feed = requireFeed(feedId)
        Result.success(realtime.status(feed, stopId, routeId).getOrThrow())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    suspend fun nearbyRealtimeVehicles(
        feedId: String,
        origin: GeoPoint? = null,
        radiusMeters: Int = DEFAULT_VEHICLE_RADIUS_METERS,
        routeId: String? = null,
        limit: Int = DEFAULT_TRANSIT_LIMIT,
    ): Result<StructuredKnowledgeResult> = try {
        val point = origin ?: requireCurrentPoint()
        val feed = requireFeed(feedId)
        val vehicles = realtime.nearbyVehicles(feed, point, radiusMeters, routeId, limit).getOrThrow()
        val answer = if (vehicles.isEmpty()) {
            "${feed.label} has no matching realtime vehicle position in the requested radius. This does not prove that no service is running."
        } else {
            buildString {
                append("Nearby ${feed.label} realtime vehicles: ")
                append(vehicles.joinToString("; ") { vehicle ->
                    buildString {
                        vehicle.routeId?.let { append("route $it, ") }
                        append("about ${spokenDistance(vehicle.distanceMeters)} away")
                        vehicle.stopId?.let { append(", near stop $it") }
                    }
                })
                append('.')
            }.take(MAX_ANSWER_CHARS)
        }
        val context = buildString {
            appendLine("GTFS-Realtime vehicle positions from ${feed.label}:")
            if (vehicles.isEmpty()) {
                appendLine("No matching VehiclePosition entity was returned in the bounded radius.")
                append("Do not infer that service is not operating from absence of a vehicle entity.")
            } else {
                vehicles.forEachIndexed { index, vehicle ->
                    appendLine(
                        "[${index + 1}] route=${vehicle.routeId ?: "unknown"}; trip=${vehicle.tripId ?: "unknown"}; " +
                            "vehicle=${vehicle.vehicleId ?: "unknown"}; stop=${vehicle.stopId ?: "unknown"}; " +
                            "distanceMeters=${vehicle.distanceMeters}; timestamp=${vehicle.timestampEpochSeconds ?: "unknown"}",
                    )
                }
            }
        }.take(MAX_CONTEXT_CHARS)
        Result.success(
            StructuredKnowledgeResult(
                answer = answer,
                context = context,
                sources = listOf(GroundingSource("${feed.label} GTFS-Realtime", safeFeedSource(feed.url))),
            ),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun requireCurrentPoint(): GeoPoint {
        check(location.hasPermission()) { "Location permission is required for nearby transit." }
        return location.currentFix()?.point
            ?: throw IllegalStateException("A current location fix is unavailable.")
    }

    private fun requireFeed(feedId: String): GtfsRealtimeFeedConfig {
        val clean = feedId.trim()
        require(clean.isNotBlank()) { "GTFS realtime feed id cannot be blank." }
        return realtimeFeeds().firstOrNull { it.id.equals(clean, ignoreCase = true) }
            ?: throw IllegalStateException("GTFS realtime feed '$clean' is not configured.")
    }

    private fun spokenDistance(distanceMeters: Int): String = when {
        distanceMeters < 1_000 -> "$distanceMeters m"
        else -> String.format(java.util.Locale.US, "%.1f km", distanceMeters / 1_000.0)
    }

    private fun safeFeedSource(url: String): String = runCatching {
        val uri = java.net.URI(url)
        java.net.URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toASCIIString()
    }.getOrDefault(GTFS_SOURCE_URL)

    private companion object {
        const val DEFAULT_TRANSIT_RADIUS_METERS = 1_500
        const val DEFAULT_VEHICLE_RADIUS_METERS = 3_000
        const val DEFAULT_TRANSIT_LIMIT = 8
        const val MAX_TRANSIT_RESULTS = 16
        const val MAX_ANSWER_CHARS = 1_800
        const val MAX_CONTEXT_CHARS = 4_000
        const val OSM_SOURCE_URL = "https://www.openstreetmap.org/copyright"
        const val GTFS_SOURCE_URL = "https://gtfs.org/realtime/"
    }
}

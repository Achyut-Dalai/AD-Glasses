package com.ad_glasses.ai.grounding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsmServiceClientTest {
    private val origin = GeoPoint(latitude = 12.9716, longitude = 77.5946)
    private val destination = GeoPoint(latitude = 12.9763, longitude = 77.5929)

    @Test
    fun defaultFossgisRootSelectsDedicatedModeGraphs() {
        val client = client(GroundingPrefs.DEFAULT_OSRM_BASE_URL)

        val driving = client.routeRequestUrl(origin, destination, RouteMode.DRIVING).toString()
        val walking = client.routeRequestUrl(origin, destination, RouteMode.WALKING).toString()
        val cycling = client.routeRequestUrl(origin, destination, RouteMode.CYCLING).toString()

        assertTrue(driving, driving.contains("routing.openstreetmap.de/routed-car/route/v1/driving/"))
        assertTrue(walking, walking.contains("routing.openstreetmap.de/routed-foot/route/v1/driving/"))
        assertTrue(cycling, cycling.contains("routing.openstreetmap.de/routed-bike/route/v1/driving/"))
        assertTrue(driving, driving.contains("steps=true"))
        assertTrue(walking, walking.contains("overview=false"))
    }

    @Test
    fun customOsrmRootUsesConfiguredModeProfiles() {
        val client = client("https://router.example.com")

        assertTrue(
            client.routeRequestUrl(origin, destination, RouteMode.DRIVING).toString(),
            client.routeRequestUrl(origin, destination, RouteMode.DRIVING).toString()
                .contains("router.example.com/route/v1/driving/"),
        )
        assertTrue(
            client.routeRequestUrl(origin, destination, RouteMode.WALKING).toString(),
            client.routeRequestUrl(origin, destination, RouteMode.WALKING).toString()
                .contains("router.example.com/route/v1/foot/"),
        )
        assertTrue(
            client.routeRequestUrl(origin, destination, RouteMode.CYCLING).toString(),
            client.routeRequestUrl(origin, destination, RouteMode.CYCLING).toString()
                .contains("router.example.com/route/v1/bike/"),
        )
    }

    @Test
    fun overpassParserKeepsNodeCoordinatesAndWayRelationCenters() {
        val client = client(GroundingPrefs.DEFAULT_OSRM_BASE_URL)
        val payload = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 1,
                  "lat": 12.9717,
                  "lon": 77.5947,
                  "tags": {"amenity": "cafe", "name": "Node Cafe"}
                },
                {
                  "type": "way",
                  "id": 2,
                  "center": {"lat": 12.9720, "lon": 77.5950},
                  "tags": {"amenity": "pharmacy", "name": "Way Pharmacy"}
                },
                {
                  "type": "relation",
                  "id": 3,
                  "center": {"lat": 12.9725, "lon": 77.5955},
                  "tags": {"tourism": "museum", "name": "Relation Museum"}
                }
              ]
            }
        """.trimIndent()

        val places = client.parseOverpass(payload, origin, limit = 8)

        assertEquals(3, places.size)
        assertEquals(setOf("Node Cafe", "Way Pharmacy", "Relation Museum"), places.map { it.name }.toSet())
        assertTrue(places.all { it.distanceMeters >= 0 })
        assertTrue(places.zipWithNext().all { (a, b) -> a.distanceMeters <= b.distanceMeters })
    }

    @Test
    fun routeParserReturnsDistanceDurationAndSafeInstructions() {
        val client = client(GroundingPrefs.DEFAULT_OSRM_BASE_URL)
        val payload = """
            {
              "code": "Ok",
              "routes": [{
                "distance": 1200.4,
                "duration": 620.2,
                "legs": [{
                  "steps": [
                    {"distance": 200, "duration": 120, "name": "Main Road", "maneuver": {"type": "depart"}},
                    {"distance": 700, "duration": 360, "name": "Park Street", "maneuver": {"type": "turn", "modifier": "left"}},
                    {"distance": 300, "duration": 140, "name": "", "maneuver": {"type": "arrive"}}
                  ]
                }]
              }]
            }
        """.trimIndent()

        val route = client.parseRoute(payload)

        assertEquals(1200, route.distanceMeters)
        assertEquals(620, route.durationSeconds)
        assertEquals(3, route.steps.size)
        assertEquals("Start onto Main Road", route.steps[0].instruction)
        assertEquals("Turn left onto Park Street", route.steps[1].instruction)
        assertEquals("Arrive at the destination", route.steps[2].instruction)
    }

    private fun client(osrmBaseUrl: String) = OsmServiceClient(
        configProvider = {
            GroundingServiceConfig(
                tavilyEnabled = false,
                nominatimBaseUrl = GroundingPrefs.DEFAULT_NOMINATIM_BASE_URL,
                overpassEndpoint = GroundingPrefs.DEFAULT_OVERPASS_ENDPOINT,
                osrmBaseUrl = osrmBaseUrl,
            )
        },
    )
}

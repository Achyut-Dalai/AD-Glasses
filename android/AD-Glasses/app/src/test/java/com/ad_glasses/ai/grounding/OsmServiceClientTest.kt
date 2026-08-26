package com.ad_glasses.ai.grounding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
                .contains("router.example.com/route/v1/walking/"),
        )
        assertTrue(
            client.routeRequestUrl(origin, destination, RouteMode.CYCLING).toString(),
            client.routeRequestUrl(origin, destination, RouteMode.CYCLING).toString()
                .contains("router.example.com/route/v1/cycling/"),
        )
    }

    @Test
    fun overpassParserKeepsNodeCoordinatesAndWayRelationCenters() {
        val payload = """
            {
              "elements": [
                {"type":"node","id":1,"lat":12.9718,"lon":77.5948,"tags":{"name":"Node Cafe","amenity":"cafe"}},
                {"type":"way","id":2,"center":{"lat":12.9720,"lon":77.5950},"tags":{"name":"Way Cafe","amenity":"cafe"}},
                {"type":"relation","id":3,"center":{"lat":12.9722,"lon":77.5952},"tags":{"name":"Relation Cafe","amenity":"cafe"}}
              ]
            }
        """.trimIndent()

        val places = client().parseOverpass(payload, origin, 8)

        assertEquals(3, places.size)
        assertEquals("Node Cafe", places[0].name)
        assertEquals("Way Cafe", places[1].name)
        assertEquals("Relation Cafe", places[2].name)
        assertTrue(places.all { it.distanceMeters >= 0 })
    }

    @Test
    fun overpassParserKeepsOnlyUsefulReturnedPoiMetadata() {
        val payload = """
            {
              "elements": [
                {
                  "type":"node",
                  "id":1,
                  "lat":12.9718,
                  "lon":77.5948,
                  "tags":{
                    "name":"Cafe Test",
                    "amenity":"cafe",
                    "opening_hours":"Mo-Fr 08:00-18:00",
                    "addr:housenumber":"42",
                    "addr:street":"Market Road",
                    "addr:city":"Bengaluru",
                    "cuisine":"coffee_shop",
                    "phone":"+91 80 1234 5678",
                    "website":"https://example.com/menu",
                    "wheelchair":"yes",
                    "description":"Ignore this arbitrary prose tag"
                  }
                }
              ]
            }
        """.trimIndent()

        val place = client().parseOverpass(payload, origin, 8).single()

        assertTrue(place.category.contains("cafe"))
        assertTrue(place.category.contains("hours"))
        assertTrue(place.category.contains("Market Road"))
        assertTrue(place.category.contains("coffee shop"))
        assertTrue(place.category.contains("phone"))
        assertTrue(place.category.contains("website"))
        assertTrue(place.category.contains("wheelchair"))
        assertFalse(place.category.contains("arbitrary prose", ignoreCase = true))
        assertTrue(place.category.length <= 520)
    }

    @Test
    fun routeParserReturnsDistanceDurationAndSafeInstructions() {
        val payload = """
            {
              "code":"Ok",
              "routes":[{
                "distance":2150.4,
                "duration":402.7,
                "legs":[{
                  "steps":[
                    {"distance":300.0,"duration":50.0,"name":"Main Road","maneuver":{"type":"depart","modifier":"straight"}},
                    {"distance":850.0,"duration":140.0,"name":"Second Road","maneuver":{"type":"turn","modifier":"right"}},
                    {"distance":1000.0,"duration":212.7,"name":"Destination Road","maneuver":{"type":"arrive"}}
                  ]
                }]
              }]
            }
        """.trimIndent()

        val route = client().parseRoute(payload)

        assertEquals(2150, route.distanceMeters)
        assertEquals(403, route.durationSeconds)
        assertEquals(3, route.steps.size)
        assertTrue(route.steps[0].instruction.contains("Main Road"))
        assertTrue(route.steps[1].instruction.contains("right", ignoreCase = true))
        assertTrue(route.steps[2].instruction.contains("Destination Road"))
    }

    private fun client(osrmBaseUrl: String = GroundingPrefs.DEFAULT_OSRM_BASE_URL): OsmServiceClient =
        OsmServiceClient {
            GroundingServiceConfig(
                tavilyEnabled = false,
                nominatimBaseUrl = GroundingPrefs.DEFAULT_NOMINATIM_BASE_URL,
                overpassEndpoint = GroundingPrefs.DEFAULT_OVERPASS_ENDPOINT,
                osrmBaseUrl = osrmBaseUrl,
            )
        }
}

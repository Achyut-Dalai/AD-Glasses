package com.ad_glasses.ai.grounding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsmTransitClientTest {
    private val client = OsmTransitClient {
        GroundingServiceConfig(
            tavilyEnabled = true,
            nominatimBaseUrl = GroundingPrefs.DEFAULT_NOMINATIM_BASE_URL,
            overpassEndpoint = GroundingPrefs.DEFAULT_OVERPASS_ENDPOINT,
            osrmBaseUrl = GroundingPrefs.DEFAULT_OSRM_BASE_URL,
        )
    }

    @Test
    fun queryCoversBusMetroRailAndTramInfrastructure() {
        val query = client.buildNearbyQuery(GeoPoint(12.9716, 77.5946), 1_500)

        assertTrue(query.contains("[\"highway\"=\"bus_stop\"]"))
        assertTrue(query.contains("public_transport"))
        assertTrue(query.contains("subway_entrance"))
        assertTrue(query.contains("tram_stop"))
        assertTrue(query.contains("[timeout:4]"))
    }

    @Test
    fun parserRanksPhysicalTransitStopsByDistance() {
        val payload = """{
          "elements": [
            {
              "type": "node",
              "id": 1,
              "lat": 12.9720,
              "lon": 77.5946,
              "tags": {
                "name": "Central Bus Stop",
                "highway": "bus_stop",
                "network": "BMTC",
                "route_ref": "201;500"
              }
            },
            {
              "type": "node",
              "id": 2,
              "lat": 12.9750,
              "lon": 77.5946,
              "tags": {
                "name": "Metro Entrance A",
                "railway": "subway_entrance",
                "network": "Namma Metro"
              }
            }
          ]
        }""".trimIndent()

        val places = client.parse(payload, GeoPoint(12.9716, 77.5946), 2_000, 8)

        assertEquals(2, places.size)
        assertEquals("Central Bus Stop", places.first().name)
        assertTrue(places.first().category.contains("bus stop"))
        assertTrue(places.first().category.contains("BMTC"))
        assertTrue(places[1].category.contains("subway entrance"))
    }
}

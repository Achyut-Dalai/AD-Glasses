package com.ad_glasses.ai.grounding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NamedOsmPoiClientTest {
    private val client = NamedOsmPoiClient(configProvider = { error("unused in parser test") })

    @Test
    fun exactBrandMatchesAreRankedByDistance() {
        val origin = GeoPoint(20.0000, 85.0000)
        val payload = """
            {
              "elements": [
                {"type":"node","id":1,"lat":20.0200,"lon":85.0000,"tags":{"name":"KFC","amenity":"fast_food","brand":"KFC"}},
                {"type":"node","id":2,"lat":20.0020,"lon":85.0000,"tags":{"name":"KFC Patia","amenity":"fast_food","brand":"KFC"}},
                {"type":"node","id":3,"lat":20.0010,"lon":85.0000,"tags":{"name":"Coffee Shop","amenity":"cafe","brand":"Other"}}
              ]
            }
        """.trimIndent()

        val places = client.parse(payload, origin, "KFC", limit = 6)

        assertEquals(2, places.size)
        assertEquals("KFC Patia", places.first().name)
        assertTrue(places.first().distanceMeters < places.last().distanceMeters)
    }

    @Test
    fun wayCentersAreAcceptedForNamedPois() {
        val payload = """
            {"elements":[{"type":"way","id":4,"center":{"lat":20.001,"lon":85.002},"tags":{"brand":"KFC","shop":"fast_food"}}]}
        """.trimIndent()

        val places = client.parse(payload, GeoPoint(20.0, 85.0), "KFC", limit = 3)

        assertEquals(1, places.size)
        assertEquals("KFC", places.single().name)
    }
}

package com.ad_glasses.ai.grounding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantGroundingPolicyTest {
    @Test
    fun nearbyCoffeeProducesCafeFilterAndRadius() {
        val intent = AssistantGroundingPolicy.spatialIntent("Find coffee shops within 200m near me")
        assertTrue(intent.needsLocation)
        assertEquals(200, intent.radiusMeters)
        assertEquals(listOf(OverpassTagFilter("amenity", "cafe")), intent.filters)
    }

    @Test
    fun distanceParsingSupportsMetricImperialAndClamps() {
        assertEquals(2_500, AssistantGroundingPolicy.parseRadiusMeters("restaurants within 2.5 km"))
        assertEquals(1_609, AssistantGroundingPolicy.parseRadiusMeters("cafes within 1 mile"))
        assertEquals(50, AssistantGroundingPolicy.parseRadiusMeters("pharmacy within 20 ft"))
        assertEquals(50, AssistantGroundingPolicy.parseRadiusMeters("pharmacy within 5 m"))
        assertEquals(5_000, AssistantGroundingPolicy.parseRadiusMeters("parks within 50 km"))
    }

    @Test
    fun routeDestinationAndModeAreExtracted() {
        val intent = AssistantGroundingPolicy.spatialIntent("Give me walking directions to Cubbon Park")
        assertTrue(intent.needsLocation)
        assertTrue(intent.routeRequested)
        assertEquals(RouteMode.WALKING, intent.routeMode)
        assertEquals("Cubbon Park", intent.routeDestination)
    }

    @Test
    fun explicitOriginRouteDoesNotRequestDeviceLocation() {
        val intent = AssistantGroundingPolicy.spatialIntent("Route from Bengaluru Palace to Cubbon Park")
        assertFalse(intent.needsLocation)
        assertTrue(intent.routeRequested)
        assertEquals("Bengaluru Palace", intent.routeOrigin)
        assertEquals("Cubbon Park", intent.routeDestination)
    }

    @Test
    fun nearestCategoryRoutesResolveFromPoiInsteadOfForwardGeocoding() {
        val intent = AssistantGroundingPolicy.spatialIntent("Navigate to the nearest pharmacy")
        assertTrue(intent.needsLocation)
        assertTrue(intent.routeRequested)
        assertTrue(intent.filters.isNotEmpty())
        assertNull(intent.routeDestination)
    }

    @Test
    fun categoryWordsAloneDoNotActivateLocation() {
        assertFalse(AssistantGroundingPolicy.spatialIntent("What makes a good restaurant?").needsLocation)
        assertFalse(AssistantGroundingPolicy.spatialIntent("How do banks work?").needsLocation)
        assertFalse(AssistantGroundingPolicy.spatialIntent("Is local food healthy?").needsLocation)
        assertFalse(AssistantGroundingPolicy.spatialIntent("Recommend a bank account for students").needsLocation)
    }

    @Test
    fun actualPoiDiscoveryActivatesLocationAndSupportsMultipleCategories() {
        val intent = AssistantGroundingPolicy.spatialIntent("Find a cafe and pharmacy near me")
        assertTrue(intent.needsLocation)
        assertTrue(intent.filters.contains(OverpassTagFilter("amenity", "cafe")))
        assertTrue(intent.filters.contains(OverpassTagFilter("amenity", "pharmacy")))
    }

    @Test
    fun generalNearbyDiscoveryUsesBoundedUsefulPoiSet() {
        val intent = AssistantGroundingPolicy.spatialIntent("What's nearby?")
        assertTrue(intent.needsLocation)
        assertTrue(intent.filters.contains(OverpassTagFilter("tourism", "attraction")))
        assertTrue(intent.filters.contains(OverpassTagFilter("amenity", "cafe")))
        assertTrue(intent.filters.contains(OverpassTagFilter("amenity", "restaurant")))
    }

    @Test
    fun locationOnlyAndLocalWeatherUseLocationWithoutPoiSearch() {
        val location = AssistantGroundingPolicy.spatialIntent("What is my current location?")
        assertTrue(location.needsLocation)
        assertTrue(location.locationOnly)
        assertTrue(location.filters.isEmpty())

        val weather = AssistantGroundingPolicy.spatialIntent("What's the weather near me today?")
        assertTrue(weather.needsLocation)
        assertFalse(weather.locationOnly)
        assertTrue(weather.filters.isEmpty())
    }

    @Test
    fun routeLikeIdiomsDoNotActivateGpsOrRouting() {
        listOf(
            "How do I get to sleep faster?",
            "Walk me through Kotlin coroutines",
            "Drive sales to one million dollars",
            "Directions to improve my writing",
            "Route traffic to the backup server",
        ).forEach { text ->
            val intent = AssistantGroundingPolicy.spatialIntent(text)
            assertFalse(text, intent.needsLocation)
            assertFalse(text, intent.routeRequested)
        }
    }

    @Test
    fun commonPoiAliasesMapToWhitelistedOsmTags() {
        assertEquals(
            listOf(OverpassTagFilter("amenity", "toilets")),
            AssistantGroundingPolicy.spatialIntent("Find a bathroom near me").filters,
        )
        assertEquals(
            listOf(OverpassTagFilter("amenity", "charging_station")),
            AssistantGroundingPolicy.spatialIntent("Nearest EV charger").filters,
        )
        assertEquals(
            listOf(OverpassTagFilter("railway", "station")),
            AssistantGroundingPolicy.spatialIntent("Find a train station nearby").filters,
        )
    }

    @Test
    fun visualIdentificationPolicyAvoidsLocalVisionTasks() {
        assertTrue(AssistantGroundingPolicy.shouldGroundVisual("What landmark is this?"))
        assertTrue(AssistantGroundingPolicy.shouldGroundVisual("Identify this plant"))
        assertTrue(AssistantGroundingPolicy.shouldGroundVisual("How much is this product worth?"))
        assertFalse(AssistantGroundingPolicy.shouldGroundVisual("Read the text on this page"))
        assertFalse(AssistantGroundingPolicy.shouldGroundVisual("What color is this building?"))
        assertFalse(AssistantGroundingPolicy.shouldGroundVisual("Summarize this document"))
        assertFalse(AssistantGroundingPolicy.shouldGroundVisual("What price is shown on this receipt?"))
    }
}

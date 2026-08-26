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
    fun distanceParsingSupportsKilometersAndClamps() {
        assertEquals(2_500, AssistantGroundingPolicy.parseRadiusMeters("restaurants within 2.5 km"))
        assertEquals(50, AssistantGroundingPolicy.parseRadiusMeters("pharmacy within 5 m"))
        assertEquals(5_000, AssistantGroundingPolicy.parseRadiusMeters("parks within 50 km"))
    }

    @Test
    fun routeDestinationAndModeAreExtracted() {
        val intent = AssistantGroundingPolicy.spatialIntent("Give me walking directions to Cubbon Park")
        assertTrue(intent.needsLocation)
        assertEquals(RouteMode.WALKING, intent.routeMode)
        assertEquals("Cubbon Park", intent.routeDestination)
    }

    @Test
    fun nearestCategoryRoutesCanResolveFromPoiInsteadOfForwardGeocoding() {
        val intent = AssistantGroundingPolicy.spatialIntent("Navigate to the nearest pharmacy")
        assertTrue(intent.needsLocation)
        assertTrue(intent.filters.isNotEmpty())
        assertNull(intent.routeDestination)
    }

    @Test
    fun visualIdentificationPolicyIsSelective() {
        assertTrue(AssistantGroundingPolicy.shouldGroundVisual("What landmark is this?"))
        assertTrue(AssistantGroundingPolicy.shouldGroundVisual("Identify this plant"))
        assertFalse(AssistantGroundingPolicy.shouldGroundVisual("Read the text on this page"))
    }
}

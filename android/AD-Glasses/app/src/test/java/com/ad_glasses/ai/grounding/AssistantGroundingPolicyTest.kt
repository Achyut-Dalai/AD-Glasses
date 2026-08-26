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
    fun genericCategoryRouteUsesPoiButNamedBusinessStaysNamedDestination() {
        val generic = AssistantGroundingPolicy.spatialIntent("Navigate to a pharmacy")
        assertTrue(generic.routeRequested)
        assertNull(generic.routeDestination)
        assertTrue(generic.filters.contains(OverpassTagFilter("amenity", "pharmacy")))

        val named = AssistantGroundingPolicy.spatialIntent("Navigate to the Ritz hotel")
        assertTrue(named.routeRequested)
        assertEquals("the Ritz hotel", named.routeDestination)
        assertTrue(named.filters.isEmpty())
    }

    @Test
    fun categoryWordsAloneDoNotActivateLocation() {
        listOf(
            "What makes a good restaurant?",
            "How do banks work?",
            "Is local food healthy?",
            "Recommend a bank account for students",
            "What is a public park?",
            "How do hospitals triage patients?",
        ).forEach { text ->
            assertFalse(text, AssistantGroundingPolicy.spatialIntent(text).needsLocation)
        }
    }

    @Test
    fun actualPoiDiscoveryActivatesLocationAndSupportsMultipleCategories() {
        val intent = AssistantGroundingPolicy.spatialIntent("Find a cafe and pharmacy near me")
        assertTrue(intent.needsLocation)
        assertTrue(intent.filters.contains(OverpassTagFilter("amenity", "cafe")))
        assertTrue(intent.filters.contains(OverpassTagFilter("amenity", "pharmacy")))
    }

    @Test
    fun explicitReferencePlaceAvoidsDeviceGps() {
        val intent = AssistantGroundingPolicy.spatialIntent("Find cafes within 1 km of Cubbon Park")
        assertFalse(intent.needsLocation)
        assertEquals("Cubbon Park", intent.referencePlace)
        assertEquals(1_000, intent.radiusMeters)
        assertTrue(intent.filters.contains(OverpassTagFilter("amenity", "cafe")))
    }

    @Test
    fun unresolvedPersonalPlacesDoNotSilentlyBecomeCurrentLocation() {
        listOf(
            "Find a pharmacy near my hotel",
            "Closest cafe to my office",
            "Find parking near my home",
        ).forEach { text ->
            val intent = AssistantGroundingPolicy.spatialIntent(text)
            assertFalse(text, intent.needsLocation)
            assertTrue(text, intent.filters.isEmpty())
            assertNull(text, intent.referencePlace)
        }
    }

    @Test
    fun generalNearbyDiscoveryUsesBoundedUsefulPoiSet() {
        listOf(
            "What's nearby?",
            "Things to do nearby",
            "What can I see around here?",
        ).forEach { text ->
            val intent = AssistantGroundingPolicy.spatialIntent(text)
            assertTrue(text, intent.needsLocation)
            assertTrue(text, intent.filters.contains(OverpassTagFilter("tourism", "attraction")))
            assertTrue(text, intent.filters.contains(OverpassTagFilter("amenity", "cafe")))
            assertTrue(text, intent.filters.contains(OverpassTagFilter("amenity", "restaurant")))
        }
    }

    @Test
    fun selfLocationPhrasesUseLocationWithoutPoiSearch() {
        listOf(
            "What is my current location?",
            "What is my address?",
            "What street am I on?",
            "Which neighborhood am I in?",
            "What city am I in?",
        ).forEach { text ->
            val intent = AssistantGroundingPolicy.spatialIntent(text)
            assertTrue(text, intent.needsLocation)
            assertTrue(text, intent.locationOnly)
            assertTrue(text, intent.filters.isEmpty())
        }
    }

    @Test
    fun localWeatherUsesLocationWithoutPoiSearch() {
        val weather = AssistantGroundingPolicy.spatialIntent("What's the weather near me today?")
        assertTrue(weather.needsLocation)
        assertFalse(weather.locationOnly)
        assertTrue(weather.filters.isEmpty())
    }

    @Test
    fun deicticAreaRequiresAnActualPoiCategory() {
        val museum = AssistantGroundingPolicy.spatialIntent("Find a museum in this area")
        assertTrue(museum.needsLocation)
        assertTrue(museum.filters.contains(OverpassTagFilter("tourism", "museum")))

        assertFalse(AssistantGroundingPolicy.spatialIntent("Explain recursion in this area of computer science").needsLocation)
        assertFalse(AssistantGroundingPolicy.spatialIntent("Find a museum in this area of the code").needsLocation)
    }

    @Test
    fun routeLikeIdiomsAndTechnicalRoutingDoNotActivateGpsOrGeocoding() {
        listOf(
            "How do I get to sleep faster?",
            "Walk me through Kotlin coroutines",
            "Drive sales to one million dollars",
            "Directions to improve my writing",
            "Route traffic to the backup server",
            "Route from API gateway to backup server",
            "Directions from graph node A to graph node B",
            "Route HTTP requests from service A to service B",
            "How should Kubernetes route packets to this pod?",
        ).forEach { text ->
            val intent = AssistantGroundingPolicy.spatialIntent(text)
            assertFalse(text, intent.needsLocation)
            assertFalse(text, intent.routeRequested)
        }
    }

    @Test
    fun metaSpatialAndTechnicalProximityLanguageDoesNotActivateLocation() {
        listOf(
            "What does 'near me' mean in search?",
            "Explain the phrase nearby",
            "How does GPS location work?",
            "What is around here in this code?",
            "Where am I in this code?",
            "Where am I in this proof?",
            "Explain local news as a media concept",
            "What is local weather?",
            "Find the nearest node in this graph",
            "Which value is closest in this array?",
            "What's nearby in this data structure?",
        ).forEach { text ->
            assertFalse(text, AssistantGroundingPolicy.spatialIntent(text).needsLocation)
        }
    }

    @Test
    fun naturalDistancePhrasesCanRequestRouting() {
        val intent = AssistantGroundingPolicy.spatialIntent("How far is Cubbon Park from me?")
        assertTrue(intent.needsLocation)
        assertTrue(intent.routeRequested)
        assertEquals("Cubbon Park", intent.routeDestination)
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
        assertEquals(
            listOf(OverpassTagFilter("leisure", "playground")),
            AssistantGroundingPolicy.spatialIntent("Find a playground nearby").filters,
        )
        assertEquals(
            listOf(OverpassTagFilter("tourism", "museum")),
            AssistantGroundingPolicy.spatialIntent("Nearest museum").filters,
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
        assertFalse(AssistantGroundingPolicy.shouldGroundVisual("Identify this language"))
        assertFalse(AssistantGroundingPolicy.shouldGroundVisual("How much is this?"))
    }
}

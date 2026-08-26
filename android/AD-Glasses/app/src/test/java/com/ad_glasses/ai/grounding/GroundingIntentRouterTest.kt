package com.ad_glasses.ai.grounding

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroundingIntentRouterTest {
    private val router = GroundingIntentRouter(ApplicationProvider.getApplicationContext())

    @Test
    fun liveCricketScoreCanRouteToTavilyNewsDayDespiteAsrRepair() {
        val route = router.parse(
            raw = """{"intent":"SEARCH","external_tool":"tavily","search_query":"India vs Sri Lanka cricket live score","topic":"news","time_range":"day"}""",
            originalPrompt = "life score of india vs sri lanks cricket match",
        )!!

        assertEquals(GroundingIntent.SEARCH, route.intent)
        assertEquals(ExternalTool.TAVILY, route.externalTool)
        assertEquals("India vs Sri Lanka cricket live score", route.searchQuery)
        assertEquals(TavilySearchTopic.NEWS, route.tavilyTopic)
        assertEquals(TavilyTimeRange.DAY, route.tavilyTimeRange)
    }

    @Test
    fun sportsDoesNotRequireASeparateTavilyTopic() {
        val route = router.parse(
            raw = """{"intent":"SEARCH","search_query":"latest Formula 1 race result","topic":"news","time_range":"day"}""",
            originalPrompt = "who won the latest formula 1 race",
        )!!

        assertEquals(TavilySearchTopic.NEWS, route.tavilyTopic)
        assertEquals(TavilyTimeRange.DAY, route.tavilyTimeRange)
    }

    @Test
    fun namedBusinessAndSpokenRadiusBecomeFreeFormSpatialSlots() {
        val route = router.parse(
            raw = """{"intent":"SPATIAL","spatial_action":"nearby","spatial_query":"KFC","osm_filters":[{"key":"brand","value":"KFC"},{"key":"name","value":"KFC"}],"radius_meters":3000,"use_current_location":true}""",
            originalPrompt = "is there any kfc within three kilometres near me",
        )!!

        assertEquals(GroundingIntent.SPATIAL, route.intent)
        assertEquals(SpatialAction.NEARBY, route.spatialAction)
        assertEquals("KFC", route.spatialQuery)
        assertEquals(3000, route.radiusMeters)
        assertEquals(
            listOf(OverpassTagFilter("brand", "KFC"), OverpassTagFilter("name", "KFC")),
            route.osmFilters,
        )
        assertTrue(route.useCurrentLocation)
    }

    @Test
    fun unsafeOrUnknownOsmFiltersAreDroppedInsteadOfBecomingOverpassQl() {
        val route = router.parse(
            raw = """{"intent":"SPATIAL","spatial_action":"nearby","spatial_query":"KFC","osm_filters":[{"key":"brand","value":"KFC"},{"key":"around","value":"5000"},{"key":"name","value":"KFC\";out body;"}],"use_current_location":true}""",
            originalPrompt = "find kfc near me",
        )!!

        assertEquals(listOf(OverpassTagFilter("brand", "KFC")), route.osmFilters)
    }

    @Test
    fun currentWeatherCanUseDedicatedWeatherCapabilityWithoutTavily() {
        val route = router.parse(
            raw = """{"intent":"SEARCH","external_tool":"weather","weather_horizon":"current","use_current_location":true}""",
            originalPrompt = "what is the weather near me right now",
        )!!

        assertEquals(GroundingIntent.SEARCH, route.intent)
        assertEquals(ExternalTool.WEATHER, route.externalTool)
        assertEquals(WeatherHorizon.CURRENT, route.weatherHorizon)
        assertTrue(route.useCurrentLocation)
        assertNull(route.spatialAction)
    }

    @Test
    fun bothCanFindNearbyPlacesThenAskTavilyToEnrichThoseCandidates() {
        val route = router.parse(
            raw = """{"intent":"BOTH","external_tool":"tavily","search_query":"KFC official menu prices and opening information","topic":"general","spatial_action":"nearby","spatial_query":"KFC","osm_filters":[{"key":"brand","value":"KFC"}],"radius_meters":3000,"use_current_location":true}""",
            originalPrompt = "find kfc within three kilometres and check their websites for menu prices",
        )!!

        assertEquals(GroundingIntent.BOTH, route.intent)
        assertEquals(ExternalTool.TAVILY, route.externalTool)
        assertEquals(SpatialAction.NEARBY, route.spatialAction)
        assertEquals("KFC", route.spatialQuery)
        assertEquals(listOf(OverpassTagFilter("brand", "KFC")), route.osmFilters)
    }

    @Test
    fun explicitWebsiteRequestBecomesValidatedTavilyDomainConstraint() {
        val route = router.parse(
            raw = """{"intent":"SEARCH","search_query":"India cricket live score","topic":"news","time_range":"day","source_domains":["https://www.espn.in/cricket/","bad host","ESPN.IN"]}""",
            originalPrompt = "check espn for the india cricket score",
        )!!

        assertEquals(listOf("www.espn.in", "espn.in"), route.sourceDomains)
    }

    @Test
    fun currentVisualEvidenceCanHelpRoutingWithoutConversationHistory() {
        val input = router.buildRouterInput(
            prompt = "what is the current price of this",
            currentTurnEvidence = "Blue cotton bedsheet package. Brand label reads Example Home. Size label reads queen.",
        )

        assertTrue(input.contains("User utterance: what is the current price of this"))
        assertTrue(input.contains("Current-turn visual observation"))
        assertTrue(input.contains("Example Home"))
        assertFalse(input.contains("previous", ignoreCase = true))
    }

    @Test
    fun stableKnowledgeCanStayDirect() {
        val route = router.parse(
            raw = """{"intent":"DIRECT"}""",
            originalPrompt = "explain recursion",
        )!!

        assertEquals(GroundingIntent.DIRECT, route.intent)
        assertNull(route.searchQuery)
        assertNull(route.spatialAction)
    }

    @Test
    fun malformedOrIncompleteToolPlansAreRejected() {
        assertNull(router.parse("not json", "current cricket score"))
        assertNull(
            router.parse(
                """{"intent":"SPATIAL","spatial_action":"nearby"}""",
                "find something near me",
            ),
        )
        assertNull(
            router.parse(
                """{"intent":"SEARCH","external_tool":"weather","use_current_location":false}""",
                "weather please",
            ),
        )
    }
}

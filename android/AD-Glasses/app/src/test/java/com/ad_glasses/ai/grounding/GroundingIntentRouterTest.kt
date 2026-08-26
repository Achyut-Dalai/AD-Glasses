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
@Config(sdk = [35])
class GroundingIntentRouterTest {
    private val router = GroundingIntentRouter(ApplicationProvider.getApplicationContext())

    @Test
    fun liveCricketScoreCanRouteToTavilyNewsDayDespiteAsrRepair() {
        val route = router.parse(
            raw = """{"intent":"SEARCH","search_query":"India vs Sri Lanka cricket live score","topic":"news","time_range":"day","synthesize":false,"spatial_action":null,"spatial_query":null,"radius_meters":null,"use_current_location":false,"reference_place":null,"route_origin":null,"route_destination":null,"route_mode":null}""",
            originalPrompt = "life score of india vs sri lanks cricket match",
        )!!

        assertEquals(GroundingIntent.SEARCH, route.intent)
        assertEquals("India vs Sri Lanka cricket live score", route.searchQuery)
        assertEquals(TavilySearchTopic.NEWS, route.tavilyTopic)
        assertEquals(TavilyTimeRange.DAY, route.tavilyTimeRange)
        assertFalse(route.synthesize)
    }

    @Test
    fun whoWonCricketMatchDoesNotNeedHardcodedPhraseMatching() {
        val route = router.parse(
            raw = """{"intent":"SEARCH","search_query":"cricket match winner latest result","topic":"news","time_range":"day","synthesize":false,"spatial_action":null,"spatial_query":null,"radius_meters":null,"use_current_location":false,"reference_place":null,"route_origin":null,"route_destination":null,"route_mode":null}""",
            originalPrompt = "who won the cricket match",
        )!!

        assertEquals(GroundingIntent.SEARCH, route.intent)
        assertEquals(TavilySearchTopic.NEWS, route.tavilyTopic)
        assertEquals(TavilyTimeRange.DAY, route.tavilyTimeRange)
    }

    @Test
    fun spokenRadiusAndNamedBusinessBecomeSpatialSlots() {
        val route = router.parse(
            raw = """{"intent":"SPATIAL","search_query":null,"topic":"general","time_range":null,"synthesize":false,"spatial_action":"nearby","spatial_query":"KFC","radius_meters":3000,"use_current_location":true,"reference_place":null,"route_origin":null,"route_destination":null,"route_mode":null}""",
            originalPrompt = "is there any kfc within three kilometres near me",
        )!!

        assertEquals(GroundingIntent.SPATIAL, route.intent)
        assertEquals(SpatialAction.NEARBY, route.spatialAction)
        assertEquals("KFC", route.spatialQuery)
        assertEquals(3000, route.radiusMeters)
        assertTrue(route.useCurrentLocation)
    }

    @Test
    fun localWeatherCanRequestBothWithoutSendingCoordinatesToSearch() {
        val route = router.parse(
            raw = """{"intent":"BOTH","search_query":"current weather","topic":"general","time_range":"day","synthesize":true,"spatial_action":"location","spatial_query":null,"radius_meters":null,"use_current_location":true,"reference_place":null,"route_origin":null,"route_destination":null,"route_mode":null}""",
            originalPrompt = "what is the weather near me right now",
        )!!

        assertEquals(GroundingIntent.BOTH, route.intent)
        assertEquals(SpatialAction.LOCATION, route.spatialAction)
        assertEquals("current weather", route.searchQuery)
        assertEquals(TavilyTimeRange.DAY, route.tavilyTimeRange)
        assertTrue(route.synthesize)
    }

    @Test
    fun stableKnowledgeCanStayDirect() {
        val route = router.parse(
            raw = """{"intent":"DIRECT","search_query":null,"topic":"general","time_range":null,"synthesize":false,"spatial_action":null,"spatial_query":null,"radius_meters":null,"use_current_location":false,"reference_place":null,"route_origin":null,"route_destination":null,"route_mode":null}""",
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
                """{"intent":"SPATIAL","spatial_action":"nearby","spatial_query":null}""",
                "find something near me",
            ),
        )
    }
}

package com.ad_glasses.ai.grounding

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransportRouterContractTest {
    private val router = GroundingIntentRouter(ApplicationProvider.getApplicationContext())

    @Test
    fun railLiveStatusUsesStructuredTrainNumber() {
        val route = router.parse(
            raw = """{"intent":"SEARCH","external_tool":"rail","action":"live_status","train_number":"12801"}""",
            originalPrompt = "where is train 12801 right now",
        )!!

        assertEquals(ExternalTool.RAIL, route.externalTool)
        assertEquals(ExternalAction.LIVE_STATUS, route.externalAction)
        assertEquals("12801", route.trainNumber)
        assertNull(route.searchQuery)
    }

    @Test
    fun railPnrStatusRequiresTenDigitPnr() {
        val route = router.parse(
            raw = """{"intent":"SEARCH","external_tool":"rail","action":"pnr_status","pnr_number":"1234567890"}""",
            originalPrompt = "check pnr 1234567890",
        )!!

        assertEquals(ExternalAction.PNR_STATUS, route.externalAction)
        assertEquals("1234567890", route.pnrNumber)

        assertNull(
            router.parse(
                raw = """{"intent":"SEARCH","external_tool":"rail","action":"pnr_status","pnr_number":"12345"}""",
                originalPrompt = "check this pnr",
            ),
        )
    }

    @Test
    fun flightStatusNormalizesSpokenFormatting() {
        val route = router.parse(
            raw = """{"intent":"SEARCH","external_tool":"flight","action":"status","flight_number":"AI-202"}""",
            originalPrompt = "is ai 202 delayed",
        )!!

        assertEquals(ExternalTool.FLIGHT, route.externalTool)
        assertEquals(ExternalAction.STATUS, route.externalAction)
        assertEquals("AI202", route.flightNumber)
    }

    @Test
    fun transitRealtimeAcceptsConfiguredFeedSelectorAndRealIds() {
        val route = router.parse(
            raw = """{"intent":"SEARCH","external_tool":"transit","action":"realtime_status","transit_feed":"Delhi Metro","route_id":"Blue","stop_id":"STP_42"}""",
            originalPrompt = "when is the next blue line metro from this stop",
        )!!

        assertEquals(ExternalTool.TRANSIT, route.externalTool)
        assertEquals(ExternalAction.REALTIME_STATUS, route.externalAction)
        assertEquals("Delhi Metro", route.transitFeed)
        assertEquals("Blue", route.transitRouteId)
        assertEquals("STP_42", route.transitStopId)
    }

    @Test
    fun transitNeverNeedsInventedStopIdToFindNearbyVehicles() {
        val route = router.parse(
            raw = """{"intent":"SEARCH","external_tool":"transit","action":"nearby_vehicles"}""",
            originalPrompt = "show realtime buses near me",
        )!!

        assertEquals(ExternalAction.NEARBY_VEHICLES, route.externalAction)
        assertNull(route.transitStopId)
        assertNull(route.transitRouteId)
    }

    @Test
    fun stopSpecificRealtimePlanCanDeferMissingIdentifierWithoutInventingOne() {
        val route = router.parse(
            raw = """{"intent":"SEARCH","external_tool":"transit","action":"realtime_status","needs_context":true}""",
            originalPrompt = "when is the next metro from there",
        )!!

        assertTrue(route.needsContext)
        assertEquals(ExternalTool.TRANSIT, route.externalTool)
        assertNull(route.transitStopId)
        assertNull(route.transitRouteId)
    }

    @Test
    fun physicalMetroStationNearbyRemainsSpatialOsm() {
        val route = router.parse(
            raw = """{"intent":"SPATIAL","spatial_action":"nearby","spatial_query":"metro station","osm_filters":[{"key":"railway","value":"station"}],"use_current_location":true}""",
            originalPrompt = "metro station near me",
        )!!

        assertEquals(GroundingIntent.SPATIAL, route.intent)
        assertEquals(SpatialAction.NEARBY, route.spatialAction)
        assertEquals("metro station", route.spatialQuery)
        assertNull(route.externalAction)
    }

    @Test
    fun transportToolsRejectFieldsOwnedByOtherTransportCapabilities() {
        assertNull(
            router.parse(
                raw = """{"intent":"SEARCH","external_tool":"rail","action":"live_status","train_number":"12801","flight_number":"AI202"}""",
                originalPrompt = "where is 12801",
            ),
        )
        assertNull(
            router.parse(
                raw = """{"intent":"SEARCH","external_tool":"flight","action":"status","flight_number":"AI202","pnr_number":"1234567890"}""",
                originalPrompt = "ai202 status",
            ),
        )
        assertNull(
            router.parse(
                raw = """{"intent":"SEARCH","external_tool":"tavily","action":"status","search_query":"AI202"}""",
                originalPrompt = "search ai202",
            ),
        )
    }

    @Test
    fun executableStructuredTransportPlansRequireTheirActionSpecificSlots() {
        assertNull(
            router.parse(
                raw = """{"intent":"SEARCH","external_tool":"rail","action":"live_status"}""",
                originalPrompt = "where is my train",
            ),
        )
        assertNull(
            router.parse(
                raw = """{"intent":"SEARCH","external_tool":"flight","action":"status"}""",
                originalPrompt = "is my flight delayed",
            ),
        )
        assertNull(
            router.parse(
                raw = """{"intent":"SEARCH","external_tool":"transit","action":"realtime_status"}""",
                originalPrompt = "when is the next metro",
            ),
        )
    }
}

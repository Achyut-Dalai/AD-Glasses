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
class GroundingIntentRouterStrictContractTest {
    private val router = GroundingIntentRouter(ApplicationProvider.getApplicationContext())

    @Test
    fun executableSearchMustProvideItsOwnStandaloneQuery() {
        assertNull(
            router.parse(
                raw = """{"intent":"SEARCH","external_tool":"tavily","topic":"news","time_range":"day"}""",
                originalPrompt = "search the live cricket score",
            ),
        )
    }

    @Test
    fun contextDependentSearchMayDeferOnlyTheMissingQuery() {
        val route = router.parse(
            raw = """{"intent":"SEARCH","external_tool":"tavily","needs_context":true}""",
            originalPrompt = "search it for its current price",
        )!!

        assertEquals(GroundingIntent.SEARCH, route.intent)
        assertEquals(ExternalTool.TAVILY, route.externalTool)
        assertTrue(route.needsContext)
        assertNull(route.searchQuery)
    }

    @Test
    fun blankExternalToolDoesNotSilentlyBecomeTavily() {
        assertNull(
            router.parse(
                raw = """{"intent":"SEARCH","external_tool":" ","search_query":"news today","topic":"news"}""",
                originalPrompt = "news today",
            ),
        )
    }

    @Test
    fun directCannotCarryIgnoredExternalConfiguration() {
        assertNull(
            router.parse(
                raw = """{"intent":"DIRECT","direct_answer":"Here is an answer","search_query":"news today"}""",
                originalPrompt = "news today",
            ),
        )
        assertNull(
            router.parse(
                raw = """{"intent":"DIRECT","direct_answer":"Here is an answer","use_current_location":true}""",
                originalPrompt = "weather near me",
            ),
        )
    }

    @Test
    fun spatialCurrentLocationFieldsRemainValid() {
        val route = router.parse(
            raw = """{"intent":"SPATIAL","spatial_action":"nearby","spatial_query":"KFC","radius_meters":3000,"use_current_location":true}""",
            originalPrompt = "find kfc within three kilometres near me",
        )!!

        assertEquals(GroundingIntent.SPATIAL, route.intent)
        assertEquals(SpatialAction.NEARBY, route.spatialAction)
        assertTrue(route.useCurrentLocation)
    }

    @Test
    fun currentLocationInputIsAllowedForWeatherButNotIgnoredByTavilySearch() {
        val weather = router.parse(
            raw = """{"intent":"SEARCH","external_tool":"weather","weather_horizon":"current","use_current_location":true}""",
            originalPrompt = "weather near me",
        )!!
        assertEquals(ExternalTool.WEATHER, weather.externalTool)

        assertNull(
            router.parse(
                raw = """{"intent":"SEARCH","external_tool":"tavily","search_query":"news near me","use_current_location":true}""",
                originalPrompt = "news near me",
            ),
        )
    }
}

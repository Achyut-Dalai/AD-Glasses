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
class GroundingFastRouteTest {
    private val router = GroundingIntentRouter(ApplicationProvider.getApplicationContext())

    @Test
    fun namedNearbyQueryBypassesModelPlanner() {
        val route = router.fastRoute("find KFC near me")!!

        assertEquals(GroundingIntent.SPATIAL, route.intent)
        assertEquals(SpatialAction.NEARBY, route.spatialAction)
        assertEquals("KFC", route.spatialQuery)
        assertTrue(route.useCurrentLocation)
        assertFalse(route.synthesize)
    }

    @Test
    fun currentSportsScoreBypassesModelPlanner() {
        val route = router.fastRoute("India vs Sri Lanka cricket live score")!!

        assertEquals(GroundingIntent.SEARCH, route.intent)
        assertEquals(ExternalTool.SPORTS, route.externalTool)
        assertEquals("India vs Sri Lanka cricket live score", route.searchQuery)
        assertFalse(route.synthesize)
    }

    @Test
    fun nonSportsScoreRemainsAmbiguous() {
        assertNull(router.fastRoute("what is my credit score"))
        assertNull(router.fastRoute("score this essay"))
    }

    @Test
    fun weatherNearMeUsesWeatherWithoutSpatialExecution() {
        val route = router.fastRoute("what is the weather near me")!!

        assertEquals(GroundingIntent.SEARCH, route.intent)
        assertEquals(ExternalTool.WEATHER, route.externalTool)
        assertNull(route.spatialAction)
        assertTrue(route.useCurrentLocation)
    }

    @Test
    fun currentLocationUsesLocalSpatialFastRoute() {
        val route = router.fastRoute("where am I")!!

        assertEquals(GroundingIntent.SPATIAL, route.intent)
        assertEquals(SpatialAction.LOCATION, route.spatialAction)
        assertTrue(route.useCurrentLocation)
    }
}

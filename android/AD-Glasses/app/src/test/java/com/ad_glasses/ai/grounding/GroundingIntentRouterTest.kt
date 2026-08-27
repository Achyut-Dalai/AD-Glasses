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

        assertEquals(ExternalTool.TAVILY, route.externalTool)
        assertEquals(TavilySearchTopic.NEWS, route.tavilyTopic)
        assertEquals(TavilyTimeRange.DAY, route.tavilyTimeRange)
    }

    @Test
    fun uncheckedWebToggleDoesNotVetoSemanticSearch() {
        val planned = GroundingRoute(
            intent = GroundingIntent.SEARCH,
            externalTool = ExternalTool.TAVILY,
            searchQuery = "India vs Sri Lanka cricket live score",
            tavilyTopic = TavilySearchTopic.NEWS,
            tavilyTimeRange = TavilyTimeRange.DAY,
        )

        val effective = router.applyExplicitWebPreference(
            route = planned,
            prompt = "India vs Sri Lanka cricket score",
            explicitWebRequest = false,
        )

        assertEquals(planned, effective)
        assertEquals(GroundingIntent.SEARCH, effective.intent)
    }

    @Test
    fun explicitWebToggleCanStillForceAStableDirectTurnToTavily() {
        val planned = GroundingRoute(
            intent = GroundingIntent.DIRECT,
            directAnswer = "A stable answer.",
        )

        val effective = router.applyExplicitWebPreference(
            route = planned,
            prompt = "verify this on the web",
            explicitWebRequest = true,
        )

        assertEquals(GroundingIntent.SEARCH, effective.intent)
        assertEquals(ExternalTool.TAVILY, effective.externalTool)
        assertEquals("verify this on the web", effective.searchQuery)
        assertNull(effective.directAnswer)
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
    fun weatherCannotMasqueradeAsSpatialNearbyLookup() {
        assertNull(
            router.parse(
                raw = """{"intent":"SPATIAL","external_tool":"weather","spatial_action":"nearby","spatial_query":"weather","radius_meters":1000,"use_current_location":true}""",
                originalPrompt = "what is the weather near me",
            ),
        )
    }

    @Test
    fun searchPlanCannotCarrySpatialExecutionFields() {
        assertNull(
            router.parse(
                raw = """{"intent":"SEARCH","external_tool":"weather","spatial_action":"nearby","spatial_query":"weather","use_current_location":true}""",
                originalPrompt = "what is the weather near me",
            ),
        )
    }

    @Test
    fun specializedKnowledgeToolsHaveValidatedSlots() {
        val wiki = router.parse(
            raw = """{"intent":"SEARCH","external_tool":"wikipedia","search_query":"James Webb Space Telescope"}""",
            originalPrompt = "tell me about james webb",
        )!!
        assertEquals(ExternalTool.WIKIPEDIA, wiki.externalTool)

        val dictionary = router.parse(
            raw = """{"intent":"SEARCH","external_tool":"dictionary","search_query":"ubiquitous"}""",
            originalPrompt = "define ubiquitous",
        )!!
        assertEquals(ExternalTool.DICTIONARY, dictionary.externalTool)

        val books = router.parse(
            raw = """{"intent":"SEARCH","external_tool":"books","search_query":"Dune Frank Herbert"}""",
            originalPrompt = "who wrote dune",
        )!!
        assertEquals(ExternalTool.BOOKS, books.externalTool)

        val currency = router.parse(
            raw = """{"intent":"SEARCH","external_tool":"currency","amount":50,"base_currency":"USD","quote_currency":"INR"}""",
            originalPrompt = "convert 50 dollars to rupees",
        )!!
        assertEquals(ExternalTool.CURRENCY, currency.externalTool)
        assertEquals(50.0, currency.currencyAmount!!, 0.0)
        assertEquals("USD", currency.baseCurrency)
        assertEquals("INR", currency.quoteCurrency)

        val translation = router.parse(
            raw = """{"intent":"SEARCH","external_tool":"translation","translation_text":"good morning","source_language":"auto","target_language":"hi"}""",
            originalPrompt = "translate good morning to hindi",
        )!!
        assertEquals(ExternalTool.TRANSLATION, translation.externalTool)
        assertEquals("good morning", translation.translationText)
        assertEquals("auto", translation.sourceLanguage)
        assertEquals("hi", translation.targetLanguage)
    }

    @Test
    fun bothCanFindNearbyPlacesThenAskTavilyToEnrichThoseCandidates() {
        val route = router.parse(
            raw = """{"intent":"BOTH","external_tool":"tavily","search_query":"KFC official menu prices and opening information","topic":"general","synthesize":true,"spatial_action":"nearby","spatial_query":"KFC","osm_filters":[{"key":"brand","value":"KFC"}],"radius_meters":3000,"use_current_location":true}""",
            originalPrompt = "find kfc within three kilometres and check their websites for menu prices",
        )!!

        assertEquals(GroundingIntent.BOTH, route.intent)
        assertEquals(ExternalTool.TAVILY, route.externalTool)
        assertEquals(SpatialAction.NEARBY, route.spatialAction)
        assertEquals("KFC", route.spatialQuery)
        assertEquals(listOf(OverpassTagFilter("brand", "KFC")), route.osmFilters)
        assertTrue(route.synthesize)
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
    fun standaloneStableKnowledgeUsesRouterAnswerWithoutSecondCall() {
        val route = router.parse(
            raw = """{"intent":"DIRECT","direct_answer":"Recursion is when a function solves a problem by calling itself on a smaller version of that problem.","needs_context":false}""",
            originalPrompt = "explain recursion",
        )!!

        assertEquals(GroundingIntent.DIRECT, route.intent)
        assertTrue(route.directAnswer!!.contains("calling itself"))
        assertFalse(route.needsContext)
        assertNull(route.searchQuery)
    }

    @Test
    fun unresolvedReferencesAreDeferredWithoutFabricatingToolSlots() {
        val route = router.parse(
            raw = """{"intent":"SEARCH","external_tool":"tavily","needs_context":true}""",
            originalPrompt = "search it for the current price",
        )!!

        assertEquals(GroundingIntent.SEARCH, route.intent)
        assertTrue(route.needsContext)
        assertNull(route.searchQuery)
    }

    @Test
    fun directWithoutAnswerIsInvalidUnlessItNeedsContext() {
        assertNull(router.parse("""{"intent":"DIRECT"}""", "explain recursion"))
        val deferred = router.parse(
            """{"intent":"DIRECT","needs_context":true}""",
            "explain that again",
        )
        assertTrue(deferred != null && deferred.needsContext)
    }

    @Test
    fun malformedOrIncompleteExecutablePlansAreRejected() {
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
        assertNull(
            router.parse(
                """{"intent":"SEARCH","external_tool":"currency","amount":50,"base_currency":"US","quote_currency":"INR"}""",
                "convert 50 dollars to rupees",
            ),
        )
        assertNull(
            router.parse(
                """{"intent":"SEARCH","external_tool":"made_up_tool","search_query":"anything"}""",
                "search anything",
            ),
        )
    }
}

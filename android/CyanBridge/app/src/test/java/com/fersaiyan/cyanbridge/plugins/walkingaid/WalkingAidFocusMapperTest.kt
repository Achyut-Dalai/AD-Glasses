package com.fersaiyan.cyanbridge.plugins.walkingaid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkingAidFocusMapperTest {

    @Test
    fun resolvesCasualDescriptionToSupportedObjects() {
        val labels = WalkingAidFocusMapper.resolve(
            "Please warn me about pets, traffic, and things I could trip over",
        )

        assertTrue(labels.containsAll(listOf("cat", "dog", "car", "bus", "backpack", "suitcase")))
    }

    @Test
    fun acceptsPluralEverydayObjectNames() {
        assertEquals(listOf("cat", "dog"), WalkingAidFocusMapper.resolve("dogs and cats"))
    }

    @Test
    fun unknownWordsDoNotCreateFakeDetectorLabels() {
        val matches = WalkingAidFocusMapper.resolve("low branches and open doors")
        assertTrue("Unexpected matches: $matches", matches.isEmpty())
    }

    @Test
    fun fuzzyMatchesTyposOnlyAgainstLabelsDetectedInFrame() {
        val matches = WalkingAidFocusMapper.matchDetectedLabels(
            text = "Warn me about bicylces and busses",
            detectedLabels = listOf("bicycle", "bus", "dog"),
        )

        assertEquals(listOf("bicycle", "bus"), matches)
    }

    @Test
    fun casualCategoryIsLimitedToObjectsDetectedInFrame() {
        val matches = WalkingAidFocusMapper.matchDetectedLabels(
            text = "Pay attention to traffic",
            detectedLabels = listOf("person", "car", "truck"),
        )

        assertEquals(listOf("car", "truck"), matches)
    }

    @Test
    fun similarSubstringDoesNotMatchShortClassName() {
        assertTrue(
            WalkingAidFocusMapper.matchDetectedLabels(
                text = "catch anything unusual",
                detectedLabels = listOf("cat"),
            ).isEmpty(),
        )
    }
}

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
        assertTrue(WalkingAidFocusMapper.resolve("low branches and open doors").isEmpty())
    }
}

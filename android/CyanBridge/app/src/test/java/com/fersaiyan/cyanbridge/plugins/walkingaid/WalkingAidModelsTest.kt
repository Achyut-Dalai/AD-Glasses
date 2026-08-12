package com.achyut.adglasses.plugins.walkingaid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WalkingAidModelsTest {

    @Test
    fun sceneRecordCreation() {
        val record = SceneRecord(
            timestampMs = 1000L,
            imagePath = "/test/image.jpg",
            description = "A person walking on a sidewalk",
            depthDescription = "Near: person 2m ahead",
            stateDecision = StateDecision.WARN,
        )

        assertEquals(1000L, record.timestampMs)
        assertEquals("/test/image.jpg", record.imagePath)
        assertEquals("A person walking on a sidewalk", record.description)
        assertEquals("Near: person 2m ahead", record.depthDescription)
        assertEquals(StateDecision.WARN, record.stateDecision)
    }

    @Test
    fun sceneRecordWithNullDepth() {
        val record = SceneRecord(
            timestampMs = 2000L,
            imagePath = "/test/another.jpg",
            description = "Empty street",
            depthDescription = null,
            stateDecision = StateDecision.SKIP,
        )

        assertEquals(2000L, record.timestampMs)
        assertEquals("Empty street", record.description)
        assertNull(record.depthDescription)
        assertEquals(StateDecision.SKIP, record.stateDecision)
    }

    @Test
    fun stateDecisionEnumValues() {
        val values = StateDecision.entries
        assertEquals(3, values.size)
        assertEquals(StateDecision.WARN, StateDecision.valueOf("WARN"))
        assertEquals(StateDecision.DESCRIBE, StateDecision.valueOf("DESCRIBE"))
        assertEquals(StateDecision.SKIP, StateDecision.valueOf("SKIP"))
    }

    @Test
    fun stateModelOutputCreation() {
        val output = StateModelOutput(
            decision = StateDecision.WARN,
            message = "Obstacle ahead on the left",
        )

        assertEquals(StateDecision.WARN, output.decision)
        assertEquals("Obstacle ahead on the left", output.message)
    }

    @Test
    fun walkingAidImageEntryCreation() {
        val entry = WalkingAidImageEntry(
            timestampMs = 3000L,
            imagePath = "/test/entry.jpg",
            description = "A busy intersection",
        )

        assertEquals(3000L, entry.timestampMs)
        assertEquals("/test/entry.jpg", entry.imagePath)
        assertEquals("A busy intersection", entry.description)
    }
}

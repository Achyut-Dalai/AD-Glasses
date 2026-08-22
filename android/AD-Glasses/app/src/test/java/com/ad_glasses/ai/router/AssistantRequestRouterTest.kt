package com.ad_glasses.ai.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssistantRequestRouterTest {
    private val router = AssistantRequestRouter()

    @Test
    fun `imperative phone request is not classified as UI automation`() {
        val decision = router.classifyHeuristically(
            AssistantRequest("Open Spotify and play my liked songs", AssistantRequestSource.GLASSES_VOICE)
        )
        assertNull(decision)
    }

    @Test
    fun `courteous phone command is not classified as UI automation`() {
        val decision = router.classifyHeuristically(
            AssistantRequest("Can you open Settings for me?", AssistantRequestSource.GLASSES_VOICE)
        )
        assertNull(decision)
    }

    @Test
    fun `read app request is not classified as UI automation`() {
        val decision = router.classifyHeuristically(
            AssistantRequest("Read my WhatsApp messages", AssistantRequestSource.GLASSES_VOICE)
        )
        assertNull(decision)
    }

    @Test
    fun `informational how question stays a normal question`() {
        val decision = router.classifyHeuristically(
            AssistantRequest("How do I open Bluetooth settings?", AssistantRequestSource.GLASSES_VOICE)
        )
        assertEquals(AssistantIntent.ANSWER_QUESTION, decision?.intent)
    }

    @Test
    fun `visual question requests image analysis`() {
        val decision = router.classifyHeuristically(
            AssistantRequest("What am I looking at?", AssistantRequestSource.GLASSES_VOICE)
        )
        assertEquals(AssistantIntent.ANALYZE_IMAGE, decision?.intent)
    }

    @Test
    fun `ambiguous request defers to normal answer path`() {
        val decision = router.classifyHeuristically(
            AssistantRequest("I need some help with Spotify", AssistantRequestSource.GLASSES_VOICE)
        )
        assertNull(decision)
    }
}

package com.adglasses.app.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRequestRouterTest {
    @Test
    fun `photo command stays local`() {
        assertEquals(
            AssistantRoute.CapturePhoto,
            AssistantRequestRouter.route("Take a photo"),
        )
    }

    @Test
    fun `explicit negation never executes local hardware`() {
        assertEquals(
            AssistantRoute.Conversation,
            AssistantRequestRouter.route("Do not take a photo"),
        )
        assertEquals(
            AssistantRoute.Conversation,
            AssistantRequestRouter.route("Don't call Mom"),
        )
        assertEquals(
            AssistantRoute.Conversation,
            AssistantRequestRouter.route("Never start video"),
        )
    }

    @Test
    fun `meta question stays conversational`() {
        assertEquals(
            AssistantRoute.Conversation,
            AssistantRequestRouter.route("How do I start video recording?"),
        )
        assertEquals(
            AssistantRoute.Conversation,
            AssistantRequestRouter.route("How do I take a photo with the glasses?"),
        )
    }

    @Test
    fun `video and audio lifecycle commands match iOS vocabulary`() {
        assertEquals(AssistantRoute.StartVideo, AssistantRequestRouter.route("Record a video"))
        assertEquals(AssistantRoute.StopVideo, AssistantRequestRouter.route("Stop recording video"))
        assertEquals(AssistantRoute.StartAudio, AssistantRequestRouter.route("Start audio recording"))
        assertEquals(AssistantRoute.StopAudio, AssistantRequestRouter.route("Finish audio recording"))
    }

    @Test
    fun `notification summary is Android local capability`() {
        assertEquals(
            AssistantRoute.Notifications,
            AssistantRequestRouter.route("What did I miss?"),
        )
        assertEquals(
            AssistantRoute.Notifications,
            AssistantRequestRouter.route("Read my notifications"),
        )
    }

    @Test
    fun `notification reply can target a conversation`() {
        assertEquals(
            AssistantRoute.ReplyNotification("Alice", "I am on my way"),
            AssistantRequestRouter.route("Reply to Alice saying I am on my way"),
        )
        assertEquals(
            AssistantRoute.ReplyNotification("Riya", "yes I can"),
            AssistantRequestRouter.route("Reply to Riya: yes I can"),
        )
    }

    @Test
    fun `short reply targets the latest replyable notification`() {
        assertEquals(
            AssistantRoute.ReplyNotification(null, "thanks"),
            AssistantRequestRouter.route("Reply thanks"),
        )
        assertEquals(
            AssistantRoute.ReplyNotification(null, "sounds good"),
            AssistantRequestRouter.route("Reply saying sounds good"),
        )
    }

    @Test
    fun `negated reply never fires a notification action`() {
        assertEquals(
            AssistantRoute.Conversation,
            AssistantRequestRouter.route("Do not reply saying yes"),
        )
    }

    @Test
    fun `phone call matcher rejects conversational idioms`() {
        assertEquals(
            AssistantRoute.Conversation,
            AssistantRequestRouter.route("Call it a day"),
        )
        assertEquals(
            AssistantRoute.PhoneCall("Mom"),
            AssistantRequestRouter.route("Call Mom"),
        )
    }

    @Test
    fun `explicit SMS extracts recipient and body`() {
        val route = AssistantRequestRouter.route("Text Mom saying I am running late")
        assertTrue(route is AssistantRoute.SendSms)
        route as AssistantRoute.SendSms
        assertEquals("Mom", route.recipient)
        assertEquals("I am running late", route.body)

        assertEquals(
            AssistantRoute.SendSms("Alex", "I am on my way"),
            AssistantRequestRouter.route("Send a message to Alex: I am on my way"),
        )
    }

    @Test
    fun `ordinary language is never promoted to an action`() {
        assertEquals(
            AssistantRoute.Conversation,
            AssistantRequestRouter.route("Tell me about photography"),
        )
        assertEquals(
            AssistantRoute.Conversation,
            AssistantRequestRouter.route("What is the weather like on Mars?"),
        )
    }
}

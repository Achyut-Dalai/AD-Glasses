package com.fersaiyan.cyanbridge.ai.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageAutomationTargetTest {
    @Test
    fun defaultGeminiUsesGeminiImageProfile() {
        assertEquals(
            ImageAutomationTarget.GEMINI,
            ImageAutomationTarget.forDefaultAssistant(ExternalImageAutomationIntents.GEMINI_PACKAGE),
        )
    }

    @Test
    fun phoneDefaultGeminiUsesGeminiImageProfile() {
        assertEquals(
            ImageAutomationTarget.GEMINI,
            ImageAutomationTarget.forDefaultAssistant(ExternalImageAutomationIntents.GEMINI_ALTERNATE_PACKAGE),
        )
    }

    @Test
    fun defaultChatGptSupportsDirectAutomation() {
        val target = ImageAutomationTarget.forDefaultAssistant(ExternalImageAutomationIntents.CHATGPT_PACKAGE)

        assertEquals(ImageAutomationTarget.CHATGPT, target)
        assertTrue(target.imageAutomationSupported)
    }

    @Test
    fun otherPhoneDefaultIsVoiceOnlyForImages() {
        val target = ImageAutomationTarget.forDefaultAssistant("com.example.assistant")

        assertEquals(ImageAutomationTarget.NONE, target)
        assertFalse(target.imageAutomationSupported)
        assertTrue(target.packageNames.isEmpty())
    }
}

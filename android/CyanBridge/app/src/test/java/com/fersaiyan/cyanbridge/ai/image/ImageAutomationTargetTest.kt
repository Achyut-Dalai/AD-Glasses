package com.fersaiyan.cyanbridge.ai.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageAutomationTargetTest {
    @Test
    fun explicitGeminiUsesGeminiImageProfile() {
        assertEquals(
            ImageAutomationTarget.GEMINI,
            ImageAutomationTarget.forAssistantMode("Gemini", defaultAssistantPackage = null),
        )
    }

    @Test
    fun phoneDefaultGeminiUsesGeminiImageProfile() {
        assertEquals(
            ImageAutomationTarget.GEMINI,
            ImageAutomationTarget.forAssistantMode(
                assistantMode = "PhoneDefault",
                defaultAssistantPackage = ExternalImageAutomationIntents.GEMINI_PACKAGE,
            ),
        )
    }

    @Test
    fun chatGptHasItsOwnUnsupportedImageBranch() {
        val target = ImageAutomationTarget.forAssistantMode(
            assistantMode = "ChatGPT",
            defaultAssistantPackage = ExternalImageAutomationIntents.GEMINI_PACKAGE,
        )

        assertEquals(ImageAutomationTarget.CHATGPT, target)
        assertFalse(target.imageAutomationSupported)
        assertFalse(TaskerImageProfileCompatibility.supports(target, "chatgpt", "chatgpt-v1"))
    }

    @Test
    fun otherPhoneDefaultIsVoiceOnlyForImages() {
        val target = ImageAutomationTarget.forAssistantMode(
            assistantMode = "PhoneDefault",
            defaultAssistantPackage = "com.example.assistant",
        )

        assertEquals(ImageAutomationTarget.NONE, target)
        assertFalse(target.imageAutomationSupported)
        assertTrue(target.packageNames.isEmpty())
    }
}

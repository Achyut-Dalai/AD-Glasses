package com.fersaiyan.cyanbridge.ai.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalAssistantAutomationPolicyTest {
    @Test
    fun readyGeminiCapabilitySupportsVoiceAndImages() {
        val capability = readyCapability(ImageAutomationTarget.GEMINI)

        assertNull(ExternalAssistantAutomationPolicy.voiceBlockingReason(capability))
        assertNull(ExternalAssistantAutomationPolicy.imageBlockingReason(capability))
    }

    @Test
    fun readyChatGptCapabilitySupportsVoiceAndImages() {
        val capability = readyCapability(ImageAutomationTarget.CHATGPT)

        assertNull(ExternalAssistantAutomationPolicy.voiceBlockingReason(capability))
        assertNull(ExternalAssistantAutomationPolicy.imageBlockingReason(capability))
    }

    @Test
    fun unsupportedDefaultAssistantIsRejectedForImages() {
        val capability = readyCapability(ImageAutomationTarget.NONE).copy(targetPackage = null)

        assertEquals(
            "Set Gemini or ChatGPT as your phone's default assistant first.",
            ExternalAssistantAutomationPolicy.imageBlockingReason(capability),
        )
    }

    @Test
    fun lockedPhoneStillAllowsVoiceHandoff() {
        val capability = readyCapability(ImageAutomationTarget.GEMINI).copy(phoneLocked = true)

        assertNull(ExternalAssistantAutomationPolicy.voiceBlockingReason(capability))
        assertEquals(
            "Unlock your phone before using external image automation.",
            ExternalAssistantAutomationPolicy.imageBlockingReason(capability),
        )
    }

    @Test
    fun imageUsesAdAccessibility() {
        val capability = readyCapability(ImageAutomationTarget.GEMINI)

        assertNull(ExternalAssistantAutomationPolicy.voiceBlockingReason(capability))
        assertNull(ExternalAssistantAutomationPolicy.imageBlockingReason(capability))
    }

    private fun readyCapability(target: ImageAutomationTarget) = ExternalAssistantAutomationCapability(
        target = target,
        targetPackage = target.packageNames.firstOrNull(),
        adAccessibilityConnected = true,
        imageShareAvailable = true,
        phoneLocked = false,
    )
}

package com.fersaiyan.cyanbridge.ai.vision

import org.junit.Assert.assertTrue
import org.junit.Test

class VisionPromptBuilderTest {

    @Test
    fun walkingProfileRequestsRussianConciseSafetyAwareOutput() {
        val prompt = VisionPromptBuilder.build(
            settings = VisionProfileSettings(
                profile = VisionProfile.WALKING,
                responseLanguageTag = "ru-RU",
                customInstructions = "Mention benches and curb edges first.",
            ),
            userQuestion = "What is directly ahead?",
        )

        assertTrue(prompt.contains("Respond only in Russian (ru-RU)."))
        assertTrue(prompt.contains("at most 18 words"))
        assertTrue(prompt.contains("Do not guess or claim that a route is safe."))
        assertTrue(prompt.contains("Mention benches and curb edges first."))
        assertTrue(prompt.contains("User question: What is directly ahead?"))
    }

    @Test
    fun detailedProfileAllowsAUsefulLongerDescription() {
        val prompt = VisionPromptBuilder.build(
            settings = VisionProfileSettings(
                profile = VisionProfile.DETAILED,
                responseLanguageTag = "pt-BR",
            ),
            userQuestion = null,
        )

        assertTrue(prompt.contains("Respond only in Portuguese (pt-BR)."))
        assertTrue(prompt.contains("at most 90 words"))
    }
}

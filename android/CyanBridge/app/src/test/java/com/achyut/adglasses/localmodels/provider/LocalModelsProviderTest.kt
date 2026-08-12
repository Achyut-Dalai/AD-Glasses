package com.achyut.adglasses.localmodels.provider

import com.achyut.adglasses.localmodels.templates.PromptMessage
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelsProviderTest {

    @Test
    fun multimodalPromptKeepsConfiguredAndRuntimeSystemInstructions() {
        val prompt = buildMultimodalPrompt(
            configuredSystemPrompt = "Always answer in Russian.",
            messages = listOf(
                PromptMessage("System", "Keep answers brief."),
                PromptMessage("User", "Describe this image."),
            ),
        )

        assertTrue(prompt.contains("Always answer in Russian."))
        assertTrue(prompt.contains("Keep answers brief."))
        assertTrue(prompt.contains("User request: Describe this image."))
    }
}

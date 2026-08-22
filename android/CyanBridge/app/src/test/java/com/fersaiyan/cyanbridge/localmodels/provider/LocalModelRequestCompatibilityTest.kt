package com.fersaiyan.cyanbridge.localmodels.provider

import com.fersaiyan.cyanbridge.localmodels.settings.LocalModelRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalModelRequestCompatibilityTest {

    @Test
    fun qwenGgufIsAcceptedForTextOnlyVoiceRequest() {
        assertNull(
            localModelRequestCompatibilityIssue(
                modelRuntime = LocalModelRuntime.LLAMA_CPP,
                modelDescriptor = "Qwen2.5 0.5B qwen2.5-0.5b-instruct-q4_k_m.gguf",
                imageRequested = false,
            ),
        )
    }

    @Test
    fun qwenGgufIsRejectedForImageRequest() {
        assertEquals(
            "Image questions require Local Runtime = LiteRT for the selected model.",
            localModelRequestCompatibilityIssue(
                modelRuntime = LocalModelRuntime.LLAMA_CPP,
                modelDescriptor = "Qwen2.5 0.5B qwen2.5-0.5b-instruct-q4_k_m.gguf",
                imageRequested = true,
            ),
        )
    }

    @Test
    fun gemma4LiteRtIsAcceptedForImageRequest() {
        assertNull(
            localModelRequestCompatibilityIssue(
                modelRuntime = LocalModelRuntime.LITERT,
                modelDescriptor = "Gemma 4 E2B gemma-4-e2b-it-int4.litertlm",
                imageRequested = true,
            ),
        )
    }

    @Test
    fun nonGemmaLiteRtIsRejectedForImageRequest() {
        assertEquals(
            "Select a Gemma LiteRT model for local image questions.",
            localModelRequestCompatibilityIssue(
                modelRuntime = LocalModelRuntime.LITERT,
                modelDescriptor = "Some vision model.litertlm",
                imageRequested = true,
            ),
        )
    }
}

package com.ad_glasses.localmodels.provider

import com.ad_glasses.localmodels.settings.LocalModelRuntime
import java.util.Locale

/**
 * Validates only capabilities required by the request.
 *
 * Text-only voice requests work with either llama.cpp/GGUF models (for example Qwen) or
 * compatible LiteRT models. Image requests intentionally remain restricted to the app's
 * currently supported Gemma 4 LiteRT media path.
 */
internal fun localModelRequestCompatibilityIssue(
    modelRuntime: LocalModelRuntime,
    modelDescriptor: String,
    imageRequested: Boolean,
): String? {
    if (!imageRequested) return null

    if (modelRuntime != LocalModelRuntime.LITERT) {
        return "Image questions require Local Runtime = LiteRT for the selected model."
    }

    val normalizedDescriptor = modelDescriptor.lowercase(Locale.US)
    if (!normalizedDescriptor.contains("gemma")) {
        return "Select a Gemma LiteRT model for local image questions."
    }

    if (!normalizedDescriptor.contains("gemma-4") && !normalizedDescriptor.contains("gemma4")) {
        return "Image questions on glasses are configured for Gemma 4 LiteRT. Please select Gemma 4 E2B/E4B."
    }

    return null
}

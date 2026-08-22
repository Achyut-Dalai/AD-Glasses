package com.fersaiyan.cyanbridge.ai.image

/**
 * The app selected for a voice request is not necessarily safe to automate for image requests.
 * Keep the image target and its profile contract explicit so UI selectors cannot drift to a
 * different assistant selected by Android.
 */
enum class ImageAutomationTarget(
    val wireName: String,
    val label: String,
    val packageNames: List<String>,
    val imageAutomationSupported: Boolean,
) {
    GEMINI(
        wireName = "gemini",
        label = "Gemini",
        packageNames = listOf(
            ExternalImageAutomationIntents.GEMINI_PACKAGE,
            ExternalImageAutomationIntents.GEMINI_ALTERNATE_PACKAGE,
        ),
        imageAutomationSupported = true,
    ),
    CHATGPT(
        wireName = "chatgpt",
        label = "ChatGPT",
        packageNames = listOf(ExternalImageAutomationIntents.CHATGPT_PACKAGE),
        imageAutomationSupported = true,
    ),
    NONE(
        wireName = "none",
        label = "Voice only",
        packageNames = emptyList(),
        imageAutomationSupported = false,
    ),
    ;

    companion object {
        fun forDefaultAssistant(defaultAssistantPackage: String?): ImageAutomationTarget = when {
            defaultAssistantPackage in GEMINI.packageNames -> GEMINI
            defaultAssistantPackage in CHATGPT.packageNames -> CHATGPT
            else -> NONE
        }
    }
}

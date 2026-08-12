package com.achyut.adglasses.ai.image

/**
 * The app selected for a voice request is not necessarily safe to automate for image requests.
 * Keep the image target and its profile contract explicit so UI selectors cannot drift to a
 * different assistant selected by Android.
 */
enum class ImageAutomationTarget(
    val wireName: String,
    val label: String,
    val packageNames: List<String>,
    val requiredProfileVersion: String?,
    val imageAutomationSupported: Boolean,
) {
    GEMINI(
        wireName = "gemini",
        label = "Gemini",
        packageNames = listOf(
            ExternalImageAutomationIntents.GEMINI_PACKAGE,
            ExternalImageAutomationIntents.GEMINI_ALTERNATE_PACKAGE,
        ),
        requiredProfileVersion = TaskerImageProfileCompatibility.GEMINI_PROFILE_VERSION,
        imageAutomationSupported = true,
    ),
    CHATGPT(
        wireName = "chatgpt",
        label = "ChatGPT",
        packageNames = listOf(ExternalImageAutomationIntents.CHATGPT_PACKAGE),
        requiredProfileVersion = TaskerImageProfileCompatibility.CHATGPT_PROFILE_VERSION,
        imageAutomationSupported = true,
    ),
    NONE(
        wireName = "none",
        label = "Voice only",
        packageNames = emptyList(),
        requiredProfileVersion = null,
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

object TaskerImageProfileCompatibility {
    const val GEMINI_PROFILE_VERSION = "gemini-v3"
    const val CHATGPT_PROFILE_VERSION = "chatgpt-v1"

    fun supports(target: ImageAutomationTarget, importedTarget: String?, importedVersion: String?): Boolean {
        return target.imageAutomationSupported &&
            importedTarget == target.wireName &&
            importedVersion == target.requiredProfileVersion
    }
}

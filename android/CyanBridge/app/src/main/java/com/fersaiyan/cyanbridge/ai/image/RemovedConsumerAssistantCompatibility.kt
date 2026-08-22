package com.fersaiyan.cyanbridge.ai.image

import android.content.Context

/**
 * Source-compatibility boundary for inherited MainActivity code while its device runtime remains in
 * place. Consumer assistant integration is intentionally unavailable: no package is resolved, no
 * share target is exposed, and no accessibility automation is executed.
 */
@Deprecated("Consumer assistant app integration is removed")
enum class ImageAutomationTarget(
    val wireName: String,
    val label: String,
    val packageNames: List<String>,
    val imageAutomationSupported: Boolean,
) {
    GEMINI("removed_gemini", "Removed assistant integration", emptyList(), false),
    CHATGPT("removed_chatgpt", "Removed assistant integration", emptyList(), false),
    NONE("none", "Unavailable", emptyList(), false),
    ;

    companion object {
        fun forDefaultAssistant(defaultAssistantPackage: String?): ImageAutomationTarget = NONE
    }
}

/** Legacy callback token retained only so inherited MainActivity dead code still compiles. */
@Deprecated("Consumer assistant app integration is removed")
enum class ExternalImageAutomationStage {
    IMAGE_STARTED,
    IMAGE_ATTACHED,
    PROMPT_SENT,
    ANSWER_READY,
    FAILED,
}

@Deprecated("Consumer assistant app integration is removed")
object DefaultAssistantResolver {
    fun packageName(context: Context): String? = null
}

@Deprecated("Consumer assistant app integration is removed")
data class ExternalAssistantAutomationCapability(
    val target: ImageAutomationTarget = ImageAutomationTarget.NONE,
    val targetPackage: String? = null,
    val adAccessibilityConnected: Boolean = false,
    val imageShareAvailable: Boolean = false,
    val phoneLocked: Boolean = false,
)

@Deprecated("Consumer assistant app integration is removed")
object ExternalAssistantAutomationInspector {
    fun inspect(context: Context): ExternalAssistantAutomationCapability =
        ExternalAssistantAutomationCapability()
}

@Deprecated("Consumer assistant app integration is removed")
object ExternalAssistantAutomationPolicy {
    private const val REMOVED = "Consumer assistant app integration has been removed. Use Cloud AI or Local AI."

    fun voiceBlockingReason(capability: ExternalAssistantAutomationCapability): String? = REMOVED
    fun imageBlockingReason(capability: ExternalAssistantAutomationCapability): String? = REMOVED
}

@Deprecated("Consumer assistant app integration is removed")
object ExternalAssistantAccessibilityAutomation {
    suspend fun fillAndSend(
        target: ImageAutomationTarget,
        targetPackage: String,
        question: String,
        timeoutMs: Long = 0L,
    ): Result<Unit> = Result.failure(
        UnsupportedOperationException("Consumer assistant app integration has been removed"),
    )
}

@Deprecated("Consumer assistant app integration is removed")
object ExternalImageAutomationStore {
    fun begin(
        context: Context,
        imagePath: String,
        imageUri: String,
        question: String,
        source: ImageQuestionSource,
    ) = Unit

    fun recordLocalStage(
        context: Context,
        stage: ExternalImageAutomationStage,
        error: String? = null,
    ) = Unit
}

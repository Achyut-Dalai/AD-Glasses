package com.ad_glasses.ai.router

/** Explicit generation intent; never infer product behavior from a token count or model name alone. */
enum class CloudGenerationMode {
    DEFAULT,
    CONCISE_CONVERSATION,
    REASONED_CONVERSATION,
}

/**
 * Provider-aware request-shape policy with provider-neutral product budgets.
 *
 * Product intent decides how much generation room a turn gets. The selected provider or model must
 * never silently make the product more or less capable by changing that ceiling. Provider/model
 * checks below exist only to map the same intent onto supported wire fields and reasoning controls.
 */
internal object CloudModelPolicy {
    /**
     * Normal conversation asks for a compact final answer, but 512 tokens leaves room for light
     * reasoning on APIs where reasoning shares the completion allowance. This is a ceiling, not a
     * target; spoken output is independently bounded by the app.
     */
    const val CONCISE_OUTPUT_TOKENS = 512

    /** Explicit deep-reasoning turns may intentionally spend more generation budget. */
    const val REASONED_OUTPUT_TOKENS = 2_048

    /** Generic/internal callers that did not select a conversation mode. */
    const val DEFAULT_OUTPUT_TOKENS = 512

    internal data class RequestTuning(
        /** OpenAI-compatible token field. Native Gemini uses maxOutputTokens separately. */
        val completionTokenField: String = "max_tokens",
        /** OpenAI/Groq/DeepSeek Chat Completions reasoning control. */
        val reasoningEffort: String? = null,
        /** Groq Qwen reasoning presentation control. */
        val reasoningFormat: String? = null,
        /** Groq GPT-OSS reasoning visibility control; mutually exclusive with reasoningFormat. */
        val includeReasoning: Boolean? = null,
        /** OpenRouter normalized reasoning control. */
        val openRouterReasoningEffort: String? = null,
        val excludeReasoning: Boolean = false,
        /** DeepSeek thinking switch; reasoning content remains separate from visible content. */
        val deepSeekThinkingType: String? = null,
        /** Retained for transport compatibility; AD no longer forces provider-specific verbosity. */
        val responseVerbosity: String? = null,
        /** Native Gemini 3.x thinking control. */
        val geminiThinkingLevel: String? = null,
        /** Native Gemini 2.5 thinking control. */
        val geminiThinkingBudget: Int? = null,
    )

    /** Product budget depends only on requested generation mode, never provider/model identity. */
    fun generationTokenLimit(mode: CloudGenerationMode): Int = when (mode) {
        CloudGenerationMode.DEFAULT -> DEFAULT_OUTPUT_TOKENS
        CloudGenerationMode.CONCISE_CONVERSATION -> CONCISE_OUTPUT_TOKENS
        CloudGenerationMode.REASONED_CONVERSATION -> REASONED_OUTPUT_TOKENS
    }

    /**
     * Map one provider-neutral product intent onto each API's supported wire controls.
     *
     * Normal conversation uses light reasoning where the selected model exposes a safe control.
     * Explicit reasoned turns raise that control. Structured reasoning may be requested only as a
     * content-free liveness heartbeat for wearable streaming; transport parsers still discard it
     * before persistence, display, or TTS.
     */
    fun requestTuning(
        profile: CloudAiProfile,
        mode: CloudGenerationMode,
        includeReasoningActivity: Boolean = false,
    ): RequestTuning {
        val model = normalizedModel(profile.model)
        val tokenField = when (profile.provider) {
            ApiProvider.OPENAI, ApiProvider.GROQ -> "max_completion_tokens"
            else -> "max_tokens"
        }
        if (mode == CloudGenerationMode.DEFAULT) {
            return RequestTuning(completionTokenField = tokenField)
        }
        val reasoned = mode == CloudGenerationMode.REASONED_CONVERSATION

        return when (profile.provider) {
            ApiProvider.OPENAI -> RequestTuning(
                completionTokenField = tokenField,
                reasoningEffort = when {
                    isOpenAiForcedReasoningModel(model) -> null
                    isOpenAiReasoningModel(model) -> if (reasoned) "medium" else "low"
                    else -> null
                },
            )

            ApiProvider.GOOGLE -> when {
                model.startsWith("gemini-3.1-flash-lite-image") -> RequestTuning(
                    completionTokenField = tokenField,
                    // This image model exposes only minimal/high.
                    geminiThinkingLevel = if (reasoned) "high" else "minimal",
                )
                model.startsWith("gemini-3.1-pro") || model.startsWith("gemini-3-pro") -> RequestTuning(
                    completionTokenField = tokenField,
                    geminiThinkingLevel = if (reasoned) "high" else "low",
                )
                isGemini3Model(model) -> RequestTuning(
                    completionTokenField = tokenField,
                    geminiThinkingLevel = if (reasoned) "medium" else "low",
                )
                isGemini25Model(model) -> RequestTuning(
                    completionTokenField = tokenField,
                    // Numeric budgets let AD keep ordinary reasoning light and explicit reasoning
                    // useful without jumping all the way to the provider's large named-tier budget.
                    geminiThinkingBudget = if (reasoned) 4_096 else 1_024,
                )
                else -> RequestTuning(completionTokenField = tokenField)
            }

            ApiProvider.GROQ -> when {
                isGroqQwen36(model) -> RequestTuning(
                    completionTokenField = tokenField,
                    // Qwen exposes none/default: use efficient dialogue normally, thinking only
                    // when product intent explicitly asks for deeper reasoning.
                    reasoningEffort = if (reasoned) "default" else "none",
                    reasoningFormat = if (includeReasoningActivity) "parsed" else "hidden",
                )
                isGroqGptOss(model) -> RequestTuning(
                    completionTokenField = tokenField,
                    reasoningEffort = if (reasoned) "medium" else "low",
                    // GPT-OSS has a dedicated reasoning field; it never enters answer content.
                    includeReasoning = includeReasoningActivity,
                )
                else -> RequestTuning(completionTokenField = tokenField)
            }

            ApiProvider.DEEPSEEK -> RequestTuning(
                completionTokenField = tokenField,
                deepSeekThinkingType = "enabled",
                reasoningEffort = if (reasoned) "high" else "low",
            )

            ApiProvider.OPENROUTER -> {
                val effort = when {
                    isOpenRouterForcedReasoningModel(model) -> null
                    isLikelyReasoningModel(model) -> if (reasoned) "medium" else "low"
                    else -> null
                }
                RequestTuning(
                    completionTokenField = tokenField,
                    openRouterReasoningEffort = effort,
                    // Reasoning metadata is excluded unless the wearable watchdog explicitly needs
                    // a heartbeat. ApiTokenClient still emits only answer content either way.
                    excludeReasoning = effort != null && !includeReasoningActivity,
                )
            }

            ApiProvider.CUSTOM -> RequestTuning(completionTokenField = tokenField)
        }
    }

    private fun normalizedModel(model: String): String = model.trim().lowercase()

    private fun isOpenAiForcedReasoningModel(model: String): Boolean =
        (model.startsWith("gpt-5") && model.contains("-pro")) ||
            model.startsWith("o1-pro") ||
            model.startsWith("o3-pro")

    private fun isOpenAiReasoningModel(model: String): Boolean {
        if (model.contains("chat-latest")) return false
        return model.startsWith("gpt-5") ||
            model.startsWith("o1") ||
            model.startsWith("o3") ||
            model.startsWith("o4")
    }

    private fun isGroqQwen36(model: String): Boolean =
        model.contains("qwen3.6") || model.contains("qwen-3.6")

    private fun isGroqGptOss(model: String): Boolean = model.contains("gpt-oss")

    private fun isGemini3Model(model: String): Boolean = model.startsWith("gemini-3")

    private fun isGemini25Model(model: String): Boolean = model.startsWith("gemini-2.5")

    private fun isOpenRouterForcedReasoningModel(model: String): Boolean =
        isOpenAiForcedReasoningModel(model.substringAfterLast('/'))

    private fun isLikelyReasoningModel(model: String): Boolean {
        val leaf = model.substringAfterLast('/')
        return isOpenAiReasoningModel(leaf) ||
            model.contains("gpt-oss") ||
            model.contains("qwen3") ||
            model.contains("qwen-3") ||
            model.contains("deepseek-r1") ||
            model.contains("deepseek/reasoner") ||
            model.contains("deepseek-reasoner") ||
            model.contains("gemini-2.5") ||
            model.contains("gemini-3")
    }
}

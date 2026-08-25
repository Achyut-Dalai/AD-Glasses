package com.ad_glasses.ai.router

/**
 * Provider/model-aware generation policy for AD's latency-sensitive conversational paths.
 *
 * A small visible answer and a small provider generation budget are not the same thing for
 * reasoning models: hidden reasoning can consume the provider token ceiling before any visible
 * answer is produced. This policy therefore keeps a small ceiling for ordinary non-reasoning
 * models, gives reasoning models enough headroom to reach a final answer, and asks providers to
 * minimize/disable reasoning only where that control is known to be supported.
 */
internal object CloudModelPolicy {
    const val CONCISE_NON_REASONING_TOKENS = 128
    const val CONCISE_REASONING_TOKENS = 512

    internal data class RequestTuning(
        /** OpenAI-compatible token field. Native Gemini uses maxOutputTokens separately. */
        val completionTokenField: String = "max_tokens",
        /** OpenAI/Groq Chat Completions reasoning control. */
        val reasoningEffort: String? = null,
        /** Groq reasoning presentation control. */
        val reasoningFormat: String? = null,
        /** OpenRouter normalized reasoning control. */
        val openRouterReasoningEffort: String? = null,
        val excludeReasoning: Boolean = false,
        /** OpenAI Responses API text verbosity control. */
        val responseVerbosity: String? = null,
        /** Native Gemini 3.x thinking control. */
        val geminiThinkingLevel: String? = null,
        /** Native Gemini 2.5 thinking control. */
        val geminiThinkingBudget: Int? = null,
    )

    /**
     * Generation ceiling for the shared concise Chat/Lens/Voice contract.
     *
     * The app independently hard-limits the user-visible answer to 50 words / 3 sentences. These
     * values exist only to give the selected provider enough generation room to reach that answer.
     */
    fun conciseConversationTokenLimit(profile: CloudAiProfile?): Int {
        if (profile == null) return CONCISE_REASONING_TOKENS
        val model = normalizedModel(profile.model)
        return when (profile.provider) {
            ApiProvider.OPENAI -> if (isOpenAiReasoningModel(model)) {
                CONCISE_REASONING_TOKENS
            } else {
                CONCISE_NON_REASONING_TOKENS
            }

            ApiProvider.GOOGLE -> when {
                isGemini25Flash(model) -> CONCISE_NON_REASONING_TOKENS
                // Gemini 2.5 Pro cannot fully disable thinking; Gemini 3.x is a thinking family.
                model.startsWith("gemini-2.5-pro") || model.startsWith("gemini-3") ->
                    CONCISE_REASONING_TOKENS
                else -> CONCISE_REASONING_TOKENS
            }

            ApiProvider.GROQ -> when {
                isGroqQwen36(model) -> CONCISE_NON_REASONING_TOKENS
                isGroqGptOss(model) -> CONCISE_REASONING_TOKENS
                else -> CONCISE_NON_REASONING_TOKENS
            }

            ApiProvider.OPENROUTER -> if (isLikelyReasoningModel(model)) {
                CONCISE_REASONING_TOKENS
            } else {
                CONCISE_NON_REASONING_TOKENS
            }

            ApiProvider.DEEPSEEK -> if (model.contains("reasoner") || model.contains("r1")) {
                CONCISE_REASONING_TOKENS
            } else {
                CONCISE_NON_REASONING_TOKENS
            }

            // A custom endpoint can map any model slug to any implementation. Stay conservative.
            ApiProvider.CUSTOM -> CONCISE_REASONING_TOKENS
        }
    }

    /** Native request controls. Unsupported controls are deliberately omitted rather than guessed. */
    fun requestTuning(profile: CloudAiProfile, maxTokens: Int): RequestTuning {
        val model = normalizedModel(profile.model)
        val concise = maxTokens <= CONCISE_REASONING_TOKENS
        val tokenField = when (profile.provider) {
            // `max_completion_tokens` is the current field for reasoning-capable OpenAI Chat
            // Completions and is also the preferred Groq field. Other compatible providers keep
            // the broadly-supported `max_tokens` field.
            ApiProvider.OPENAI, ApiProvider.GROQ -> "max_completion_tokens"
            else -> "max_tokens"
        }
        if (!concise) return RequestTuning(completionTokenField = tokenField)

        return when (profile.provider) {
            ApiProvider.OPENAI -> RequestTuning(
                completionTokenField = tokenField,
                reasoningEffort = if (isOpenAiReasoningModel(model)) "low" else null,
                responseVerbosity = if (model.startsWith("gpt-5")) "low" else null,
            )

            ApiProvider.GOOGLE -> when {
                model.startsWith("gemini-3.7-") -> RequestTuning(
                    completionTokenField = tokenField,
                    geminiThinkingLevel = "low",
                )
                isGemini25Flash(model) -> RequestTuning(
                    completionTokenField = tokenField,
                    geminiThinkingBudget = 0,
                )
                model.startsWith("gemini-2.5-pro") -> RequestTuning(
                    completionTokenField = tokenField,
                    // 2.5 Pro cannot disable thinking. Use its documented minimum budget rather
                    // than requesting an unsupported zero budget.
                    geminiThinkingBudget = 128,
                )
                else -> RequestTuning(completionTokenField = tokenField)
            }

            ApiProvider.GROQ -> when {
                isGroqQwen36(model) -> RequestTuning(
                    completionTokenField = tokenField,
                    reasoningEffort = "none",
                    reasoningFormat = "hidden",
                )
                isGroqGptOss(model) -> RequestTuning(
                    completionTokenField = tokenField,
                    reasoningEffort = "low",
                    reasoningFormat = "hidden",
                )
                else -> RequestTuning(completionTokenField = tokenField)
            }

            ApiProvider.OPENROUTER -> if (isLikelyReasoningModel(model)) {
                RequestTuning(
                    completionTokenField = tokenField,
                    openRouterReasoningEffort = "low",
                    excludeReasoning = true,
                )
            } else {
                RequestTuning(completionTokenField = tokenField)
            }

            // Do not send provider-specific reasoning fields to DeepSeek or arbitrary compatible
            // endpoints until a concrete model contract is known. The sanitizer remains the final
            // defense if such a model emits reasoning text in its visible content.
            ApiProvider.DEEPSEEK,
            ApiProvider.CUSTOM -> RequestTuning(completionTokenField = tokenField)
        }
    }

    private fun normalizedModel(model: String): String = model.trim().lowercase()

    private fun isOpenAiReasoningModel(model: String): Boolean =
        model.startsWith("gpt-5") ||
            model.startsWith("o1") ||
            model.startsWith("o3") ||
            model.startsWith("o4")

    private fun isGroqQwen36(model: String): Boolean =
        model.contains("qwen3.6") || model.contains("qwen-3.6")

    private fun isGroqGptOss(model: String): Boolean = model.contains("gpt-oss")

    private fun isGemini25Flash(model: String): Boolean =
        model.startsWith("gemini-2.5-flash") || model.startsWith("gemini-2.5-flash-lite")

    private fun isLikelyReasoningModel(model: String): Boolean =
        isOpenAiReasoningModel(model.substringAfterLast('/')) ||
            model.contains("gpt-oss") ||
            model.contains("qwen3") ||
            model.contains("qwen-3") ||
            model.contains("deepseek-r1") ||
            model.contains("deepseek/reasoner") ||
            model.contains("deepseek-reasoner") ||
            model.contains("gemini-2.5") ||
            model.contains("gemini-3")
}

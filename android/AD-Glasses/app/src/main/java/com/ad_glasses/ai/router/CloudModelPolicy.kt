package com.ad_glasses.ai.router

/** Explicit generation intent; never infer product behavior from a token count. */
internal enum class CloudGenerationMode {
    DEFAULT,
    CONCISE_CONVERSATION,
}

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
    const val CONCISE_FORCED_HIGH_REASONING_TOKENS = 2_048

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
            ApiProvider.OPENAI -> when {
                isOpenAiForcedHighReasoningModel(model) -> CONCISE_FORCED_HIGH_REASONING_TOKENS
                isOpenAiReasoningModel(model) -> CONCISE_REASONING_TOKENS
                else -> CONCISE_NON_REASONING_TOKENS
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

    /**
     * Compatibility bridge for transports not yet carrying the explicit generation mode. Only the
     * two normal conversational ceilings map to concise tuning. Automation currently uses 384 and
     * therefore keeps provider-default reasoning rather than inheriting Chat/Voice behavior.
     */
    fun requestTuning(profile: CloudAiProfile, maxTokens: Int): RequestTuning = requestTuning(
        profile = profile,
        mode = if (
            maxTokens == CONCISE_NON_REASONING_TOKENS ||
            maxTokens == CONCISE_REASONING_TOKENS
        ) {
            CloudGenerationMode.CONCISE_CONVERSATION
        } else {
            CloudGenerationMode.DEFAULT
        },
    )

    /** Native request controls. Unsupported controls are deliberately omitted rather than guessed. */
    fun requestTuning(
        profile: CloudAiProfile,
        mode: CloudGenerationMode,
    ): RequestTuning {
        val model = normalizedModel(profile.model)
        val tokenField = when (profile.provider) {
            // `max_completion_tokens` is the current field for OpenAI Chat Completions and is also
            // the preferred Groq field. Other compatible providers keep broad `max_tokens` support.
            ApiProvider.OPENAI, ApiProvider.GROQ -> "max_completion_tokens"
            else -> "max_tokens"
        }
        if (mode != CloudGenerationMode.CONCISE_CONVERSATION) {
            return RequestTuning(completionTokenField = tokenField)
        }

        return when (profile.provider) {
            ApiProvider.OPENAI -> {
                val reasoningEffort = when {
                    // GPT-5 Pro only supports high reasoning. Asking it for low would be an API
                    // error, so leave its fixed reasoning policy alone and give it extra headroom.
                    isOpenAiForcedHighReasoningModel(model) -> null
                    // GPT-5.1 supports none and defaults to none. For normal AD conversation there
                    // is no benefit in turning hidden reasoning back on.
                    model.startsWith("gpt-5.1") && !model.contains("chat-latest") -> "none"
                    isOpenAiReasoningModel(model) -> "low"
                    else -> null
                }
                RequestTuning(
                    completionTokenField = tokenField,
                    reasoningEffort = reasoningEffort,
                    responseVerbosity = if (model.startsWith("gpt-5")) "low" else null,
                )
            }

            ApiProvider.GOOGLE -> when {
                // 3.7 rejects `minimal`; `low` is its lowest supported level.
                model.startsWith("gemini-3.7-") -> RequestTuning(
                    completionTokenField = tokenField,
                    geminiThinkingLevel = "low",
                )
                // 3.6/3.5 Flash and 3.5 Flash-Lite support minimal for latency-sensitive chat.
                model.startsWith("gemini-3.6-") ||
                    model.startsWith("gemini-3.5-flash") -> RequestTuning(
                    completionTokenField = tokenField,
                    geminiThinkingLevel = "minimal",
                )
                // Pro variants cannot disable thinking; low is the safe supported floor.
                model.startsWith("gemini-3.1-pro") ||
                    model.startsWith("gemini-3-pro") -> RequestTuning(
                    completionTokenField = tokenField,
                    geminiThinkingLevel = "low",
                )
                // Gemini 3.1 Flash-Lite and Gemini 3 Flash support minimal.
                model.startsWith("gemini-3.1-flash-lite") ||
                    model.startsWith("gemini-3-flash") -> RequestTuning(
                    completionTokenField = tokenField,
                    geminiThinkingLevel = "minimal",
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

    private fun isOpenAiForcedHighReasoningModel(model: String): Boolean =
        model.startsWith("gpt-5-pro") ||
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

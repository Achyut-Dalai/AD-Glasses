package com.ad_glasses.ai.router

/** Explicit generation intent; never infer product behavior from a token count or model name alone. */
enum class CloudGenerationMode {
    DEFAULT,
    CONCISE_CONVERSATION,
    REASONED_CONVERSATION,
}

/**
 * Provider/model-aware transport policy.
 *
 * Normal Chat/Lens/Voice uses [CloudGenerationMode.CONCISE_CONVERSATION]: hidden reasoning is
 * disabled where the provider supports it and the provider ceiling stays near the visible answer.
 * A larger ceiling is reserved for [CloudGenerationMode.REASONED_CONVERSATION] only on APIs where
 * reasoning consumes the same generation allowance as visible output. Gemini reports/throttles
 * thinking separately, so its visible answer stays at the concise ceiling even for reasoned turns.
 */
internal object CloudModelPolicy {
    /** Roughly enough for the shared <=50 word / <=3 sentence final-answer contract. */
    const val CONCISE_OUTPUT_TOKENS = 96

    /** Headroom for APIs where unavoidable hidden reasoning shares the completion-token ceiling. */
    const val CONCISE_MANDATORY_REASONING_TOKENS = 256

    /** Pro-style models cannot be made cheap; keep them functional without making this the default. */
    const val CONCISE_FORCED_REASONING_TOKENS = 512

    /** Explicit deep-reasoning turns may intentionally spend more shared generation budget. */
    const val REASONED_OUTPUT_TOKENS = 1_024
    const val REASONED_FORCED_REASONING_TOKENS = 2_048

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
        /** OpenAI Responses API text verbosity control. */
        val responseVerbosity: String? = null,
        /** Native Gemini 3.x thinking control. */
        val geminiThinkingLevel: String? = null,
        /** Native Gemini 2.5 thinking control. */
        val geminiThinkingBudget: Int? = null,
    )

    fun generationTokenLimit(
        profile: CloudAiProfile?,
        mode: CloudGenerationMode,
    ): Int = when (mode) {
        CloudGenerationMode.DEFAULT -> 512
        CloudGenerationMode.REASONED_CONVERSATION -> when {
            // Gemini's maxOutputTokens bounds candidate output; thinking is controlled/reported
            // separately. Keep the final answer short even when the requested thinking level rises.
            profile?.provider == ApiProvider.GOOGLE -> CONCISE_OUTPUT_TOKENS
            profile?.let(::isForcedReasoningProfile) == true -> REASONED_FORCED_REASONING_TOKENS
            else -> REASONED_OUTPUT_TOKENS
        }
        CloudGenerationMode.CONCISE_CONVERSATION -> conciseTokenLimit(profile)
    }

    /** Source-compatible helper used by existing callers/tests while the explicit mode is adopted. */
    fun conciseConversationTokenLimit(profile: CloudAiProfile?): Int =
        generationTokenLimit(profile, CloudGenerationMode.CONCISE_CONVERSATION)

    private fun conciseTokenLimit(profile: CloudAiProfile?): Int {
        if (profile == null) return CONCISE_MANDATORY_REASONING_TOKENS
        val model = normalizedModel(profile.model)
        return when (profile.provider) {
            ApiProvider.OPENAI -> when {
                isOpenAiForcedReasoningModel(model) -> CONCISE_FORCED_REASONING_TOKENS
                openAiCanDisableReasoning(model) || !isOpenAiReasoningModel(model) -> CONCISE_OUTPUT_TOKENS
                else -> CONCISE_MANDATORY_REASONING_TOKENS
            }

            // Gemini candidate output and thinking are independently controlled. Never inflate the
            // visible answer ceiling merely because a Gemini family has mandatory thinking.
            ApiProvider.GOOGLE -> CONCISE_OUTPUT_TOKENS

            ApiProvider.GROQ -> when {
                isGroqQwen36(model) -> CONCISE_OUTPUT_TOKENS
                isGroqGptOss(model) -> CONCISE_MANDATORY_REASONING_TOKENS
                else -> CONCISE_OUTPUT_TOKENS
            }

            ApiProvider.DEEPSEEK -> CONCISE_OUTPUT_TOKENS // v4 supports thinking.type=disabled.

            ApiProvider.OPENROUTER -> when {
                isOpenRouterForcedReasoningModel(model) -> CONCISE_FORCED_REASONING_TOKENS
                openRouterCanDisableReasoning(model) || !isLikelyReasoningModel(model) -> CONCISE_OUTPUT_TOKENS
                else -> CONCISE_MANDATORY_REASONING_TOKENS
            }

            // A custom endpoint may map any slug to any implementation. Do not invent provider
            // reasoning controls; 128 keeps the default modest while leaving a little compatibility room.
            ApiProvider.CUSTOM -> 128
        }
    }

    /**
     * Compatibility bridge for older transport call sites. New inference code passes [mode]
     * explicitly so a coincidental token value can never switch reasoning policy.
     */
    fun requestTuning(profile: CloudAiProfile, maxTokens: Int): RequestTuning = requestTuning(
        profile = profile,
        mode = when (maxTokens) {
            conciseTokenLimit(profile) -> CloudGenerationMode.CONCISE_CONVERSATION
            REASONED_OUTPUT_TOKENS,
            REASONED_FORCED_REASONING_TOKENS -> CloudGenerationMode.REASONED_CONVERSATION
            else -> CloudGenerationMode.DEFAULT
        },
    )

    /**
     * Native request controls. Unsupported controls are deliberately omitted rather than guessed.
     *
     * [includeReasoningActivity] is used only by the adaptive wearable stream watchdog. Providers
     * may return structured reasoning metadata so the app can know a request is alive; the transport
     * still discards that content before persistence, display, or TTS.
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
            ApiProvider.OPENAI -> {
                val effort = when {
                    isOpenAiForcedReasoningModel(model) -> null // fixed model policy; avoid unsupported values.
                    reasoned && isOpenAiReasoningModel(model) -> "medium"
                    reasoned -> null
                    openAiCanDisableReasoning(model) -> "none"
                    isOpenAiBaseGpt5(model) -> "minimal"
                    isOpenAiReasoningModel(model) -> "low"
                    else -> null
                }
                RequestTuning(
                    completionTokenField = tokenField,
                    reasoningEffort = effort,
                    // Reasoning depth and final-answer verbosity are independent. Even a reasoned
                    // wearable turn should end with the shared short final answer.
                    responseVerbosity = if (model.startsWith("gpt-5")) "low" else null,
                )
            }

            ApiProvider.GOOGLE -> when {
                model.startsWith("gemini-3.7-") -> RequestTuning(
                    completionTokenField = tokenField,
                    geminiThinkingLevel = if (reasoned) "medium" else "low",
                )
                model.startsWith("gemini-3.1-flash-lite-image") -> RequestTuning(
                    completionTokenField = tokenField,
                    // This image model exposes only minimal/high, unlike the general Flash family.
                    geminiThinkingLevel = if (reasoned) "high" else "minimal",
                )
                isGeminiMinimalThinkingModel(model) -> RequestTuning(
                    completionTokenField = tokenField,
                    geminiThinkingLevel = if (reasoned) "medium" else "minimal",
                )
                model.startsWith("gemini-3.1-pro") || model.startsWith("gemini-3-pro") -> RequestTuning(
                    completionTokenField = tokenField,
                    geminiThinkingLevel = if (reasoned) "high" else "low",
                )
                isGemini25Flash(model) -> RequestTuning(
                    completionTokenField = tokenField,
                    geminiThinkingBudget = if (reasoned) 1_024 else 0,
                )
                model.startsWith("gemini-2.5-pro") -> RequestTuning(
                    completionTokenField = tokenField,
                    geminiThinkingBudget = if (reasoned) 1_024 else 128,
                )
                else -> RequestTuning(completionTokenField = tokenField)
            }

            ApiProvider.GROQ -> when {
                isGroqQwen36(model) -> RequestTuning(
                    completionTokenField = tokenField,
                    reasoningEffort = if (reasoned) "default" else "none",
                    // Parsed mode is requested only when a reasoning turn needs a heartbeat. The
                    // parser still drops the reasoning field before the answer reaches the app.
                    reasoningFormat = if (reasoned && includeReasoningActivity) "parsed" else "hidden",
                )
                isGroqGptOss(model) -> RequestTuning(
                    completionTokenField = tokenField,
                    reasoningEffort = if (reasoned) "medium" else "low",
                    // GPT-OSS does not support reasoning_format. Include its dedicated reasoning
                    // field only when the watchdog needs activity; it is never surfaced to users.
                    includeReasoning = includeReasoningActivity,
                )
                else -> RequestTuning(completionTokenField = tokenField)
            }

            ApiProvider.DEEPSEEK -> RequestTuning(
                completionTokenField = tokenField,
                deepSeekThinkingType = if (reasoned) "enabled" else "disabled",
                reasoningEffort = if (reasoned) "high" else null,
            )

            ApiProvider.OPENROUTER -> {
                val effort = when {
                    reasoned && isLikelyReasoningModel(model) -> "medium"
                    reasoned -> null
                    openRouterCanDisableReasoning(model) -> "none"
                    isOpenRouterForcedReasoningModel(model) -> null
                    model.substringAfterLast('/').let(::isOpenAiBaseGpt5) -> "minimal"
                    isLikelyReasoningModel(model) -> "low"
                    else -> null
                }
                RequestTuning(
                    completionTokenField = tokenField,
                    openRouterReasoningEffort = effort,
                    // For wearable streaming we may temporarily receive structured reasoning as a
                    // heartbeat, but ApiTokenClient filters it before any user-visible pipeline.
                    excludeReasoning = effort != null && !includeReasoningActivity,
                )
            }

            ApiProvider.CUSTOM -> RequestTuning(completionTokenField = tokenField)
        }
    }

    private fun isForcedReasoningProfile(profile: CloudAiProfile): Boolean {
        val model = normalizedModel(profile.model)
        return when (profile.provider) {
            ApiProvider.OPENAI -> isOpenAiForcedReasoningModel(model)
            ApiProvider.OPENROUTER -> isOpenRouterForcedReasoningModel(model)
            else -> false
        }
    }

    private fun normalizedModel(model: String): String = model.trim().lowercase()

    private fun isOpenAiForcedReasoningModel(model: String): Boolean =
        (model.startsWith("gpt-5") && model.contains("-pro")) ||
            model.startsWith("o1-pro") ||
            model.startsWith("o3-pro")

    private fun isOpenAiBaseGpt5(model: String): Boolean =
        model == "gpt-5" ||
            (model.startsWith("gpt-5-") && !model.contains("chat-latest"))

    private fun openAiCanDisableReasoning(model: String): Boolean {
        if (!model.startsWith("gpt-5") || model.contains("-pro") || model.contains("chat-latest")) return false
        return Regex("^gpt-5\\.(?:[1-9]|[1-9][0-9])(?:$|[-.])").containsMatchIn(model)
    }

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

    private fun isGeminiMinimalThinkingModel(model: String): Boolean =
        model.startsWith("gemini-3.6-") ||
            model.startsWith("gemini-3.5-flash") ||
            model.startsWith("gemini-3.1-flash-lite") ||
            model.startsWith("gemini-3-flash")

    private fun openRouterCanDisableReasoning(model: String): Boolean {
        val leaf = model.substringAfterLast('/')
        return leaf.contains("qwen3.6") ||
            leaf.contains("qwen-3.6") ||
            leaf.startsWith("gemini-2.5-flash") ||
            (leaf.startsWith("gpt-5.") && !leaf.contains("-pro"))
    }

    private fun isOpenRouterForcedReasoningModel(model: String): Boolean {
        val leaf = model.substringAfterLast('/')
        return isOpenAiForcedReasoningModel(leaf)
    }

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

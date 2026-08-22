package com.ad_glasses.ai.runtime

import android.content.Context

enum class ADIntelligenceProfile {
    BALANCED,
    FAST,
    PRIVATE,
    CUSTOM,
}

enum class ADConversationEngine {
    AUTO,
    GEMINI_LIVE,
    GEMINI_STANDARD,
    LOCAL,
}

enum class ADSpeechEngine {
    AUTO,
    GEMINI_NATIVE_AUDIO,
    MOONSHINE,
}

enum class ADVisionEngine {
    AUTO,
    GEMINI_LIVE,
    GEMINI_STANDARD,
    LOCAL_GEMMA,
}

enum class ADFileEngine {
    AUTO,
    GEMINI_FILES,
    GEMINI_INLINE,
    LOCAL,
}

enum class ADGroundingPolicy {
    AUTO,
    ALWAYS,
    NEVER,
}

enum class ADVisibleFallbackPolicy {
    ASK,
    NEVER,
    ALLOW,
}

data class ADIntelligenceConfig(
    val profile: ADIntelligenceProfile,
    val conversation: ADConversationEngine,
    val speech: ADSpeechEngine,
    val vision: ADVisionEngine,
    val files: ADFileEngine,
    val grounding: ADGroundingPolicy,
    val visibleFallback: ADVisibleFallbackPolicy,
) {
    val screenOffFirst: Boolean
        get() = visibleFallback != ADVisibleFallbackPolicy.ALLOW
}

/**
 * User-owned routing policy for AD Assistant.
 *
 * Providers, modalities and executors are intentionally independent. This is the experiment
 * surface for deciding what works best on a particular phone/glasses combination without
 * rewriting the assistant architecture.
 */
object ADIntelligencePrefs {
    private const val PREFS = "ad_intelligence_runtime"
    private const val KEY_PROFILE = "profile"
    private const val KEY_CONVERSATION = "conversation"
    private const val KEY_SPEECH = "speech"
    private const val KEY_VISION = "vision"
    private const val KEY_FILES = "files"
    private const val KEY_GROUNDING = "grounding"
    private const val KEY_VISIBLE_FALLBACK = "visible_fallback"

    fun get(context: Context): ADIntelligenceConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val profile = enumValue(prefs.getString(KEY_PROFILE, null), ADIntelligenceProfile.BALANCED)
        val defaults = defaults(profile)
        return ADIntelligenceConfig(
            profile = profile,
            conversation = enumValue(prefs.getString(KEY_CONVERSATION, null), defaults.conversation),
            speech = enumValue(prefs.getString(KEY_SPEECH, null), defaults.speech),
            vision = enumValue(prefs.getString(KEY_VISION, null), defaults.vision),
            files = enumValue(prefs.getString(KEY_FILES, null), defaults.files),
            grounding = enumValue(prefs.getString(KEY_GROUNDING, null), defaults.grounding),
            visibleFallback = enumValue(prefs.getString(KEY_VISIBLE_FALLBACK, null), defaults.visibleFallback),
        )
    }

    fun applyProfile(context: Context, profile: ADIntelligenceProfile) {
        val resolved = defaults(profile)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILE, profile.name)
            .putString(KEY_CONVERSATION, resolved.conversation.name)
            .putString(KEY_SPEECH, resolved.speech.name)
            .putString(KEY_VISION, resolved.vision.name)
            .putString(KEY_FILES, resolved.files.name)
            .putString(KEY_GROUNDING, resolved.grounding.name)
            .putString(KEY_VISIBLE_FALLBACK, resolved.visibleFallback.name)
            .apply()
    }

    fun setConversation(context: Context, value: ADConversationEngine) = setCustom(context, KEY_CONVERSATION, value.name)
    fun setSpeech(context: Context, value: ADSpeechEngine) = setCustom(context, KEY_SPEECH, value.name)
    fun setVision(context: Context, value: ADVisionEngine) = setCustom(context, KEY_VISION, value.name)
    fun setFiles(context: Context, value: ADFileEngine) = setCustom(context, KEY_FILES, value.name)
    fun setGrounding(context: Context, value: ADGroundingPolicy) = setCustom(context, KEY_GROUNDING, value.name)
    fun setVisibleFallback(context: Context, value: ADVisibleFallbackPolicy) = setCustom(context, KEY_VISIBLE_FALLBACK, value.name)

    private fun setCustom(context: Context, key: String, value: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILE, ADIntelligenceProfile.CUSTOM.name)
            .putString(key, value)
            .apply()
    }

    private fun defaults(profile: ADIntelligenceProfile): ADIntelligenceConfig = when (profile) {
        ADIntelligenceProfile.BALANCED -> ADIntelligenceConfig(
            profile = profile,
            conversation = ADConversationEngine.AUTO,
            speech = ADSpeechEngine.AUTO,
            vision = ADVisionEngine.AUTO,
            files = ADFileEngine.AUTO,
            grounding = ADGroundingPolicy.AUTO,
            visibleFallback = ADVisibleFallbackPolicy.ASK,
        )
        ADIntelligenceProfile.FAST -> ADIntelligenceConfig(
            profile = profile,
            conversation = ADConversationEngine.GEMINI_LIVE,
            speech = ADSpeechEngine.GEMINI_NATIVE_AUDIO,
            vision = ADVisionEngine.GEMINI_LIVE,
            files = ADFileEngine.GEMINI_INLINE,
            grounding = ADGroundingPolicy.AUTO,
            visibleFallback = ADVisibleFallbackPolicy.ASK,
        )
        ADIntelligenceProfile.PRIVATE -> ADIntelligenceConfig(
            profile = profile,
            conversation = ADConversationEngine.LOCAL,
            speech = ADSpeechEngine.MOONSHINE,
            vision = ADVisionEngine.LOCAL_GEMMA,
            files = ADFileEngine.LOCAL,
            grounding = ADGroundingPolicy.NEVER,
            visibleFallback = ADVisibleFallbackPolicy.NEVER,
        )
        ADIntelligenceProfile.CUSTOM -> ADIntelligenceConfig(
            profile = profile,
            conversation = ADConversationEngine.AUTO,
            speech = ADSpeechEngine.AUTO,
            vision = ADVisionEngine.AUTO,
            files = ADFileEngine.AUTO,
            grounding = ADGroundingPolicy.AUTO,
            visibleFallback = ADVisibleFallbackPolicy.ASK,
        )
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}

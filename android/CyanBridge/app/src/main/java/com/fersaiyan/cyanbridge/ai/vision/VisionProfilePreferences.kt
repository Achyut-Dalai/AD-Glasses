package com.fersaiyan.cyanbridge.ai.vision

import android.content.Context
import java.util.Locale

object VisionProfilePreferences {
    private const val PREFS = "vision_profile"
    private const val KEY_PROFILE = "profile"
    private const val KEY_CUSTOM_INSTRUCTIONS = "custom_instructions"
    private const val MAX_CUSTOM_INSTRUCTIONS_CHARS = 1_500

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context): VisionProfileSettings {
        val preferences = preferences(context)
        val profile = preferences.getString(KEY_PROFILE, null)
            ?.let { raw -> VisionProfile.entries.firstOrNull { it.name == raw } }
            ?: VisionProfile.WALKING
        return VisionProfileSettings(
            profile = profile,
            responseLanguageTag = currentLanguageTag(context),
            customInstructions = preferences.getString(KEY_CUSTOM_INSTRUCTIONS, "").orEmpty(),
        )
    }

    fun setProfile(context: Context, profile: VisionProfile) {
        preferences(context).edit().putString(KEY_PROFILE, profile.name).apply()
    }

    fun setCustomInstructions(context: Context, instructions: String) {
        preferences(context).edit()
            .putString(KEY_CUSTOM_INSTRUCTIONS, instructions.trim().take(MAX_CUSTOM_INSTRUCTIONS_CHARS))
            .apply()
    }

    private fun currentLanguageTag(context: Context): String {
        return context.resources.configuration.locales[0]
            ?.toLanguageTag()
            ?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().toLanguageTag()
    }
}

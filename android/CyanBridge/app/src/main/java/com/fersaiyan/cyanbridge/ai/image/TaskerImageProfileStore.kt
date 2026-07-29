package com.fersaiyan.cyanbridge.ai.image

import android.content.Context
import java.util.UUID

/** Stores the target/version explicitly reported by an imported Tasker profile. */
object TaskerImageProfileStore {
    private const val PREFS = "tasker_image_profile"
    private const val KEY_TARGET = "target"
    private const val KEY_VERSION = "version"
    private const val KEY_PENDING_TOKEN = "pending_token"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun target(context: Context): String? = prefs(context).getString(KEY_TARGET, null)

    fun version(context: Context): String? = prefs(context).getString(KEY_VERSION, null)

    fun beginVerification(context: Context): String {
        val token = UUID.randomUUID().toString()
        prefs(context).edit().putString(KEY_PENDING_TOKEN, token).apply()
        return token
    }

    fun verifyAndRecord(context: Context, target: String?, version: String?, token: String?): Boolean {
        if (target.isNullOrBlank() || version.isNullOrBlank() || token.isNullOrBlank()) return false
        if (token != prefs(context).getString(KEY_PENDING_TOKEN, null)) return false
        prefs(context).edit()
            .putString(KEY_TARGET, target)
            .putString(KEY_VERSION, version)
            .remove(KEY_PENDING_TOKEN)
            .apply()
        return true
    }
}

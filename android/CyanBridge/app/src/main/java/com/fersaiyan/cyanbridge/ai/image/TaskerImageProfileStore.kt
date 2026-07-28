package com.fersaiyan.cyanbridge.ai.image

import android.content.Context

/** Stores the target/version explicitly reported by an imported Tasker profile. */
object TaskerImageProfileStore {
    private const val PREFS = "tasker_image_profile"
    private const val KEY_TARGET = "target"
    private const val KEY_VERSION = "version"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun target(context: Context): String? = prefs(context).getString(KEY_TARGET, null)

    fun version(context: Context): String? = prefs(context).getString(KEY_VERSION, null)

    fun record(context: Context, target: String?, version: String?) {
        if (target.isNullOrBlank() || version.isNullOrBlank()) return
        prefs(context).edit()
            .putString(KEY_TARGET, target)
            .putString(KEY_VERSION, version)
            .apply()
    }
}

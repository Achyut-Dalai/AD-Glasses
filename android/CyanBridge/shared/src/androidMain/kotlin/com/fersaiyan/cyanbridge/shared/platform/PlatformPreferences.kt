package com.achyut.adglasses.shared.platform

import android.content.Context
import android.content.SharedPreferences

private lateinit var appContext: Context

fun initPlatformPreferences(context: Context) {
    appContext = context.applicationContext
}

actual class PlatformPreferences(private val prefs: SharedPreferences) {
    actual fun getString(key: String, defaultValue: String): String = prefs.getString(key, defaultValue) ?: defaultValue
    actual fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    actual fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)
    actual fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    actual fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)
    actual fun putLong(key: String, value: Long) { prefs.edit().putLong(key, value).apply() }
    actual fun getFloat(key: String, defaultValue: Float): Float = prefs.getFloat(key, defaultValue)
    actual fun putFloat(key: String, value: Float) { prefs.edit().putFloat(key, value).apply() }
    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)
    actual fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    actual fun remove(key: String) { prefs.edit().remove(key).apply() }
    actual fun clear() { prefs.edit().clear().apply() }
    actual fun contains(key: String): Boolean = prefs.contains(key)
}

actual fun createPlatformPreferences(name: String): PlatformPreferences {
    check(::appContext.isInitialized) { "Call initPlatformPreferences(context) before creating preferences" }
    return PlatformPreferences(appContext.getSharedPreferences(name, Context.MODE_PRIVATE))
}

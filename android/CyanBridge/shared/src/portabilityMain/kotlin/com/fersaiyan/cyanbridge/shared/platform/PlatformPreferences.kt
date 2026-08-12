package com.achyut.adglasses.shared.platform

import java.util.concurrent.ConcurrentHashMap

private val stores = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

actual class PlatformPreferences(private val name: String) {
    private val store = stores.getOrPut(name) { ConcurrentHashMap() }

    actual fun getString(key: String, defaultValue: String): String = store.getOrDefault(key, defaultValue)
    actual fun putString(key: String, value: String) { store[key] = value }
    actual fun getInt(key: String, defaultValue: Int): Int = store[key]?.toIntOrNull() ?: defaultValue
    actual fun putInt(key: String, value: Int) { store[key] = value.toString() }
    actual fun getLong(key: String, defaultValue: Long): Long = store[key]?.toLongOrNull() ?: defaultValue
    actual fun putLong(key: String, value: Long) { store[key] = value.toString() }
    actual fun getFloat(key: String, defaultValue: Float): Float = store[key]?.toFloatOrNull() ?: defaultValue
    actual fun putFloat(key: String, value: Float) { store[key] = value.toString() }
    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean = store[key]?.toBooleanStrictOrNull() ?: defaultValue
    actual fun putBoolean(key: String, value: Boolean) { store[key] = value.toString() }
    actual fun remove(key: String) { store.remove(key) }
    actual fun clear() { store.clear() }
    actual fun contains(key: String): Boolean = store.containsKey(key)
}

actual fun createPlatformPreferences(name: String): PlatformPreferences = PlatformPreferences(name)

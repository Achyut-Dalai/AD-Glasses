package com.fersaiyan.cyanbridge.ui

import android.content.Context
import com.fersaiyan.cyanbridge.shared.plugins.NativePluginIds

object CommunityPluginPrefs {
    private const val PREFS = "community_plugins"
    private const val KEY_GLASS_TAB_SHORTCUT_PLUGIN = "glasses_tab_shortcut_plugin"
    private const val DEFAULT_GLASS_TAB_SHORTCUT_PLUGIN = NativePluginIds.MEETING_SPARK_NOTES

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun nativePluginKey(pluginId: String) = "native_plugin_enabled_$pluginId"

    fun isNativePluginEnabled(context: Context, pluginId: String): Boolean {
        if (pluginId == NativePluginIds.ERRAND_BRAIN) return false
        return prefs(context).getBoolean(nativePluginKey(pluginId), false)
    }

    fun setNativePluginEnabled(context: Context, pluginId: String, enabled: Boolean) {
        val value = if (pluginId == NativePluginIds.ERRAND_BRAIN) false else enabled
        prefs(context).edit().putBoolean(nativePluginKey(pluginId), value).apply()
    }

    fun getGlassesTabShortcutPluginId(context: Context): String? {
        return prefs(context)
            .getString(KEY_GLASS_TAB_SHORTCUT_PLUGIN, DEFAULT_GLASS_TAB_SHORTCUT_PLUGIN)
            ?.takeIf { it.isNotBlank() && it != NativePluginIds.ERRAND_BRAIN }
    }

    fun isGlassesTabShortcutEnabled(context: Context, pluginId: String): Boolean {
        if (pluginId == NativePluginIds.ERRAND_BRAIN) return false
        return getGlassesTabShortcutPluginId(context) == pluginId
    }

    fun setGlassesTabShortcutEnabled(context: Context, pluginId: String, enabled: Boolean) {
        val editor = prefs(context).edit()
        if (pluginId == NativePluginIds.ERRAND_BRAIN) {
            if (getGlassesTabShortcutPluginId(context) == pluginId) {
                editor.putString(KEY_GLASS_TAB_SHORTCUT_PLUGIN, "")
            }
            editor.apply()
            return
        }
        if (enabled) {
            editor.putString(KEY_GLASS_TAB_SHORTCUT_PLUGIN, pluginId)
        } else if (getGlassesTabShortcutPluginId(context) == pluginId) {
            editor.putString(KEY_GLASS_TAB_SHORTCUT_PLUGIN, "")
        }
        editor.apply()
    }
}

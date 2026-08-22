package com.ad_glasses.ai

import android.content.Context
import com.ad_glasses.shared.glasses.AiWakeWordRoute

object AiWakeWordPreferences {
    private const val PREFS = "ai_wake_word"
    private const val KEY_ROUTE = "route"

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun route(context: Context): AiWakeWordRoute =
        AiWakeWordRoute.fromRaw(preferences(context).getString(KEY_ROUTE, null))

    fun setRoute(context: Context, route: AiWakeWordRoute) {
        preferences(context).edit().putString(KEY_ROUTE, route.name).apply()
    }
}

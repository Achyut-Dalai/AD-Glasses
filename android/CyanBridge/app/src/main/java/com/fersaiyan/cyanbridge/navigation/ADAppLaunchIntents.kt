package com.fersaiyan.cyanbridge.navigation

import android.content.Context
import android.content.Intent

/** Launches the installed AD Glasses product without coupling background components to MainActivity. */
object ADAppLaunchIntents {
    fun productHome(context: Context): Intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        } ?: Intent().setClassName(context.packageName, "${context.packageName}.ui.WelcomeActivity").apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
}

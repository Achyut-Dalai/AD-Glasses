package com.achyut.adglasses.localagent

import android.content.Context
import com.achyut.adglasses.agent.LocalAgentPrefs as AutomationPrefs

object LocalAgentSafetyPolicy {
    fun blockedReason(context: Context, packageName: String?): String? {
        val pkg = packageName?.trim()?.lowercase().orEmpty()
        if (pkg.isBlank()) return null

        if (AutomationPrefs.getCaptureBlacklistPackages(context).contains(pkg)) {
            return "The current app is blocked in AD Glasses privacy settings."
        }
        return null
    }
}

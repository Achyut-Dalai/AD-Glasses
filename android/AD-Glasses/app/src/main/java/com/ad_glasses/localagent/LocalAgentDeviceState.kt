package com.ad_glasses.localagent

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

/**
 * Fail-closed device-state gate for Local Agent control and capture. Accessibility can remain
 * connected while the screen is off or keyguard is shown, so service connectivity alone is not a
 * sufficient signal that it is safe to inspect or operate the current UI.
 */
object LocalAgentDeviceState {

    enum class Availability(
        val errorCode: String,
        val statusText: String,
    ) {
        READY(errorCode = "", statusText = "Ready"),
        NON_INTERACTIVE(
            errorCode = "device_not_interactive",
            statusText = "Phone screen is off or inactive",
        ),
        LOCKED(
            errorCode = "device_locked",
            statusText = "Phone is locked",
        ),
        UNAVAILABLE(
            errorCode = "device_state_unavailable",
            statusText = "Unable to verify phone lock state",
        ),
    }

    fun availability(context: Context): Availability {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return fromSignals(
            interactive = power?.isInteractive,
            keyguardLocked = keyguard?.isKeyguardLocked,
        )
    }

    fun isReady(context: Context): Boolean = availability(context) == Availability.READY

    internal fun fromSignals(
        interactive: Boolean?,
        keyguardLocked: Boolean?,
    ): Availability {
        if (interactive == null || keyguardLocked == null) return Availability.UNAVAILABLE
        if (!interactive) return Availability.NON_INTERACTIVE
        return if (keyguardLocked) Availability.LOCKED else Availability.READY
    }
}

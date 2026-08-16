package com.fersaiyan.cyanbridge.ui.adglasses

import com.fersaiyan.cyanbridge.devices.ADDeviceSupportPolicy
import com.fersaiyan.cyanbridge.shared.devices.DeviceProfile
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

internal data class ADDevicePresentation(
    val connected: Boolean,
    val connecting: Boolean,
    val statusLabel: String,
    val identityLabel: String?,
    val shouldOpenSetup: Boolean,
)

/**
 * Converts low-level dashboard/device state into the small product-facing model used
 * across Home, Settings, Sync and Device Center. Unsupported compatibility profiles
 * are intentionally treated as absent rather than surfaced to the user.
 */
internal fun buildADDevicePresentation(
    state: GlassesDashboardUiState,
    profile: DeviceProfile?,
): ADDevicePresentation {
    val productProfile = profile?.takeIf { ADDeviceSupportPolicy.isPairable(it.selectedClass) }
    val rawConnection = state.connectionLabel.trim()
    val connected = rawConnection.contains("connected", ignoreCase = true) &&
        !rawConnection.contains("disconnected", ignoreCase = true)
    val connecting = rawConnection.contains("connecting", ignoreCase = true) ||
        rawConnection.contains("reconnect", ignoreCase = true)

    val deviceName = productProfile?.advertisedName
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
    val deviceClass = productProfile?.let {
        state.deviceClassLabel
            .trim()
            .takeIf { label -> label.isNotBlank() && !label.equals("Unknown", ignoreCase = true) }
    }

    val identity = when {
        deviceName != null && deviceClass != null &&
            !deviceName.contains(deviceClass, ignoreCase = true) -> "$deviceName · $deviceClass"
        deviceName != null -> deviceName
        deviceClass != null -> deviceClass
        else -> null
    }

    val status = when {
        connected -> "Connected"
        connecting -> rawConnection.ifBlank { "Connecting" }
        rawConnection.isBlank() || rawConnection.equals("Unknown", ignoreCase = true) -> "Disconnected"
        else -> rawConnection
    }

    return ADDevicePresentation(
        connected = connected,
        connecting = connecting,
        statusLabel = status,
        identityLabel = identity,
        shouldOpenSetup = !connected && identity == null,
    )
}

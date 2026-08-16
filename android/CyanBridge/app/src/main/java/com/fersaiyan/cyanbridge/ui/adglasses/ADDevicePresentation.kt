package com.fersaiyan.cyanbridge.ui.adglasses

import com.fersaiyan.cyanbridge.shared.devices.DeviceProfile
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

internal data class ADDevicePresentation(
    val connected: Boolean,
    val connecting: Boolean,
    val statusLabel: String,
    val identityLabel: String?,
    val shouldOpenSetup: Boolean,
)

internal fun buildADDevicePresentation(
    state: GlassesDashboardUiState,
    profile: DeviceProfile?,
): ADDevicePresentation {
    val rawConnection = state.connectionLabel.trim()
    val connected = rawConnection.contains("connected", ignoreCase = true) &&
        !rawConnection.contains("disconnected", ignoreCase = true)
    val connecting = rawConnection.contains("connecting", ignoreCase = true) ||
        rawConnection.contains("reconnect", ignoreCase = true)

    val deviceName = profile?.advertisedName
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
    val deviceClass = state.deviceClassLabel
        .trim()
        .takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }

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

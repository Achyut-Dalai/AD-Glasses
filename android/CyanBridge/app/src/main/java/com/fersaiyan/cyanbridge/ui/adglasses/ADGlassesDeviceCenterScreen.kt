package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.devices.ADDeviceSupportPolicy
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

/** Product-facing device center. Vendor-specific details stay behind capability/runtime layers. */
@Composable
internal fun ADGlassesDeviceCenterScreen(
    state: GlassesDashboardUiState,
    host: ADHostActions,
    onBack: () -> Unit,
    onSync: () -> Unit,
    onFirmware: () -> Unit,
    onAdvanced: () -> Unit,
) {
    val context = LocalContext.current
    val profile = DeviceProfileStore.loadLastSelected(context)
        ?.takeIf { ADDeviceSupportPolicy.isPairable(it.selectedClass) }
    val presentation = buildADDevicePresentation(state, profile)
    val identity = presentation.identityLabel ?: if (profile == null) "No glasses connected" else "Glasses"

    ADPageLayout("Device Center", onBack) {
        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(ADColors.CyanSoft, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ADGlassesMark(Modifier.size(width = 42.dp, height = 24.dp))
                }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text(identity, style = MaterialTheme.typography.titleLarge)
                    Text(
                        presentation.statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
                ADStatusChip(
                    text = if (presentation.connected) "CONNECTED" else "OFFLINE",
                    tone = if (presentation.connected) ADStatusTone.SUCCESS else ADStatusTone.NEUTRAL,
                    showCheck = presentation.connected,
                )
            }

            if (presentation.connected &&
                ((state.showBattery && state.batteryPercent != null) || (state.showStorage && state.storageLabel != "--"))
            ) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = ADColors.Separator)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.showBattery && state.batteryPercent != null) {
                        ADDeviceStat(
                            icon = Icons.Outlined.BatteryFull,
                            label = "Battery",
                            value = "${state.batteryPercent}%",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (state.showStorage && state.storageLabel != "--") {
                        ADDeviceStat(
                            icon = Icons.Outlined.Storage,
                            label = "Storage",
                            value = state.storageLabel,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            when {
                presentation.connected -> Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(
                        onClick = host.onDisconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = ADColors.Graphite),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp),
                    ) { Text("Disconnect") }
                    OutlinedButton(
                        onClick = host.onOpenDeviceSetup,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp),
                    ) { Text("Change") }
                }
                profile != null -> Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(
                        onClick = host.onReconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = ADColors.Graphite),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp),
                    ) { Text("Reconnect") }
                    OutlinedButton(
                        onClick = host.onOpenDeviceSetup,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp),
                    ) { Text("Change") }
                }
                else -> Button(
                    onClick = host.onOpenDeviceSetup,
                    colors = ButtonDefaults.buttonColors(containerColor = ADColors.Graphite),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                ) { Text("Connect glasses") }
            }
        }

        ADSectionTitle("Device tools")
        ADCard {
            ADSettingsRow(
                icon = Icons.Outlined.Sync,
                title = "Sync media",
                subtitle = "Bring glasses captures into your library",
                onClick = onSync,
            )
            HorizontalDivider(Modifier.padding(start = 52.dp), color = ADColors.Separator)
            ADSettingsRow(
                icon = Icons.Outlined.SystemUpdateAlt,
                title = "Firmware",
                subtitle = "Updates and recovery with preflight checks",
                onClick = onFirmware,
                iconTint = ADColors.Ink,
                iconBackground = ADColors.SurfaceSubtle,
            )
            HorizontalDivider(Modifier.padding(start = 52.dp), color = ADColors.Separator)
            ADSettingsRow(
                icon = Icons.Outlined.DeveloperMode,
                title = "Advanced",
                subtitle = "Connection diagnostics and Android controls",
                onClick = onAdvanced,
                iconTint = ADColors.Ink,
                iconBackground = ADColors.SurfaceSubtle,
            )
        }

        Text(
            "Voice, camera and phone intelligence are exposed automatically when the connected glasses support them.",
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun ADDeviceStat(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ADColors.CyanDeep, modifier = Modifier.size(18.dp))
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
            Text(value, style = MaterialTheme.typography.labelLarge, color = ADColors.Ink)
        }
    }
}

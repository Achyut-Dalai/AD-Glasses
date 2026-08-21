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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Tune
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
                        .size(44.dp)
                        .background(
                            if (presentation.connected) ADColors.SuccessSoft else ADColors.SurfaceSubtle,
                            RoundedCornerShape(13.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Bluetooth,
                        contentDescription = null,
                        tint = if (presentation.connected) ADColors.Success else ADColors.Muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
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

            Spacer(Modifier.height(12.dp))

            when {
                presentation.connected -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = host.onDisconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                        modifier = Modifier.weight(1f),
                    ) { Text("Disconnect") }
                    OutlinedButton(
                        onClick = host.onOpenDeviceSetup,
                        modifier = Modifier.weight(1f),
                    ) { Text("Change glasses") }
                }
                profile != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = host.onReconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                        modifier = Modifier.weight(1f),
                    ) { Text("Reconnect") }
                    OutlinedButton(
                        onClick = host.onOpenDeviceSetup,
                        modifier = Modifier.weight(1f),
                    ) { Text("Change glasses") }
                }
                else -> Button(
                    onClick = host.onOpenDeviceSetup,
                    colors = ButtonDefaults.buttonColors(containerColor = ADColors.Blue),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Connect glasses") }
            }
        }

        ADSectionTitle("Glasses status")
        ADCard {
            ADDeviceMetric(
                icon = Icons.Outlined.Bluetooth,
                label = "Connection",
                value = if (presentation.connected) "Bluetooth connected" else "Not connected",
            )
            if (state.showBattery && state.batteryPercent != null) {
                HorizontalDivider(Modifier.padding(start = 30.dp), color = ADColors.Separator)
                ADDeviceMetric(
                    icon = Icons.Outlined.BatteryFull,
                    label = "Battery",
                    value = "${state.batteryPercent}%",
                )
            }
            if (state.showStorage && state.storageLabel != "--") {
                HorizontalDivider(Modifier.padding(start = 30.dp), color = ADColors.Separator)
                ADDeviceMetric(
                    icon = Icons.Outlined.Storage,
                    label = "Storage",
                    value = state.storageLabel,
                )
            }
        }

        ADSectionTitle("Capabilities")
        ADCard {
            ADDeviceCapability("Voice", "Ask and control through the glasses")
            HorizontalDivider(color = ADColors.Separator)
            ADDeviceCapability("Camera", "See and capture through the glasses camera")
            HorizontalDivider(color = ADColors.Separator)
            ADDeviceCapability("Phone intelligence", "AI, web, memory, tasks and phone actions")
        }

        ADSectionTitle("Device tools")
        ADCard {
            ADSettingsRow(
                icon = Icons.Outlined.Sync,
                title = "Sync media",
                subtitle = "Bring glasses captures into your library",
                onClick = onSync,
                iconTint = Color.White,
                iconBackground = ADColors.Blue,
            )
            HorizontalDivider(Modifier.padding(start = 42.dp), color = ADColors.Separator)
            ADSettingsRow(
                icon = Icons.Outlined.SystemUpdateAlt,
                title = "Firmware",
                subtitle = "Updates and recovery with preflight checks",
                onClick = onFirmware,
                iconTint = Color.White,
                iconBackground = ADColors.Warning,
            )
            HorizontalDivider(Modifier.padding(start = 42.dp), color = ADColors.Separator)
            ADSettingsRow(
                icon = Icons.Outlined.Tune,
                title = "Advanced",
                subtitle = "Connection diagnostics and Android controls",
                onClick = onAdvanced,
                iconTint = Color.White,
                iconBackground = ADColors.Muted,
            )
        }
    }
}

@Composable
private fun ADDeviceMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(26.dp).background(ADColors.SurfaceSubtle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(15.dp))
        }
        Text(
            label,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(value, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
    }
}

@Composable
private fun ADDeviceCapability(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(1.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
    }
}

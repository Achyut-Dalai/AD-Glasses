package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
) {
    val context = LocalContext.current
    val profile = DeviceProfileStore.loadLastSelected(context)
        ?.takeIf { ADDeviceSupportPolicy.isPairable(it.selectedClass) }
    val presentation = buildADDevicePresentation(state, profile)
    val identity = presentation.identityLabel ?: "Your glasses"
    val status = when {
        presentation.connected -> "Connected"
        presentation.connecting -> "Connecting…"
        profile != null -> "Ready to reconnect"
        else -> "Not connected"
    }
    val showBattery = presentation.connected && state.showBattery && state.batteryPercent != null
    val showStorage = presentation.connected && state.showStorage && state.storageLabel != "--"

    ADPageLayout("Device Center", onBack) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = ADColors.Ink,
                        contentColor = ADColors.Surface,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Bluetooth, contentDescription = null, modifier = Modifier.size(21.dp))
                        }
                    }
                    Column(Modifier.padding(start = 11.dp).weight(1f)) {
                        Text(
                            identity,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = ADColors.Muted,
                        )
                    }
                }

                if (showBattery || showStorage) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (showBattery) {
                            ADDeviceQuickMetric(
                                icon = Icons.Outlined.BatteryFull,
                                label = "Battery",
                                value = "${state.batteryPercent}%",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (showStorage) {
                            ADDeviceQuickMetric(
                                icon = Icons.Outlined.Storage,
                                label = "Storage",
                                value = state.storageLabel,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
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
                        colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Connect glasses") }
                }
            }
        }

        ADSectionTitle("Capabilities")
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ADDeviceCapabilityTile(
                icon = Icons.Outlined.Mic,
                title = "Voice",
                modifier = Modifier.weight(1f),
            )
            ADDeviceCapabilityTile(
                icon = Icons.Outlined.CameraAlt,
                title = "Camera",
                modifier = Modifier.weight(1f),
            )
            ADDeviceCapabilityTile(
                icon = Icons.Outlined.AutoAwesome,
                title = "AI",
                modifier = Modifier.weight(1f),
                emphasized = true,
            )
        }

        ADSectionTitle("Device tools")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ADDeviceToolTile(
                icon = Icons.Outlined.Sync,
                title = "Sync media",
                modifier = Modifier.weight(1.15f),
                emphasized = true,
                onClick = onSync,
            )
            ADDeviceToolTile(
                icon = Icons.Outlined.SystemUpdateAlt,
                title = "Firmware",
                modifier = Modifier.weight(0.85f),
                onClick = onFirmware,
            )
        }
    }
}

@Composable
private fun ADDeviceQuickMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(17.dp))
            Column(Modifier.padding(start = 7.dp).weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ADDeviceCapabilityTile(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = modifier.heightIn(min = 82.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (emphasized) ADColors.Ink else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (emphasized) ADColors.Surface else ADColors.Ink,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ADDeviceToolTile(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 92.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (emphasized) ADColors.Ink else MaterialTheme.colorScheme.surface,
        contentColor = if (emphasized) ADColors.Surface else ADColors.Ink,
        border = if (emphasized) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(35.dp),
                shape = RoundedCornerShape(11.dp),
                color = if (emphasized) ADColors.Surface.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (emphasized) ADColors.Surface else ADColors.Ink,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
    }
}

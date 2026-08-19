package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    onAdvanced: () -> Unit,
) {
    val context = LocalContext.current
    val profile = DeviceProfileStore.loadLastSelected(context)
        ?.takeIf { ADDeviceSupportPolicy.isPairable(it.selectedClass) }
    val presentation = buildADDevicePresentation(state, profile)
    val identity = presentation.identityLabel ?: "Your glasses"

    ADPageLayout("Device Center", onBack) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(19.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ADGlassesMark(Modifier.size(width = 30.dp, height = 17.dp))
                        }
                    }
                    Column(Modifier.padding(start = 11.dp).weight(1f)) {
                        Text(
                            identity,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(6.dp).background(
                                    if (presentation.connected) ADColors.Success else MaterialTheme.colorScheme.outline,
                                    CircleShape,
                                ),
                            )
                            Text(
                                when {
                                    presentation.connected -> "Connected"
                                    presentation.connecting -> "Connecting…"
                                    else -> "Not connected"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }

                    if (presentation.connected) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.showBattery && state.batteryPercent != null) {
                                ADDeviceValue(Icons.Outlined.BatteryFull, "${state.batteryPercent}%")
                            }
                            if (state.showStorage && state.storageLabel != "--") {
                                ADDeviceValue(Icons.Outlined.Storage, state.storageLabel)
                            }
                        }
                    }
                }

                when {
                    presentation.connected -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = host.onDisconnect, modifier = Modifier.weight(1f)) {
                            Text("Disconnect")
                        }
                        Button(onClick = host.onOpenDeviceSetup, modifier = Modifier.weight(1f)) {
                            Text("Change glasses")
                        }
                    }
                    profile != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = host.onReconnect, modifier = Modifier.weight(1f)) {
                            Text("Reconnect")
                        }
                        OutlinedButton(onClick = host.onOpenDeviceSetup, modifier = Modifier.weight(1f)) {
                            Text("Change glasses")
                        }
                    }
                    else -> Button(onClick = host.onOpenDeviceSetup, modifier = Modifier.fillMaxWidth()) {
                        Text("Connect glasses")
                    }
                }
            }
        }

        ADSectionTitle("Device tools")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                ADSettingsRow(
                    icon = Icons.Outlined.Sync,
                    title = "Sync media",
                    subtitle = "Bring captures into Library",
                    onClick = onSync,
                )
                HorizontalDivider(Modifier.padding(start = 47.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ADSettingsRow(
                    icon = Icons.Outlined.SystemUpdateAlt,
                    title = "Firmware",
                    subtitle = "Updates and recovery",
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = onFirmware,
                )
                HorizontalDivider(Modifier.padding(start = 47.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ADSettingsRow(
                    icon = Icons.Outlined.DeveloperMode,
                    title = "Advanced",
                    subtitle = "Diagnostics and Android controls",
                    iconTint = MaterialTheme.colorScheme.secondary,
                    iconBackground = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onAdvanced,
                )
            }
        }
    }
}

@Composable
private fun ADDeviceValue(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = Modifier.size(14.dp))
            Text(
                value,
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

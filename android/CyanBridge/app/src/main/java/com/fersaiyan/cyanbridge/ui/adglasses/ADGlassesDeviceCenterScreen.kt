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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdateAlt
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
    val identity = presentation.identityLabel ?: if (profile == null) "No glasses connected" else "Your glasses"
    val connectionDetail = when {
        presentation.connected -> "Connected"
        presentation.connecting -> "Connecting…"
        profile != null -> "Ready to reconnect"
        else -> "Connect to manage your glasses"
    }

    ADPageLayout("Device Center", onBack) {
        Text(
            "Connection, device health and the tools that belong to your glasses—nothing else.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = ADColors.Ink,
            contentColor = ADColors.Surface,
        ) {
            Column(Modifier.padding(17.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(17.dp),
                        color = ADColors.Surface.copy(alpha = 0.13f),
                        contentColor = ADColors.Surface,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Bluetooth, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            identity,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(8.dp).background(
                                    if (presentation.connected) ADColors.Success else ADColors.Surface.copy(alpha = 0.42f),
                                    CircleShape,
                                ),
                            )
                            Text(
                                connectionDetail,
                                modifier = Modifier.padding(start = 7.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = ADColors.Surface.copy(alpha = 0.68f),
                            )
                        }
                    }
                }

                if (presentation.connected) {
                    val showBattery = state.showBattery && state.batteryPercent != null
                    val showStorage = state.showStorage && state.storageLabel != "--"
                    if (showBattery || showStorage) {
                        Spacer(Modifier.height(15.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (showBattery) {
                                ADDeviceMetric(
                                    icon = Icons.Outlined.BatteryFull,
                                    label = "Battery",
                                    value = "${state.batteryPercent}%",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (showStorage) {
                                ADDeviceMetric(
                                    icon = Icons.Outlined.Storage,
                                    label = "Storage",
                                    value = state.storageLabel,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(15.dp))
                when {
                    presentation.connected -> {
                        ADPrimaryButton(text = "Disconnect", onClick = host.onDisconnect)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = host.onOpenDeviceSetup,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(17.dp),
                            border = BorderStroke(1.dp, ADColors.Surface.copy(alpha = 0.28f)),
                        ) {
                            Text("Change glasses", color = ADColors.Surface)
                        }
                    }
                    profile != null -> {
                        ADPrimaryButton(text = "Reconnect", onClick = host.onReconnect)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = host.onOpenDeviceSetup,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(17.dp),
                            border = BorderStroke(1.dp, ADColors.Surface.copy(alpha = 0.28f)),
                        ) {
                            Text("Change glasses", color = ADColors.Surface)
                        }
                    }
                    else -> ADPrimaryButton(text = "Connect glasses", onClick = host.onOpenDeviceSetup)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ADSectionTitle("Capabilities")
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ADDeviceCapability(
                    icon = Icons.Outlined.GraphicEq,
                    title = "Voice",
                    detail = "Ask and control",
                    modifier = Modifier.weight(1f),
                )
                ADDeviceCapability(
                    icon = Icons.Outlined.CameraAlt,
                    title = "Camera",
                    detail = "See and capture",
                    modifier = Modifier.weight(1f),
                )
                ADDeviceCapability(
                    icon = Icons.Outlined.Psychology,
                    title = "AI",
                    detail = "Phone intelligence",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ADSectionTitle("Device tools")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = ADColors.Surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    ADSettingsRow(
                        icon = Icons.Outlined.Sync,
                        title = "Sync media",
                        subtitle = "Bring glasses captures into your library",
                        onClick = onSync,
                    )
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ADSettingsRow(
                        icon = Icons.Outlined.SystemUpdateAlt,
                        title = "Firmware",
                        subtitle = "Updates and recovery with preflight checks",
                        onClick = onFirmware,
                    )
                }
            }
        }
    }
}

@Composable
private fun ADDeviceMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 62.dp),
        shape = RoundedCornerShape(17.dp),
        color = ADColors.Surface.copy(alpha = 0.11f),
        contentColor = ADColors.Surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.Surface.copy(alpha = 0.55f))
                Text(
                    value,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ADDeviceCapability(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 108.dp),
        shape = RoundedCornerShape(21.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier.size(38.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(19.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = ADColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

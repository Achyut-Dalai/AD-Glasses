package com.fersaiyan.cyanbridge.ui.adglasses

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
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.TipsAndUpdates
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
import androidx.compose.ui.text.font.FontWeight
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
        ADPageHero(
            icon = Icons.Outlined.Bluetooth,
            title = identity,
            detail = if (presentation.connected) {
                "${presentation.statusLabel}. Your glasses are ready for AI, capture and local media sync."
            } else {
                "${presentation.statusLabel}. Connect to restore glasses controls."
            },
            status = if (presentation.connected) "CONNECTED" else "OFFLINE",
            statusTone = if (presentation.connected) ADStatusTone.SUCCESS else ADStatusTone.NEUTRAL,
        ) {
            when {
                presentation.connected -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = host.onDisconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Disconnect") }
                    OutlinedButton(
                        onClick = host.onOpenDeviceSetup,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Change") }
                }

                profile != null -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = host.onReconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Reconnect") }
                    OutlinedButton(
                        onClick = host.onOpenDeviceSetup,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Change") }
                }

                else -> Button(
                    onClick = host.onOpenDeviceSetup,
                    colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Connect glasses") }
            }
        }

        if (presentation.connected || state.showBattery || state.showStorage) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ADSectionEyebrow("At a glance")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ADMetricBlock(
                        label = "Connection",
                        value = if (presentation.connected) "Bluetooth" else "Offline",
                        modifier = Modifier.weight(1f),
                    )
                    ADMetricBlock(
                        label = "Battery",
                        value = state.batteryPercent?.let { "$it%" } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                }
                ADMetricBlock(
                    label = "Storage",
                    value = state.storageLabel.takeUnless { it == "--" }.orEmpty().ifBlank { "Available after connection" },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ADSectionEyebrow("What these glasses can do")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ADDeviceCapabilityCard(
                    icon = Icons.Outlined.RecordVoiceOver,
                    title = "Voice",
                    detail = "Ask and control",
                    modifier = Modifier.weight(1f),
                )
                ADDeviceCapabilityCard(
                    icon = Icons.Outlined.PhotoCamera,
                    title = "Vision",
                    detail = "See and capture",
                    modifier = Modifier.weight(1f),
                )
            }
            ADDeviceCapabilityCard(
                icon = Icons.Outlined.TipsAndUpdates,
                title = "Phone intelligence",
                detail = "AI, web, memory, tasks and supported Android actions stay powered by the phone.",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ADSectionEyebrow("Device tools")
            ADCard {
                ADSettingsRow(
                    icon = Icons.Outlined.Sync,
                    title = "Sync media",
                    subtitle = "Bring glasses captures into Library",
                    onClick = onSync,
                )
                HorizontalDivider(Modifier.padding(start = 52.dp), color = ADColors.Separator)
                ADSettingsRow(
                    icon = Icons.Outlined.SystemUpdateAlt,
                    title = "Firmware",
                    subtitle = "Validated updates with staged preflight checks",
                    onClick = onFirmware,
                )
                HorizontalDivider(Modifier.padding(start = 52.dp), color = ADColors.Separator)
                ADSettingsRow(
                    icon = Icons.Outlined.DeveloperMode,
                    title = "Advanced",
                    subtitle = "Diagnostics and Android system controls",
                    onClick = onAdvanced,
                )
            }
        }
    }
}

@Composable
private fun ADDeviceCapabilityCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(ADColors.Surface, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(ADColors.SurfaceSubtle, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
    }
}

package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Storage
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
        ADScreenIntro(
            eyebrow = "Hardware",
            title = "Your glasses, at a glance",
            detail = "Connection, device health and the tools that belong to the glasses—kept separate from app settings.",
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = ADColors.Ink,
            contentColor = ADColors.Surface,
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = ADColors.Surface.copy(alpha = 0.13f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ADGlyphIcon(ADGlyph.DEVICE, ADColors.Surface, Modifier.size(30.dp))
                        }
                    }
                    Column(Modifier.padding(start = 13.dp).weight(1f)) {
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
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
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

                Spacer(Modifier.height(16.dp))
                when {
                    presentation.connected -> {
                        ADPrimaryButton(text = "Disconnect", onClick = host.onDisconnect)
                        Spacer(Modifier.height(8.dp))
                        ADDeviceSecondaryAction("Change glasses", host.onOpenDeviceSetup)
                    }
                    profile != null -> {
                        ADPrimaryButton(text = "Reconnect", onClick = host.onReconnect)
                        Spacer(Modifier.height(8.dp))
                        ADDeviceSecondaryAction("Change glasses", host.onOpenDeviceSetup)
                    }
                    else -> ADPrimaryButton(text = "Connect glasses", onClick = host.onOpenDeviceSetup)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ADSectionTitle("Capabilities")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ADDeviceCapability(
                    glyph = ADGlyph.ASK,
                    title = "Voice",
                    detail = "Ask and control",
                    modifier = Modifier.weight(1f),
                )
                ADDeviceCapability(
                    glyph = ADGlyph.PHOTO,
                    title = "Camera",
                    detail = "See and capture",
                    modifier = Modifier.weight(1f),
                )
                ADDeviceCapability(
                    glyph = ADGlyph.AI,
                    title = "AI",
                    detail = "Phone intelligence",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ADSectionTitle("Device tools")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = ADColors.Surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(7.dp)) {
                    ADDeviceTool(
                        glyph = ADGlyph.SYNC,
                        title = "Sync media",
                        detail = "Bring glasses captures into your library",
                        onClick = onSync,
                    )
                    ADDeviceTool(
                        glyph = ADGlyph.FIRMWARE,
                        title = "Firmware",
                        detail = "Updates and recovery with preflight checks",
                        onClick = onFirmware,
                    )
                }
            }
        }
    }
}

@Composable
private fun ADDeviceSecondaryAction(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ADColors.Surface.copy(alpha = 0.28f)),
    ) {
        Text(text, color = ADColors.Surface, style = MaterialTheme.typography.labelLarge)
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
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(18.dp),
        color = ADColors.Surface.copy(alpha = 0.11f),
        contentColor = ADColors.Surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.Surface.copy(alpha = 0.55f))
                Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ADDeviceCapability(
    glyph: ADGlyph,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 116.dp),
        shape = RoundedCornerShape(22.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier.size(42.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(23.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted, maxLines = 2)
            }
        }
    }
}

@Composable
private fun ADDeviceTool(
    glyph: ADGlyph,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(25.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 2)
        }
        Surface(shape = CircleShape, color = ADColors.SurfaceSubtle) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ADColors.Muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

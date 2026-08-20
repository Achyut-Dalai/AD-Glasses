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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.devices.ADDeviceSupportPolicy
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

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
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            color = Color.Black.copy(alpha = 0.40f),
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Column(Modifier.padding(13.dp)) {
                Text("DEVICE", style = MaterialTheme.typography.labelSmall, color = ADColors.InkSoft)
                Spacer(Modifier.size(3.dp))
                Text(
                    identity,
                    style = MaterialTheme.typography.titleLarge,
                    color = ADColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(6.dp).background(
                            when {
                                presentation.connected -> ADColors.Success
                                presentation.connecting -> ADColors.Warning
                                else -> ADColors.Red
                            },
                            CircleShape,
                        ),
                    )
                    Text(
                        connectionDetail,
                        modifier = Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }

                if (presentation.connected) {
                    val showBattery = state.showBattery && state.batteryPercent != null
                    val showStorage = state.showStorage && state.storageLabel != "--"
                    if (showBattery || showStorage) {
                        Spacer(Modifier.size(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (showBattery) ADDeviceMetric("BATTERY", "${state.batteryPercent}%", Modifier.weight(1f))
                            if (showStorage) ADDeviceMetric("STORAGE", state.storageLabel, Modifier.weight(1f))
                        }
                    }
                }

                Spacer(Modifier.size(10.dp))
                when {
                    presentation.connected -> {
                        ADPrimaryButton(text = "Disconnect", onClick = host.onDisconnect)
                        Spacer(Modifier.size(6.dp))
                        ADDeviceSecondaryAction("Change glasses", host.onOpenDeviceSetup)
                    }
                    profile != null -> {
                        ADPrimaryButton(text = "Reconnect", onClick = host.onReconnect)
                        Spacer(Modifier.size(6.dp))
                        ADDeviceSecondaryAction("Change glasses", host.onOpenDeviceSetup)
                    }
                    else -> ADPrimaryButton(text = "Connect glasses", onClick = host.onOpenDeviceSetup)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSectionTitle("Capabilities")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ADDeviceCapability(ADGlyph.ASK, "Voice", "Ask & control", Modifier.weight(1f))
                ADDeviceCapability(ADGlyph.PHOTO, "Camera", "See & capture", Modifier.weight(1f))
                ADDeviceCapability(ADGlyph.AI, "AI", "Phone intelligence", Modifier.weight(1f))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSectionTitle("Device tools")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = ADColors.Surface.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    ADDeviceTool(ADGlyph.SYNC, "Sync media", "Bring captures into your library", onSync)
                    ADDeviceTool(ADGlyph.FIRMWARE, "Firmware", "Updates and recovery", onFirmware)
                }
            }
        }
    }
}

@Composable
private fun ADDeviceSecondaryAction(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Text(text, color = ADColors.Ink, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ADDeviceMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(10.dp),
        color = ADColors.SurfaceSubtle,
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
            Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        modifier = modifier.heightIn(min = 82.dp),
        shape = RoundedCornerShape(12.dp),
        color = ADColors.Surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(modifier = Modifier.padding(9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            ADGlyphIcon(
                glyph,
                ADColors.Ink,
                Modifier.size(20.dp),
                accent = if (glyph == ADGlyph.AI) ADColors.Red else null,
            )
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted, maxLines = 1)
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ADGlyphIcon(
            glyph,
            ADColors.Ink,
            Modifier.size(20.dp),
            accent = if (glyph == ADGlyph.FIRMWARE) ADColors.Red else null,
        )
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1)
        }
        ADGlyphIcon(ADGlyph.NEXT, ADColors.Muted, Modifier.size(16.dp))
    }
}

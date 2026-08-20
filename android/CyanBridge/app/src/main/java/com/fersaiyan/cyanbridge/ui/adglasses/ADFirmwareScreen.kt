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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

/** Device-level firmware surface; backend behavior is unchanged. */
@Composable
internal fun ADFirmwareScreen(
    state: GlassesDashboardUiState,
    host: ADHostActions,
    onBack: () -> Unit,
) {
    val ota = state.ota
    val otaProgress = ota.progress
    val supportedProfile = state.showHeyCyanControls
    val bluetoothReady = supportedProfile && state.connectionLabel.startsWith("Connected", ignoreCase = true)
    var riskAcknowledged by remember { mutableStateOf(false) }

    ADPageLayout("Firmware", onBack) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.40f),
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ADGlyphIcon(
                        ADGlyph.FIRMWARE,
                        ADColors.Ink,
                        Modifier.size(22.dp),
                        accent = if (otaProgress != null) ADColors.Red else null,
                    )
                    Column(Modifier.padding(start = 9.dp).weight(1f)) {
                        Text("FIRMWARE", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                        Text(
                            ota.stateLabel.ifBlank { "Ready" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (otaProgress != null) Box(Modifier.size(6.dp).background(ADColors.Red, CircleShape))
                }
                Text(
                    ota.detail.ifBlank { "No firmware session is active." },
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                )
                if (otaProgress != null) {
                    Spacer(Modifier.height(9.dp))
                    LinearProgressIndicator(
                        progress = { (otaProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = ADColors.Red,
                        trackColor = ADColors.SurfaceSubtle,
                    )
                    Text(
                        "${otaProgress.coerceIn(0, 100)}%",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = ADColors.Muted,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSectionTitle("Preflight")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ADFirmwareCheckCard("Support", "Validated path", supportedProfile, Modifier.weight(1f))
                ADFirmwareCheckCard("Bluetooth", "Glasses connected", bluetoothReady, Modifier.weight(1f))
            }
        }

        if (!supportedProfile) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = ADColors.Surface.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text("Firmware isn’t available for these glasses yet.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Updates appear once this hardware has a validated firmware path.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
            }
        }

        if (ota.canStart && supportedProfile) {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { riskAcknowledged = !riskAcknowledged },
                shape = RoundedCornerShape(12.dp),
                color = ADColors.Surface.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, if (riskAcknowledged) ADColors.Ink.copy(alpha = .28f) else ADColors.Outline),
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(30.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (riskAcknowledged) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                            null,
                            tint = if (riskAcknowledged) ADColors.Ink else ADColors.Warning,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Column(Modifier.padding(start = 9.dp).weight(1f)) {
                        Text("I understand the update risk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text("Keep glasses connected until complete", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                    }
                    Box(Modifier.size(5.dp).background(if (riskAcknowledged) ADColors.Red else ADColors.Warning, CircleShape))
                }
            }

            Surface(
                onClick = host.onChooseFirmwareFiles,
                enabled = bluetoothReady && riskAcknowledged,
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (bluetoothReady && riskAcknowledged) ADColors.Red else ADColors.SurfaceSubtle,
                contentColor = if (bluetoothReady && riskAcknowledged) Color.White else ADColors.Muted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Choose firmware files", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (ota.canCancel) {
            OutlinedButton(
                onClick = host.onCancelFirmware,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape = RoundedCornerShape(11.dp),
                border = BorderStroke(1.dp, ADColors.Red.copy(alpha = .55f)),
            ) {
                Icon(Icons.Outlined.StopCircle, null, tint = ADColors.Red, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Cancel update", color = ADColors.Red, style = MaterialTheme.typography.labelLarge)
            }
        }

        Text(
            "Use validated files only. Never interrupt the glasses during an update.",
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
        )
    }
}

@Composable
private fun ADFirmwareCheckCard(
    title: String,
    detail: String,
    ready: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(12.dp),
        color = ADColors.Surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(modifier = Modifier.padding(9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    null,
                    tint = if (ready) ADColors.Ink else ADColors.Muted,
                    modifier = Modifier.size(16.dp),
                )
                if (!ready) {
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(4.dp).background(ADColors.Red, CircleShape))
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted, maxLines = 1)
            }
        }
    }
}

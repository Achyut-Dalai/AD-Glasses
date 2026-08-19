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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

/**
 * Product-facing firmware surface. The current OTA backend is HeyCyan-specific,
 * but the UI remains glasses-generic so another hardware adapter can reuse it later.
 */
@Composable
internal fun ADFirmwareScreen(
    state: GlassesDashboardUiState,
    host: ADHostActions,
    onBack: () -> Unit,
) {
    val ota = state.ota
    val otaProgress = ota.progress
    val supportedProfile = state.showHeyCyanControls
    val bluetoothReady = supportedProfile &&
        state.connectionLabel.startsWith("Connected", ignoreCase = true)
    var riskAcknowledged by remember { mutableStateOf(false) }

    ADPageLayout("Firmware", onBack) {
        Text(
            "Firmware is a device-level operation, so this screen stays deliberate: check readiness first, then update.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
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
                            ADGlyphIcon(ADGlyph.FIRMWARE, ADColors.Surface, Modifier.size(30.dp))
                        }
                    }
                    Column(Modifier.padding(start = 13.dp).weight(1f)) {
                        Text(
                            ota.stateLabel.ifBlank { "Firmware ready" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            ota.detail.ifBlank { "No firmware session is active" },
                            style = MaterialTheme.typography.bodySmall,
                            color = ADColors.Surface.copy(alpha = 0.68f),
                        )
                    }
                }

                if (otaProgress != null) {
                    Spacer(Modifier.height(17.dp))
                    LinearProgressIndicator(
                        progress = { (otaProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = ADColors.Surface,
                        trackColor = ADColors.Surface.copy(alpha = 0.16f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${otaProgress.coerceIn(0, 100)}% complete",
                        style = MaterialTheme.typography.labelSmall,
                        color = ADColors.Surface.copy(alpha = 0.68f),
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ADSectionTitle("Preflight")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ADFirmwareCheckCard(
                    title = "Support",
                    detail = "Validated firmware path",
                    ready = supportedProfile,
                    modifier = Modifier.weight(1f),
                )
                ADFirmwareCheckCard(
                    title = "Bluetooth",
                    detail = "Glasses connected",
                    ready = bluetoothReady,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (!supportedProfile) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = ADColors.Surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(15.dp)) {
                    Text("Firmware isn’t available for these glasses yet.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Updates appear here once the connected glasses have a validated firmware path.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
            }
        }

        if (ota.canStart && supportedProfile) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { riskAcknowledged = !riskAcknowledged },
                shape = RoundedCornerShape(22.dp),
                color = if (riskAcknowledged) ADColors.SurfaceSubtle else ADColors.Surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (riskAcknowledged) ADColors.Ink else ADColors.WarningSoft,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (riskAcknowledged) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = if (riskAcknowledged) ADColors.Surface else ADColors.Warning,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                    Column(Modifier.padding(start = 11.dp).weight(1f)) {
                        Text(
                            "I understand the update risk",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "Keep the glasses connected until the update finishes",
                            style = MaterialTheme.typography.bodySmall,
                            color = ADColors.Muted,
                        )
                    }
                    ADStatusChip(
                        if (riskAcknowledged) "READY" else "REQUIRED",
                        if (riskAcknowledged) ADStatusTone.SUCCESS else ADStatusTone.WARNING,
                    )
                }
            }

            ADPrimaryButton(
                text = "Choose firmware files",
                onClick = host.onChooseFirmwareFiles,
                enabled = bluetoothReady && riskAcknowledged,
            )
        }

        if (ota.canCancel) {
            OutlinedButton(
                onClick = host.onCancelFirmware,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Outlined.StopCircle, contentDescription = null, tint = ADColors.Error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(7.dp))
                Text("Cancel update", color = ADColors.Error, style = MaterialTheme.typography.labelLarge)
            }
        }

        Text(
            "Use validated files only. Firmware changes affect the glasses directly and should never be interrupted mid-update.",
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
        modifier = modifier.heightIn(min = 118.dp),
        shape = RoundedCornerShape(22.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (ready) ADColors.Ink else ADColors.SurfaceSubtle,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = if (ready) ADColors.Surface else ADColors.Muted,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
            }
        }
    }
}

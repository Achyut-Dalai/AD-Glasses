package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val bluetoothReady = supportedProfile && state.connectionLabel.startsWith("Connected", ignoreCase = true)
    val updateActive = otaProgress != null || ota.canCancel
    var riskAcknowledged by remember { mutableStateOf(false) }

    ADPageLayout("Firmware", onBack) {
        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .background(
                            if (updateActive) ADColors.WarningSoft else ADColors.SurfaceSubtle,
                            RoundedCornerShape(14.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.SystemUpdateAlt,
                        contentDescription = null,
                        tint = if (updateActive) ADColors.Warning else ADColors.Ink,
                        modifier = Modifier.size(22.dp),
                    )
                }
                androidx.compose.foundation.layout.Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(ota.stateLabel.ifBlank { "Firmware" }, style = MaterialTheme.typography.titleLarge)
                    Text(
                        ota.detail.ifBlank { "No firmware session is active" },
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
                if (updateActive) ADStatusChip("UPDATING", ADStatusTone.WARNING)
            }
            if (otaProgress != null) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { (otaProgress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = ADColors.Warning,
                    trackColor = ADColors.WarningSoft,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${otaProgress.coerceIn(0, 100)}% complete",
                    style = MaterialTheme.typography.labelLarge,
                    color = ADColors.Warning,
                )
            }
        }

        ADSectionTitle("Preflight")
        ADCard {
            ADFirmwareCheck("Firmware support", supportedProfile)
            HorizontalDivider(Modifier.padding(start = 34.dp), color = ADColors.Separator)
            ADFirmwareCheck("Bluetooth connected", bluetoothReady)
        }

        if (!supportedProfile) {
            ADCard {
                Text("Firmware isn’t available for these glasses yet.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Updates appear here once the connected glasses have a validated firmware path.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                )
            }
        }

        if (ota.canStart && supportedProfile) {
            ADCard(
                modifier = Modifier.fillMaxWidth().clickable { riskAcknowledged = !riskAcknowledged },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (riskAcknowledged) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = if (riskAcknowledged) ADColors.Success else ADColors.Warning,
                    )
                    Text(
                        "I understand firmware update risk",
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ADStatusChip(
                        if (riskAcknowledged) "READY" else "REQUIRED",
                        if (riskAcknowledged) ADStatusTone.SUCCESS else ADStatusTone.WARNING,
                    )
                }
            }

            Button(
                onClick = host.onChooseFirmwareFiles,
                enabled = bluetoothReady && riskAcknowledged,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Graphite),
            ) {
                Icon(Icons.Outlined.SystemUpdateAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Choose firmware files")
            }
        }

        if (ota.canCancel) {
            OutlinedButton(
                onClick = host.onCancelFirmware,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ADColors.Error),
            ) {
                Icon(Icons.Outlined.StopCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cancel update")
            }
        }

        Text(
            "Firmware changes affect the glasses directly. Use validated files and keep the glasses connected until the update finishes.",
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
        )
    }
}

@Composable
private fun ADFirmwareCheck(title: String, ready: Boolean) {
    Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (ready) ADColors.Success else ADColors.Muted,
            modifier = Modifier.size(20.dp),
        )
        Text(
            title,
            Modifier.padding(start = 10.dp).weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        ADStatusChip(
            if (ready) "READY" else "PENDING",
            if (ready) ADStatusTone.SUCCESS else ADStatusTone.NEUTRAL,
        )
    }
}

package com.fersaiyan.cyanbridge.ui.adglasses

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    val bluetoothReady = supportedProfile && state.connectionLabel.startsWith("Connected", ignoreCase = true)
    var riskAcknowledged by remember { mutableStateOf(false) }

    val sessionActive = otaProgress != null || ota.canCancel
    val heroDetail = when {
        ota.detail.isNotBlank() -> ota.detail
        sessionActive -> "Keep the glasses connected while the staged update is running."
        supportedProfile -> "Firmware uses a staged, validated update path with readiness checks before files are selected."
        else -> "Firmware updates are not available for the selected glasses yet."
    }

    ADPageLayout("Firmware", onBack) {
        ADPageHero(
            icon = Icons.Outlined.SystemUpdateAlt,
            title = ota.stateLabel.ifBlank { if (sessionActive) "Updating glasses" else "Firmware update" },
            detail = heroDetail,
            status = when {
                sessionActive -> "IN PROGRESS"
                ota.canStart && supportedProfile && bluetoothReady -> "READY"
                supportedProfile -> "CHECK"
                else -> "UNAVAILABLE"
            },
            statusTone = when {
                sessionActive -> ADStatusTone.WARNING
                ota.canStart && supportedProfile && bluetoothReady -> ADStatusTone.SUCCESS
                supportedProfile -> ADStatusTone.WARNING
                else -> ADStatusTone.NEUTRAL
            },
        ) {
            if (otaProgress != null) {
                LinearProgressIndicator(
                    progress = { (otaProgress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = ADColors.Warning,
                    trackColor = ADColors.WarningSoft,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${otaProgress.coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("Do not disconnect", style = MaterialTheme.typography.bodySmall, color = ADColors.Warning)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ADSectionEyebrow("Preflight")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ADFirmwareReadinessCard(
                    title = "Supported",
                    detail = if (supportedProfile) "Validated path" else "Not yet",
                    ready = supportedProfile,
                    modifier = Modifier.weight(1f),
                )
                ADFirmwareReadinessCard(
                    title = "Bluetooth",
                    detail = if (bluetoothReady) "Connected" else "Required",
                    ready = bluetoothReady,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (!supportedProfile) {
            ADCard {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = ADColors.Muted)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("No validated firmware path", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Updates appear here only after the connected glasses have a supported and validated firmware workflow.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ADColors.Muted,
                        )
                    }
                }
            }
        }

        if (ota.canStart && supportedProfile) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ADSectionEyebrow("Before you continue")
                ADCard(
                    modifier = Modifier.clickable { riskAcknowledged = !riskAcknowledged },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (riskAcknowledged) ADColors.SuccessSoft else ADColors.WarningSoft,
                                    RoundedCornerShape(14.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (riskAcknowledged) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = if (riskAcknowledged) ADColors.Success else ADColors.Warning,
                            )
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("I understand firmware update risk", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Use only the intended files and keep the glasses connected until AD reports completion.",
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
            }

            Button(
                onClick = host.onChooseFirmwareFiles,
                enabled = bluetoothReady && riskAcknowledged,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Warning),
            ) {
                Icon(Icons.Outlined.SystemUpdateAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Choose firmware files")
            }
        }

        if (ota.canCancel) {
            OutlinedButton(
                onClick = host.onCancelFirmware,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ADColors.Error),
            ) {
                Icon(Icons.Outlined.StopCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cancel update")
            }
        }

        Text(
            "Firmware is the one area of AD that directly changes software on the glasses, so the UI keeps readiness and risk explicit instead of hiding them behind a generic update button.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )
    }
}

@Composable
private fun ADFirmwareReadinessCard(
    title: String,
    detail: String,
    ready: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(ADColors.Surface, RoundedCornerShape(20.dp))
            .padding(15.dp),
    ) {
        Icon(
            if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (ready) ADColors.Success else ADColors.Muted,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(11.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
    }
}

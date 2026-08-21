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
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
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
        ADScreenIntro(
            eyebrow = "Device update",
            title = ota.stateLabel.ifBlank { "Firmware" },
            detail = ota.detail.ifBlank { "Update supported glasses using validated firmware files only." },
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = ADColors.Surface,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(46.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.SystemUpdate,
                            contentDescription = null,
                            tint = if (otaProgress != null) ADColors.Red else ADColors.Ink,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Column(Modifier.padding(start = 11.dp).weight(1f)) {
                        Text("FIRMWARE", style = ADMetaTextStyle, color = ADColors.Muted)
                        Text(
                            when {
                                otaProgress != null -> "Update in progress"
                                bluetoothReady -> "Ready for a validated file"
                                supportedProfile -> "Connect glasses to continue"
                                else -> "Not available for this hardware"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = ADColors.Ink,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Box(
                        Modifier.size(7.dp).background(
                            when {
                                otaProgress != null -> ADColors.Red
                                bluetoothReady -> ADColors.Success
                                else -> ADColors.Muted
                            },
                            CircleShape,
                        ),
                    )
                }

                if (otaProgress != null) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${otaProgress.coerceIn(0, 100)}%",
                            style = MaterialTheme.typography.headlineLarge,
                            color = ADColors.Ink,
                        )
                        Text(
                            " complete",
                            modifier = Modifier.padding(start = 5.dp, bottom = 3.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = ADColors.Muted,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (otaProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = ADColors.Red,
                        trackColor = ADColors.SurfaceSubtle,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSectionTitle("Preflight")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ADFirmwareCheckCard(
                    icon = Icons.Outlined.SystemUpdate,
                    title = "Hardware",
                    detail = if (supportedProfile) "Validated path" else "Not supported yet",
                    ready = supportedProfile,
                    modifier = Modifier.weight(1f),
                )
                ADFirmwareCheckCard(
                    icon = Icons.Outlined.Bluetooth,
                    title = "Connection",
                    detail = if (bluetoothReady) "Glasses connected" else "Connection required",
                    ready = bluetoothReady,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (!supportedProfile) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp),
                color = ADColors.Surface,
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Row(Modifier.padding(11.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(18.dp))
                    Column(Modifier.padding(start = 9.dp).weight(1f)) {
                        Text("Firmware is not available for these glasses yet", style = MaterialTheme.typography.titleMedium, color = ADColors.Ink)
                        Text(
                            "Updates appear only after this hardware has a validated update path.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ADColors.Muted,
                        )
                    }
                }
            }
        }

        if (ota.canStart && supportedProfile) {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { riskAcknowledged = !riskAcknowledged },
                shape = RoundedCornerShape(13.dp),
                color = ADColors.Surface,
                border = BorderStroke(1.dp, if (riskAcknowledged) ADColors.Ink.copy(alpha = .30f) else ADColors.Outline),
            ) {
                Row(modifier = Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(32.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (riskAcknowledged) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                            contentDescription = null,
                            tint = if (riskAcknowledged) ADColors.Success else ADColors.Warning,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(Modifier.padding(start = 9.dp).weight(1f)) {
                        Text("I understand the update risk", style = MaterialTheme.typography.titleMedium, color = ADColors.Ink, fontWeight = FontWeight.Medium)
                        Text("Keep glasses connected until the update completes", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                    }
                }
            }

            Surface(
                onClick = host.onChooseFirmwareFiles,
                enabled = bluetoothReady && riskAcknowledged,
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                shape = RoundedCornerShape(11.dp),
                color = if (bluetoothReady && riskAcknowledged) ADColors.Ink else ADColors.SurfaceSubtle,
                contentColor = if (bluetoothReady && riskAcknowledged) Color.Black else ADColors.Muted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Choose firmware files", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (ota.canCancel) {
            Surface(
                onClick = host.onCancelFirmware,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape = RoundedCornerShape(11.dp),
                color = ADColors.RedSoft,
                border = BorderStroke(1.dp, ADColors.Red.copy(alpha = .45f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = null, tint = ADColors.Red, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Cancel update", color = ADColors.Red, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Text(
            "Never interrupt the glasses while firmware is being written.",
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
        )
    }
}

@Composable
private fun ADFirmwareCheckCard(
    icon: ImageVector,
    title: String,
    detail: String,
    ready: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 92.dp),
        shape = RoundedCornerShape(13.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(31.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.Close,
                    contentDescription = null,
                    tint = if (ready) ADColors.Success else ADColors.Muted,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, color = ADColors.Ink, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 2)
            }
        }
    }
}

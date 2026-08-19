package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.devices.ADDeviceSupportPolicy
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

/** Native media-transfer status and control surface. */
@Composable
internal fun ADSyncScreen(
    state: GlassesDashboardUiState,
    host: ADHostActions,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val profile = DeviceProfileStore.loadLastSelected(context)
        ?.takeIf { ADDeviceSupportPolicy.isPairable(it.selectedClass) }
    val presentation = buildADDevicePresentation(state = state, profile = profile)
    val transfer = state.transfer
    val transferProgress = transfer.progress
    val knownCounts = transfer.countsLabel
        .takeUnless { it.isBlank() || it == "Photos: --  Videos: --  Audio: --" }
    val flow = transfer.flowLabel.takeUnless { it.isBlank() || it == "--" } ?: "Local Wi-Fi"

    ADPageLayout("Sync", onBack) {
        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(ADColors.BlueSoft, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Sync,
                        contentDescription = null,
                        tint = ADColors.Blue,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        when {
                            transfer.isVisible -> "Transfer in progress"
                            presentation.connected -> "Sync media"
                            else -> "Glasses offline"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        when {
                            transfer.isVisible -> transfer.detail
                            presentation.connected -> "Ready to bring captures onto this phone"
                            else -> "Connect glasses to begin"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
            }

            if (transfer.isVisible) {
                Spacer(Modifier.height(12.dp))
                if (transferProgress != null) {
                    LinearProgressIndicator(
                        progress = { transferProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = ADColors.Blue,
                        trackColor = ADColors.SurfaceSubtle,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${(transferProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                } else {
                    CircularProgressIndicator(
                        color = ADColors.Blue,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }

        ADSectionTitle("Details")
        ADCard {
            ADSyncMetricRow(
                Icons.Outlined.Bluetooth,
                "Connection",
                if (presentation.connected) presentation.identityLabel ?: "Connected" else presentation.statusLabel,
            )
            HorizontalDivider(Modifier.padding(start = 28.dp), color = ADColors.Separator)
            ADSyncMetricRow(Icons.Outlined.Wifi, "Transfer", flow)
            HorizontalDivider(Modifier.padding(start = 28.dp), color = ADColors.Separator)
            ADSyncMetricRow(Icons.Outlined.Storage, "Media", knownCounts ?: "Scanned when sync starts")
        }

        Button(
            onClick = when {
                transfer.isVisible -> host.onStopSync
                !presentation.connected -> host.onOpenDeviceSetup
                else -> host.onStartSync
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (transfer.isVisible) ADColors.Error else ADColors.Ink,
            ),
        ) {
            Icon(
                when {
                    transfer.isVisible -> Icons.Outlined.StopCircle
                    !presentation.connected -> Icons.Outlined.Bluetooth
                    else -> Icons.Outlined.Sync
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                when {
                    transfer.isVisible -> "Cancel transfer"
                    !presentation.connected -> "Connect glasses"
                    else -> "Start sync"
                },
            )
        }
    }
}

@Composable
private fun ADSyncMetricRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(18.dp))
        Text(
            label,
            Modifier.padding(start = 8.dp).weight(0.9f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            modifier = Modifier.weight(1.25f),
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val progress = transfer.progress
    val knownCounts = transfer.countsLabel
        .takeUnless { it.isBlank() || it == "Photos: --  Videos: --  Audio: --" }
    val flow = transfer.flowLabel.takeUnless { it.isBlank() || it == "--" } ?: "Local Wi-Fi"

    val title = when {
        transfer.isVisible -> "Bringing media over"
        presentation.connected -> "Sync from your glasses"
        else -> "Connect to sync"
    }
    val detail = when {
        transfer.isVisible -> transfer.detail.ifBlank { "Your glasses and phone are transferring captures locally." }
        presentation.connected -> "Photos, videos and audio can move from the glasses into Library over the supported local transfer path."
        else -> "Your glasses need to be connected before a media session can start."
    }

    ADPageLayout("Sync", onBack) {
        ADPageHero(
            icon = Icons.Outlined.Sync,
            title = title,
            detail = detail,
            status = when {
                transfer.isVisible -> "ACTIVE"
                presentation.connected -> "READY"
                else -> "OFFLINE"
            },
            statusTone = when {
                transfer.isVisible -> ADStatusTone.INFO
                presentation.connected -> ADStatusTone.SUCCESS
                else -> ADStatusTone.NEUTRAL
            },
        ) {
            if (transfer.isVisible) {
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = ADColors.Ink,
                        trackColor = ADColors.SurfaceSubtle,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        Text("Keep the glasses nearby", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = ADColors.Ink,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                        Text(
                            "Preparing local transfer…",
                            modifier = Modifier.padding(start = 11.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ADColors.Muted,
                        )
                    }
                }
            } else {
                Button(
                    onClick = if (presentation.connected) host.onStartSync else host.onOpenDeviceSetup,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                ) {
                    Icon(
                        if (presentation.connected) Icons.Outlined.Sync else Icons.Outlined.Bluetooth,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (presentation.connected) "Start sync" else "Connect glasses")
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ADSectionEyebrow("Transfer path")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ADMetricBlock(
                    label = "Glasses",
                    value = if (presentation.connected) presentation.identityLabel ?: "Connected" else "Offline",
                    modifier = Modifier.weight(1f),
                )
                ADMetricBlock(
                    label = "Link",
                    value = flow,
                    modifier = Modifier.weight(1f),
                )
            }
            ADMetricBlock(
                label = "Media detected",
                value = knownCounts ?: "Scanned when sync begins",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ADCard {
            ADSectionEyebrow("How sync works")
            Spacer(Modifier.height(7.dp))
            ADSyncExplanation(Icons.Outlined.Bluetooth, "Bluetooth coordinates the glasses session")
            ADSyncExplanation(Icons.Outlined.Wifi, "The supported local Wi-Fi path carries larger media")
            ADSyncExplanation(Icons.Outlined.Storage, "Finished captures appear in Library on this phone")
        }

        if (transfer.isVisible) {
            Button(
                onClick = host.onStopSync,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Error),
            ) {
                Icon(Icons.Outlined.StopCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cancel transfer")
            }
        }
    }
}

@Composable
private fun ADSyncExplanation(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(20.dp))
        Text(
            text,
            modifier = Modifier.padding(start = 11.dp).weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )
    }
}

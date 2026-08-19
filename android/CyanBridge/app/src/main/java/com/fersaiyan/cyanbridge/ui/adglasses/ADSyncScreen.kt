package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
        Text(
            "Bring captures from the glasses onto this phone. Transfer state stays visible without turning the page into a diagnostics panel.",
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
                            ADGlyphIcon(ADGlyph.SYNC, ADColors.Surface, Modifier.size(30.dp))
                        }
                    }
                    Column(Modifier.padding(start = 13.dp).weight(1f)) {
                        Text(
                            when {
                                transfer.isVisible -> "Transfer in progress"
                                presentation.connected -> "Ready to sync"
                                else -> "Connect your glasses"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            when {
                                transfer.isVisible -> transfer.detail
                                presentation.connected -> "New captures will be copied into your Library"
                                else -> "A glasses connection is required before transfer"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = ADColors.Surface.copy(alpha = 0.68f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (transfer.isVisible) {
                    Spacer(Modifier.height(17.dp))
                    if (transferProgress != null) {
                        LinearProgressIndicator(
                            progress = { transferProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            color = ADColors.Surface,
                            trackColor = ADColors.Surface.copy(alpha = 0.16f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${(transferProgress * 100).toInt()}% complete",
                            style = MaterialTheme.typography.labelSmall,
                            color = ADColors.Surface.copy(alpha = 0.68f),
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = ADColors.Surface,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                "Preparing transfer…",
                                style = MaterialTheme.typography.bodySmall,
                                color = ADColors.Surface.copy(alpha = 0.68f),
                                modifier = Modifier.padding(start = 9.dp),
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ADSectionTitle("Transfer details")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ADSyncMetricCard(
                    icon = Icons.Outlined.Bluetooth,
                    label = "Connection",
                    value = if (presentation.connected) presentation.identityLabel ?: "Connected" else presentation.statusLabel,
                    modifier = Modifier.weight(1f),
                )
                ADSyncMetricCard(
                    icon = Icons.Outlined.Wifi,
                    label = "Transport",
                    value = flow,
                    modifier = Modifier.weight(1f),
                )
            }
            ADSyncMetricCard(
                icon = Icons.Outlined.Storage,
                label = "Media",
                value = knownCounts ?: "Scanned when sync starts",
                modifier = Modifier.fillMaxWidth(),
                horizontal = true,
            )
        }

        ADPrimaryButton(
            text = when {
                transfer.isVisible -> "Cancel transfer"
                !presentation.connected -> "Connect glasses"
                else -> "Start sync"
            },
            onClick = when {
                transfer.isVisible -> host.onStopSync
                !presentation.connected -> host.onOpenDeviceSetup
                else -> host.onStartSync
            },
            destructive = transfer.isVisible,
        )
    }
}

@Composable
private fun ADSyncMetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
) {
    Surface(
        modifier = modifier.heightIn(min = if (horizontal) 76.dp else 112.dp),
        shape = RoundedCornerShape(22.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (horizontal) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(42.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                    Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier.size(42.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

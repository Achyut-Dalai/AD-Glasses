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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.40f),
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ADGlyphIcon(
                        glyph = ADGlyph.SYNC,
                        tint = ADColors.Ink,
                        modifier = Modifier.size(22.dp),
                        accent = if (transfer.isVisible) ADColors.Red else null,
                    )
                    Column(Modifier.padding(start = 9.dp).weight(1f)) {
                        Text("TRANSFER", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                        Text(
                            when {
                                transfer.isVisible -> "Sync in progress"
                                presentation.connected -> "Ready to sync"
                                else -> "Connect your glasses"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (transfer.isVisible) Box(Modifier.size(6.dp).background(ADColors.Red, CircleShape))
                }

                Text(
                    when {
                        transfer.isVisible -> transfer.detail
                        presentation.connected -> "Copy new glasses media into your Library."
                        else -> "A glasses connection is required before transfer."
                    },
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (transfer.isVisible) {
                    Spacer(Modifier.height(10.dp))
                    if (transferProgress != null) {
                        LinearProgressIndicator(
                            progress = { transferProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                            color = ADColors.Red,
                            trackColor = ADColors.SurfaceSubtle,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "${(transferProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = ADColors.Muted,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = ADColors.Red,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 1.5.dp,
                            )
                            Text(
                                "Preparing transfer…",
                                modifier = Modifier.padding(start = 7.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = ADColors.Muted,
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSectionTitle("Transfer details")
            ADSyncInfoRow(
                glyph = ADGlyph.BLUETOOTH,
                label = "Connection",
                value = if (presentation.connected) presentation.identityLabel ?: "Connected" else presentation.statusLabel,
            )
            ADSyncInfoRow(glyph = ADGlyph.NETWORK, label = "Transport", value = flow)
            ADSyncInfoRow(
                glyph = ADGlyph.STORAGE,
                label = "Media",
                value = knownCounts ?: "Scanned when sync starts",
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
private fun ADSyncInfoRow(
    glyph: ADGlyph,
    label: String,
    value: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        color = ADColors.Surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(30.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(16.dp))
            }
            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

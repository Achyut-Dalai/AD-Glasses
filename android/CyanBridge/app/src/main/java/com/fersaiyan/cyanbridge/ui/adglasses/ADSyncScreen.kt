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
        ADScreenIntro(
            eyebrow = "Library transfer",
            title = when {
                transfer.isVisible -> "Bringing media over"
                presentation.connected -> "Ready to sync"
                else -> "Connect to sync"
            },
            detail = when {
                transfer.isVisible -> transfer.detail
                presentation.connected -> "Copy new captures and recordings from your glasses into Library."
                else -> "Connect your glasses first, then transfer over the local link."
            },
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
                        ADMatrixGlyphIcon(
                            glyph = ADMatrixGlyph.SYNC,
                            tint = ADColors.Ink,
                            modifier = Modifier.size(27.dp),
                            accent = if (transfer.isVisible) ADColors.Red else null,
                        )
                    }
                    Column(Modifier.padding(start = 11.dp).weight(1f)) {
                        Text("TRANSFER", style = ADMetaTextStyle, color = ADColors.Muted)
                        Text(
                            when {
                                transfer.isVisible -> "Sync in progress"
                                presentation.connected -> "Connection ready"
                                else -> "Waiting for glasses"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = ADColors.Ink,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(
                                when {
                                    transfer.isVisible -> ADColors.Red
                                    presentation.connected -> ADColors.Success
                                    else -> ADColors.Muted
                                },
                                CircleShape,
                            ),
                    )
                }

                if (transfer.isVisible) {
                    Spacer(Modifier.height(14.dp))
                    if (transferProgress != null) {
                        val progress = transferProgress.coerceIn(0f, 1f)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${(progress * 100).toInt()}%",
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
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = ADColors.Red,
                            trackColor = ADColors.SurfaceSubtle,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = ADColors.Red,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 1.5.dp,
                            )
                            Text(
                                "Preparing transfer…",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ADColors.Muted,
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSectionTitle("Transfer details")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ADSyncMetric(
                    glyph = ADMatrixGlyph.LENS,
                    label = "Connection",
                    value = if (presentation.connected) presentation.identityLabel ?: "Connected" else presentation.statusLabel,
                    modifier = Modifier.weight(1f),
                )
                ADSyncMetric(
                    glyph = ADMatrixGlyph.RELAY,
                    label = "Transport",
                    value = flow,
                    modifier = Modifier.weight(1f),
                )
            }
            ADSyncMetric(
                glyph = ADMatrixGlyph.STORAGE,
                label = "Media",
                value = knownCounts ?: "Scanned when sync starts",
                modifier = Modifier.fillMaxWidth(),
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
private fun ADSyncMetric(
    glyph: ADMatrixGlyph,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 66.dp),
        shape = RoundedCornerShape(13.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ADMatrixGlyphIcon(glyph, ADColors.Ink, Modifier.size(18.dp))
            }
            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                Text(label.uppercase(), style = ADMetaTextStyle, color = ADColors.Muted)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ADColors.InkSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

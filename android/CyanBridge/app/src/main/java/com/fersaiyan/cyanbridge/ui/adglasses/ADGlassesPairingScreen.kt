package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.devices.ScannedDevice

/** Product pairing surface. The scanner decides which detected devices are eligible. */
@Composable
fun ADGlassesPairingScreen(
    devices: List<ScannedDevice>,
    isScanning: Boolean,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (ScannedDevice) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(ADColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ADTopBar(showBack = true, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 24.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "intro") {
                ADScreenIntro(
                    eyebrow = "Pairing",
                    title = "Connect your glasses",
                    detail = "Keep them nearby and ready to pair. AD Glasses only shows supported devices from the scanner.",
                )
            }

            item(key = "scanner") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    color = ADColors.Ink,
                    contentColor = ADColors.Surface,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ADScanVisual(isScanning = isScanning, found = devices.isNotEmpty())
                        Spacer(Modifier.size(16.dp))
                        Text(
                            when {
                                isScanning -> "Looking for nearby glasses"
                                devices.isNotEmpty() -> "Glasses found"
                                else -> "Ready when you are"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(
                            when {
                                isScanning -> "Scanning for supported glasses around this phone."
                                devices.isNotEmpty() -> "Choose a detected pair below to continue."
                                else -> "Start a scan when your glasses are nearby and ready to pair."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = ADColors.Surface.copy(alpha = 0.68f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.size(18.dp))
                        if (isScanning) {
                            OutlinedButton(
                                onClick = onStopScan,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, ADColors.Surface.copy(alpha = 0.28f)),
                            ) {
                                Icon(Icons.Outlined.Close, null, tint = ADColors.Surface, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(7.dp))
                                Text("Stop scanning", color = ADColors.Surface, style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Surface(
                                onClick = onScan,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = ADColors.Surface,
                                contentColor = ADColors.Ink,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(7.dp))
                                    Text(
                                        if (devices.isEmpty()) "Scan for glasses" else "Scan again",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (devices.isNotEmpty()) {
                item(key = "nearby-title") { ADSectionTitle("Nearby") }
                item(key = "nearby-list") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = ADColors.Surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                            devices.forEachIndexed { index, device ->
                                ADPairingDeviceRow(device = device, onClick = { onConnect(device) })
                                if (index != devices.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 52.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!isScanning && devices.isEmpty()) {
                item(key = "empty") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = ADColors.Surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(15.dp)) {
                            Text("Nothing nearby yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.size(3.dp))
                            Text(
                                "Check Bluetooth, keep the glasses close, and make sure they are not connected to another companion app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ADColors.Muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ADScanVisual(isScanning: Boolean, found: Boolean) {
    Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(146.dp).background(ADColors.Surface.copy(alpha = 0.05f), CircleShape))
        Box(Modifier.size(112.dp).background(ADColors.Surface.copy(alpha = 0.08f), CircleShape))
        Surface(
            modifier = Modifier.size(76.dp),
            shape = RoundedCornerShape(25.dp),
            color = ADColors.Surface.copy(alpha = 0.13f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                ADGlyphIcon(
                    ADGlyph.DEVICE,
                    if (found) ADColors.Surface else ADColors.Surface.copy(alpha = 0.86f),
                    Modifier.size(39.dp),
                )
            }
        }
        if (isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(102.dp),
                strokeWidth = 2.2.dp,
                color = ADColors.Surface.copy(alpha = 0.82f),
                trackColor = ADColors.Surface.copy(alpha = 0.12f),
            )
        }
    }
}

@Composable
private fun ADPairingDeviceRow(
    device: ScannedDevice,
    onClick: () -> Unit,
) {
    val deviceClass = device.detectedClass
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(42.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ADGlyphIcon(ADGlyph.DEVICE, ADColors.Ink, Modifier.size(24.dp))
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(
                device.advertisedName?.takeIf { it.isNotBlank() } ?: deviceClass.displayName(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (device.rssi != 0) signalLabel(device.rssi) else "Ready to connect",
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(shape = CircleShape, color = ADColors.SurfaceSubtle) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Connect",
                    tint = ADColors.Muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun signalLabel(rssi: Int): String = when {
    rssi >= -55 -> "Strong signal"
    rssi >= -70 -> "Good signal"
    rssi >= -82 -> "Nearby"
    else -> "Weak signal"
}

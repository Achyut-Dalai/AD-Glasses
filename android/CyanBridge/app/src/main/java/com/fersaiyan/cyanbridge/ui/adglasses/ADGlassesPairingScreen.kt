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
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    ADWallpaperBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            ADTopBar(title = "Pairing", showBack = true, onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                item(key = "scanner") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.76f),
                        border = BorderStroke(1.dp, ADColors.Outline),
                    ) {
                        Column(
                            modifier = Modifier.padding(13.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ADScanVisual(isScanning = isScanning, found = devices.isNotEmpty())
                            Spacer(Modifier.size(10.dp))
                            Text(
                                when {
                                    isScanning -> "Looking for nearby glasses"
                                    devices.isNotEmpty() -> "Glasses found"
                                    else -> "Connect your glasses"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                when {
                                    isScanning -> "Scanning for supported glasses around this phone."
                                    devices.isNotEmpty() -> "Choose a detected pair below."
                                    else -> "Keep them nearby and ready to pair."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = ADColors.Muted,
                            )
                            Spacer(Modifier.size(11.dp))
                            if (isScanning) {
                                OutlinedButton(
                                    onClick = onStopScan,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
                                    shape = RoundedCornerShape(11.dp),
                                    border = BorderStroke(1.dp, ADColors.Outline),
                                ) {
                                    Icon(Icons.Outlined.Close, null, tint = ADColors.Ink, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.size(5.dp))
                                    Text("Stop scanning", color = ADColors.Ink, style = MaterialTheme.typography.labelLarge)
                                }
                            } else {
                                Surface(
                                    onClick = onScan,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                                    shape = RoundedCornerShape(11.dp),
                                    color = ADColors.Red,
                                    contentColor = Color.White,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ADGlyphIcon(ADGlyph.SYNC, Color.White, Modifier.size(16.dp))
                                        Spacer(Modifier.size(5.dp))
                                        Text(if (devices.isEmpty()) "Scan for glasses" else "Scan again", style = MaterialTheme.typography.labelLarge)
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
                            shape = RoundedCornerShape(13.dp),
                            color = ADColors.Surface.copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, ADColors.Outline),
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp)) {
                                devices.forEachIndexed { index, device ->
                                    ADPairingDeviceRow(device = device, onClick = { onConnect(device) })
                                    if (index != devices.lastIndex) HorizontalDivider(color = ADColors.Separator)
                                }
                            }
                        }
                    }
                }

                if (!isScanning && devices.isEmpty()) {
                    item(key = "empty") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = ADColors.Surface.copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, ADColors.Outline),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Nothing nearby yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    "Check Bluetooth and keep the glasses close.",
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
}

@Composable
private fun ADScanVisual(isScanning: Boolean, found: Boolean) {
    Box(modifier = Modifier.size(94.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(56.dp)
                .background(ADColors.SurfaceSubtle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(if (found) 12.dp else 9.dp)
                    .background(if (found) ADColors.Success else ADColors.Ink, CircleShape),
            )
        }
        if (isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(90.dp),
                strokeWidth = 1.5.dp,
                color = ADColors.Red,
                trackColor = ADColors.SurfaceSubtle,
            )
        }
    }
}

@Composable
private fun ADPairingDeviceRow(device: ScannedDevice, onClick: () -> Unit) {
    val deviceClass = device.detectedClass
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = RoundedCornerShape(9.dp),
            color = ADColors.SurfaceSubtle,
            contentColor = ADColors.Ink,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
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
            )
        }
        ADGlyphIcon(ADGlyph.NEXT, ADColors.Muted, Modifier.size(16.dp))
    }
}

private fun signalLabel(rssi: Int): String = when {
    rssi >= -55 -> "Strong signal"
    rssi >= -70 -> "Good signal"
    rssi >= -82 -> "Nearby"
    else -> "Weak signal"
}

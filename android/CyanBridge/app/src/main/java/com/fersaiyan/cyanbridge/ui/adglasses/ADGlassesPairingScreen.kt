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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                item(key = "intro") {
                    ADScreenIntro(
                        eyebrow = "Connect glasses",
                        title = when {
                            isScanning -> "Looking nearby"
                            devices.isNotEmpty() -> "Glasses found"
                            else -> "Find your glasses"
                        },
                        detail = "Keep the glasses nearby and ready to pair. Only supported devices are shown.",
                    )
                }

                item(key = "scanner") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = ADColors.Surface,
                        contentColor = ADColors.Ink,
                        border = BorderStroke(1.dp, ADColors.Outline),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ADScanVisual(isScanning = isScanning, found = devices.isNotEmpty())
                            Spacer(Modifier.size(10.dp))
                            Text(
                                when {
                                    isScanning -> "Scanning for AD-compatible glasses"
                                    devices.isNotEmpty() -> "Choose a device below"
                                    else -> "Ready to scan"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = ADColors.Ink,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.size(11.dp))
                            Surface(
                                onClick = if (isScanning) onStopScan else onScan,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                                shape = RoundedCornerShape(11.dp),
                                color = if (isScanning) ADColors.SurfaceSubtle else ADColors.Ink,
                                contentColor = if (isScanning) ADColors.Ink else Color.Black,
                                border = if (isScanning) BorderStroke(1.dp, ADColors.Outline) else null,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (isScanning) Icons.Outlined.Close else Icons.Outlined.Search,
                                        contentDescription = null,
                                        tint = if (isScanning) ADColors.Ink else Color.Black,
                                        modifier = Modifier.size(17.dp),
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        if (isScanning) "Stop scanning" else if (devices.isEmpty()) "Scan for glasses" else "Scan again",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
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
                            shape = RoundedCornerShape(14.dp),
                            color = ADColors.Surface,
                            contentColor = ADColors.Ink,
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
                            shape = RoundedCornerShape(13.dp),
                            color = ADColors.Surface,
                            contentColor = ADColors.Ink,
                            border = BorderStroke(1.dp, ADColors.Outline),
                        ) {
                            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Info, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(18.dp))
                                Column(Modifier.padding(start = 9.dp)) {
                                    Text("Nothing nearby yet", style = MaterialTheme.typography.titleMedium, color = ADColors.Ink)
                                    Text("Wake the glasses and try scanning again.", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                                }
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
    Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(68.dp),
            shape = RoundedCornerShape(20.dp),
            color = ADColors.SurfaceSubtle,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Bluetooth,
                    contentDescription = null,
                    tint = ADColors.Ink,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        if (isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(100.dp),
                strokeWidth = 1.5.dp,
                color = ADColors.Red,
                trackColor = ADColors.SurfaceSubtle,
            )
        }
        if (found && !isScanning) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .background(ADColors.SuccessSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ADColors.Success, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun ADPairingDeviceRow(device: ScannedDevice, onClick: () -> Unit) {
    val deviceClass = device.detectedClass
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text(
                device.advertisedName?.takeIf { it.isNotBlank() } ?: deviceClass.displayName(),
                style = MaterialTheme.typography.titleMedium,
                color = ADColors.Ink,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (device.rssi != 0) signalLabel(device.rssi) else "Ready",
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
                maxLines = 1,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(17.dp))
    }
}

private fun signalLabel(rssi: Int): String = when {
    rssi >= -55 -> "Strong signal"
    rssi >= -70 -> "Good signal"
    rssi >= -82 -> "Nearby"
    else -> "Weak signal"
}

package com.fersaiyan.cyanbridge.ui.adglasses

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
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        ADTopBar(title = "Connect glasses", showBack = true, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 5.dp,
                bottom = 20.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "scanner") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ADScanVisual(isScanning = isScanning, found = devices.isNotEmpty())
                    Spacer(Modifier.size(14.dp))
                    Text(
                        when {
                            isScanning -> "Looking for nearby glasses"
                            devices.isNotEmpty() -> "Glasses found"
                            else -> "Find your glasses"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        when {
                            isScanning -> "Keep your glasses nearby and ready to pair."
                            devices.isNotEmpty() -> "Select a detected pair to connect."
                            else -> "Keep your glasses nearby and ready to pair, then scan again."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = ADColors.Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.size(13.dp))
                    if (isScanning) {
                        OutlinedButton(
                            onClick = onStopScan,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(7.dp))
                            Text("Stop scanning")
                        }
                    } else {
                        Button(
                            onClick = onScan,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Blue),
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(7.dp))
                            Text(if (devices.isEmpty()) "Scan for glasses" else "Scan again")
                        }
                    }
                }
            }

            if (devices.isNotEmpty()) {
                item(key = "nearby-title") {
                    Text(
                        "Nearby",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "nearby-list") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ADColors.Surface, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp),
                    ) {
                        devices.forEachIndexed { index, device ->
                            ADPairingDeviceRow(device = device, onClick = { onConnect(device) })
                            if (index != devices.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 43.dp),
                                    color = ADColors.Separator,
                                )
                            }
                        }
                    }
                }
            }

            if (!isScanning && devices.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ADColors.Surface, RoundedCornerShape(16.dp))
                            .padding(13.dp),
                    ) {
                        Text("No supported glasses found", style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun ADScanVisual(isScanning: Boolean, found: Boolean) {
    Box(modifier = Modifier.size(142.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .background(ADColors.BlueSoft.copy(alpha = 0.30f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(ADColors.BlueSoft.copy(alpha = 0.58f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(ADColors.Surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 2.25.dp,
                    color = ADColors.Blue,
                )
            }
            Icon(
                Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = if (found) ADColors.Success else ADColors.Ink,
                modifier = Modifier.size(24.dp),
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
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(ADColors.SurfaceSubtle, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = ADColors.Ink,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text(
                device.advertisedName?.takeIf { it.isNotBlank() } ?: deviceClass.displayName(),
                style = MaterialTheme.typography.titleMedium,
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
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "Connect",
            tint = ADColors.Muted,
            modifier = Modifier.size(19.dp),
        )
    }
}

private fun signalLabel(rssi: Int): String = when {
    rssi >= -55 -> "Strong signal"
    rssi >= -70 -> "Good signal"
    rssi >= -82 -> "Nearby"
    else -> "Weak signal"
}

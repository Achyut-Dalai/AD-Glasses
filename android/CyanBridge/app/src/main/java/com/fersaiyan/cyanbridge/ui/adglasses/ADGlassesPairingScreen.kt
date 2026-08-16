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
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.KeyboardArrowRight
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
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 30.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "scanner") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ADScanVisual(isScanning = isScanning, found = devices.isNotEmpty())
                    Spacer(Modifier.size(20.dp))
                    Text(
                        when {
                            isScanning -> "Looking for HeyCyan glasses"
                            devices.isNotEmpty() -> "Glasses found"
                            else -> "Find your glasses"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.size(7.dp))
                    Text(
                        when {
                            isScanning -> "Keep your glasses nearby and ready to pair."
                            devices.isNotEmpty() -> "Choose your glasses below to connect."
                            else -> "Keep the glasses nearby, then scan again."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = ADColors.Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                    Spacer(Modifier.size(18.dp))
                    if (isScanning) {
                        OutlinedButton(
                            onClick = onStopScan,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Stop scanning")
                        }
                    } else {
                        Button(
                            onClick = onScan,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(if (devices.isEmpty()) "Scan again" else "Refresh")
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
                            .background(ADColors.Surface, RoundedCornerShape(18.dp))
                            .padding(horizontal = 15.dp),
                    ) {
                        devices.forEachIndexed { index, device ->
                            ADPairingDeviceRow(device = device, onClick = { onConnect(device) })
                            if (index != devices.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 50.dp),
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
                            .background(ADColors.Surface, RoundedCornerShape(18.dp))
                            .padding(16.dp),
                    ) {
                        Text("No supported glasses found", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.size(5.dp))
                        Text(
                            "Make sure Bluetooth is on and the glasses are not connected to another companion app.",
                            style = MaterialTheme.typography.bodyMedium,
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
    Box(modifier = Modifier.size(178.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(166.dp)
                .background(ADColors.BlueSoft.copy(alpha = 0.35f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(118.dp)
                .background(ADColors.BlueSoft.copy(alpha = 0.62f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(ADColors.Surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(46.dp),
                    strokeWidth = 2.5.dp,
                    color = ADColors.Blue,
                )
            }
            Icon(
                Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = if (found) ADColors.Success else ADColors.Ink,
                modifier = Modifier.size(28.dp),
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
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = ADColors.Ink)
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(
                device.advertisedName?.takeIf { it.isNotBlank() } ?: deviceClass.displayName(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (device.rssi != 0) signalLabel(device.rssi) else "Ready to connect",
                style = MaterialTheme.typography.bodyMedium,
                color = ADColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = "Connect", tint = ADColors.Muted)
    }
}

private fun signalLabel(rssi: Int): String = when {
    rssi >= -55 -> "Strong signal"
    rssi >= -70 -> "Good signal"
    rssi >= -82 -> "Nearby"
    else -> "Weak signal"
}

package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.devices.ScannedDevice

/**
 * Product pairing surface for the hardware AD Glasses currently validates.
 * Classification happens before a device reaches this screen; the user never has
 * to choose an internal device type.
 */
@Composable
fun ADHeyCyanPairingScreen(
    devices: List<ScannedDevice>,
    isScanning: Boolean,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (ScannedDevice) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "Connect glasses", showBack = true, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = 30.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ADCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(ADColors.BlueSoft, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = ADColors.Blue,
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.BluetoothSearching,
                                    contentDescription = null,
                                    tint = ADColors.Blue,
                                )
                            }
                        }
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(
                                if (isScanning) "Looking for HeyCyan" else "Find your glasses",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                when {
                                    isScanning && devices.isEmpty() -> "Keep your glasses nearby and ready to connect."
                                    isScanning -> "${devices.size} ${if (devices.size == 1) "pair" else "pairs"} of HeyCyan glasses found"
                                    devices.isEmpty() -> "Scan for the HeyCyan glasses you want this phone to use."
                                    else -> "Tap your glasses to connect."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = ADColors.Muted,
                            )
                        }
                    }

                    Spacer(Modifier.size(16.dp))
                    if (isScanning) {
                        OutlinedButton(
                            onClick = onStopScan,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Stop scan")
                        }
                    } else {
                        Button(
                            onClick = onScan,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                        ) {
                            Icon(Icons.Outlined.BluetoothSearching, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(if (devices.isEmpty()) "Scan for glasses" else "Scan again")
                        }
                    }
                }
            }

            if (devices.isNotEmpty()) {
                item { ADSectionTitle("Nearby") }
                item {
                    ADCard {
                        devices.forEachIndexed { index, device ->
                            ADHeyCyanPairingRow(device = device, onClick = { onConnect(device) })
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
                item {
                    Text(
                        "If the glasses do not appear, make sure Bluetooth is on and another companion app is not actively connected, then scan again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ADColors.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ADHeyCyanPairingRow(
    device: ScannedDevice,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
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
                device.advertisedName?.takeIf { it.isNotBlank() } ?: "HeyCyan glasses",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append("HeyCyan")
                    if (device.rssi != 0) append(" · ${adSignalLabel(device.rssi)}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = ADColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = "Connect", tint = ADColors.Muted)
    }
}

private fun adSignalLabel(rssi: Int): String = when {
    rssi >= -55 -> "Strong"
    rssi >= -70 -> "Good"
    rssi >= -82 -> "Nearby"
    else -> "Weak"
}

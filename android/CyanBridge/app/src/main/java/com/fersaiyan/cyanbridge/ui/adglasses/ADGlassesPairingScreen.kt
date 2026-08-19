package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "scanner") {
                ADPairingScannerCard(
                    isScanning = isScanning,
                    found = devices.isNotEmpty(),
                    onScan = onScan,
                    onStopScan = onStopScan,
                )
            }

            if (devices.isNotEmpty()) {
                item(key = "nearby-title") { ADSectionTitle("Nearby") }
                item(key = "nearby-list") {
                    ADCard {
                        devices.forEachIndexed { index, device ->
                            ADPairingDeviceRow(device = device, onClick = { onConnect(device) })
                            if (index != devices.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 52.dp),
                                    color = ADColors.Separator,
                                )
                            }
                        }
                    }
                }
            }

            if (!isScanning && devices.isEmpty()) {
                item(key = "empty") {
                    ADCard {
                        Text("No supported glasses found", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.size(4.dp))
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
private fun ADPairingScannerCard(
    isScanning: Boolean,
    found: Boolean,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ADColors.Surface, shape)
            .border(1.dp, ADColors.Outline, shape)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ADScanVisual(isScanning = isScanning, found = found)
        Spacer(Modifier.size(14.dp))
        Text(
            when {
                isScanning -> "Looking for nearby glasses"
                found -> "Glasses found"
                else -> "Find your glasses"
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            when {
                isScanning -> "Keep them nearby and ready to pair."
                found -> "Choose a detected pair below."
                else -> "Keep them nearby and ready to pair, then scan again."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(16.dp))
        if (isScanning) {
            OutlinedButton(
                onClick = onStopScan,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(7.dp))
                Text("Stop scanning")
            }
        } else {
            Button(
                onClick = onScan,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Graphite),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(7.dp))
                Text(if (found) "Scan again" else "Scan for glasses")
            }
        }
    }
}

@Composable
private fun ADScanVisual(isScanning: Boolean, found: Boolean) {
    Box(modifier = Modifier.size(136.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.size(128.dp).background(ADColors.CyanSoft.copy(alpha = 0.45f), CircleShape),
        )
        Box(
            modifier = Modifier.size(94.dp).background(ADColors.CyanSoft.copy(alpha = 0.78f), CircleShape),
        )
        Box(
            modifier = Modifier.size(66.dp).background(ADColors.Surface, CircleShape).border(1.dp, ADColors.Outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(58.dp),
                    strokeWidth = 2.25.dp,
                    color = ADColors.Cyan,
                )
            }
            ADGlassesMark(Modifier.size(width = 42.dp, height = 24.dp))
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(34.dp)
                .background(if (found) ADColors.SuccessSoft else ADColors.Surface, CircleShape)
                .border(1.dp, ADColors.Outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = if (found) ADColors.Success else ADColors.CyanDeep,
                modifier = Modifier.size(18.dp),
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(ADColors.CyanSoft, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ADGlassesMark(Modifier.size(width = 31.dp, height = 18.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
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
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun signalLabel(rssi: Int): String = when {
    rssi >= -55 -> "Strong signal"
    rssi >= -70 -> "Good signal"
    rssi >= -82 -> "Nearby"
    else -> "Weak signal"
}

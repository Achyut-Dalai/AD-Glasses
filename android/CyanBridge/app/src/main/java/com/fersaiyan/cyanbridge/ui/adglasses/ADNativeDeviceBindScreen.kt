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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.devices.DeviceClass
import com.fersaiyan.cyanbridge.shared.devices.ScannedDevice

/** AD Glasses pairing surface. The Activity keeps ownership of scanner and transport logic. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ADNativeDeviceBindScreen(
    devices: List<ScannedDevice>,
    isScanning: Boolean,
    connectingDevice: ScannedDevice?,
    selectedClass: DeviceClass,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectDevice: (ScannedDevice) -> Unit,
    onSelectedClassChange: (DeviceClass) -> Unit,
    onConfirmConnection: () -> Unit,
    onDismissConnection: () -> Unit,
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
                            Modifier
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
                                if (isScanning) "Looking for glasses" else "Find nearby glasses",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                when {
                                    isScanning && devices.isEmpty() -> "Keep the glasses nearby and ready to pair."
                                    isScanning -> "${devices.size} nearby ${if (devices.size == 1) "device" else "devices"} found"
                                    devices.isEmpty() -> "Bluetooth and Nearby Devices are used only for discovery and connection."
                                    else -> "Choose the glasses you want AD Glasses to use."
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
                            ADPairingDeviceRow(device = device, onClick = { onSelectDevice(device) })
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
                        "If nothing appears, make sure another companion app is not actively connected to the glasses, then scan again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ADColors.Muted,
                    )
                }
            }
        }
    }

    connectingDevice?.let { device ->
        ModalBottomSheet(
            onDismissRequest = onDismissConnection,
            containerColor = ADColors.Surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Use these glasses", style = MaterialTheme.typography.headlineMedium)
                Text(
                    device.advertisedName?.takeIf { it.isNotBlank() } ?: "Nearby Bluetooth device",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Choose the hardware profile only if detection looks wrong. AD Glasses remembers your choice for this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ADColors.Muted,
                )

                ADCard {
                    DeviceClass.entries
                        .filter { it != DeviceClass.UNKNOWN }
                        .forEachIndexed { index, deviceClass ->
                            ADPairingClassRow(
                                deviceClass = deviceClass,
                                selected = selectedClass == deviceClass,
                                onClick = { onSelectedClassChange(deviceClass) },
                            )
                            if (index != DeviceClass.entries.count { it != DeviceClass.UNKNOWN } - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 46.dp),
                                    color = ADColors.Separator,
                                )
                            }
                        }
                }

                Button(
                    onClick = onConfirmConnection,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                ) {
                    Icon(Icons.Outlined.Bluetooth, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Connect")
                }
            }
        }
    }
}

@Composable
private fun ADPairingDeviceRow(
    device: ScannedDevice,
    onClick: () -> Unit,
) {
    val effectiveClass = device.effectiveSelectedClass()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = ADColors.Ink)
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(
                device.advertisedName?.takeIf { it.isNotBlank() } ?: effectiveClass.displayName(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(effectiveClass.displayName())
                    if (device.rssi != 0) append(" · ${signalLabel(device.rssi)}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = ADColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null, tint = ADColors.Muted)
    }
}

@Composable
private fun ADPairingClassRow(
    deviceClass: DeviceClass,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(
                    if (selected) ADColors.SuccessSoft else ADColors.SurfaceSubtle,
                    RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = if (selected) ADColors.Success else ADColors.Muted,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            deviceClass.displayName(),
            modifier = Modifier.padding(start = 11.dp).weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (selected) ADStatusChip("SELECTED", ADStatusTone.SUCCESS)
    }
}

private fun signalLabel(rssi: Int): String = when {
    rssi >= -55 -> "Strong"
    rssi >= -70 -> "Good"
    rssi >= -82 -> "Nearby"
    else -> "Weak"
}

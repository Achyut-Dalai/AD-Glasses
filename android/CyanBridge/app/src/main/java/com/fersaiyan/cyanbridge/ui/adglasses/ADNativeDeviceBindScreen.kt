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
import com.fersaiyan.cyanbridge.devices.ADDeviceSupportPolicy
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
                                    isScanning && devices.isEmpty() -> "Keep your HeyCyan glasses nearby and ready to pair."
                                    isScanning -> "${devices.size} possible ${if (devices.size == 1) "device" else "devices"} found"
                                    devices.isEmpty() -> "HeyCyan is the primary supported glasses profile. Meta is reserved for future use."
                                    else -> "Choose your glasses. Devices recognized as unsupported are hidden from setup."
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
                    "If detection is uncertain, choose between the two AD Glasses product profiles. Other upstream device types stay internal and are not enabled here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ADColors.Muted,
                )

                ADCard {
                    ADDeviceSupportPolicy.selectable.forEachIndexed { index, deviceClass ->
                        ADPairingClassRow(
                            deviceClass = deviceClass,
                            selected = selectedClass == deviceClass,
                            onClick = { onSelectedClassChange(deviceClass) },
                        )
                        if (index != ADDeviceSupportPolicy.selectable.lastIndex) {
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
    val classLabel = when {
        ADDeviceSupportPolicy.isValidated(effectiveClass) -> "HeyCyan"
        ADDeviceSupportPolicy.isPlanned(effectiveClass) -> "Meta Ray-Ban · future"
        else -> "Unidentified glasses"
    }
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
                device.advertisedName?.takeIf { it.isNotBlank() } ?: classLabel,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(classLabel)
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
    val detail = when {
        ADDeviceSupportPolicy.isValidated(deviceClass) -> "Supported now"
        ADDeviceSupportPolicy.isPlanned(deviceClass) -> "Reserved for future use"
        else -> "Not enabled"
    }
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
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(deviceClass.displayName(), style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        if (selected) ADStatusChip("SELECTED", ADStatusTone.SUCCESS)
    }
}

private fun signalLabel(rssi: Int): String = when {
    rssi >= -55 -> "Strong"
    rssi >= -70 -> "Good"
    rssi >= -82 -> "Nearby"
    else -> "Weak"
}

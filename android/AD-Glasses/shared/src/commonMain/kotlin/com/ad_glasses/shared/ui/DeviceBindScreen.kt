package com.ad_glasses.shared.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ad_glasses.shared.devices.DeviceClass
import com.ad_glasses.shared.devices.ScannedDevice
import com.ad_glasses.shared.generated.resources.*
import com.ad_glasses.shared.icons.AppIcon
import com.ad_glasses.shared.icons.imageVector
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun DeviceBindScreen(
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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.device_bind_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            AppIcon.Back.imageVector(),
                            contentDescription = stringResource(Res.string.action_back),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScanStage(
                    active = isScanning,
                    foundCount = devices.size,
                    onScan = onScan,
                    onStopScan = onStopScan,
                )
            }

            if (devices.isNotEmpty()) {
                item {
                    Text(
                        text = if (devices.size == 1) "1 nearby device" else "${devices.size} nearby devices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                items(devices, key = { it.macAddress }) { device ->
                    DeviceResultRow(device = device, onClick = { onSelectDevice(device) })
                }
            }
        }
    }

    connectingDevice?.let { device ->
        AlertDialog(
            onDismissRequest = onDismissConnection,
            title = { Text(stringResource(Res.string.device_bind_select_type)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = device.advertisedName ?: device.macAddress,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    DeviceClass.entries.filter { it != DeviceClass.UNKNOWN }.forEach { type ->
                        FilterChip(
                            selected = selectedClass == type,
                            onClick = { onSelectedClassChange(type) },
                            label = { Text(localizedDeviceClass(type)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmConnection) {
                    Text(stringResource(Res.string.action_connect))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissConnection) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun ScanStage(
    active: Boolean,
    foundCount: Int,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (active) ActiveScanVisual() else IdleScanVisual()
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (active) stringResource(Res.string.device_bind_scanning) else stringResource(Res.string.device_bind_scan),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = if (active && foundCount > 0) {
                    if (foundCount == 1) "1 device found" else "$foundCount devices found"
                } else if (active) {
                    stringResource(Res.string.device_bind_looking)
                } else {
                    "Bluetooth and Nearby Devices"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            if (active) {
                OutlinedButton(onClick = onStopScan, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(Res.string.action_cancel))
                }
            } else {
                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(AppIcon.BluetoothSearching.imageVector(), null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.device_bind_scan))
                }
            }
        }
    }
}

@Composable
private fun ActiveScanVisual() {
    val transition = rememberInfiniteTransition(label = "deviceScan")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1100, easing = LinearEasing)),
        label = "scanSweep",
    )
    ScanVisual(sweepAngle = sweep, active = true)
}

@Composable
private fun IdleScanVisual() {
    ScanVisual(sweepAngle = 0f, active = false)
}

@Composable
private fun ScanVisual(sweepAngle: Float, active: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    Box(modifier = Modifier.size(176.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.dp.toPx()
            drawCircle(primary.copy(alpha = 0.045f), radius = size.minDimension * 0.49f)
            drawCircle(
                outline.copy(alpha = 0.72f),
                radius = size.minDimension * 0.47f,
                style = Stroke(width = stroke),
            )
            drawCircle(
                primary.copy(alpha = 0.13f),
                radius = size.minDimension * 0.34f,
                style = Stroke(width = stroke),
            )
            if (active) {
                val arcInset = 4.dp.toPx()
                drawArc(
                    color = primary,
                    startAngle = sweepAngle - 55f,
                    sweepAngle = 55f,
                    useCenter = false,
                    topLeft = Offset(arcInset, arcInset),
                    size = Size(size.width - arcInset * 2, size.height - arcInset * 2),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(68.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (active) AppIcon.BluetoothSearching.imageVector() else AppIcon.Bluetooth.imageVector(),
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(31.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun DeviceResultRow(device: ScannedDevice, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcon.Bluetooth.imageVector(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = device.advertisedName ?: device.macAddress,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.device_bind_detected, localizedDeviceClass(device.effectiveSelectedClass())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.device_bind_details, device.macAddress, device.rssi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(AppIcon.ChevronRight.imageVector(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

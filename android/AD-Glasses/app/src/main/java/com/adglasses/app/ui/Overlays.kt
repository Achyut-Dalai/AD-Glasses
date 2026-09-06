package com.adglasses.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adglasses.app.core.bluetooth.ClassicBluetoothState
import com.adglasses.app.core.model.ConnectionPhase
import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceCenterDialog(vm: ADViewModel, dismiss: () -> Unit) {
    val state by vm.glasses.collectAsStateWithLifecycle()
    val scanned by vm.scanned.collectAsStateWithLifecycle()
    val classic by vm.classicBluetooth.collectAsStateWithLifecycle()

    LaunchedEffect(state.isReady) {
        if (state.isReady) vm.refreshClassicBluetooth()
    }

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("Device Center", fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            IconButton(onClick = dismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        ),
                    )
                },
            ) { inner ->
                ADAmbientBackground(Modifier.padding(inner)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            Text(
                                "Your glasses use two links: low-power BLE for control and Classic Bluetooth for calls and audio.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        item {
                            SectionLabel("CONTROL")
                            ADGroupedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        DeviceIcon(
                                            icon = Icons.Filled.Bluetooth,
                                            tint = if (state.isReady) ADAccent.Green else ADAccent.Indigo,
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                state.deviceName ?: "AD Glasses",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                when {
                                                    state.isReady -> "BLE control connected"
                                                    state.phase == ConnectionPhase.Scanning -> "Finding nearby glasses"
                                                    state.phase == ConnectionPhase.Error -> state.detail ?: "Connection failed"
                                                    else -> state.detail ?: state.phase.name.lowercase().replaceFirstChar { it.uppercase() }
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        state.batteryPercent?.let { battery ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Icon(Icons.Filled.BatteryFull, null, Modifier.size(18.dp))
                                                Text("$battery%", fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }

                                    if (state.isReady) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            OutlinedButton(
                                                onClick = vm::disconnect,
                                                modifier = Modifier.weight(1f),
                                            ) { Text("Disconnect") }
                                            TextButton(
                                                onClick = vm::forget,
                                                modifier = Modifier.weight(1f),
                                            ) { Text("Forget") }
                                        }
                                    }
                                }
                            }
                        }

                        if (state.isReady) {
                            item {
                                SectionLabel("CALLS & AUDIO")
                                ADGroupedCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            DeviceIcon(
                                                icon = Icons.Filled.HeadsetMic,
                                                tint = if (classic is ClassicBluetoothState.Connected) ADAccent.Green else ADAccent.Blue,
                                            )
                                            Column(Modifier.weight(1f)) {
                                                Text("JS-01 audio", fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    classicBluetoothLabel(classic),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (classic is ClassicBluetoothState.Failed) {
                                                        MaterialTheme.colorScheme.error
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                )
                                            }
                                            if (classic is ClassicBluetoothState.Connected) {
                                                Icon(Icons.Filled.VolumeUp, null, tint = ADAccent.Green)
                                            }
                                        }

                                        if (classic !is ClassicBluetoothState.Connected) {
                                            Button(
                                                onClick = vm::ensureClassicBluetooth,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(if (classic is ClassicBluetoothState.Failed) "Retry pairing" else "Pair / connect audio")
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            item {
                                Button(
                                    onClick = vm::scan,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    enabled = state.phase != ConnectionPhase.Connecting && state.phase != ConnectionPhase.Discovering,
                                ) {
                                    if (state.phase == ConnectionPhase.Scanning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(Modifier.size(9.dp))
                                        Text("Scanning…")
                                    } else {
                                        Icon(Icons.Filled.Search, null)
                                        Spacer(Modifier.size(8.dp))
                                        Text(if (scanned.isEmpty()) "Find glasses" else "Scan again")
                                    }
                                }
                            }

                            if (scanned.isNotEmpty()) {
                                item { SectionLabel("NEARBY") }
                                items(scanned, key = { it.address }) { device ->
                                    Surface(
                                        onClick = { vm.connect(device) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            DeviceIcon(Icons.Filled.Bluetooth, ADAccent.Indigo)
                                            Column(Modifier.weight(1f)) {
                                                Text(device.name, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    "${signalLabel(device.rssi)} · BLE control",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Text(
                                                "${device.rssi} dBm",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(13.dp),
        color = tint.copy(alpha = 0.10f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun signalLabel(rssi: Int): String = when {
    rssi >= -55 -> "Very close"
    rssi >= -68 -> "Nearby"
    rssi >= -80 -> "In range"
    else -> "Weak signal"
}

private fun classicBluetoothLabel(state: ClassicBluetoothState): String = when (state) {
    ClassicBluetoothState.Idle -> "Audio link not checked yet"
    ClassicBluetoothState.Searching -> "Finding the JS-01 calls/audio radio"
    is ClassicBluetoothState.Pairing -> "Pairing ${state.name} · confirm Android's pairing dialog"
    is ClassicBluetoothState.Paired -> "Paired with ${state.name} · waiting for audio profiles"
    is ClassicBluetoothState.Connecting -> "Connecting ${state.name} audio profiles"
    is ClassicBluetoothState.Connected -> buildString {
        append("Connected to ${state.name}")
        val profiles = buildList {
            if (state.calls) add("calls")
            if (state.media) add("media")
        }
        if (profiles.isNotEmpty()) append(" · ").append(profiles.joinToString(" + "))
    }
    is ClassicBluetoothState.Failed -> state.reason
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationDialog(vm: ADViewModel, dismiss: () -> Unit) {
    val state by vm.translation.collectAsStateWithLifecycle()
    var input by remember(state.input) { mutableStateOf(state.input) }
    var source by remember(state.source) { mutableStateOf(state.source) }
    var target by remember(state.target) { mutableStateOf(state.target) }

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("Translate", fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            IconButton(onClick = dismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        ),
                    )
                },
            ) { inner ->
                ADAmbientBackground(Modifier.padding(inner), strong = true) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            Text(
                                "Translate naturally, then hear the result in the target language.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        item {
                            ADGlassSurface(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 22.dp,
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text("Say or type", fontWeight = FontWeight.SemiBold)
                                    OutlinedTextField(
                                        value = input,
                                        onValueChange = { input = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("What would you like to translate?") },
                                        minLines = 4,
                                        maxLines = 8,
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LanguageButton(source, Modifier.weight(1f)) { source = it }
                                IconButton(
                                    onClick = {
                                        val previousSource = source
                                        source = target
                                        target = previousSource
                                    },
                                ) {
                                    Icon(Icons.Filled.SwapHoriz, contentDescription = "Swap languages")
                                }
                                LanguageButton(target, Modifier.weight(1f)) { target = it }
                            }
                        }

                        item {
                            Button(
                                onClick = { vm.translate(input, source, target) },
                                enabled = !state.working && input.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) {
                                if (state.working) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(Modifier.size(9.dp))
                                    Text("Translating…")
                                } else {
                                    Text("Translate")
                                }
                            }
                        }

                        if (state.output.isNotBlank()) {
                            item {
                                ADGlassSurface(
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 22.dp,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(34.dp)
                                                    .background(
                                                        Brush.linearGradient(listOf(ADAccent.Indigo, ADAccent.Blue)),
                                                        CircleShape,
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text("AD", color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                            Column {
                                                Text(languageName(target), fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    "Translation",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        Text(state.output, style = MaterialTheme.typography.titleMedium)
                                        OutlinedButton(
                                            onClick = vm::speakTranslation,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Filled.VolumeUp, null)
                                            Spacer(Modifier.size(8.dp))
                                            Text("Speak translation")
                                        }
                                    }
                                }
                            }
                        }

                        state.error?.let { error ->
                            item {
                                Text(
                                    error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageButton(
    tag: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val languages = remember(Locale.getDefault()) {
        TranslateLanguage.getAllLanguages()
            .distinct()
            .sortedWith(
                compareBy<String> { code ->
                    when (code) {
                        "en" -> 0
                        "hi" -> 1
                        else -> 2
                    }
                }.thenBy { code -> languageName(code) }
            )
    }

    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(languageName(tag), maxLines = 1)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            languages.forEach { code ->
                DropdownMenuItem(
                    text = {
                        Text(if (code == tag) "✓ ${languageName(code)}" else languageName(code))
                    },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun languageName(tag: String): String =
    Locale.forLanguageTag(tag)
        .getDisplayLanguage(Locale.getDefault())
        .ifBlank { tag }

@Composable
fun StatusOverlay(text: String, busy: Boolean, dismiss: (() -> Unit)?) {
    AlertDialog(
        onDismissRequest = { dismiss?.invoke() },
        title = { Text(if (busy) "Working" else "AD Glasses") },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Text(text)
            }
        },
        confirmButton = {
            if (!busy) TextButton(onClick = { dismiss?.invoke() }) { Text("OK") }
        },
    )
}

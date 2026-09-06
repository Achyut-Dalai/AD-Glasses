package com.adglasses.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

@Composable
fun DeviceCenterDialog(vm: ADViewModel, dismiss: () -> Unit) {
    val state by vm.glasses.collectAsStateWithLifecycle()
    val scanned by vm.scanned.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("AD Glasses") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.detail ?: state.phase.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.isReady) {
                    Text(
                        "${state.deviceName ?: "Glasses"}${state.batteryPercent?.let { " · $it%" }.orEmpty()}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::disconnect) { Text("Disconnect") }
                        TextButton(onClick = vm::forget) { Text("Forget") }
                    }
                } else {
                    Button(onClick = vm::scan, modifier = Modifier.fillMaxWidth()) { Text("Scan for glasses") }
                    LazyColumn {
                        items(scanned, key = { it.address }) { device ->
                            Surface(
                                onClick = { vm.connect(device) },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(device.name, fontWeight = FontWeight.SemiBold)
                                    Text("${device.address} · ${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
    )
}

@Composable
fun TranslationDialog(vm: ADViewModel, dismiss: () -> Unit) {
    val state by vm.translation.collectAsStateWithLifecycle()
    var input by remember(state.input) { mutableStateOf(state.input) }
    var source by remember(state.source) { mutableStateOf(state.source) }
    var target by remember(state.target) { mutableStateOf(state.target) }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Translate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Text") },
                    minLines = 3,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LanguageButton(source, Modifier.weight(1f)) { source = it }
                    TextButton(
                        onClick = {
                            val previousSource = source
                            source = target
                            target = previousSource
                        }
                    ) { Text("⇄") }
                    LanguageButton(target, Modifier.weight(1f)) { target = it }
                }
                if (state.working) CircularProgressIndicator(Modifier.size(24.dp))
                if (state.output.isNotBlank()) {
                    HorizontalDivider()
                    Text(state.output, style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = vm::speakTranslation) { Text("Speak") }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.translate(input, source, target) },
                enabled = !state.working && input.isNotBlank(),
            ) { Text("Translate") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Done") } },
    )
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
                        Text(
                            if (code == tag) "✓ ${languageName(code)}" else languageName(code),
                        )
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

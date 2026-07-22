package com.fersaiyan.cyanbridge.plugins.visualdiary

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.shared.plugins.NativePluginIds
import com.fersaiyan.cyanbridge.ui.NativePluginShortcutPreference
import com.fersaiyan.cyanbridge.ui.installComposeHostWithLegacyAdapter
import com.fersaiyan.cyanbridge.ui.setThemedComposeContent

class VisualDiarySettingsActivity : AppCompatActivity() {
    private var visualDiaryEnabled by mutableStateOf(false)
    private var lastError by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshUi()
        val composeView = installComposeHostWithLegacyAdapter(R.layout.activity_visual_diary_settings)
        setThemedComposeContent(composeView) {
            VisualDiarySettingsScreen(
                enabled = visualDiaryEnabled,
                lastError = lastError,
                onBack = ::finish,
                onEnabledChanged = ::setEnabled,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun setEnabled(enabled: Boolean) {
        if (enabled) VisualDiaryService.enable(this) else VisualDiaryService.disable(this)
        refreshUi()
    }

    private fun refreshUi() {
        visualDiaryEnabled = VisualDiaryPreferences.isEnabled(this)
        lastError = VisualDiaryPreferences.getLastError(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualDiarySettingsScreen(
    enabled: Boolean,
    lastError: String,
    onBack: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var interval by remember { mutableIntStateOf(VisualDiaryPreferences.getIntervalMinutes(context)) }
    var prompt by remember { mutableStateOf(VisualDiaryPreferences.getCustomPrompt(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Visual Diary Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Capture glasses thumbnails on a schedule, describe scenes with the selected Gemma vision model, and append concise notes to daily memory.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SwitchSetting("Visual Diary enabled", enabled, onEnabledChanged)
            NativePluginShortcutPreference(
                pluginId = NativePluginIds.VISUAL_DIARY,
                pluginTitle = "Visual Diary",
            )
            if (lastError.isNotBlank()) {
                Text(
                    "Last stop reason: $lastError",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            SectionTitle("Capture")
            NumberSetting(
                label = "Capture interval (minutes)",
                value = interval,
                range = 1..240,
                onValueChanged = {
                    interval = it
                    VisualDiaryPreferences.setIntervalMinutes(context, it)
                },
            )
            OutlinedButton(
                onClick = { VisualDiaryService.captureNow(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Capture scene now") }
            SectionTitle("Scene descriptions")
            OutlinedTextField(
                value = prompt,
                onValueChange = {
                    prompt = it
                    VisualDiaryPreferences.setCustomPrompt(context, it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Scene description prompt") },
                minLines = 3,
                maxLines = 7,
            )
            OutlinedButton(
                onClick = {
                    onEnabledChanged(false)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Stop visual diary") }
            SectionTitle("Shared memory")
            Text(
                "Visual Diary appends notes to shared daily memory. Privacy, retention, and vault controls are managed in Settings > Memory Privacy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun NumberSetting(
    label: String,
    value: Int,
    range: IntRange,
    onValueChanged: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label)
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                it.toIntOrNull()?.takeIf { number -> number in range }?.let(onValueChanged)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

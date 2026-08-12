package com.achyut.adglasses.plugins.visualdiary

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
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.achyut.adglasses.R
import com.achyut.adglasses.devices.DeviceCapabilityHelper
import com.achyut.adglasses.shared.plugins.NativePluginIds
import com.achyut.adglasses.ui.NativePluginShortcutPreference
import com.achyut.adglasses.ui.installComposeHostWithLegacyAdapter
import com.achyut.adglasses.ui.setThemedComposeContent

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

    val hasCamera = remember { DeviceCapabilityHelper.hasCamera(context) }
    val cameraUnavailableReason = remember { DeviceCapabilityHelper.unavailableCameraReason(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compose_plugin_settings_title, stringResource(R.string.compose_plugin_name_visual_diary))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.compose_back))
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
            if (!hasCamera && cameraUnavailableReason != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = stringResource(R.string.compose_camera_warning),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.compose_camera_hardware_required),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = stringResource(R.string.compose_visual_camera_description, cameraUnavailableReason),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.compose_visual_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            SwitchSetting(stringResource(R.string.compose_visual_enabled), enabled && hasCamera, if (hasCamera) onEnabledChanged else { _ -> })
            NativePluginShortcutPreference(
                pluginId = NativePluginIds.VISUAL_DIARY,
                pluginTitle = stringResource(R.string.compose_plugin_name_visual_diary),
            )
            if (lastError.isNotBlank()) {
                Text(
                    stringResource(R.string.compose_last_stop_reason, lastError),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            SectionTitle(stringResource(R.string.compose_capture))
            NumberSetting(
                label = stringResource(R.string.compose_capture_interval_minutes),
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
            ) { Text(stringResource(R.string.compose_capture_scene_now)) }
            SectionTitle(stringResource(R.string.compose_scene_descriptions))
            OutlinedTextField(
                value = prompt,
                onValueChange = {
                    prompt = it
                    VisualDiaryPreferences.setCustomPrompt(context, it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.compose_scene_description_prompt)) },
                minLines = 3,
                maxLines = 7,
            )
            OutlinedButton(
                onClick = {
                    onEnabledChanged(false)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.compose_stop_visual_diary)) }
            SectionTitle(stringResource(R.string.compose_shared_memory))
            Text(
                stringResource(R.string.compose_visual_shared_memory_description),
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

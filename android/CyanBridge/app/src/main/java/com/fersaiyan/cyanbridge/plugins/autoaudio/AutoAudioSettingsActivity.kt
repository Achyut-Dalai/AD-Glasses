package com.fersaiyan.cyanbridge.plugins.autoaudio

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
import com.fersaiyan.cyanbridge.media.autocapture.AutoAudioCapturePrefs
import com.fersaiyan.cyanbridge.media.autocapture.AutoAudioCaptureService
import com.fersaiyan.cyanbridge.plugins.PluginVoicePermissions
import com.fersaiyan.cyanbridge.shared.plugins.NativePluginIds
import com.fersaiyan.cyanbridge.ui.CommunityPluginPrefs
import com.fersaiyan.cyanbridge.ui.NativePluginShortcutPreference
import com.fersaiyan.cyanbridge.ui.installComposeHostWithLegacyAdapter
import com.fersaiyan.cyanbridge.ui.setThemedComposeContent

class AutoAudioSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val composeView = installComposeHostWithLegacyAdapter(R.layout.activity_auto_audio_settings)
        setThemedComposeContent(composeView) {
            AutoAudioSettingsScreen(
                onBack = ::finish,
                onEnabledChanged = ::setEnabled,
            )
        }
    }

    private fun setEnabled(enabled: Boolean) {
        AutoAudioCapturePrefs.setEnabled(this, enabled)
        if (!enabled) {
            CommunityPluginPrefs.setNativePluginEnabled(this, NativePluginIds.AUTO_AUDIO, false)
            AutoAudioCaptureService.stop(this)
            return
        }
        PluginVoicePermissions.ensure(this) {
            CommunityPluginPrefs.setNativePluginEnabled(this, NativePluginIds.AUTO_AUDIO, true)
            AutoAudioCaptureService.start(this)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoAudioSettingsScreen(
    onBack: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var enabled by remember { mutableStateOf(AutoAudioCapturePrefs.isEnabled(context)) }
    var visualNotes by remember { mutableStateOf(AutoAudioCapturePrefs.isVisualNotesEnabled(context)) }
    var speechExtend by remember { mutableStateOf(AutoAudioCapturePrefs.isSpeechExtendEnabled(context)) }
    var loopsPerSync by remember { mutableIntStateOf(AutoAudioCapturePrefs.getLoopsPerSync(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto Audio Settings") },
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
                "Record 15-minute glasses audio chunks, extend active speech, and periodically sync the media without replacing the existing loop.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SwitchSetting("Auto audio capture enabled", enabled) {
                enabled = it
                onEnabledChanged(it)
            }
            NativePluginShortcutPreference(
                pluginId = NativePluginIds.AUTO_AUDIO,
                pluginTitle = "Auto Audio",
            )
            SwitchSetting("Extend capture when speech continues", speechExtend) {
                speechExtend = it
                AutoAudioCapturePrefs.setSpeechExtendEnabled(context, it)
            }
            SwitchSetting("Create visual notes after loops", visualNotes) {
                visualNotes = it
                AutoAudioCapturePrefs.setVisualNotesEnabled(context, it)
            }
            NumberSetting(
                label = "Loops before P2P sync",
                value = loopsPerSync,
                range = 1..96,
                onValueChanged = {
                    loopsPerSync = it
                    AutoAudioCapturePrefs.setLoopsPerSync(context, it)
                },
            )
            Text(
                "Status: ${AutoAudioCapturePrefs.getLastPauseReason(context).ifBlank { "Ready" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    enabled = false
                    onEnabledChanged(false)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Stop loop") }
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
private fun NumberSetting(
    label: String,
    value: Int,
    range: IntRange,
    onValueChanged: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label)
        androidx.compose.material3.OutlinedTextField(
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

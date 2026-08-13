package com.achyut.adglasses.plugins.autoaudio

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.achyut.adglasses.R
import com.achyut.adglasses.media.autocapture.AutoAudioCapturePrefs
import com.achyut.adglasses.media.autocapture.AutoAudioCaptureService
import com.achyut.adglasses.plugins.PluginVoicePermissions
import com.achyut.adglasses.shared.plugins.NativePluginIds
import com.achyut.adglasses.ui.CommunityPluginPrefs
import com.achyut.adglasses.ui.NativePluginShortcutPreference
import com.achyut.adglasses.ui.installComposeHostWithLegacyAdapter
import com.achyut.adglasses.ui.setThemedComposeContent

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
                title = { Text(stringResource(R.string.compose_plugin_settings_title, stringResource(R.string.compose_plugin_name_auto_audio))) },
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
            Text(
                stringResource(R.string.compose_auto_audio_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            SwitchSetting(stringResource(R.string.compose_auto_audio_capture_enabled), enabled) {
                enabled = it
                onEnabledChanged(it)
            }
            NativePluginShortcutPreference(
                pluginId = NativePluginIds.AUTO_AUDIO,
                pluginTitle = stringResource(R.string.compose_plugin_name_auto_audio),
            )
            SwitchSetting(stringResource(R.string.compose_auto_audio_extend), speechExtend) {
                speechExtend = it
                AutoAudioCapturePrefs.setSpeechExtendEnabled(context, it)
            }
            SwitchSetting(stringResource(R.string.compose_auto_audio_visual_notes), visualNotes) {
                visualNotes = it
                AutoAudioCapturePrefs.setVisualNotesEnabled(context, it)
            }
            NumberSetting(
                label = stringResource(R.string.compose_auto_audio_loops),
                value = loopsPerSync,
                range = 1..96,
                onValueChanged = {
                    loopsPerSync = it
                    AutoAudioCapturePrefs.setLoopsPerSync(context, it)
                },
            )
            Text(
                stringResource(
                    R.string.compose_status,
                    AutoAudioCapturePrefs.getLastPauseReason(context).ifBlank {
                        stringResource(R.string.compose_ready)
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    enabled = false
                    onEnabledChanged(false)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.compose_auto_audio_stop_loop)) }
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

package com.fersaiyan.cyanbridge.plugins.autodiary

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.localagent.daily.DailyFactsReminderScheduler
import com.fersaiyan.cyanbridge.localagent.dailyfacts.DailyBulletsSettings
import com.fersaiyan.cyanbridge.ui.NativePluginShortcutPreference
import com.fersaiyan.cyanbridge.ui.hasAccessibilityServicePermission
import com.fersaiyan.cyanbridge.ui.localagent.AppBlacklistActivity
import com.fersaiyan.cyanbridge.ui.localagent.DailyFactsActivity
import com.fersaiyan.cyanbridge.ui.localagent.ScreenCapturesActivity
import com.fersaiyan.cyanbridge.ui.localagent.DailySummaryActivity
import com.fersaiyan.cyanbridge.ui.installComposeHostWithLegacyAdapter
import com.fersaiyan.cyanbridge.ui.setThemedComposeContent

class AutoDiarySettingsActivity : AppCompatActivity() {
    private var autoDiaryEnabled by mutableStateOf(false)
    private var accessibilityEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshUi()
        val composeView = installComposeHostWithLegacyAdapter(R.layout.activity_auto_diary_settings)
        setThemedComposeContent(composeView) {
            AutoDiarySettingsScreen(
                enabled = autoDiaryEnabled,
                accessibilityEnabled = accessibilityEnabled,
                onBack = ::finish,
                onEnabledChanged = ::setEnabled,
                onOpenAccessibility = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onOpenBlacklist = {
                    startActivity(Intent(this, AppBlacklistActivity::class.java))
                },
                onOpenCaptures = {
                    startActivity(Intent(this, ScreenCapturesActivity::class.java))
                },
                onOpenSummary = {
                    startActivity(Intent(this, DailySummaryActivity::class.java))
                },
                onOpenDailyFactsDraft = {
                    startActivity(
                        Intent(this, DailyFactsActivity::class.java)
                            .putExtra(DailyFactsActivity.EXTRA_MODE, DailyFactsActivity.MODE_DRAFT),
                    )
                },
                onOpenConfirmedDailyFacts = {
                    startActivity(
                        Intent(this, DailyFactsActivity::class.java)
                            .putExtra(DailyFactsActivity.EXTRA_MODE, DailyFactsActivity.MODE_CONFIRMED),
                    )
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun setEnabled(enabled: Boolean) {
        if (enabled) AutoDiaryService.enable(this) else AutoDiaryService.disable(this)
        refreshUi()
    }

    private fun refreshUi() {
        accessibilityEnabled = hasAccessibilityServicePermission(this)
        autoDiaryEnabled = AutoDiaryService.isEnabled(this)
        if (autoDiaryEnabled && accessibilityEnabled) {
            AutoDiaryService.startIfEnabled(this)
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoDiarySettingsScreen(
    enabled: Boolean,
    accessibilityEnabled: Boolean,
    onBack: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBlacklist: () -> Unit,
    onOpenCaptures: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenDailyFactsDraft: () -> Unit,
    onOpenConfirmedDailyFacts: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var interval by remember { mutableIntStateOf(LocalAgentPrefs.getCaptureIntervalMin(context)) }
    var reminder by remember { mutableStateOf(LocalAgentPrefs.isDailyFactsReminderEnabled(context)) }
    var autoSaveFacts by remember { mutableStateOf(com.fersaiyan.cyanbridge.localagent.userfacts.ChatMemoryPrefs.isAutoSaveDailyFactsEnabled(context)) }
    var extractFacts by remember { mutableStateOf(com.fersaiyan.cyanbridge.localagent.userfacts.ChatMemoryPrefs.isExtractUserFactCandidatesEnabled(context)) }
    var maxTokens by remember { mutableIntStateOf(DailyBulletsSettings.getMaxTokensPerBullet(context)) }
    var prompt by remember { mutableStateOf(DailyBulletsSettings.getBulletPrompt(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutoDiary Settings") },
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
                "Build a private daily memory from screen context and conversations. Screen capture requires the CyanBridge accessibility service; Local Agent phone control can remain off.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SwitchSetting(
                label = "AutoDiary enabled",
                checked = enabled,
                onCheckedChange = onEnabledChanged,
            )
            NativePluginShortcutPreference(
                pluginId = com.fersaiyan.cyanbridge.shared.plugins.NativePluginIds.AUTO_DIARY,
                pluginTitle = "AutoDiary",
            )
            Text("Screen capture", style = MaterialTheme.typography.titleMedium)
            Text(
                if (accessibilityEnabled) {
                    "Accessibility service enabled. AutoDiary can collect screen context."
                } else if (enabled) {
                    "AutoDiary is paused. Enable the accessibility service to resume screen capture."
                } else {
                    "Accessibility service required. AutoDiary cannot collect screen context until it is enabled."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (accessibilityEnabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            NumberSetting(
                label = "Capture interval (minutes)",
                value = interval,
                range = 1..1440,
                onValueChanged = {
                    interval = it
                    LocalAgentPrefs.setCaptureIntervalMin(context, it)
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenAccessibility, modifier = Modifier.weight(1f)) {
                    Text("Accessibility settings")
                }
                OutlinedButton(onClick = onOpenBlacklist, modifier = Modifier.weight(1f)) {
                    Text("Blacklist apps")
                }
            }
            OutlinedButton(onClick = onOpenCaptures, modifier = Modifier.fillMaxWidth()) {
                Text("View screen captures")
            }
            Text("Daily processing", style = MaterialTheme.typography.titleMedium)
            SwitchSetting("Daily facts reminder", reminder) {
                reminder = it
                LocalAgentPrefs.setDailyFactsReminderEnabled(context, it)
                DailyFactsReminderScheduler.scheduleIfEnabled(context, it)
            }
            SwitchSetting("Auto-save daily facts", autoSaveFacts) {
                autoSaveFacts = it
                com.fersaiyan.cyanbridge.localagent.userfacts.ChatMemoryPrefs.setAutoSaveDailyFactsEnabled(context, it)
            }
            SwitchSetting("Extract user fact candidates", extractFacts) {
                extractFacts = it
                com.fersaiyan.cyanbridge.localagent.userfacts.ChatMemoryPrefs.setExtractUserFactCandidatesEnabled(context, it)
            }
            NumberSetting(
                label = "Daily summary refresh (hours)",
                value = LocalAgentPrefs.getDailySummaryAutoRefreshHours(context),
                range = 1..24,
                onValueChanged = { LocalAgentPrefs.setDailySummaryAutoRefreshHours(context, it) },
            )
            OutlinedButton(onClick = onOpenSummary, modifier = Modifier.fillMaxWidth()) {
                Text("Open daily summary")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenDailyFactsDraft, modifier = Modifier.weight(1f)) {
                    Text("Edit daily facts")
                }
                OutlinedButton(onClick = onOpenConfirmedDailyFacts, modifier = Modifier.weight(1f)) {
                    Text("View confirmed facts")
                }
            }
            Text("Bulletization", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = prompt,
                onValueChange = {
                    prompt = it
                    DailyBulletsSettings.setCustomBulletPrompt(context, it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Bullet prompt") },
                minLines = 3,
                maxLines = 7,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    DailyBulletsSettings.restoreDefaultBulletPrompt(context)
                    prompt = DailyBulletsSettings.DEFAULT_BULLET_PROMPT
                }) { Text("Restore default prompt") }
                NumberSetting(
                    label = "Max tokens per bullet",
                    value = maxTokens,
                    range = 0..100_000,
                    onValueChanged = {
                        maxTokens = it
                        DailyBulletsSettings.setMaxTokensPerBullet(context, it)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Text("Shared memory privacy", style = MaterialTheme.typography.titleMedium)
            Text(
                "OCR sync, retention, deletion, and vault controls are shared across AutoDiary, Visual Diary, and Local Agent. Manage them in Settings > Memory Privacy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
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
    modifier: Modifier = Modifier,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in range
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                it.toIntOrNull()?.takeIf { number -> number in range }?.let(onValueChanged)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = text.isNotBlank() && !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

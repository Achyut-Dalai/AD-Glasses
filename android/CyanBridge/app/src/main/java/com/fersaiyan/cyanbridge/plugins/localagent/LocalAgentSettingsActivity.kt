package com.fersaiyan.cyanbridge.plugins.localagent

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs as AutomationPrefs
import com.fersaiyan.cyanbridge.agent.LocalModelsConfigureActivity
import com.fersaiyan.cyanbridge.bridge.notifications.NotificationForwarderService
import com.fersaiyan.cyanbridge.localagent.LocalAgentController
import com.fersaiyan.cyanbridge.localagent.LocalAgentIntents
import com.fersaiyan.cyanbridge.localagent.LocalAgentPrefs as RuntimePrefs
import com.fersaiyan.cyanbridge.localagent.LocalAgentShizukuFallback
import com.fersaiyan.cyanbridge.localagent.LocalAgentTaskHistory
import com.fersaiyan.cyanbridge.localagent.LocalAgentTelegramService
import com.fersaiyan.cyanbridge.localagent.LocalAgentTelegramProtocol
import com.fersaiyan.cyanbridge.localagent.accessibility.LocalAgentAccessibilityService
import com.fersaiyan.cyanbridge.localagent.memory.LocalAgentMemoryStore
import com.fersaiyan.cyanbridge.localmodels.session.LocalChatSessionManager
import com.fersaiyan.cyanbridge.plugins.autodiary.AutoDiarySettingsActivity
import com.fersaiyan.cyanbridge.shared.plugins.NativePluginIds
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType
import com.fersaiyan.cyanbridge.ui.NativePluginShortcutPreference
import com.fersaiyan.cyanbridge.ui.hasNotificationPermission
import com.fersaiyan.cyanbridge.ui.installComposeHostWithLegacyAdapter
import com.fersaiyan.cyanbridge.ui.localagent.PendingActionsActivity
import com.fersaiyan.cyanbridge.ui.setThemedComposeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalAgentSettingsActivity : AppCompatActivity() {

    private var uiState by mutableStateOf(LocalAgentSettingsUiState())
    private var receiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            when (intent?.action) {
                LocalAgentIntents.ACTION_STATUS_CHANGED -> {
                    intent.getStringExtra(LocalAgentIntents.EXTRA_STATUS)?.let {
                        RuntimePrefs.setStatus(this@LocalAgentSettingsActivity, it)
                    }
                    intent.getStringExtra(LocalAgentIntents.EXTRA_LAST_ERROR)?.let {
                        RuntimePrefs.setLastError(this@LocalAgentSettingsActivity, it)
                    }
                }

                LocalAgentIntents.ACTION_TELEGRAM_STATUS_CHANGED -> Unit
                else -> return
            }
            refreshUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshUi()
        val composeView = installComposeHostWithLegacyAdapter(R.layout.activity_local_agent_settings)
        setThemedComposeContent(composeView) { LocalAgentSettingsContent() }
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            LocalBroadcastManager.getInstance(this).registerReceiver(
                statusReceiver,
                IntentFilter().apply {
                    addAction(LocalAgentIntents.ACTION_STATUS_CHANGED)
                    addAction(LocalAgentIntents.ACTION_TELEGRAM_STATUS_CHANGED)
                },
            )
            receiverRegistered = true
        }
        LocalAgentController.requestStatus(this)
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onStop() {
        if (receiverRegistered) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    private fun refreshUi() {
        LocalAgentPlugin.syncNativePluginState(this)
        uiState = LocalAgentSettingsUiState(
            enabled = LocalAgentPlugin.isEnabled(this),
            providerType = AutomationPrefs.getProviderType(this),
            accessibilityEnabled = isAccessibilityServiceEnabled(),
            maxSteps = AutomationPrefs.getMaxSteps(this),
            requireActionConfirmation = RuntimePrefs.isRequireActionConfirmationEnabled(this),
            autoExecuteLowRisk = RuntimePrefs.isAutoExecuteLowRiskEnabled(this),
            notificationListenerEnabled = NotificationForwarderService.isListenerBound(this),
            whatsappNotificationReadAloud = RuntimePrefs.isWhatsAppNotificationReadAloudEnabled(this),
            telegramConfigured = RuntimePrefs.isTelegramConfigured(this),
            telegramAllowedChatId = RuntimePrefs.getTelegramAllowedChatId(this),
            telegramRemoteControlEnabled = RuntimePrefs.isTelegramRemoteControlEnabled(this),
            telegramStatus = RuntimePrefs.getTelegramStatus(this),
            telegramLastError = RuntimePrefs.getTelegramLastError(this),
            screenshotPlanningEnabled = RuntimePrefs.isScreenshotPlanningEnabled(this),
            remoteScreenshotUploadEnabled = RuntimePrefs.isRemoteScreenshotUploadEnabled(this),
            screenshotStatus = RuntimePrefs.getScreenshotStatus(this),
            shizukuFallbackEnabled = RuntimePrefs.isShizukuFallbackEnabled(this),
            shizukuAvailability = LocalAgentShizukuFallback.availability().statusText,
            shizukuStatus = RuntimePrefs.getShizukuStatus(this),
            status = RuntimePrefs.getStatus(this),
            lastError = RuntimePrefs.getLastError(this),
        )
    }

    private fun setEnabled(enabled: Boolean) {
        LocalAgentPlugin.setEnabled(this, enabled)
        refreshUi()
    }

    private fun setPlanningProvider(type: AgentProviderType) {
        LocalAgentPlugin.setPlanningProvider(this, type)
        if (type != AgentProviderType.LOCAL_AGENT) {
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { LocalChatSessionManager.unload() }
            }
        }
        refreshUi()
    }

    private fun setRequireActionConfirmation(enabled: Boolean) {
        // Keep the old automation preference aligned with the runtime policy.
        AutomationPrefs.setRequireConfirmationEnabled(this, enabled)
        RuntimePrefs.setRequireActionConfirmationEnabled(this, enabled)
        refreshUi()
    }

    private fun saveTelegramConfiguration(botToken: String, allowedChatId: String): Boolean {
        if (LocalAgentTelegramProtocol.normalizeChatId(allowedChatId) == null) {
            Toast.makeText(this, "Enter a valid numeric Telegram chat ID", Toast.LENGTH_SHORT).show()
            return false
        }
        if (botToken.isNotBlank() && !LocalAgentTelegramProtocol.isValidBotToken(botToken)) {
            Toast.makeText(this, "Enter a valid Telegram bot token", Toast.LENGTH_SHORT).show()
            return false
        }
        if (botToken.isNotBlank() && !RuntimePrefs.setTelegramBotToken(this, botToken)) {
            Toast.makeText(this, "Unable to save Telegram bot token", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!RuntimePrefs.setTelegramAllowedChatId(this, allowedChatId)) {
            Toast.makeText(this, "Unable to save Telegram chat ID", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!RuntimePrefs.isTelegramConfigured(this)) {
            Toast.makeText(this, "Paste a bot token before enabling remote control", Toast.LENGTH_SHORT).show()
            return false
        }
        RuntimePrefs.setTelegramStatus(
            this,
            if (RuntimePrefs.isTelegramRemoteControlEnabled(this)) {
                "Configuration updated"
            } else {
                "Configured; remote control is off"
            },
        )
        RuntimePrefs.clearTelegramLastError(this)
        refreshUi()
        Toast.makeText(this, "Telegram configuration saved", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun clearTelegramToken() {
        RuntimePrefs.setTelegramRemoteControlEnabled(this, false)
        LocalAgentTelegramService.stop(this)
        if (RuntimePrefs.clearTelegramBotToken(this)) {
            RuntimePrefs.setTelegramStatus(this, "Configuration required")
            RuntimePrefs.clearTelegramLastError(this)
            Toast.makeText(this, "Telegram bot token cleared", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Unable to clear Telegram bot token", Toast.LENGTH_SHORT).show()
        }
        refreshUi()
    }

    private fun setTelegramRemoteControl(enabled: Boolean) {
        if (enabled) {
            when {
                !LocalAgentPlugin.isEnabled(this) -> {
                    Toast.makeText(this, "Enable Local Agent phone control first", Toast.LENGTH_SHORT).show()
                    return
                }
                !RuntimePrefs.isTelegramConfigured(this) -> {
                    Toast.makeText(this, "Save a bot token and allowed chat ID first", Toast.LENGTH_SHORT).show()
                    return
                }
                !hasNotificationPermission(this) -> {
                    Toast.makeText(this, "Notification permission is required for remote control", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            RuntimePrefs.setTelegramRemoteControlEnabled(this, true)
            RuntimePrefs.setTelegramStatus(this, "Starting")
            RuntimePrefs.clearTelegramLastError(this)
            LocalAgentTelegramService.start(this)
        } else {
            RuntimePrefs.setTelegramRemoteControlEnabled(this, false)
            RuntimePrefs.setTelegramStatus(this, "Disabled")
            RuntimePrefs.clearTelegramLastError(this)
            LocalAgentTelegramService.stop(this)
        }
        refreshUi()
    }

    private fun setShizukuFallback(enabled: Boolean) {
        RuntimePrefs.setShizukuFallbackEnabled(this, enabled)
        if (enabled) {
            RuntimePrefs.setShizukuStatus(this, LocalAgentShizukuFallback.availability().statusText)
        } else {
            LocalAgentShizukuFallback.disconnect(this)
            RuntimePrefs.setShizukuStatus(this, "Disabled")
        }
        refreshUi()
    }

    private fun requestShizukuPermission() {
        Toast.makeText(
            this,
            LocalAgentShizukuFallback.requestPermission(this),
            Toast.LENGTH_SHORT,
        ).show()
        refreshUi()
    }

    private fun openNotificationListenerSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            .onFailure {
                Toast.makeText(this, "Unable to open notification access settings", Toast.LENGTH_SHORT).show()
            }
    }

    private fun runAgentCommand(
        optimisticStatus: String,
        command: () -> LocalAgentController.CommandResult,
    ) {
        val result = command()
        if (result.ok) {
            RuntimePrefs.setStatus(this, optimisticStatus)
            RuntimePrefs.clearLastError(this)
        } else {
            RuntimePrefs.setStatus(this, "Error")
            RuntimePrefs.setLastError(this, result.error ?: result.userMessage)
        }
        Toast.makeText(this, result.userMessage, Toast.LENGTH_SHORT).show()
        LocalAgentController.requestStatus(this)
        refreshUi()
    }

    private fun openAccessibilitySettings() {
        AlertDialog.Builder(this)
            .setTitle(R.string.onboarding_accessibility_disclosure_title)
            .setMessage(R.string.onboarding_accessibility_disclosure_body)
            .setNegativeButton("Not now", null)
            .setPositiveButton("Continue") { _, _ ->
                runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    .onFailure {
                        Toast.makeText(this, "Unable to open accessibility settings", Toast.LENGTH_SHORT).show()
                    }
            }
            .show()
    }

    private fun editMemoryFile(title: String, file: java.io.File) {
        LocalAgentMemoryStore.ensureSeedFiles(this)
        val input = EditText(this).apply {
            setText(LocalAgentMemoryStore.readText(file))
            setSelection(text?.length ?: 0)
            minLines = 8
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                LocalAgentMemoryStore.writeText(file, input.text?.toString().orEmpty())
                Toast.makeText(this, "$title saved", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showContextDebug() {
        val debug = RuntimePrefs.getLastContextInjectionDebug(this)
        val atMs = RuntimePrefs.getLastContextInjectionAtMs(this)
        val message = if (debug.isBlank()) {
            "No context injection recorded yet."
        } else {
            "Last injected: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(atMs))}\n\n$debug"
        }
        AlertDialog.Builder(this)
            .setTitle("Context Injection Debug")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showTaskHistory() {
        val entries = LocalAgentTaskHistory.recent(this, limit = 8)
        val message = if (entries.isEmpty()) {
            "No completed or stopped Local Agent tasks yet."
        } else {
            entries.joinToString("\n\n") { entry ->
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                    .format(Date(entry.createdAtMs))
                buildString {
                    appendLine(timestamp)
                    appendLine("${entry.status}, ${entry.stepCount} step(s)")
                    if (entry.usedSavedSkill) appendLine("Used a saved low-risk navigation path")
                    append(entry.goal)
                }
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Local Agent Activity")
            .setMessage(message)
            .setNegativeButton("Clear history") { _, _ ->
                LocalAgentTaskHistory.clear(this)
                Toast.makeText(this, "Local Agent history cleared", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        if (!enabled) return false
        val expected = ComponentName(this, LocalAgentAccessibilityService::class.java).flattenToString()
        val services = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return services.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LocalAgentSettingsContent() {
        val state = uiState
        var telegramBotToken by remember { mutableStateOf("") }
        var telegramAllowedChatId by remember(state.telegramAllowedChatId) {
            mutableStateOf(state.telegramAllowedChatId)
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Local Agent Settings") },
                    navigationIcon = {
                        IconButton(onClick = ::finish) {
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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Private phone automation using the existing accessibility runtime. " +
                        "It can plan screen actions, request approval for risky work, and use your local memory.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SwitchSetting(
                    label = "Enable Local Agent phone control",
                    checked = state.enabled,
                    onCheckedChange = ::setEnabled,
                )
                NativePluginShortcutPreference(
                    pluginId = NativePluginIds.LOCAL_AGENT,
                    pluginTitle = "Local Agent",
                )

                SectionTitle("Planning")
                Text(
                    "AI provider and local model configuration are managed in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        startActivity(Intent(this@LocalAgentSettingsActivity, com.fersaiyan.cyanbridge.ui.SettingsActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Settings")
                }

                SectionTitle("Screenshot Planning")
                Text(
                    "Screenshots are optional, temporary planning attachments. Accessibility text and structured UI " +
                        "remain available when screenshot capture or vision is unavailable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchSetting(
                    label = "Use screenshots for Local Agent planning",
                    checked = state.screenshotPlanningEnabled,
                    onCheckedChange = {
                        RuntimePrefs.setScreenshotPlanningEnabled(this@LocalAgentSettingsActivity, it)
                        refreshUi()
                    },
                )
                Text(
                    "Android 11+ and Accessibility screenshot support are required. Screenshots are deleted after each " +
                        "planning step and are not added to Local Agent history or memory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchSetting(
                    label = "Allow screenshots to be sent to remote planners",
                    checked = state.remoteScreenshotUploadEnabled,
                    enabled = state.screenshotPlanningEnabled,
                    onCheckedChange = {
                        RuntimePrefs.setRemoteScreenshotUploadEnabled(this@LocalAgentSettingsActivity, it)
                        refreshUi()
                    },
                )
                Text(
                    "Off by default. Enable this only if you consent to the selected cloud, relay, or remote OpenAI-compatible " +
                        "planner receiving the current screenshot. Otherwise remote planning stays text-only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Screenshot status: ${state.screenshotStatus}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionTitle("Runtime and Safety")
                StatusCard(state)
                Text(
                    "The agent can inspect accessible app UI, tap, type, submit fields, scroll, and open apps. " +
                        "Calls, SMS, and email open the native dialer or composer and are high-risk under the default approval policy. " +
                        "Phone control and screenshot planning stop while the phone is locked or inactive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingAction(
                    label = "Accessibility service",
                    detail = if (state.accessibilityEnabled) "Enabled" else "Disabled",
                    actionLabel = "Open settings",
                    onClick = ::openAccessibilitySettings,
                )
                NumberSetting(
                    label = "Maximum steps per request",
                    value = state.maxSteps,
                    range = 1..200,
                    onValueChanged = {
                        AutomationPrefs.setMaxSteps(this@LocalAgentSettingsActivity, it)
                        refreshUi()
                    },
                )
                SwitchSetting(
                    label = "Require approval before actions",
                    checked = state.requireActionConfirmation,
                    onCheckedChange = ::setRequireActionConfirmation,
                )
                SwitchSetting(
                    label = "Auto-execute low-risk actions",
                    checked = state.autoExecuteLowRisk,
                    enabled = state.requireActionConfirmation,
                    onCheckedChange = {
                        RuntimePrefs.setAutoExecuteLowRiskEnabled(this@LocalAgentSettingsActivity, it)
                        refreshUi()
                    },
                )
                OutlinedButton(
                    onClick = {
                        startActivity(Intent(this@LocalAgentSettingsActivity, PendingActionsActivity::class.java))
                    },
                    enabled = state.requireActionConfirmation,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("View pending actions")
                }
                Text(
                    "Successful low-risk navigation paths for the exact same request can be reused locally. " +
                        "Risky actions are never replayed automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingAction(
                    label = "Recent automation activity",
                    detail = "Local metadata only; no screen text is saved here",
                    actionLabel = "View",
                    onClick = ::showTaskHistory,
                )

                SectionTitle("Telegram Remote Control")
                Text(
                    "Disabled by default. CyanBridge polls Telegram only while this foreground listener is enabled, " +
                        "and accepts commands only from the exact chat ID below. Use a private chat: configuring a group " +
                        "chat grants every group member access to these commands.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = telegramBotToken,
                    onValueChange = { telegramBotToken = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Bot token (stored encrypted)") },
                    placeholder = { Text(if (state.telegramConfigured) "Token already saved" else "123456:token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(
                    value = telegramAllowedChatId,
                    onValueChange = { telegramAllowedChatId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Allowed Telegram chat ID") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (saveTelegramConfiguration(telegramBotToken, telegramAllowedChatId)) {
                                telegramBotToken = ""
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save configuration")
                    }
                    OutlinedButton(
                        onClick = ::clearTelegramToken,
                        enabled = state.telegramConfigured,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Clear token")
                    }
                }
                SwitchSetting(
                    label = "Enable Telegram remote phone control",
                    checked = state.telegramRemoteControlEnabled,
                    enabled = state.telegramConfigured,
                    onCheckedChange = ::setTelegramRemoteControl,
                )
                Text(
                    "Supported commands: /task <request>, /status, /read, /stop. /task uses the same Local Agent " +
                        "approval and privacy rules; /read is queued for approval under the default policy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Telegram status: ${state.telegramStatus}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.telegramLastError.isNotBlank() && state.telegramLastError != "(none)") {
                    Text(
                        "Telegram error: ${state.telegramLastError}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                SectionTitle("Optional Shizuku Fallback")
                Text(
                    "Disabled by default. If Accessibility fails after the normal approval decision, Shizuku can retry only " +
                        "fixed IME submit, swipe, Back, and Home operations. It never executes a shell command supplied by a model.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchSetting(
                    label = "Use Shizuku after Accessibility fails",
                    checked = state.shizukuFallbackEnabled,
                    onCheckedChange = ::setShizukuFallback,
                )
                SettingAction(
                    label = "Shizuku access",
                    detail = state.shizukuAvailability,
                    actionLabel = "Grant access",
                    onClick = ::requestShizukuPermission,
                )
                Text(
                    "Shizuku status: ${state.shizukuStatus}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionTitle("Notification Read Aloud")
                Text(
                    "With Android notification access, CyanBridge can read incoming WhatsApp notification text aloud " +
                        "while the phone is unlocked. It never reads the WhatsApp database or announces notifications while locked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingAction(
                    label = "Notification access",
                    detail = if (state.notificationListenerEnabled) "Enabled" else "Disabled",
                    actionLabel = "Open settings",
                    onClick = ::openNotificationListenerSettings,
                )
                SwitchSetting(
                    label = "Read WhatsApp notifications aloud",
                    checked = state.whatsappNotificationReadAloud,
                    enabled = state.notificationListenerEnabled,
                    onCheckedChange = {
                        RuntimePrefs.setWhatsAppNotificationReadAloudEnabled(this@LocalAgentSettingsActivity, it)
                        refreshUi()
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            Toast.makeText(this@LocalAgentSettingsActivity, "Enter a goal before starting the agent.", Toast.LENGTH_SHORT).show()
                            LocalAgentController.requestStatus(this@LocalAgentSettingsActivity)
                        },
                        enabled = state.enabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Status")
                    }
                    OutlinedButton(
                        onClick = {
                            runAgentCommand("Stopping...") {
                                LocalAgentController.stop(this@LocalAgentSettingsActivity)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Stop")
                    }
                    OutlinedButton(
                        onClick = {
                            Toast.makeText(
                                this@LocalAgentSettingsActivity,
                                "Demo: Local Agent will read the current screen in 5 seconds.",
                                Toast.LENGTH_LONG,
                            ).show()
                            runAgentCommand("Running demo...") {
                                LocalAgentController.demo(this@LocalAgentSettingsActivity)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Demo")
                    }
                }

                SectionTitle("Shared Memory")
                Text(
                    "AutoDiary owns passive screen capture, daily facts, bullets, and summaries. Visual Diary also writes concise scene notes into this shared local memory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        startActivity(Intent(this@LocalAgentSettingsActivity, AutoDiarySettingsActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open AutoDiary settings")
                }

                SectionTitle("Personalization and Debug")
                OutlinedButton(
                    onClick = {
                        LocalAgentMemoryStore.ensureSeedFiles(this@LocalAgentSettingsActivity)
                        editMemoryFile(
                            title = "Agent personality",
                            file = LocalAgentMemoryStore.agentPersonaFile(this@LocalAgentSettingsActivity),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Edit agent personality")
                }
                OutlinedButton(
                    onClick = {
                        LocalAgentMemoryStore.ensureSeedFiles(this@LocalAgentSettingsActivity)
                        editMemoryFile(
                            title = "User facts",
                            file = LocalAgentMemoryStore.userFactsFile(this@LocalAgentSettingsActivity),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Edit user facts")
                }
                OutlinedButton(
                    onClick = ::showContextDebug,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("View last injected context")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private data class LocalAgentSettingsUiState(
    val enabled: Boolean = false,
    val providerType: AgentProviderType = AgentProviderType.TASKER,
    val accessibilityEnabled: Boolean = false,
    val maxSteps: Int = 8,
    val requireActionConfirmation: Boolean = true,
    val autoExecuteLowRisk: Boolean = true,
    val notificationListenerEnabled: Boolean = false,
    val whatsappNotificationReadAloud: Boolean = false,
    val telegramConfigured: Boolean = false,
    val telegramAllowedChatId: String = "",
    val telegramRemoteControlEnabled: Boolean = false,
    val telegramStatus: String = "Disabled",
    val telegramLastError: String = "(none)",
    val screenshotPlanningEnabled: Boolean = false,
    val remoteScreenshotUploadEnabled: Boolean = false,
    val screenshotStatus: String = "Text-only planning",
    val shizukuFallbackEnabled: Boolean = false,
    val shizukuAvailability: String = "Shizuku is unavailable",
    val shizukuStatus: String = "Disabled",
    val status: String = "Unknown",
    val lastError: String = "",
)

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ProviderOption(
    type: AgentProviderType,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelected)
        Text(type.label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatusCard(state: LocalAgentSettingsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Status: ${state.status}", style = MaterialTheme.typography.bodyMedium)
            if (state.lastError.isNotBlank() && state.lastError != "(none)") {
                Text(
                    "Last error: ${state.lastError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SettingAction(
    label: String,
    detail: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onClick) { Text(actionLabel) }
    }
}

@Composable
private fun SwitchSetting(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
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
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in range
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        if (text.isNotBlank() && !valid) {
            Text(
                "Use ${range.first} to ${range.last}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

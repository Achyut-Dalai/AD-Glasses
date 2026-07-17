package com.fersaiyan.cyanbridge.shared.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.fersaiyan.cyanbridge.shared.generated.resources.*
import com.fersaiyan.cyanbridge.shared.navigation.AppDestination
import com.fersaiyan.cyanbridge.shared.navigation.icon
import com.fersaiyan.cyanbridge.shared.navigation.label
import com.fersaiyan.cyanbridge.shared.icons.imageVector
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType
import com.fersaiyan.cyanbridge.shared.settings.CaptureSource
import com.fersaiyan.cyanbridge.shared.settings.MemoryPrivacyMode
import com.fersaiyan.cyanbridge.shared.settings.MemorySourceType
import com.fersaiyan.cyanbridge.shared.settings.SettingsSection
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

data class SettingsUiState(
    val isProSubscribed: Boolean = false,
    val proPlan: String = "Pro",
    val appLanguageLabel: String = "System default",
    val visionProfileLabel: String = "Walking",
    val providerType: AgentProviderType = AgentProviderType.PRO_SUBSCRIPTION,
    val localAgentAutomationEnabled: Boolean = false,
    val localAgentRequireConfirmation: Boolean = true,
    val localAgentMaxSteps: Int = 8,
    val accessibilityEnabled: Boolean = false,
    val autoCaptureEnabled: Boolean = false,
    val captureIntervalMinutes: Int = 10,
    val dailyFactsReminderEnabled: Boolean = false,
    val dailySummaryRefreshHours: Int = 6,
    val autoSaveDailyFactsEnabled: Boolean = true,
    val extractUserFactCandidatesEnabled: Boolean = true,
    val maxTokensPerBullet: Int = 0,
    val memoryMode: MemoryPrivacyMode = MemoryPrivacyMode.PRIVATE_LOCAL,
    val memoryModeAvailability: String = "",
    val memorySyncStatus: String = "",
    val memoryCloudStatus: String = "",
    val syncExplicit: Boolean = true,
    val syncDaily: Boolean = true,
    val syncOcr: Boolean = false,
    val syncDerived: Boolean = false,
    val ocrRetentionDays: Int = 7,
    val vaultLocked: Boolean = false,
    val vaultRequiresPassphrase: Boolean = false,
    val transcriptStorageEnabled: Boolean = false,
    val redactNamesEnabled: Boolean = true,
    val includeFullTranscriptionInExports: Boolean = false,
    val autoAudioCaptureEnabled: Boolean = false,
    val autoAudioVisualNotesEnabled: Boolean = false,
    val autoAudioSpeechExtendEnabled: Boolean = false,
    val autoAudioLoopsPerSync: Int = 1,
    val autoAudioDebugText: String = "",
    val requireActionConfirmation: Boolean = true,
    val autoExecuteLowRisk: Boolean = false,
    val agentStatus: String = "Unknown",
    val agentLastError: String = "",
    val meetingRecording: Boolean = false,
    val meetingCaptureSource: CaptureSource? = null,
)

/** Platform-owned effects stay in the platform layer; this composable only renders state and dispatches intent. */
interface SettingsScreenActions {
    fun onDestinationSelected(destination: AppDestination)
    fun openAppearance()
    fun openAppLanguageSelection()
    fun openVisionProfileSelection()
    fun editVisionInstructions()
    fun openSubscription()
    fun setProviderType(type: AgentProviderType)
    fun openLocalModels()
    fun setLocalAgentAutomationEnabled(enabled: Boolean)
    fun setLocalAgentRequireConfirmation(enabled: Boolean)
    fun setLocalAgentMaxSteps(value: Int)
    fun openAccessibilitySettings()
    fun setAutoCaptureEnabled(enabled: Boolean)
    fun setCaptureIntervalMinutes(value: Int)
    fun openBlacklistApps()
    fun openScreenCaptures()
    fun setDailyFactsReminderEnabled(enabled: Boolean)
    fun openDailyFactsDraft()
    fun openConfirmedDailyFacts()
    fun openDailySummary()
    fun setDailySummaryRefreshHours(value: Int)
    fun setAutoSaveDailyFactsEnabled(enabled: Boolean)
    fun setExtractUserFactCandidatesEnabled(enabled: Boolean)
    fun editBulletPrompt()
    fun resetBulletPrompt()
    fun setMaxTokensPerBullet(value: Int)
    fun editAgentPersona()
    fun editUserFacts()
    fun viewContextDebug()
    fun setMemoryMode(mode: MemoryPrivacyMode)
    fun setMemorySync(source: MemorySourceType, enabled: Boolean)
    fun setOcrRetentionDays(value: Int)
    fun deletePassiveCapture()
    fun lockVault()
    fun unlockVault()
    fun setVaultPassphrase()
    fun clearVaultPassphrase()
    fun resetVault()
    fun setTranscriptStorageEnabled(enabled: Boolean)
    fun setRedactNamesEnabled(enabled: Boolean)
    fun setIncludeFullTranscriptionEnabled(enabled: Boolean)
    fun setAutoAudioCaptureEnabled(enabled: Boolean)
    fun setAutoAudioVisualNotesEnabled(enabled: Boolean)
    fun setAutoAudioSpeechExtendEnabled(enabled: Boolean)
    fun setAutoAudioLoopsPerSync(value: Int)
    fun exportLocalData()
    fun importLocalData()
    fun clearLocalData()
    fun setRequireActionConfirmation(enabled: Boolean)
    fun setAutoExecuteLowRisk(enabled: Boolean)
    fun openPendingActions()
    fun startAgent()
    fun stopAgent()
    fun runAgentDemo()
    fun sendDebugLogs()
    fun stopMeetingCapture()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    expandedSections: Set<SettingsSection>,
    onToggleSection: (SettingsSection) -> Unit,
    actions: SettingsScreenActions,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(title = { Text(stringResource(Res.string.settings_title)) })
        },
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == AppDestination.SETTINGS,
                        onClick = { actions.onDestinationSelected(destination) },
                        icon = {
                            Icon(
                                imageVector = destination.icon.imageVector(),
                                contentDescription = null,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.meetingRecording) {
                item {
                    MeetingRecordingBanner(
                        source = state.meetingCaptureSource,
                        onStop = actions::stopMeetingCapture,
                    )
                }
            }
            item {
                Text(
                    text = "Privacy-first defaults, local controls, and automation settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                QuickActionCard(
                    title = "Appearance",
                    subtitle = "Theme, accent profile, dynamic color, and contrast",
                    actionLabel = "Open",
                    onClick = actions::openAppearance,
                    testTag = "settings_appearance",
                )
            }
            item {
                QuickActionCard(
                    title = stringResource(Res.string.settings_language),
                    subtitle = stringResource(
                        Res.string.settings_language_description,
                        state.appLanguageLabel,
                    ),
                    actionLabel = stringResource(Res.string.action_change),
                    onClick = actions::openAppLanguageSelection,
                    testTag = "settings_language",
                )
            }
            item {
                QuickActionCard(
                    title = if (state.isProSubscribed) "Pro Subscription Settings" else "Pro Subscription",
                    subtitle = if (state.isProSubscribed) {
                        "Current plan: ${state.proPlan}. Manage premium features and perks."
                    } else {
                        "Unlock premium features and help fund new smartglasses support."
                    },
                    actionLabel = if (state.isProSubscribed) "Manage" else "Open",
                    onClick = actions::openSubscription,
                    testTag = "settings_subscription",
                )
            }
            item {
                SettingsSectionCard(
                    title = "AI / Automation",
                    expanded = SettingsSection.AI_AUTOMATION in expandedSections,
                    onToggle = { onToggleSection(SettingsSection.AI_AUTOMATION) },
                ) {
                    AiAutomationContent(state, actions)
                }
            }
            item {
                SettingsSectionCard(
                    title = "Local Agent",
                    expanded = SettingsSection.LOCAL_AGENT in expandedSections,
                    onToggle = { onToggleSection(SettingsSection.LOCAL_AGENT) },
                ) {
                    LocalAgentContent(state, actions)
                }
            }
            item {
                SettingsSectionCard(
                    title = "Memory Privacy",
                    expanded = SettingsSection.MEMORY_PRIVACY in expandedSections,
                    onToggle = { onToggleSection(SettingsSection.MEMORY_PRIVACY) },
                ) {
                    MemoryPrivacyContent(state, actions)
                }
            }
            item {
                SettingsSectionCard(
                    title = "Transcripts & Auto Audio",
                    expanded = SettingsSection.TRANSCRIPTS in expandedSections,
                    onToggle = { onToggleSection(SettingsSection.TRANSCRIPTS) },
                ) {
                    TranscriptsContent(state, actions)
                }
            }
            item {
                SettingsSectionCard(
                    title = "Data",
                    expanded = SettingsSection.DATA in expandedSections,
                    onToggle = { onToggleSection(SettingsSection.DATA) },
                ) {
                    DataContent(actions)
                }
            }
            item {
                SettingsSectionCard(
                    title = "Agent Actions",
                    expanded = SettingsSection.AGENT in expandedSections,
                    onToggle = { onToggleSection(SettingsSection.AGENT) },
                ) {
                    AgentActionsContent(state, actions)
                }
            }
            item {
                SettingsSectionCard(
                    title = "Support",
                    expanded = SettingsSection.SUPPORT in expandedSections,
                    onToggle = { onToggleSection(SettingsSection.SUPPORT) },
                ) {
                    SupportContent(actions)
                }
            }
            item {
                SettingsSectionCard(
                    title = "FAQ",
                    expanded = SettingsSection.FAQ in expandedSections,
                    onToggle = { onToggleSection(SettingsSection.FAQ) },
                ) {
                    FaqContent()
                }
            }
        }
    }
}

@Composable
private fun MeetingRecordingBanner(
    source: CaptureSource?,
    onStop: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Recording active", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = when (source) {
                        CaptureSource.BLUETOOTH_MIC -> "Bluetooth mic"
                        CaptureSource.PHONE_MIC -> "Phone mic"
                        null -> "Detecting audio source"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onStop) { Text("Stop") }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_section_$title")
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                )
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun AiAutomationContent(state: SettingsUiState, actions: SettingsScreenActions) {
    Text("Provider type", style = MaterialTheme.typography.labelLarge)
    AgentProviderType.entries.forEach { type ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { actions.setProviderType(type) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = state.providerType == type,
                onClick = { actions.setProviderType(type) },
            )
            Text(type.label, style = MaterialTheme.typography.bodyMedium)
        }
    }
    ActionButton("Configure Local Models", actions::openLocalModels)
    if (state.providerType == AgentProviderType.PRO_SUBSCRIPTION) {
        ActionButton("Configure Pro Subscription", actions::openSubscription)
    }
    SettingRow(
        label = stringResource(Res.string.vision_profile_title),
        subtitle = stringResource(Res.string.vision_profile_description, state.visionProfileLabel),
    ) {
        TextButton(onClick = actions::openVisionProfileSelection) {
            Text(stringResource(Res.string.action_configure))
        }
    }
    SettingRow(label = stringResource(Res.string.vision_instructions_title)) {
        TextButton(onClick = actions::editVisionInstructions) {
            Text(stringResource(Res.string.action_edit))
        }
    }
    HorizontalDivider()
    SwitchRow(
        label = "Enable Local Agent automation",
        checked = state.localAgentAutomationEnabled,
        onCheckedChange = actions::setLocalAgentAutomationEnabled,
    )
    SwitchRow(
        label = "Require confirmation for Local Agent",
        checked = state.localAgentRequireConfirmation,
        onCheckedChange = actions::setLocalAgentRequireConfirmation,
    )
    NumberSettingRow(
        label = "Local Agent max steps",
        value = state.localAgentMaxSteps,
        onValueChanged = actions::setLocalAgentMaxSteps,
        validRange = 1..100_000,
    )
    SettingRow(
        label = "Accessibility service",
        subtitle = if (state.accessibilityEnabled) "Enabled" else "Disabled",
    ) {
        TextButton(onClick = actions::openAccessibilitySettings) { Text("Open settings") }
    }
}

@Composable
private fun LocalAgentContent(state: SettingsUiState, actions: SettingsScreenActions) {
    SwitchRow(
        label = "Auto-capture screen context",
        checked = state.autoCaptureEnabled,
        onCheckedChange = actions::setAutoCaptureEnabled,
    )
    NumberSettingRow(
        label = "Capture interval (minutes)",
        value = state.captureIntervalMinutes,
        onValueChanged = actions::setCaptureIntervalMinutes,
        validRange = 1..100_000,
    )
    SettingRow("Blacklist apps", "Prevent capture in selected apps") {
        TextButton(onClick = actions::openBlacklistApps) { Text("Open") }
    }
    SettingRow("Screen captures") {
        TextButton(onClick = actions::openScreenCaptures) { Text("Open") }
    }
    HorizontalDivider()
    SwitchRow(
        label = "Daily facts reminder",
        checked = state.dailyFactsReminderEnabled,
        onCheckedChange = actions::setDailyFactsReminderEnabled,
    )
    SettingRow("Daily facts draft") {
        TextButton(onClick = actions::openDailyFactsDraft) { Text("Open") }
    }
    SettingRow("Confirmed daily facts") {
        TextButton(onClick = actions::openConfirmedDailyFacts) { Text("Open") }
    }
    SettingRow("Daily summary") {
        TextButton(onClick = actions::openDailySummary) { Text("Open") }
    }
    NumberSettingRow(
        label = "Daily summary refresh (hours)",
        value = state.dailySummaryRefreshHours,
        onValueChanged = actions::setDailySummaryRefreshHours,
        validRange = 1..24,
    )
    SwitchRow(
        label = "Auto-save daily facts",
        checked = state.autoSaveDailyFactsEnabled,
        onCheckedChange = actions::setAutoSaveDailyFactsEnabled,
    )
    SwitchRow(
        label = "Extract user fact candidates",
        checked = state.extractUserFactCandidatesEnabled,
        onCheckedChange = actions::setExtractUserFactCandidatesEnabled,
    )
    HorizontalDivider()
    SettingRow("Bullet generation prompt") {
        Row {
            TextButton(onClick = actions::editBulletPrompt) { Text("Edit") }
            TextButton(onClick = actions::resetBulletPrompt) { Text("Reset") }
        }
    }
    NumberSettingRow(
        label = "Max tokens per bullet",
        value = state.maxTokensPerBullet,
        onValueChanged = actions::setMaxTokensPerBullet,
        validRange = 0..100_000,
    )
    SettingRow("Agent personality") {
        TextButton(onClick = actions::editAgentPersona) { Text("Edit") }
    }
    SettingRow("User facts") {
        TextButton(onClick = actions::editUserFacts) { Text("Edit") }
    }
    SettingRow("Context injection debug") {
        TextButton(onClick = actions::viewContextDebug) { Text("View") }
    }
}

@Composable
private fun MemoryPrivacyContent(state: SettingsUiState, actions: SettingsScreenActions) {
    Text(
        text = "Current mode: ${state.memoryMode.title}",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = state.memoryModeAvailability.ifBlank { state.memoryMode.description },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.memorySyncStatus.isNotBlank()) {
        Text(
            text = state.memorySyncStatus,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.memoryCloudStatus.isNotBlank()) {
        Text(
            text = state.memoryCloudStatus,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    MemoryPrivacyMode.entries.forEach { mode ->
        val requiresPro = mode != MemoryPrivacyMode.PRIVATE_LOCAL
        val enabled = state.isProSubscribed || !requiresPro
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.5f)
                .clickable(enabled = enabled) { actions.setMemoryMode(mode) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = state.memoryMode == mode,
                onClick = { if (enabled) actions.setMemoryMode(mode) },
                enabled = enabled,
            )
            Column {
                Text(mode.title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (enabled) mode.description else "Requires a Pro subscription",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider()
    Text("Sync eligibility", style = MaterialTheme.typography.labelLarge)
    SwitchRow("Explicit facts", state.syncExplicit) {
        actions.setMemorySync(MemorySourceType.EXPLICIT_USER_FACT, it)
    }
    SwitchRow("Daily facts", state.syncDaily) {
        actions.setMemorySync(MemorySourceType.AUTO_DAILY_FACT, it)
    }
    SwitchRow("Screen OCR", state.syncOcr) {
        actions.setMemorySync(MemorySourceType.SCREEN_OCR, it)
    }
    SwitchRow("Derived summaries", state.syncDerived) {
        actions.setMemorySync(MemorySourceType.DERIVED_SUMMARY, it)
    }
    NumberSettingRow(
        label = "Screen OCR retention (days)",
        value = state.ocrRetentionDays,
        onValueChanged = actions::setOcrRetentionDays,
        validRange = 1..365,
    )
    ActionButton("Delete passive OCR capture", actions::deletePassiveCapture, destructive = true)
    HorizontalDivider()
    Text(
        text = buildString {
            append("Vault is ")
            append(if (state.vaultLocked) "locked" else "unlocked")
            append('.')
            if (state.vaultRequiresPassphrase) append(" Passphrase required for unlock.")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.vaultLocked) {
        ActionButton("Unlock vault", actions::unlockVault)
    } else {
        ActionButton("Lock vault", actions::lockVault)
    }
    ActionButton("Set vault passphrase", actions::setVaultPassphrase)
    ActionButton("Clear vault passphrase", actions::clearVaultPassphrase)
    ActionButton("Reset memory vault", actions::resetVault, destructive = true)
}

@Composable
private fun TranscriptsContent(state: SettingsUiState, actions: SettingsScreenActions) {
    SwitchRow(
        label = "Store transcripts",
        checked = state.transcriptStorageEnabled,
        onCheckedChange = actions::setTranscriptStorageEnabled,
    )
    SwitchRow(
        label = "Redact names",
        checked = state.redactNamesEnabled,
        onCheckedChange = actions::setRedactNamesEnabled,
    )
    SwitchRow(
        "Include full transcription in exports",
        state.includeFullTranscriptionInExports,
        onCheckedChange = actions::setIncludeFullTranscriptionEnabled,
    )
    HorizontalDivider()
    SwitchRow(
        label = "Auto audio capture",
        checked = state.autoAudioCaptureEnabled,
        onCheckedChange = actions::setAutoAudioCaptureEnabled,
    )
    SwitchRow(
        label = "Create visual notes",
        checked = state.autoAudioVisualNotesEnabled,
        onCheckedChange = actions::setAutoAudioVisualNotesEnabled,
    )
    SwitchRow(
        label = "Extend capture when speech continues",
        checked = state.autoAudioSpeechExtendEnabled,
        onCheckedChange = actions::setAutoAudioSpeechExtendEnabled,
    )
    NumberSettingRow(
        label = "Loops before sync",
        value = state.autoAudioLoopsPerSync,
        onValueChanged = actions::setAutoAudioLoopsPerSync,
        validRange = 1..96,
    )
    Text(
        text = state.autoAudioDebugText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DataContent(actions: SettingsScreenActions) {
    Text(
        text = "Back up or restore chats, memory files, recordings, and app settings.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ActionButton("Export local data", actions::exportLocalData)
    ActionButton("Import local data", actions::importLocalData)
    ActionButton("Clear local data", actions::clearLocalData, destructive = true)
}

@Composable
private fun AgentActionsContent(state: SettingsUiState, actions: SettingsScreenActions) {
    SwitchRow(
        label = "Require approval for actions",
        checked = state.requireActionConfirmation,
        onCheckedChange = actions::setRequireActionConfirmation,
    )
    SwitchRow(
        label = "Auto-execute low-risk actions",
        checked = state.autoExecuteLowRisk,
        enabled = state.requireActionConfirmation,
        onCheckedChange = actions::setAutoExecuteLowRisk,
    )
    ActionButton("View pending actions", actions::openPendingActions, enabled = state.requireActionConfirmation)
    HorizontalDivider()
    Text("Status: ${state.agentStatus}", style = MaterialTheme.typography.bodyMedium)
    if (state.agentLastError.isNotBlank()) {
        Text(
            text = "Last error: ${state.agentLastError}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    ActionButton("Start agent", actions::startAgent)
    ActionButton("Stop agent", actions::stopAgent)
    ActionButton("Run agent demo", actions::runAgentDemo)
}

@Composable
private fun SupportContent(actions: SettingsScreenActions) {
    Text(
        text = "Send an issue description with diagnostic logs to help investigate Bluetooth, sync, voice, or app failures.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ActionButton("Send debug logs", actions::sendDebugLogs)
}

@Composable
private fun FaqContent() {
    val items = listOf(
        "How do I set up Local Models?" to "Select Local Models in AI / Automation, then configure a model on this device.",
        "Do I need a subscription?" to "No. Tasker and Local Models can be used without a subscription. Pro is optional.",
        "How is data handled?" to "Data stays on this phone by default. You can export, import, or clear it from Data.",
        "Can I review the source?" to "Yes. CyanBridge is open source and its behavior can be reviewed in the project repository.",
    )
    items.forEach { (question, answer) ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(question, style = MaterialTheme.typography.titleSmall)
            Text(
                answer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun SwitchRow(
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
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun NumberSettingRow(
    label: String,
    value: Int,
    onValueChanged: (Int) -> Unit,
    validRange: IntRange,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in validRange
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = text,
            onValueChange = { next ->
                text = next
                next.toIntOrNull()?.takeIf { it in validRange }?.let(onValueChanged)
            },
            modifier = Modifier.width(132.dp),
            singleLine = true,
            isError = text.isNotBlank() && !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (text.isNotBlank() && !valid) {
            Text(
                text = "Use ${validRange.first} to ${validRange.last}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

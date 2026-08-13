package com.achyut.adglasses.shared.ui.settings

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.achyut.adglasses.shared.generated.resources.*
import com.achyut.adglasses.shared.navigation.AppDestination
import com.achyut.adglasses.shared.navigation.icon
import com.achyut.adglasses.shared.navigation.label
import com.achyut.adglasses.shared.icons.imageVector
import com.achyut.adglasses.shared.settings.AgentProviderType
import com.achyut.adglasses.shared.settings.CaptureSource
import com.achyut.adglasses.shared.settings.MemoryPrivacyMode
import com.achyut.adglasses.shared.settings.MemorySourceType
import com.achyut.adglasses.shared.settings.SettingsSection
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

data class SettingsUiState(
    val appLanguageLabel: String = "System default",
    val providerType: AgentProviderType = AgentProviderType.LOCAL_AGENT,
    val defaultImageQuestion: String = "Describe what is in front of me. Mention important text, objects, hazards, and details that may be useful.",
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
    val meetingRecording: Boolean = false,
    val meetingCaptureSource: CaptureSource? = null,
)

/** Platform-owned effects stay in the platform layer; this composable only renders state and dispatches intent. */
interface SettingsScreenActions {
    fun onDestinationSelected(destination: AppDestination)
    fun openAppearance()
    fun openAppLanguageSelection()
    fun setProviderType(type: AgentProviderType)
    fun openLocalModels()
    fun setDefaultImageQuestion(question: String)
    fun resetDefaultImageQuestion()
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
    fun exportLocalData()
    fun importLocalData()
    fun clearLocalData()
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
                SettingsSectionCard(
                    title = "AI",
                    expanded = SettingsSection.AI_AUTOMATION in expandedSections,
                    onToggle = { onToggleSection(SettingsSection.AI_AUTOMATION) },
                ) {
                    AiAutomationContent(state, actions)
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
                    title = "Transcripts",
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
    Text(
        "Choose the provider used for AI and memory-aware requests.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
    OutlinedButton(
        onClick = actions::openLocalModels,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Configure local models")
    }
    Text(
        text = stringResource(Res.string.image_questions_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    OutlinedTextField(
        value = state.defaultImageQuestion,
        onValueChange = actions::setDefaultImageQuestion,
        label = { Text(stringResource(Res.string.default_image_question)) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("default_image_question"),
        minLines = 3,
        maxLines = 6,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = actions::resetDefaultImageQuestion) {
            Text(stringResource(Res.string.reset_default_image_question))
        }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { actions.setMemoryMode(mode) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = state.memoryMode == mode,
                onClick = { actions.setMemoryMode(mode) },
            )
            Column {
                Text(mode.title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = mode.description,
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
        "How do I set up Local Models?" to "Open Local Agent from Native Plugins, then configure a model on this device.",
        "How is data handled?" to "Data stays on this phone by default. You can export, import, or clear it from Data.",
        "Can I review the source?" to "Yes. AdGlasses is open source and its behavior can be reviewed in the project repository.",
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
    enabled: Boolean = true,
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
            enabled = enabled,
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

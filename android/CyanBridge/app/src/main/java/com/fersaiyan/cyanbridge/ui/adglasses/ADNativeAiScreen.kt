package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.ai.orchestrator.AndroidCapabilityCommandExecutor
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import com.fersaiyan.cyanbridge.ai.router.AiProviderType
import com.fersaiyan.cyanbridge.ai.router.CliRelayBackend
import com.fersaiyan.cyanbridge.shared.glasses.GlassesAssistantMode
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType
import com.fersaiyan.cyanbridge.ui.hasAccessibilityServicePermission

enum class ADAiChoice { GEMINI, OPENAI_CODEX, LOCAL }

@Composable
internal fun ADNativeAiScreen(
    onRelaySettings: () -> Unit,
    onLocalSettings: () -> Unit,
    onAssistantApps: () -> Unit,
    onOpenCapability: (ADAutomation) -> Unit = {},
) {
    val context = LocalContext.current
    val capabilityExecutor = remember(context) { AndroidCapabilityCommandExecutor(context) }
    var selected by remember { mutableStateOf(resolveAiChoice(context)) }

    fun select(choice: ADAiChoice) {
        selected = choice
        LocalAgentPrefs.setGlassesAssistantMode(context, GlassesAssistantMode.CUSTOM_AI_PROVIDER)
        when (choice) {
            ADAiChoice.GEMINI -> {
                LocalAgentPrefs.setProviderType(context, AgentProviderType.PRO_SUBSCRIPTION)
                AiProviderPrefs.setProvider(context, AiProviderType.CLI_RELAY)
                AiProviderPrefs.setRelayBackend(context, CliRelayBackend.GEMINI)
            }
            ADAiChoice.OPENAI_CODEX -> {
                LocalAgentPrefs.setProviderType(context, AgentProviderType.PRO_SUBSCRIPTION)
                AiProviderPrefs.setProvider(context, AiProviderType.CLI_RELAY)
                AiProviderPrefs.setRelayBackend(context, CliRelayBackend.CODEX)
            }
            ADAiChoice.LOCAL -> {
                LocalAgentPrefs.setProviderType(context, AgentProviderType.LOCAL_AGENT)
                AiProviderPrefs.setProvider(context, AiProviderType.LOCAL_MODELS)
            }
        }
    }

    fun capabilityState(automation: ADAutomation, defaultDetail: String): Pair<Boolean, String> {
        val capability = automation.toAssistantCapability()
        val active = capabilityExecutor.isActive(capability)
        if (active) return true to "On · $defaultDetail"

        val currentListener = if (capabilityExecutor.isExclusiveVoiceCapability(capability)) {
            capabilityExecutor.activeVoiceCapability(excluding = capability)
        } else {
            null
        }
        return false to if (currentListener != null) {
            "Switch from ${capabilityExecutor.displayName(currentListener)}"
        } else {
            defaultDetail
        }
    }

    val relayConfigured = AiProviderPrefs.isRelayConfigured(context)
    val automationReady = hasAccessibilityServicePermission(context)
    val translateState = capabilityState(ADAutomation.TRANSLATOR, "Live translation")
    val soundbitesState = capabilityState(ADAutomation.MEETING_NOTES, "Audio to notes")
    val timelineState = capabilityState(ADAutomation.VISUAL_DIARY, "Searchable visual memory")
    val dayNoteState = capabilityState(ADAutomation.AUTO_DIARY, "Daily moments, distilled")
    val cronState = capabilityState(ADAutomation.ERRAND_BRAIN, "Scheduled tasks")
    val automationState = capabilityState(ADAutomation.LOCAL_AGENT, "Apps & Android actions")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 18.dp, 16.dp, 34.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ADAiCapabilityTile(
                        Icons.Rounded.Translate,
                        "Translate",
                        translateState.second,
                        translateState.first,
                        Modifier.weight(1f),
                    ) {
                        onOpenCapability(ADAutomation.TRANSLATOR)
                    }
                    ADAiCapabilityTile(
                        Icons.Outlined.GraphicEq,
                        ADAutomation.MEETING_NOTES.title,
                        soundbitesState.second,
                        soundbitesState.first,
                        Modifier.weight(1f),
                    ) {
                        onOpenCapability(ADAutomation.MEETING_NOTES)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ADAiCapabilityTile(
                        Icons.Outlined.Timeline,
                        ADAutomation.VISUAL_DIARY.title,
                        timelineState.second,
                        timelineState.first,
                        Modifier.weight(1f),
                    ) {
                        onOpenCapability(ADAutomation.VISUAL_DIARY)
                    }
                    ADAiCapabilityTile(
                        Icons.Outlined.AutoStories,
                        ADAutomation.AUTO_DIARY.title,
                        dayNoteState.second,
                        dayNoteState.first,
                        Modifier.weight(1f),
                    ) {
                        onOpenCapability(ADAutomation.AUTO_DIARY)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ADAiCapabilityTile(
                        Icons.Outlined.EventRepeat,
                        ADAutomation.ERRAND_BRAIN.title,
                        cronState.second,
                        cronState.first,
                        Modifier.weight(1f),
                    ) {
                        onOpenCapability(ADAutomation.ERRAND_BRAIN)
                    }
                    ADAiCapabilityTile(
                        Icons.Outlined.Bolt,
                        ADAutomation.LOCAL_AGENT.title,
                        automationState.second,
                        automationState.first,
                        Modifier.weight(1f),
                    ) {
                        if (automationReady) onOpenCapability(ADAutomation.LOCAL_AGENT)
                        else context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                }
            }
        }
        item {
            ADAiSection("Default AI") {
                ADAiChoiceRow(Icons.Outlined.AutoAwesome, "Gemini", "Gemini through your relay", selected == ADAiChoice.GEMINI) { select(ADAiChoice.GEMINI) }
                ADAiSectionDivider()
                ADAiChoiceRow(Icons.Outlined.Cloud, "OpenAI / Codex", "OpenAI-compatible route through your relay", selected == ADAiChoice.OPENAI_CODEX) { select(ADAiChoice.OPENAI_CODEX) }
                ADAiSectionDivider()
                ADAiChoiceRow(Icons.Outlined.Computer, "Local AI", "Run a configured model on this phone", selected == ADAiChoice.LOCAL) { select(ADAiChoice.LOCAL) }
            }
        }
        item {
            ADAiSection("Connections") {
                ADAiActionRow(Icons.Outlined.Apps, "Assistant apps", "Optional Gemini or ChatGPT app handoff", onAssistantApps)
                ADAiSectionDivider()
                ADAiActionRow(Icons.Outlined.Cloud, "Relay", if (relayConfigured) "Server, backend and web access" else "Add your relay server", onRelaySettings)
                ADAiSectionDivider()
                ADAiActionRow(Icons.Outlined.Computer, "Local & compatible models", "Local files and OpenAI-compatible endpoints", onLocalSettings)
            }
        }
    }
}

@Composable
private fun ADAiCapabilityTile(
    icon: ImageVector,
    title: String,
    detail: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = 112.dp)
            .background(if (active) ADColors.SurfaceSubtle else ADColors.Surface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(ADColors.BlueSoft, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = ADColors.Blue, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            if (active) ADStatusChip("ON", ADStatusTone.SUCCESS)
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ADAiSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Column(modifier = Modifier.fillMaxWidth().background(ADColors.Surface, RoundedCornerShape(18.dp)).padding(horizontal = 15.dp), content = content)
    }
}

@Composable
private fun ADAiSectionDivider() { HorizontalDivider(Modifier.padding(start = 49.dp), color = ADColors.Separator) }

@Composable
private fun ADAiChoiceRow(icon: ImageVector, title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(if (selected) ADColors.BlueSoft else ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (selected) ADColors.Blue else ADColors.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        if (selected) Icon(Icons.Outlined.CheckCircle, "Selected", tint = ADColors.Blue, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun ADAiActionRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ADColors.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted, modifier = Modifier.size(22.dp))
    }
}

private fun resolveAiChoice(context: android.content.Context): ADAiChoice = when (LocalAgentPrefs.getProviderType(context)) {
    AgentProviderType.LOCAL_AGENT -> ADAiChoice.LOCAL
    AgentProviderType.PRO_SUBSCRIPTION -> when (AiProviderPrefs.getRelayBackend(context)) {
        CliRelayBackend.GEMINI -> ADAiChoice.GEMINI
        CliRelayBackend.CODEX -> ADAiChoice.OPENAI_CODEX
    }
    AgentProviderType.TASKER -> ADAiChoice.GEMINI
}

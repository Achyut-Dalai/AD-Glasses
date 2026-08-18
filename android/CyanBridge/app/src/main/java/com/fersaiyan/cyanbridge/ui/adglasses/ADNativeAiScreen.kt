package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.ai.orchestrator.AndroidCapabilityCommandExecutor
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapability
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapabilityAction
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapabilityCommand
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapabilityRuntimeEvents
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
) {
    val context = LocalContext.current
    val runtimeVersion by AssistantCapabilityRuntimeEvents.version.collectAsState()
    val capabilityExecutor = remember(context, runtimeVersion) { AndroidCapabilityCommandExecutor(context) }
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

    fun setCapability(capability: AssistantCapability, enabled: Boolean) {
        if (capability == AssistantCapability.LOCAL_AGENT && enabled && !hasAccessibilityServicePermission(context)) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val result = capabilityExecutor.execute(
            AssistantCapabilityCommand(
                capability = capability,
                action = if (enabled) AssistantCapabilityAction.START else AssistantCapabilityAction.STOP,
            ),
        )
        if (result.spokenText.contains("needs", ignoreCase = true) ||
            result.spokenText.contains("couldn’t", ignoreCase = true)
        ) {
            Toast.makeText(context, result.spokenText, Toast.LENGTH_SHORT).show()
        }
    }

    val relayConfigured = AiProviderPrefs.isRelayConfigured(context)
    val timelineActive = capabilityExecutor.isActive(AssistantCapability.VISUAL_DIARY)
    val dayNoteActive = capabilityExecutor.isActive(AssistantCapability.AUTO_DIARY)
    val automationActive = capabilityExecutor.isActive(AssistantCapability.LOCAL_AGENT)
    val automationReady = hasAccessibilityServicePermission(context)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 18.dp, 16.dp, 34.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Memory", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ADAiPersistentCard(
                        icon = Icons.Outlined.Timeline,
                        title = "Timeline",
                        detail = "Build a searchable visual memory from moments captured by your glasses.",
                        checked = timelineActive,
                        modifier = Modifier.weight(1f),
                        onCheckedChange = { setCapability(AssistantCapability.VISUAL_DIARY, it) },
                    )
                    ADAiPersistentCard(
                        icon = Icons.Outlined.AutoStories,
                        title = "DayNote",
                        detail = "Distill useful moments into a private note for each day.",
                        checked = dayNoteActive,
                        modifier = Modifier.weight(1f),
                        onCheckedChange = { setCapability(AssistantCapability.AUTO_DIARY, it) },
                    )
                }
            }
        }

        item {
            ADAiSection("Phone actions") {
                ADAiToggleRow(
                    icon = Icons.Outlined.Bolt,
                    title = "Automation",
                    detail = if (automationReady) {
                        "Let your assistant complete supported Android actions."
                    } else {
                        "Grant Accessibility permission when you want the assistant to act in apps."
                    },
                    checked = automationActive,
                    onCheckedChange = { setCapability(AssistantCapability.LOCAL_AGENT, it) },
                )
            }
        }

        item {
            ADAiSection("Default AI") {
                ADAiChoiceRow(
                    Icons.Outlined.AutoAwesome,
                    "Gemini",
                    "Gemini through your relay",
                    selected == ADAiChoice.GEMINI,
                ) { select(ADAiChoice.GEMINI) }
                ADAiSectionDivider()
                ADAiChoiceRow(
                    Icons.Outlined.Cloud,
                    "OpenAI / Codex",
                    "OpenAI-compatible route through your relay",
                    selected == ADAiChoice.OPENAI_CODEX,
                ) { select(ADAiChoice.OPENAI_CODEX) }
                ADAiSectionDivider()
                ADAiChoiceRow(
                    Icons.Outlined.Computer,
                    "Local AI",
                    "Run a configured model on this phone",
                    selected == ADAiChoice.LOCAL,
                ) { select(ADAiChoice.LOCAL) }
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
private fun ADAiPersistentCard(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier.heightIn(min = 188.dp),
        shape = RoundedCornerShape(22.dp),
        color = if (checked) ADColors.SurfaceSubtle else ADColors.Surface,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(if (checked) ADColors.Ink else ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (checked) ADColors.Surface else ADColors.Ink,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
    }
}

@Composable
private fun ADAiToggleRow(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(if (checked) ADColors.Ink else ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (checked) ADColors.Surface else ADColors.Ink,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ADAiSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ADColors.Surface, RoundedCornerShape(18.dp))
                .padding(horizontal = 15.dp),
            content = content,
        )
    }
}

@Composable
private fun ADAiSectionDivider() {
    HorizontalDivider(Modifier.padding(start = 49.dp), color = ADColors.Separator)
}

@Composable
private fun ADAiChoiceRow(
    icon: ImageVector,
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).background(if (selected) ADColors.BlueSoft else ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
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
private fun ADAiActionRow(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
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

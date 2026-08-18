package com.fersaiyan.cyanbridge.ui.adglasses

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
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
import androidx.compose.ui.text.style.TextOverflow
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

enum class ADAiChoice { GEMINI, OPENAI_CODEX, LOCAL }

/** Configuration surface. Frequently used glasses capabilities live on Home. */
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
    var feedback by remember { mutableStateOf<String?>(null) }

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
        val result = capabilityExecutor.execute(
            AssistantCapabilityCommand(
                capability = capability,
                action = if (enabled) AssistantCapabilityAction.START else AssistantCapabilityAction.STOP,
            ),
        )
        feedback = result.spokenText
    }

    val relayConfigured = AiProviderPrefs.isRelayConfigured(context)
    val timelineActive = capabilityExecutor.isActive(AssistantCapability.VISUAL_DIARY)
    val dayNoteActive = capabilityExecutor.isActive(AssistantCapability.AUTO_DIARY)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 18.dp, 16.dp, 34.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("AI", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Choose the intelligence behind your glasses and manage background memory features.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ADColors.Muted,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Memory", style = MaterialTheme.typography.titleLarge)
                ADPersistentCapabilityCard(
                    icon = Icons.Outlined.Timeline,
                    title = "Timeline",
                    summary = "Turn visual captures into a searchable timeline you can revisit by moment.",
                    detail = "Runs in the background when enabled · visual memory stays tied to your captures.",
                    checked = timelineActive,
                    onCheckedChange = { setCapability(AssistantCapability.VISUAL_DIARY, it) },
                )
                ADPersistentCapabilityCard(
                    icon = Icons.Outlined.AutoStories,
                    title = "DayNote",
                    summary = "Distill the moments that matter into a private note for each day.",
                    detail = "Background daily memory · designed to be enabled once and left alone.",
                    checked = dayNoteActive,
                    onCheckedChange = { setCapability(AssistantCapability.AUTO_DIARY, it) },
                )
                feedback?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                        modifier = Modifier.padding(horizontal = 3.dp),
                    )
                }
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
                ) { select(ADAIChoice = ADAiChoice.OPENAI_CODEX, onSelect = ::select) }
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
                ADAiActionRow(
                    Icons.Outlined.Cloud,
                    "Relay",
                    if (relayConfigured) "Server, backend and web access" else "Add your relay server",
                    onRelaySettings,
                )
                ADAiSectionDivider()
                ADAiActionRow(
                    Icons.Outlined.Computer,
                    "Local & compatible models",
                    "Local files and OpenAI-compatible endpoints",
                    onLocalSettings,
                )
            }
        }
    }
}

@Composable
private fun ADPersistentCapabilityCard(
    icon: ImageVector,
    title: String,
    summary: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (checked) ADColors.SurfaceSubtle else ADColors.Surface,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(if (checked) ADColors.Ink else ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (checked) androidx.compose.ui.graphics.Color.White else ADColors.Ink,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (checked) "ON" else "OFF",
                        style = MaterialTheme.typography.labelSmall,
                        color = ADColors.Muted,
                    )
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            Spacer(Modifier.height(14.dp))
            Text(summary, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
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
            Modifier.size(38.dp).background(
                if (selected) ADColors.Ink else ADColors.SurfaceSubtle,
                RoundedCornerShape(11.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                tint = if (selected) androidx.compose.ui.graphics.Color.White else ADColors.Ink,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        if (selected) Icon(Icons.Outlined.CheckCircle, "Selected", tint = ADColors.Ink, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun ADAiActionRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
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
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun select(ADAIChoice: ADAiChoice, onSelect: (ADAiChoice) -> Unit) {
    onSelect(ADAIChoice)
}

private fun resolveAiChoice(context: android.content.Context): ADAiChoice = when (LocalAgentPrefs.getProviderType(context)) {
    AgentProviderType.LOCAL_AGENT -> ADAiChoice.LOCAL
    AgentProviderType.PRO_SUBSCRIPTION -> when (AiProviderPrefs.getRelayBackend(context)) {
        CliRelayBackend.GEMINI -> ADAiChoice.GEMINI
        CliRelayBackend.CODEX -> ADAiChoice.OPENAI_CODEX
    }
    AgentProviderType.TASKER -> ADAiChoice.GEMINI
}

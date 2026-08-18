package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Timeline
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val selectedName = when (selected) {
        ADAiChoice.GEMINI -> "Gemini"
        ADAiChoice.OPENAI_CODEX -> "OpenAI / Codex"
        ADAiChoice.LOCAL -> "Local AI"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 34.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("AI", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Choose the intelligence behind your glasses and manage the background memory features that stay enabled over time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ADColors.Muted,
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = ADColors.Ink,
                contentColor = Color.White,
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(24.dp))
                            }
                        }
                        Column(Modifier.padding(start = 13.dp).weight(1f)) {
                            Text("Default AI", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.68f))
                            Text(selectedName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ADAiProviderPill("Gemini", selected == ADAiChoice.GEMINI, Modifier.weight(1f)) { select(ADAiChoice.GEMINI) }
                        ADAiProviderPill("Codex", selected == ADAiChoice.OPENAI_CODEX, Modifier.weight(1f)) { select(ADAiChoice.OPENAI_CODEX) }
                        ADAiProviderPill("Local", selected == ADAiChoice.LOCAL, Modifier.weight(1f)) { select(ADAiChoice.LOCAL) }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ADSectionEyebrow("Memory")
                ADPersistentCapabilityCard(
                    icon = Icons.Outlined.Timeline,
                    title = "Timeline",
                    summary = "Turn visual captures into a searchable timeline you can revisit by moment.",
                    detail = "Persistent visual memory · stays linked to captures on this phone.",
                    checked = timelineActive,
                    onCheckedChange = { setCapability(AssistantCapability.VISUAL_DIARY, it) },
                )
                ADPersistentCapabilityCard(
                    icon = Icons.Outlined.AutoStories,
                    title = "DayNote",
                    summary = "Distill the moments that matter into a private note for each day.",
                    detail = "Persistent daily memory · designed to be enabled and left running.",
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ADSectionEyebrow("Connections & models")
                ADCard {
                    ADAiActionRow(
                        Icons.Outlined.Cloud,
                        "Relay",
                        if (relayConfigured) "Configured for remote AI and web routes" else "Add your remote AI relay",
                        onRelaySettings,
                    )
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 52.dp), color = ADColors.Separator)
                    ADAiActionRow(
                        Icons.Outlined.Computer,
                        "Local & compatible models",
                        "Imported local models and OpenAI-compatible endpoints",
                        onLocalSettings,
                    )
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 52.dp), color = ADColors.Separator)
                    ADAiActionRow(
                        Icons.Outlined.Apps,
                        "Assistant apps",
                        "Optional Gemini or ChatGPT app handoff",
                        onAssistantApps,
                    )
                }
            }
        }
    }
}

@Composable
private fun ADAiProviderPill(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color.White else Color.White.copy(alpha = 0.10f),
        contentColor = if (selected) ADColors.Ink else Color.White,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(if (checked) ADColors.Ink else ADColors.SurfaceSubtle, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (checked) Color.White else ADColors.Ink,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(if (checked) "ON" else "OFF", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            Spacer(Modifier.height(15.dp))
            Text(summary, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
    }
}

@Composable
private fun ADAiActionRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = ADColors.Muted)
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

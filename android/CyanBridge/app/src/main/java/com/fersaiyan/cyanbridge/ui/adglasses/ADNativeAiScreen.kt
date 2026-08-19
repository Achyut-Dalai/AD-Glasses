package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    val diaryActive = capabilityExecutor.isActive(AssistantCapability.AUTO_DIARY)
    val automationActive = capabilityExecutor.isActive(AssistantCapability.LOCAL_AGENT)
    val automationReady = hasAccessibilityServicePermission(context)
    val selectedName = when (selected) {
        ADAiChoice.GEMINI -> "Gemini"
        ADAiChoice.OPENAI_CODEX -> "OpenAI / Codex"
        ADAiChoice.LOCAL -> "Local AI"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 18.dp, 16.dp, 34.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ADAiProviderCard(
                selectedName = selectedName,
                selected = selected,
                onSelect = ::select,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ADAiCapabilityCard(
                    icon = Icons.Outlined.Timeline,
                    title = "Timeline",
                    detail = "Search moments over time",
                    active = timelineActive,
                    modifier = Modifier.weight(1f),
                    onClick = { setCapability(AssistantCapability.VISUAL_DIARY, !timelineActive) },
                )
                ADAiCapabilityCard(
                    icon = Icons.Outlined.AutoStories,
                    title = "Diary",
                    detail = "A private recap of your day",
                    active = diaryActive,
                    modifier = Modifier.weight(1f),
                    onClick = { setCapability(AssistantCapability.AUTO_DIARY, !diaryActive) },
                )
            }
        }

        item {
            ADAiCapabilityRow(
                icon = Icons.Outlined.Bolt,
                title = "Automation",
                detail = if (automationReady) "Act in supported phone apps" else "Tap to allow phone actions",
                active = automationActive,
                onClick = { setCapability(AssistantCapability.LOCAL_AGENT, !automationActive) },
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = ADColors.Surface,
            ) {
                Column(Modifier.padding(horizontal = 15.dp)) {
                    ADAiActionRow(
                        Icons.Outlined.Apps,
                        "Assistant apps",
                        "Gemini or ChatGPT handoff",
                        onAssistantApps,
                    )
                    ADAiSectionDivider()
                    ADAiActionRow(
                        Icons.Outlined.Cloud,
                        "Relay",
                        if (relayConfigured) "Remote AI and web" else "Add your relay server",
                        onRelaySettings,
                    )
                    ADAiSectionDivider()
                    ADAiActionRow(
                        Icons.Outlined.Computer,
                        "Local models",
                        "On-device and compatible models",
                        onLocalSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun ADAiProviderCard(
    selectedName: String,
    selected: ADAiChoice,
    onSelect: (ADAiChoice) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = ADColors.Ink,
        contentColor = ADColors.Surface,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = ADColors.Surface.copy(alpha = 0.14f),
                    contentColor = ADColors.Surface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }
                Text(
                    selectedName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 13.dp).weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ADAiProviderPill("Gemini", selected == ADAiChoice.GEMINI, Modifier.weight(1f)) {
                    onSelect(ADAiChoice.GEMINI)
                }
                ADAiProviderPill("Codex", selected == ADAiChoice.OPENAI_CODEX, Modifier.weight(1f)) {
                    onSelect(ADAiChoice.OPENAI_CODEX)
                }
                ADAiProviderPill("Local", selected == ADAiChoice.LOCAL, Modifier.weight(1f)) {
                    onSelect(ADAiChoice.LOCAL)
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
        color = if (selected) ADColors.Surface else ADColors.Surface.copy(alpha = 0.12f),
        contentColor = if (selected) ADColors.Ink else ADColors.Surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ADAiCapabilityCard(
    icon: ImageVector,
    title: String,
    detail: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 146.dp),
        shape = RoundedCornerShape(22.dp),
        color = if (active) ADColors.SurfaceSubtle else ADColors.Surface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(if (active) ADColors.Ink else ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (active) ADColors.Surface else ADColors.Ink,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (active) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = "Enabled",
                        tint = ADColors.Ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ADAiCapabilityRow(
    icon: ImageVector,
    title: String,
    detail: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (active) ADColors.SurfaceSubtle else ADColors.Surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(if (active) ADColors.Ink else ADColors.SurfaceSubtle, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (active) ADColors.Surface else ADColors.Ink,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (active) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Enabled",
                    tint = ADColors.Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ADAiSectionDivider() {
    HorizontalDivider(Modifier.padding(start = 49.dp), color = ADColors.Separator)
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
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

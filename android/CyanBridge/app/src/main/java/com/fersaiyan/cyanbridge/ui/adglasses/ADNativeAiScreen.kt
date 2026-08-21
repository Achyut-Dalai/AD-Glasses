package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.SmartToy
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 9.dp, 12.dp, 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ADScreenIntro(
                eyebrow = "Intelligence",
                title = "AI",
                detail = "Choose the engine and the skills your glasses can use.",
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ADSectionTitle("Answer with")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ADAiProviderTile(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "Gemini",
                        selected = selected == ADAiChoice.GEMINI,
                        modifier = Modifier.weight(1f),
                    ) { select(ADAiChoice.GEMINI) }
                    ADAiProviderTile(
                        icon = Icons.Outlined.Code,
                        title = "Codex",
                        selected = selected == ADAiChoice.OPENAI_CODEX,
                        modifier = Modifier.weight(1f),
                    ) { select(ADAiChoice.OPENAI_CODEX) }
                    ADAiProviderTile(
                        icon = Icons.Outlined.Computer,
                        title = "Local",
                        selected = selected == ADAiChoice.LOCAL,
                        modifier = Modifier.weight(1f),
                    ) { select(ADAiChoice.LOCAL) }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ADSectionTitle("Skills")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ADAiControlTile(
                        icon = Icons.Outlined.History,
                        title = "Timeline",
                        detail = "Search moments over time",
                        active = timelineActive,
                        modifier = Modifier.weight(1f),
                    ) { setCapability(AssistantCapability.VISUAL_DIARY, !timelineActive) }
                    ADAiControlTile(
                        icon = Icons.Outlined.MenuBook,
                        title = "Diary",
                        detail = "Private daily recap",
                        active = diaryActive,
                        modifier = Modifier.weight(1f),
                    ) { setCapability(AssistantCapability.AUTO_DIARY, !diaryActive) }
                }
                ADAiControlTile(
                    icon = Icons.Outlined.SmartToy,
                    title = "Automation",
                    detail = if (automationReady) "Phone access ready" else "Tap to allow phone access",
                    active = automationActive,
                    modifier = Modifier.fillMaxWidth(),
                ) { setCapability(AssistantCapability.LOCAL_AGENT, !automationActive) }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ADSectionTitle("Configuration")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ADAiConfigurationTile(
                        icon = Icons.Outlined.Apps,
                        title = "Apps",
                        detail = "Gemini / ChatGPT",
                        modifier = Modifier.weight(1f),
                        onClick = onAssistantApps,
                    )
                    ADAiConfigurationTile(
                        icon = Icons.Outlined.Cloud,
                        title = "Relay",
                        detail = if (relayConfigured) "Ready" else "Set up",
                        modifier = Modifier.weight(1f),
                        onClick = onRelaySettings,
                    )
                    ADAiConfigurationTile(
                        icon = Icons.Outlined.Computer,
                        title = "Local",
                        detail = "On device",
                        modifier = Modifier.weight(1f),
                        onClick = onLocalSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun ADAiProviderTile(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 92.dp),
        shape = RoundedCornerShape(17.dp),
        color = if (selected) ADColors.SurfacePressed else ADColors.Surface,
        border = BorderStroke(1.dp, if (selected) ADColors.Ink.copy(alpha = .25f) else ADColors.Outline),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = if (selected) ADColors.Red else ADColors.SurfaceSubtle,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(19.dp))
                    }
                }
                if (selected) {
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(6.dp).background(ADColors.Red, CircleShape))
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = ADColors.Ink,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ADAiControlTile(
    icon: ImageVector,
    title: String,
    detail: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 108.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (active) ADColors.SurfacePressed else ADColors.Surface,
        border = BorderStroke(1.dp, if (active) ADColors.Ink.copy(alpha = .22f) else ADColors.Outline),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(39.dp),
                    shape = CircleShape,
                    color = if (active) ADColors.Red else ADColors.SurfaceSubtle,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(20.dp))
                    }
                }
                if (active) {
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(6.dp).background(ADColors.Red, CircleShape))
                }
            }
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = ADColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
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
private fun ADAiConfigurationTile(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(17.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = ADColors.SurfaceSubtle,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(19.dp))
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, color = ADColors.Ink, maxLines = 1)
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

private fun resolveAiChoice(context: android.content.Context): ADAiChoice = when (LocalAgentPrefs.getProviderType(context)) {
    AgentProviderType.LOCAL_AGENT -> ADAiChoice.LOCAL
    AgentProviderType.PRO_SUBSCRIPTION -> when (AiProviderPrefs.getRelayBackend(context)) {
        CliRelayBackend.GEMINI -> ADAiChoice.GEMINI
        CliRelayBackend.CODEX -> ADAiChoice.OPENAI_CODEX
    }
    AgentProviderType.TASKER -> ADAiChoice.GEMINI
}

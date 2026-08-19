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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Timeline
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "AI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Choose how your glasses answer, remember and act.",
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
            )
        }

        ADAiProviderCard(
            selectedName = selectedName,
            selected = selected,
            onSelect = ::select,
        )

        ADAiSectionHeading("Capabilities")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ADAiCapabilityCard(
                icon = Icons.Outlined.Timeline,
                title = "Timeline",
                detail = "Find moments",
                active = timelineActive,
                modifier = Modifier.weight(1f),
                onClick = { setCapability(AssistantCapability.VISUAL_DIARY, !timelineActive) },
            )
            ADAiCapabilityCard(
                icon = Icons.Outlined.AutoStories,
                title = "Diary",
                detail = "Daily recap",
                active = diaryActive,
                modifier = Modifier.weight(1f),
                onClick = { setCapability(AssistantCapability.AUTO_DIARY, !diaryActive) },
            )
        }
        ADAutomationCard(
            active = automationActive,
            ready = automationReady,
            onClick = { setCapability(AssistantCapability.LOCAL_AGENT, !automationActive) },
        )

        ADAiSectionHeading("Configuration")
        ADConfigurationPanel(
            relayConfigured = relayConfigured,
            onAssistantApps = onAssistantApps,
            onRelaySettings = onRelaySettings,
            onLocalSettings = onLocalSettings,
        )
    }
}

@Composable
private fun ADAiSectionHeading(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 2.dp),
    )
}

@Composable
private fun ADAiProviderCard(
    selectedName: String,
    selected: ADAiChoice,
    onSelect: (ADAiChoice) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = ADColors.Ink,
        contentColor = ADColors.Surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = ADColors.Surface.copy(alpha = 0.14f),
                    contentColor = ADColors.Surface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(21.dp))
                    }
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text(
                        "Model",
                        style = MaterialTheme.typography.labelSmall,
                        color = ADColors.Surface.copy(alpha = 0.60f),
                    )
                    Text(
                        selectedName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
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
        modifier = modifier.heightIn(min = 36.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) ADColors.Surface else ADColors.Surface.copy(alpha = 0.12f),
        contentColor = if (selected) ADColors.Ink else ADColors.Surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
    val borderColor = if (active) ADColors.Ink.copy(alpha = 0.30f) else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 98.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (active) ADColors.SurfaceSubtle else ADColors.Surface,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(if (active) ADColors.Ink else ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (active) ADColors.Surface else ADColors.Ink,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (active) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = "Enabled",
                        tint = ADColors.Ink,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ADAutomationCard(
    active: Boolean,
    ready: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (active) ADColors.Ink.copy(alpha = 0.30f) else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 108.dp),
        shape = RoundedCornerShape(22.dp),
        color = if (active) ADColors.SurfaceSubtle else ADColors.Surface,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(16.dp),
                color = ADColors.Ink,
                contentColor = ADColors.Surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }

            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Automation",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (active) {
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "Enabled",
                            tint = ADColors.Ink,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "Open apps, navigate screens and run supported phone actions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (ready) "Phone access ready" else "Tap to allow phone access",
                    style = MaterialTheme.typography.labelSmall,
                    color = ADColors.Ink,
                )
            }
        }
    }
}

@Composable
private fun ADConfigurationPanel(
    relayConfigured: Boolean,
    onAssistantApps: () -> Unit,
    onRelaySettings: () -> Unit,
    onLocalSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ADConfigurationCell(
                icon = Icons.Outlined.Apps,
                title = "Apps",
                detail = "Handoff",
                modifier = Modifier.weight(1f),
                onClick = onAssistantApps,
            )
            ADConfigurationCell(
                icon = Icons.Outlined.Cloud,
                title = "Relay",
                detail = if (relayConfigured) "Ready" else "Set up",
                modifier = Modifier.weight(1f),
                onClick = onRelaySettings,
            )
            ADConfigurationCell(
                icon = Icons.Outlined.Computer,
                title = "Local",
                detail = "Models",
                modifier = Modifier.weight(1f),
                onClick = onLocalSettings,
            )
        }
    }
}

@Composable
private fun ADConfigurationCell(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 82.dp),
        shape = RoundedCornerShape(15.dp),
        color = ADColors.SurfaceSubtle,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(5.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = ADColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

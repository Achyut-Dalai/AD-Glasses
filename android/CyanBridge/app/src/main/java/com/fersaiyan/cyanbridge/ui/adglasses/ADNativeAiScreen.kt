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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
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

private enum class ADSkillArtwork {
    TIMELINE,
    DIARY,
}

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
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "AI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "Choose how your glasses answer, remember and act for you.",
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
            )
        }

        Spacer(Modifier.height(8.dp))
        ADAiProviderCard(
            selectedName = selectedName,
            selected = selected,
            onSelect = ::select,
        )

        Spacer(Modifier.height(9.dp))
        ADAiSectionHeading("Skills")
        Spacer(Modifier.height(5.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                ADAiSkillCard(
                    artwork = ADSkillArtwork.TIMELINE,
                    title = "Timeline",
                    detail = "Search moments over time",
                    active = timelineActive,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { setCapability(AssistantCapability.VISUAL_DIARY, !timelineActive) },
                )
                ADAiSkillCard(
                    artwork = ADSkillArtwork.DIARY,
                    title = "Diary",
                    detail = "A private recap of your day",
                    active = diaryActive,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { setCapability(AssistantCapability.AUTO_DIARY, !diaryActive) },
                )
            }

            Spacer(Modifier.height(7.dp))

            ADAutomationCard(
                active = automationActive,
                ready = automationReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.58f),
                onClick = { setCapability(AssistantCapability.LOCAL_AGENT, !automationActive) },
            )
        }

        Spacer(Modifier.height(9.dp))
        ADAiSectionHeading("Configuration")
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ADConfigurationCard(
                icon = Icons.Outlined.Apps,
                title = "Apps",
                detail = "Gemini or ChatGPT",
                modifier = Modifier.weight(1f),
                onClick = onAssistantApps,
            )
            ADConfigurationCard(
                icon = Icons.Outlined.Cloud,
                title = "Relay",
                detail = if (relayConfigured) "Remote AI ready" else "Set up remote AI",
                modifier = Modifier.weight(1f),
                onClick = onRelaySettings,
            )
            ADConfigurationCard(
                icon = Icons.Outlined.Computer,
                title = "Local",
                detail = "On-device models",
                modifier = Modifier.weight(1f),
                onClick = onLocalSettings,
            )
        }
    }
}

@Composable
private fun ADAiSectionHeading(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 1.dp),
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
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = ADColors.Surface.copy(alpha = 0.14f),
                    contentColor = ADColors.Surface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        "Selected model",
                        style = MaterialTheme.typography.labelSmall,
                        color = ADColors.Surface.copy(alpha = 0.62f),
                    )
                    Text(
                        selectedName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        modifier = modifier.heightIn(min = 38.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) ADColors.Surface else ADColors.Surface.copy(alpha = 0.12f),
        contentColor = if (selected) ADColors.Ink else ADColors.Surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ADAiSkillCard(
    artwork: ADSkillArtwork,
    title: String,
    detail: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val borderColor = if (active) ADColors.Ink.copy(alpha = 0.30f) else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (active) ADColors.SurfaceSubtle else ADColors.Surface,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (artwork) {
                    ADSkillArtwork.TIMELINE -> ADTimelineArtwork(active, Modifier.fillMaxSize())
                    ADSkillArtwork.DIARY -> ADDiaryArtwork(active, Modifier.fillMaxSize())
                }
                if (active) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd),
                        shape = CircleShape,
                        color = ADColors.Surface,
                        contentColor = ADColors.Ink,
                    ) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Enabled",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(1.dp))
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

@Composable
private fun ADTimelineArtwork(active: Boolean, modifier: Modifier = Modifier) {
    val container = if (active) ADColors.Ink else ADColors.SurfaceSubtle
    val ink = if (active) ADColors.Surface else ADColors.Ink
    val softInk = ink.copy(alpha = 0.34f)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(ink, CircleShape),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                ADAiArtworkEventLine(ink, softInk, 0.86f)
                ADAiArtworkEventLine(ink, softInk, 0.68f)
                ADAiArtworkEventLine(ink, softInk, 0.78f)
            }
        }
    }
}

@Composable
private fun ADAiArtworkEventLine(
    ink: androidx.compose.ui.graphics.Color,
    softInk: androidx.compose.ui.graphics.Color,
    fraction: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .background(ink.copy(alpha = 0.86f), RoundedCornerShape(3.dp)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth((fraction - 0.16f).coerceAtLeast(0.32f))
                .height(2.dp)
                .background(softInk, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun ADDiaryArtwork(active: Boolean, modifier: Modifier = Modifier) {
    val container = if (active) ADColors.Ink else ADColors.SurfaceSubtle
    val page = if (active) ADColors.Surface.copy(alpha = 0.13f) else ADColors.Surface
    val ink = if (active) ADColors.Surface else ADColors.Ink
    val softInk = ink.copy(alpha = 0.30f)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        color = container,
    ) {
        Surface(
            modifier = Modifier.padding(9.dp),
            shape = RoundedCornerShape(13.dp),
            color = page,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(6.dp)
                            .background(ink.copy(alpha = 0.88f), RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(ink.copy(alpha = 0.48f), CircleShape),
                    )
                }
                Spacer(Modifier.height(1.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(3.dp)
                        .background(softInk, RoundedCornerShape(2.dp)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.76f)
                        .height(3.dp)
                        .background(softInk, RoundedCornerShape(2.dp)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.58f)
                        .height(3.dp)
                        .background(softInk, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun ADAutomationCard(
    active: Boolean,
    ready: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val borderColor = if (active) ADColors.Ink.copy(alpha = 0.30f) else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = if (active) ADColors.SurfaceSubtle else ADColors.Surface,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "Automation",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Let AI open apps, navigate screens and complete supported actions on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = if (active) ADColors.Ink else ADColors.SurfaceSubtle,
                    contentColor = if (active) ADColors.Surface else ADColors.Ink,
                ) {
                    Text(
                        if (ready) "Phone access ready" else "Tap to allow phone access",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight(),
            ) {
                ADAutomationArtwork(active, Modifier.fillMaxSize())
                if (active) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd),
                        shape = CircleShape,
                        color = ADColors.Ink,
                        contentColor = ADColors.Surface,
                    ) {
                        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Enabled",
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ADAutomationArtwork(active: Boolean, modifier: Modifier = Modifier) {
    val container = if (active) ADColors.Surface else ADColors.SurfaceSubtle
    val ink = ADColors.Ink

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier
                    .width(50.dp)
                    .fillMaxHeight(0.84f),
                shape = RoundedCornerShape(16.dp),
                color = ink,
            ) {
                Surface(
                    modifier = Modifier.padding(5.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = ADColors.Surface,
                ) {
                    Column(
                        modifier = Modifier.padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(ink, RoundedCornerShape(4.dp)),
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(ADColors.SurfaceSubtle, RoundedCornerShape(4.dp)),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(ADColors.SurfaceSubtle, RoundedCornerShape(3.dp)),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .height(4.dp)
                                .background(ADColors.SurfaceSubtle, RoundedCornerShape(3.dp)),
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(15.dp)
                                .background(ink, RoundedCornerShape(7.dp)),
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(0.74f),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                ADAutomationActionChip("AI", active)
                ADAutomationActionChip(null, active)
                ADAutomationActionChip(null, active)
            }
        }
    }
}

@Composable
private fun ADAutomationActionChip(label: String?, active: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(9.dp),
        color = if (active) ADColors.SurfaceSubtle else ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(ADColors.Ink, CircleShape),
            )
            Spacer(Modifier.width(5.dp))
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = ADColors.Ink,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(3.dp)
                        .background(ADColors.Muted.copy(alpha = 0.36f), RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun ADConfigurationCard(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 92.dp),
        shape = RoundedCornerShape(17.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(10.dp),
                color = ADColors.SurfaceSubtle,
                contentColor = ADColors.Ink,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
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

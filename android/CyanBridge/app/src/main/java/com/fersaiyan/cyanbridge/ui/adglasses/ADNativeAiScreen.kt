package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.R
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

private enum class ADSkillArtwork { TIMELINE, DIARY }

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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 10.dp, 12.dp, 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ADAssetIcon(R.drawable.ad_codex_ai, Modifier.size(42.dp), "AI")
                Column(Modifier.padding(start = 10.dp)) {
                    Text("INTELLIGENCE", style = MaterialTheme.typography.labelSmall, color = ADColors.InkSoft)
                    Text("AI that feels like yours", style = MaterialTheme.typography.headlineLarge, color = ADColors.Ink)
                }
            }
        }

        item { ADAiProviderCard(selectedName, selected, ::select) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ADSectionTitle("Skills")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ADAiSkillCard(
                        artwork = ADSkillArtwork.TIMELINE,
                        title = "Timeline",
                        detail = "Search moments over time",
                        active = timelineActive,
                        modifier = Modifier.weight(1f),
                        onClick = { setCapability(AssistantCapability.VISUAL_DIARY, !timelineActive) },
                    )
                    ADAiSkillCard(
                        artwork = ADSkillArtwork.DIARY,
                        title = "Diary",
                        detail = "A private recap of your day",
                        active = diaryActive,
                        modifier = Modifier.weight(1f),
                        onClick = { setCapability(AssistantCapability.AUTO_DIARY, !diaryActive) },
                    )
                }
                ADAutomationCard(
                    active = automationActive,
                    ready = automationReady,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { setCapability(AssistantCapability.LOCAL_AGENT, !automationActive) },
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ADSectionTitle("Configuration")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ADConfigurationCard(Icons.Outlined.Apps, "Apps", "Gemini / ChatGPT", Modifier.weight(1f), onAssistantApps)
                    ADConfigurationCard(Icons.Outlined.Cloud, "Relay", if (relayConfigured) "Ready" else "Set up", Modifier.weight(1f), onRelaySettings)
                    ADConfigurationCard(Icons.Outlined.Computer, "Local", "On device", Modifier.weight(1f), onLocalSettings)
                }
            }
        }
    }
}

@Composable
private fun ADAiProviderCard(selectedName: String, selected: ADAiChoice, onSelect: (ADAiChoice) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ADAssetIcon(R.drawable.ad_codex_ai, Modifier.size(34.dp), null)
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text("ANSWER WITH", style = MaterialTheme.typography.labelSmall, color = ADColors.InkSoft)
                    Text(selectedName, style = MaterialTheme.typography.titleLarge, color = ADColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(5.dp).background(ADColors.Red, CircleShape))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ADAiProviderPill("Gemini", selected == ADAiChoice.GEMINI, Modifier.weight(1f)) { onSelect(ADAiChoice.GEMINI) }
                ADAiProviderPill("Codex", selected == ADAiChoice.OPENAI_CODEX, Modifier.weight(1f)) { onSelect(ADAiChoice.OPENAI_CODEX) }
                ADAiProviderPill("Local", selected == ADAiChoice.LOCAL, Modifier.weight(1f)) { onSelect(ADAiChoice.LOCAL) }
            }
        }
    }
}

@Composable
private fun ADAiProviderPill(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val container by animateColorAsState(if (selected) ADColors.SurfacePressed else ADColors.SurfaceSubtle, label = "provider-container")
    val content by animateColorAsState(if (selected) ADColors.Ink else ADColors.InkSoft, label = "provider-content")
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 38.dp),
        shape = RoundedCornerShape(10.dp),
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, if (selected) ADColors.Ink.copy(alpha = 0.42f) else ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Box(Modifier.size(4.dp).background(ADColors.Red, CircleShape))
                Spacer(Modifier.width(5.dp))
            }
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 154.dp),
        shape = RoundedCornerShape(17.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, if (active) ADColors.Ink.copy(alpha = 0.46f) else ADColors.Outline),
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.fillMaxWidth().height(76.dp)) {
                when (artwork) {
                    ADSkillArtwork.TIMELINE -> ADTimelineArtwork(active, Modifier.fillMaxSize())
                    ADSkillArtwork.DIARY -> ADDiaryArtwork(active, Modifier.fillMaxSize())
                }
                if (active) Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(6.dp).background(ADColors.Red, CircleShape))
            }
            Spacer(Modifier.height(9.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = ADColors.Ink, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ADTimelineArtwork(active: Boolean, modifier: Modifier = Modifier) {
    val ink = ADColors.Ink
    val softInk = ADColors.InkSoft.copy(alpha = 0.32f)
    Surface(modifier = modifier, shape = RoundedCornerShape(13.dp), color = ADColors.SurfaceSubtle) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                repeat(3) { index ->
                    Box(Modifier.size(6.dp).background(if (active && index == 1) ADColors.Red else ink, CircleShape))
                }
            }
            Column(Modifier.padding(start = 9.dp).weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                ADAiArtworkEventLine(ink, softInk, 0.90f)
                ADAiArtworkEventLine(ink, softInk, 0.72f)
                ADAiArtworkEventLine(ink, softInk, 0.82f)
            }
        }
    }
}

@Composable
private fun ADAiArtworkEventLine(ink: Color, softInk: Color, fraction: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(ink.copy(alpha = 0.88f), RoundedCornerShape(3.dp)))
        Box(Modifier.fillMaxWidth((fraction - 0.18f).coerceAtLeast(0.32f)).height(2.dp).background(softInk, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun ADDiaryArtwork(active: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(13.dp), color = ADColors.SurfaceSubtle) {
        Surface(
            modifier = Modifier.padding(8.dp),
            shape = RoundedCornerShape(10.dp),
            color = ADColors.Surface,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(28.dp).height(5.dp).background(ADColors.Ink, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(6.dp).background(if (active) ADColors.Red else ADColors.Muted, CircleShape))
                }
                Spacer(Modifier.height(1.dp))
                Box(Modifier.fillMaxWidth(0.92f).height(2.dp).background(ADColors.InkSoft.copy(alpha = 0.42f), RoundedCornerShape(2.dp)))
                Box(Modifier.fillMaxWidth(0.76f).height(2.dp).background(ADColors.InkSoft.copy(alpha = 0.32f), RoundedCornerShape(2.dp)))
                Box(Modifier.fillMaxWidth(0.58f).height(2.dp).background(ADColors.InkSoft.copy(alpha = 0.26f), RoundedCornerShape(2.dp)))
            }
        }
    }
}

@Composable
private fun ADAutomationCard(active: Boolean, ready: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 138.dp),
        shape = RoundedCornerShape(18.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, if (active) ADColors.Ink.copy(alpha = 0.46f) else ADColors.Outline),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.06f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Automation", style = MaterialTheme.typography.titleLarge, color = ADColors.Ink, fontWeight = FontWeight.SemiBold)
                    if (active) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(5.dp).background(ADColors.Red, CircleShape))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Let AI open apps, navigate screens and complete supported actions on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(9.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = ADColors.SurfaceSubtle, border = BorderStroke(1.dp, ADColors.Outline)) {
                    Text(
                        if (ready) "PHONE ACCESS READY" else "TAP TO ALLOW ACCESS",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = ADColors.InkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            ADAutomationArtwork(active, Modifier.weight(0.94f).height(112.dp))
        }
    }
}

@Composable
private fun ADAutomationArtwork(active: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(15.dp), color = ADColors.SurfaceSubtle, border = BorderStroke(1.dp, ADColors.Outline)) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Surface(modifier = Modifier.width(44.dp).fillMaxHeight(0.84f), shape = RoundedCornerShape(13.dp), color = ADColors.Ink) {
                Surface(modifier = Modifier.padding(4.dp), shape = RoundedCornerShape(10.dp), color = ADColors.Surface) {
                    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.size(10.dp).background(ADColors.Ink, RoundedCornerShape(3.dp)))
                            Box(Modifier.size(10.dp).background(ADColors.SurfacePressed, RoundedCornerShape(3.dp)))
                        }
                        Box(Modifier.fillMaxWidth().height(3.dp).background(ADColors.SurfacePressed, RoundedCornerShape(2.dp)))
                        Box(Modifier.fillMaxWidth(0.72f).height(3.dp).background(ADColors.SurfacePressed, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.fillMaxWidth().height(13.dp).background(if (active) ADColors.RedAction else ADColors.Ink, RoundedCornerShape(6.dp)))
                    }
                }
            }
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f).fillMaxHeight(0.72f), verticalArrangement = Arrangement.SpaceEvenly) {
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
        shape = RoundedCornerShape(8.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, if (active) ADColors.Ink.copy(alpha = 0.32f) else ADColors.Outline),
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(if (active && label != null) ADColors.Red else ADColors.Ink, CircleShape))
            Spacer(Modifier.width(5.dp))
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.Ink)
            } else {
                Box(Modifier.fillMaxWidth(0.72f).height(2.dp).background(ADColors.InkSoft.copy(alpha = 0.38f), RoundedCornerShape(2.dp)))
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
        shape = RoundedCornerShape(14.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Surface(modifier = Modifier.size(32.dp), shape = RoundedCornerShape(9.dp), color = ADColors.SurfaceSubtle, contentColor = ADColors.Ink) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
            }
            Spacer(Modifier.height(9.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = ADColors.Ink, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

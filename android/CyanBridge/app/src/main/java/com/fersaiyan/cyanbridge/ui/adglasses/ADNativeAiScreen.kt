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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 10.dp,
            bottom = 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).background(ADColors.Surface, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ADGlyphIcon(ADGlyph.AI, ADColors.Ink, Modifier.size(25.dp), accent = ADColors.Red)
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text("INTELLIGENCE", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                    Text("AI", style = MaterialTheme.typography.headlineLarge)
                }
            }
        }

        item {
            ADAiProviderCard(selectedName = selectedName, selected = selected, onSelect = ::select)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ADSectionTitle("Skills")
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ADAiSkillCard(
                        artwork = ADSkillArtwork.TIMELINE,
                        title = "Timeline",
                        detail = "Search moments",
                        active = timelineActive,
                        modifier = Modifier.weight(1f),
                        onClick = { setCapability(AssistantCapability.VISUAL_DIARY, !timelineActive) },
                    )
                    ADAiSkillCard(
                        artwork = ADSkillArtwork.DIARY,
                        title = "Diary",
                        detail = "Daily private recap",
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
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ADSectionTitle("Configuration")
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ADConfigurationCard(Icons.Outlined.Apps, "Apps", "Gemini / ChatGPT", Modifier.weight(1f), onAssistantApps)
                    ADConfigurationCard(Icons.Outlined.Cloud, "Relay", if (relayConfigured) "Ready" else "Set up", Modifier.weight(1f), onRelaySettings)
                    ADConfigurationCard(Icons.Outlined.Computer, "Local", "On device", Modifier.weight(1f), onLocalSettings)
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
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.40f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ADGlyphIcon(ADGlyph.AI, ADColors.Ink, Modifier.size(22.dp), accent = ADColors.Red)
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text("ANSWER WITH", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                    Text(selectedName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(6.dp).background(ADColors.Red, CircleShape))
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
    val container by animateColorAsState(if (selected) ADColors.Ink else ADColors.SurfaceSubtle, label = "provider-container")
    val content by animateColorAsState(if (selected) Color.Black else ADColors.InkSoft, label = "provider-content")
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        shape = RoundedCornerShape(10.dp),
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, if (selected) ADColors.Ink else ADColors.Outline),
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
    val cardColor by animateColorAsState(if (active) ADColors.SurfacePressed else ADColors.Surface.copy(alpha = 0.88f), label = "skill-card")
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 108.dp),
        shape = RoundedCornerShape(14.dp),
        color = cardColor,
        border = BorderStroke(1.dp, if (active) ADColors.Ink.copy(alpha = 0.28f) else ADColors.Outline),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                when (artwork) {
                    ADSkillArtwork.TIMELINE -> ADTimelineArtwork(active, Modifier.fillMaxSize())
                    ADSkillArtwork.DIARY -> ADDiaryArtwork(active, Modifier.fillMaxSize())
                }
                if (active) Box(Modifier.align(Alignment.TopEnd).size(5.dp).background(ADColors.Red, CircleShape))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ADTimelineArtwork(active: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(9.dp), color = ADColors.SurfaceSubtle) {
        Row(modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { index ->
                    Box(Modifier.size(if (index == 1 && active) 5.dp else 4.dp).background(if (index == 1 && active) ADColors.Red else ADColors.Ink, CircleShape))
                }
            }
            Column(Modifier.padding(start = 8.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth(.88f).height(2.dp).background(ADColors.Ink.copy(alpha = .75f), CircleShape))
                Box(Modifier.fillMaxWidth(.68f).height(2.dp).background(ADColors.Ink.copy(alpha = .40f), CircleShape))
                Box(Modifier.fillMaxWidth(.78f).height(2.dp).background(ADColors.Ink.copy(alpha = .58f), CircleShape))
            }
        }
    }
}

@Composable
private fun ADDiaryArtwork(active: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(9.dp), color = ADColors.SurfaceSubtle) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(28.dp).height(4.dp).background(ADColors.Ink, CircleShape))
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(5.dp).background(if (active) ADColors.Red else ADColors.Muted, CircleShape))
            }
            Box(Modifier.fillMaxWidth(.88f).height(2.dp).background(ADColors.Ink.copy(alpha = .45f), CircleShape))
            Box(Modifier.fillMaxWidth(.72f).height(2.dp).background(ADColors.Ink.copy(alpha = .30f), CircleShape))
        }
    }
}

@Composable
private fun ADAutomationCard(active: Boolean, ready: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(14.dp),
        color = ADColors.Surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, if (active) ADColors.Ink.copy(alpha = .28f) else ADColors.Outline),
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                ADGlyphIcon(ADGlyph.AI, ADColors.Ink, Modifier.size(22.dp), accent = if (active) ADColors.Red else null)
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Automation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (active) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(5.dp).background(ADColors.Red, CircleShape))
                    }
                }
                Text(if (ready) "Phone access ready" else "Tap to allow phone access", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1)
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
        modifier = modifier.heightIn(min = 78.dp),
        shape = RoundedCornerShape(13.dp),
        color = ADColors.Surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(modifier = Modifier.padding(9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = ADColors.Ink, modifier = Modifier.size(17.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

package com.fersaiyan.cyanbridge.ui.adglasses

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MenuBook
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
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantConversationSession
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import com.fersaiyan.cyanbridge.ai.router.AiProviderType
import com.fersaiyan.cyanbridge.shared.glasses.GlassesAssistantMode
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType

enum class ADAiChoice { API, LOCAL }

@Composable
internal fun ADNativeAiScreen(
    onApiSettings: () -> Unit,
    onLocalSettings: () -> Unit,
) {
    val context = LocalContext.current
    val runtimeVersion by AssistantCapabilityRuntimeEvents.version.collectAsState()
    val capabilityExecutor = remember(context, runtimeVersion) { AndroidCapabilityCommandExecutor(context) }
    var selected by remember { mutableStateOf(resolveAiChoice(context)) }

    fun select(choice: ADAiChoice) {
        val previous = resolveAiChoice(context)
        when (choice) {
            ADAiChoice.API -> {
                if (!AiProviderPrefs.isApiConfigured(context)) {
                    Toast.makeText(context, "Add an API key first", Toast.LENGTH_SHORT).show()
                    onApiSettings()
                    return
                }
                AiProviderPrefs.setProvider(context, AiProviderType.API_TOKEN)
                LocalAgentPrefs.setProviderType(context, AgentProviderType.PRO_SUBSCRIPTION)
            }
            ADAiChoice.LOCAL -> {
                AiProviderPrefs.setProvider(context, AiProviderType.LOCAL_MODELS)
                LocalAgentPrefs.setProviderType(context, AgentProviderType.LOCAL_AGENT)
            }
        }
        LocalAgentPrefs.setGlassesAssistantMode(context, GlassesAssistantMode.CUSTOM_AI_PROVIDER)
        selected = choice
        if (previous != choice) AssistantConversationSession.get(context).startNewConversation()
    }

    fun setCapability(capability: AssistantCapability, enabled: Boolean) {
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

    val timelineActive = capabilityExecutor.isActive(AssistantCapability.VISUAL_DIARY)
    val diaryActive = capabilityExecutor.isActive(AssistantCapability.AUTO_DIARY)
    val selectedName = when (selected) {
        ADAiChoice.API -> "${AiProviderPrefs.getApiProvider(context).label} API"
        ADAiChoice.LOCAL -> "Local AI"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text("AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
        Text(
            "One direct API route, or a model running on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
        )

        Spacer(Modifier.height(8.dp))
        ADAiProviderCard(selectedName, selected, ::select)

        Spacer(Modifier.height(10.dp))
        Text("Skills", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ADAiSkillCard(
                icon = Icons.Outlined.History,
                title = "Timeline",
                detail = "Search moments over time",
                active = timelineActive,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { setCapability(AssistantCapability.VISUAL_DIARY, !timelineActive) }
            ADAiSkillCard(
                icon = Icons.Outlined.MenuBook,
                title = "Diary",
                detail = "A private recap of your day",
                active = diaryActive,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { setCapability(AssistantCapability.AUTO_DIARY, !diaryActive) }
        }

        Spacer(Modifier.height(10.dp))
        Text("Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ADConfigurationCard(
                icon = Icons.Outlined.Key,
                title = "API",
                detail = if (AiProviderPrefs.isApiConfigured(context)) {
                    AiProviderPrefs.getApiProvider(context).label
                } else {
                    "Add provider key"
                },
                modifier = Modifier.weight(1f),
                onClick = onApiSettings,
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
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = ADColors.Surface, modifier = Modifier.size(20.dp))
                    }
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("Selected route", style = MaterialTheme.typography.labelSmall, color = ADColors.Surface.copy(alpha = 0.62f))
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
                ADAiProviderPill("API", selected == ADAiChoice.API, Modifier.weight(1f)) { onSelect(ADAiChoice.API) }
                ADAiProviderPill("Local", selected == ADAiChoice.LOCAL, Modifier.weight(1f)) { onSelect(ADAiChoice.LOCAL) }
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
    icon: ImageVector,
    title: String,
    detail: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (active) ADColors.SurfaceSubtle else ADColors.Surface,
        border = BorderStroke(
            1.dp,
            if (active) ADColors.Ink.copy(alpha = 0.30f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = if (active) ADColors.Ink else ADColors.SurfaceSubtle,
                    contentColor = if (active) ADColors.Surface else ADColors.Ink,
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(22.dp)) }
                }
                if (active) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        "Enabled",
                        modifier = Modifier.align(Alignment.TopEnd).size(20.dp),
                        tint = ADColors.Ink,
                    )
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 2)
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
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(10.dp),
                color = ADColors.SurfaceSubtle,
                contentColor = ADColors.Ink,
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(18.dp)) }
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted, maxLines = 2)
            }
        }
    }
}

private fun resolveAiChoice(context: android.content.Context): ADAiChoice =
    if (AiProviderPrefs.getProvider(context) == AiProviderType.LOCAL_MODELS ||
        LocalAgentPrefs.getProviderType(context) == AgentProviderType.LOCAL_AGENT
    ) {
        ADAiChoice.LOCAL
    } else {
        ADAiChoice.API
    }

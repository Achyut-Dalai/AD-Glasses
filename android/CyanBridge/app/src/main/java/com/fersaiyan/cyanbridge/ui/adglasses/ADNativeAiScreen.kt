package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import com.fersaiyan.cyanbridge.ai.router.AiProviderType
import com.fersaiyan.cyanbridge.ai.router.CliRelayBackend
import com.fersaiyan.cyanbridge.shared.glasses.GlassesAssistantMode
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType
import com.fersaiyan.cyanbridge.ui.hasAccessibilityServicePermission

enum class ADAiChoice {
    GEMINI,
    OPENAI_CODEX,
    LOCAL,
}

@Composable
internal fun ADNativeAiScreen(
    onRelaySettings: () -> Unit,
    onLocalSettings: () -> Unit,
    onAssistantApps: () -> Unit,
    onOpenCapability: (ADAutomation) -> Unit = {},
) {
    val context = LocalContext.current
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

    val relayConfigured = AiProviderPrefs.isRelayConfigured(context)
    val phoneControlReady = hasAccessibilityServicePermission(context)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 18.dp,
            bottom = 34.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Capabilities", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ADAiCapabilityTile(
                        icon = Icons.Rounded.Translate,
                        title = "Translate",
                        detail = "Live translation",
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenCapability(ADAutomation.TRANSLATOR) },
                    )
                    ADAiCapabilityTile(
                        icon = Icons.Outlined.Description,
                        title = "Meeting Notes",
                        detail = "Record & summarize",
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenCapability(ADAutomation.MEETING_NOTES) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ADAiCapabilityTile(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = "Visual Diary",
                        detail = "Searchable captures",
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenCapability(ADAutomation.VISUAL_DIARY) },
                    )
                    ADAiCapabilityTile(
                        icon = Icons.Outlined.Memory,
                        title = "Daily Diary",
                        detail = "Private daily context",
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenCapability(ADAutomation.AUTO_DIARY) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ADAiCapabilityTile(
                        icon = Icons.Outlined.Checklist,
                        title = "Errands",
                        detail = "Tasks & reminders",
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenCapability(ADAutomation.ERRAND_BRAIN) },
                    )
                    ADAiCapabilityTile(
                        icon = Icons.Outlined.PhoneAndroid,
                        title = "Phone Control",
                        detail = if (phoneControlReady) "Android actions" else "Setup required",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (phoneControlReady) {
                                onOpenCapability(ADAutomation.LOCAL_AGENT)
                            } else {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        },
                    )
                }
            }
        }

        item {
            ADAiSection(title = "Default AI") {
                ADAiChoiceRow(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "Gemini",
                    detail = "Gemini through your relay",
                    selected = selected == ADAiChoice.GEMINI,
                    onClick = { select(ADAiChoice.GEMINI) },
                )
                ADAiSectionDivider()
                ADAiChoiceRow(
                    icon = Icons.Outlined.Cloud,
                    title = "OpenAI / Codex",
                    detail = "OpenAI-compatible route through your relay",
                    selected = selected == ADAiChoice.OPENAI_CODEX,
                    onClick = { select(ADAiChoice.OPENAI_CODEX) },
                )
                ADAiSectionDivider()
                ADAiChoiceRow(
                    icon = Icons.Outlined.Computer,
                    title = "Local AI",
                    detail = "Run a configured model on this phone",
                    selected = selected == ADAiChoice.LOCAL,
                    onClick = { select(ADAiChoice.LOCAL) },
                )
            }
        }

        item {
            ADAiSection(title = "Connections") {
                ADAiActionRow(
                    icon = Icons.Outlined.Apps,
                    title = "Assistant apps",
                    detail = "Optional Gemini or ChatGPT app handoff",
                    onClick = onAssistantApps,
                )
                ADAiSectionDivider()
                ADAiActionRow(
                    icon = Icons.Outlined.Cloud,
                    title = "Relay",
                    detail = if (relayConfigured) "Server, backend and web access" else "Add your relay server",
                    onClick = onRelaySettings,
                )
                ADAiSectionDivider()
                ADAiActionRow(
                    icon = Icons.Outlined.Computer,
                    title = "Local & compatible models",
                    detail = "Local files and OpenAI-compatible endpoints",
                    onClick = onLocalSettings,
                )
            }
        }
    }
}

@Composable
private fun ADAiCapabilityTile(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = 112.dp)
            .background(ADColors.Surface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ADAiSection(
    title: String,
    content: @Composable Column.() -> Unit,
) {
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(
                if (selected) ADColors.BlueSoft else ADColors.SurfaceSubtle,
                RoundedCornerShape(11.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) ADColors.Blue else ADColors.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        if (selected) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected", tint = ADColors.Blue, modifier = Modifier.size(21.dp))
        }
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(22.dp))
    }
}

private fun resolveAiChoice(context: android.content.Context): ADAiChoice {
    return when (LocalAgentPrefs.getProviderType(context)) {
        AgentProviderType.LOCAL_AGENT -> ADAiChoice.LOCAL
        AgentProviderType.PRO_SUBSCRIPTION -> when (AiProviderPrefs.getRelayBackend(context)) {
            CliRelayBackend.GEMINI -> ADAiChoice.GEMINI
            CliRelayBackend.CODEX -> ADAiChoice.OPENAI_CODEX
        }
        AgentProviderType.TASKER -> ADAiChoice.GEMINI
    }
}

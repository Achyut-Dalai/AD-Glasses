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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.KeyboardArrowRight
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
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.agent.CloudSettingsActivity
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.agent.LocalModelsConfigureActivity
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
internal fun ADNativeAiScreen() {
    val context = LocalContext.current
    var selected by remember {
        mutableStateOf(resolveAiChoice(context))
    }

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

    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "AI")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 30.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Default AI", style = MaterialTheme.typography.titleLarge)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ADColors.Surface, RoundedCornerShape(18.dp))
                            .padding(horizontal = 15.dp),
                    ) {
                        ADAiChoiceRow(
                            icon = Icons.Outlined.AutoAwesome,
                            title = "Gemini",
                            detail = "Use the Gemini relay backend",
                            selected = selected == ADAiChoice.GEMINI,
                            onClick = { select(ADAiChoice.GEMINI) },
                        )
                        HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
                        ADAiChoiceRow(
                            icon = Icons.Outlined.Cloud,
                            title = "OpenAI / Codex",
                            detail = "Use the Codex relay backend",
                            selected = selected == ADAiChoice.OPENAI_CODEX,
                            onClick = { select(ADAIChoice.OPENAI_CODEX) },
                        )
                        HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
                        ADAiChoiceRow(
                            icon = Icons.Outlined.Memory,
                            title = "Local AI",
                            detail = "Run a configured model on this phone",
                            selected = selected == ADAiChoice.LOCAL,
                            onClick = { select(ADAIChoice.LOCAL) },
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Capabilities", style = MaterialTheme.typography.titleLarge)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ADColors.Surface, RoundedCornerShape(18.dp))
                            .padding(horizontal = 15.dp),
                    ) {
                        ADAiStatusRow(
                            icon = Icons.Outlined.Visibility,
                            title = "Vision",
                            detail = "Ask what I see through the glasses camera",
                            status = "Ready",
                        )
                        HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
                        ADAiActionRow(
                            icon = Icons.Outlined.Public,
                            title = "Web Search",
                            detail = if (relayConfigured) "Available for fresh and current questions" else "Configure a relay to enable web-backed answers",
                            onClick = {
                                context.startActivity(Intent(context, CloudSettingsActivity::class.java))
                            },
                        )
                        HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
                        ADAiActionRow(
                            icon = Icons.Outlined.PhoneAndroid,
                            title = "Phone control",
                            detail = if (phoneControlReady) {
                                "Ready for supported Android actions"
                            } else {
                                "Accessibility access is required for phone actions"
                            },
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Configuration", style = MaterialTheme.typography.titleLarge)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ADColors.Surface, RoundedCornerShape(18.dp))
                            .padding(horizontal = 15.dp),
                    ) {
                        ADAiActionRow(
                            icon = Icons.Outlined.Settings,
                            title = "Relay settings",
                            detail = if (relayConfigured) "Server and model routing" else "Add your relay server",
                            onClick = {
                                context.startActivity(Intent(context, CloudSettingsActivity::class.java))
                            },
                        )
                        HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
                        ADAiActionRow(
                            icon = Icons.Outlined.Computer,
                            title = "Local and OpenAI-compatible models",
                            detail = "Install local models or configure an OpenAI-compatible server",
                            onClick = {
                                context.startActivity(Intent(context, LocalModelsConfigureActivity::class.java))
                            },
                        )
                    }
                }
            }
        }
    }
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(
                if (selected) ADColors.BlueSoft else ADColors.SurfaceSubtle,
                RoundedCornerShape(11.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (selected) ADColors.Blue else ADColors.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        if (selected) {
            Icon(Icons.Outlined.CheckCircle, "Selected", tint = ADColors.Blue, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun ADAiStatusRow(
    icon: ImageVector,
    title: String,
    detail: String,
    status: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = ADColors.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        Text(status, style = MaterialTheme.typography.labelMedium, color = ADColors.Success)
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = ADColors.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted, modifier = Modifier.size(22.dp))
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

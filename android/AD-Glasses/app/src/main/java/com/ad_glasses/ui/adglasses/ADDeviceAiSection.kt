package com.ad_glasses.ui.adglasses

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.ad_glasses.agent.LocalAgentPrefs
import com.ad_glasses.ai.orchestrator.AssistantConversationSession
import com.ad_glasses.ai.router.AiProviderPrefs
import com.ad_glasses.ai.router.AiProviderType
import com.ad_glasses.shared.settings.AgentProviderType

enum class ADAiChoice { CLOUD, LOCAL }

/** Cloud/Local routing and configuration embedded directly in Device Center. */
@Composable
internal fun ADDeviceAiSection(
    onCloudSettings: () -> Unit,
    onLocalSettings: () -> Unit,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(resolveAiChoice(context)) }

    fun select(choice: ADAiChoice) {
        val previous = resolveAiChoice(context)
        when (choice) {
            ADAiChoice.CLOUD -> {
                if (!AiProviderPrefs.isApiConfigured(context)) {
                    Toast.makeText(context, "Add a Cloud API key first", Toast.LENGTH_SHORT).show()
                    onCloudSettings()
                    return
                }
                AiProviderPrefs.setProvider(context, AiProviderType.CLOUD_API)
                LocalAgentPrefs.setProviderType(context, AgentProviderType.CLOUD_AI)
            }
            ADAiChoice.LOCAL -> {
                AiProviderPrefs.setProvider(context, AiProviderType.LOCAL_MODELS)
                LocalAgentPrefs.setProviderType(context, AgentProviderType.LOCAL_AGENT)
            }
        }
        selected = choice
        if (previous != choice) AssistantConversationSession.get(context).startNewConversation()
    }

    val selectedName = when (selected) {
        ADAiChoice.CLOUD -> "Cloud · ${AiProviderPrefs.getApiProvider(context).label}"
        ADAiChoice.LOCAL -> "Local AI"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Cloud AI for REST/Realtime sessions, with Local AI as the offline fallback.",
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
        )
        Spacer(Modifier.height(8.dp))
        ADAiProviderCard(selectedName, selected, ::select)

        Spacer(Modifier.height(12.dp))
        Text("Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ADConfigurationCard(
                icon = Icons.Outlined.Cloud,
                title = "Cloud",
                detail = if (AiProviderPrefs.isApiConfigured(context)) {
                    AiProviderPrefs.getApiProvider(context).label
                } else {
                    "REST + Realtime"
                },
                modifier = Modifier.weight(1f),
                onClick = onCloudSettings,
            )
            ADConfigurationCard(
                icon = Icons.Outlined.Computer,
                title = "Local",
                detail = "Offline models",
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
                ADAiProviderPill("Cloud", selected == ADAiChoice.CLOUD, Modifier.weight(1f)) { onSelect(ADAiChoice.CLOUD) }
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
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                }
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
        ADAiChoice.CLOUD
    }

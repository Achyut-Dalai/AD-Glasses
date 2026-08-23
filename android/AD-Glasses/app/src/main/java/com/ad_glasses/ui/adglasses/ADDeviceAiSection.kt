package com.ad_glasses.ui.adglasses

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
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ad_glasses.ai.orchestrator.AssistantInferenceContextPolicy
import com.ad_glasses.ai.router.AiProviderPrefs

/** AI and voice overview embedded directly in Device Center. */
@Composable
internal fun ADDeviceAiSection(
    onCloudSettings: () -> Unit,
) {
    val context = LocalContext.current
    val active = AiProviderPrefs.getActiveProfile(context)
    val configured = AiProviderPrefs.isApiConfigured(context)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Cloud AI handles reasoning. Voice capture/playback stay on Android, while model context is kept short without deleting your Chat history.",
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
        )
        Spacer(Modifier.height(8.dp))

        Surface(
            onClick = onCloudSettings,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            shape = RoundedCornerShape(20.dp),
            color = ADColors.Surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = ADColors.SurfaceSubtle,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Cloud, null, tint = Color.Black, modifier = Modifier.size(19.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        active?.name ?: "Cloud AI profiles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            active == null -> "Add an API profile"
                            !configured -> "${active.provider.label} · setup incomplete"
                            else -> "${active.provider.label} · ${active.model}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ADStatusChip(
                    text = if (configured) "READY" else "SETUP",
                    tone = if (configured) ADStatusTone.SUCCESS else ADStatusTone.NEUTRAL,
                    showCheck = configured,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = ADColors.SurfaceSubtle,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Voice & conversation",
                        style = MaterialTheme.typography.titleSmall,
                        color = ADColors.Ink,
                    )
                    Text(
                        "Android speech + TTS · ${AssistantInferenceContextPolicy.INACTIVITY_TTL_MS / 1_000}s active context · full history kept",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
                ADStatusChip(
                    text = "BOUNDED",
                    tone = ADStatusTone.NEUTRAL,
                    showCheck = false,
                )
            }
        }
    }
}

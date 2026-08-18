package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.sp
import com.fersaiyan.cyanbridge.ai.orchestrator.AndroidCapabilityCommandExecutor
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapability
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapabilityAction
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapabilityCommand

/** Native control surface for an AI capability. No plugin SettingsActivity is required. */
@Composable
internal fun ADNativeCapabilityDetailScreen(
    automation: ADAutomation,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val executor = remember(context) { AndroidCapabilityCommandExecutor(context) }
    val capability = automation.toAssistantCapability()
    var active by remember(automation) { mutableStateOf(executor.isActive(capability)) }
    var resultText by remember(automation) { mutableStateOf<String?>(null) }
    var lastSucceeded by remember(automation) { mutableStateOf<Boolean?>(null) }

    val switchingFrom = if (!active && executor.isExclusiveVoiceCapability(capability)) {
        executor.activeVoiceCapability(excluding = capability)
    } else {
        null
    }

    fun execute(action: AssistantCapabilityAction) {
        val result = executor.execute(
            AssistantCapabilityCommand(
                capability = capability,
                action = action,
            ),
        )
        resultText = result.spokenText
        val failure = result.richText.startsWith("Capability command failed:", ignoreCase = true) ||
            result.spokenText.contains("needs permission setup", ignoreCase = true) ||
            result.spokenText.contains("couldn’t change", ignoreCase = true)
        lastSucceeded = !failure
        active = executor.isActive(capability)
    }

    ADPageLayout(automation.title, onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .background(ADColors.SurfaceSubtle, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    automation.capabilityIcon(),
                    contentDescription = null,
                    tint = ADColors.Ink,
                    modifier = Modifier.size(29.dp),
                )
            }
            Text(
                text = automation.outcome.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = ADColors.Muted,
                letterSpacing = 1.05.sp,
            )
            Text(
                text = automation.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = ADColors.Muted,
            )
        }

        switchingFrom?.let { previous ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ADColors.SurfaceSubtle,
                shape = RoundedCornerShape(17.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = ADColors.Muted,
                        modifier = Modifier.size(21.dp),
                    )
                    Text(
                        "${executor.displayName(previous)} is using live listening. Turning on ${automation.title} will switch live listening to this capability.",
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ADColors.Ink,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (active) ADColors.SurfaceSubtle else ADColors.Surface,
            shape = RoundedCornerShape(22.dp),
            shadowElevation = if (active) 0.dp else 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (active) "On" else "Off",
                        style = MaterialTheme.typography.titleLarge,
                        color = ADColors.Ink,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = when {
                            active && executor.isExclusiveVoiceCapability(capability) -> "Listening is assigned to this capability."
                            active -> "Ready when you use it from the glasses or phone."
                            switchingFrom != null -> "Turning this on will move live listening here."
                            else -> "Turn it on when you want this capability available."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
                Switch(
                    checked = active,
                    onCheckedChange = { enabled ->
                        execute(if (enabled) AssistantCapabilityAction.START else AssistantCapabilityAction.STOP)
                    },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "DETAILS",
                style = MaterialTheme.typography.labelSmall,
                color = ADColors.Muted,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 5.dp),
            )
            ADCapabilityDetailRow(
                icon = Icons.Outlined.Lock,
                label = "Processing",
                value = automation.boundary,
            )
            HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
            ADCapabilityDetailRow(
                icon = Icons.Outlined.FolderOpen,
                label = "Saved as",
                value = automation.nativeOutput(),
            )
        }

        resultText?.let { message ->
            val failed = lastSucceeded == false
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (failed) ADColors.WarningSoft else ADColors.SuccessSoft,
                shape = RoundedCornerShape(17.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        if (failed) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = if (failed) ADColors.Warning else ADColors.Success,
                        modifier = Modifier.size(21.dp),
                    )
                    Text(
                        message,
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ADColors.Ink,
                    )
                }
            }
        }

        Text(
            "Tip · You can also turn this capability on or off by voice from the glasses.",
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun ADCapabilityDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
            Spacer(Modifier.height(1.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, color = ADColors.Ink)
        }
    }
}

private fun ADAutomation.capabilityIcon(): ImageVector = when (this) {
    ADAutomation.LOCAL_AGENT -> Icons.Outlined.Bolt
    ADAutomation.MEETING_NOTES -> Icons.Outlined.GraphicEq
    ADAutomation.LIVE_CAPTIONS -> Icons.Outlined.GraphicEq
    ADAutomation.TRANSLATOR -> Icons.Rounded.Translate
    ADAutomation.ERRAND_BRAIN -> Icons.Outlined.EventRepeat
    ADAutomation.AUTO_DIARY -> Icons.Outlined.AutoStories
    ADAutomation.AUTO_AUDIO -> Icons.Outlined.Mic
    ADAutomation.VISUAL_DIARY -> Icons.Outlined.Timeline
}

internal fun ADAutomation.toAssistantCapability(): AssistantCapability = when (this) {
    ADAutomation.TRANSLATOR -> AssistantCapability.TRANSLATOR
    ADAutomation.MEETING_NOTES -> AssistantCapability.MEETING_NOTES
    ADAutomation.LIVE_CAPTIONS -> AssistantCapability.LIVE_CAPTIONS
    ADAutomation.ERRAND_BRAIN -> AssistantCapability.ERRAND_BRAIN
    ADAutomation.AUTO_DIARY -> AssistantCapability.AUTO_DIARY
    ADAutomation.AUTO_AUDIO -> AssistantCapability.AUTO_AUDIO
    ADAutomation.VISUAL_DIARY -> AssistantCapability.VISUAL_DIARY
    ADAutomation.LOCAL_AGENT -> AssistantCapability.LOCAL_AGENT
}

private fun ADAutomation.nativeOutput(): String = when (this) {
    ADAutomation.LOCAL_AGENT -> "Approved Android action"
    ADAutomation.MEETING_NOTES -> "Transcript and notes"
    ADAutomation.LIVE_CAPTIONS -> "Live captions"
    ADAutomation.TRANSLATOR -> "Translated speech"
    ADAutomation.ERRAND_BRAIN -> "Scheduled tasks and reminders"
    ADAutomation.AUTO_DIARY -> "Private daily note"
    ADAutomation.AUTO_AUDIO -> "Audio and transcript"
    ADAutomation.VISUAL_DIARY -> "Visual timeline"
}

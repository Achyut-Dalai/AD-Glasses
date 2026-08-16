package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.ai.orchestrator.AndroidModeCommandExecutor
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantMode
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantModeAction
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantModeCommand

/** Native start/stop surface for a task. No plugin SettingsActivity is required. */
@Composable
internal fun ADNativeTaskDetailScreen(
    automation: ADAutomation,
    initiallyActive: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val executor = remember(context) { AndroidModeCommandExecutor(context) }
    var active by remember(automation, initiallyActive) { mutableStateOf(initiallyActive) }
    var resultText by remember(automation) { mutableStateOf<String?>(null) }
    var lastSucceeded by remember(automation) { mutableStateOf<Boolean?>(null) }

    fun execute(action: AssistantModeAction) {
        val result = executor.execute(
            AssistantModeCommand(
                mode = automation.toAssistantMode(),
                action = action,
            ),
        )
        resultText = result.spokenText
        val failure = result.richText.startsWith("Mode command failed:", ignoreCase = true) ||
            result.spokenText.contains("needs permission setup", ignoreCase = true) ||
            result.spokenText.contains("couldn’t change", ignoreCase = true)
        lastSucceeded = !failure
        if (!failure) active = action == AssistantModeAction.START
    }

    ADPageLayout(automation.title, onBack) {
        ADCard {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(48.dp).background(
                        if (active) ADColors.SuccessSoft else ADColors.BlueSoft,
                        RoundedCornerShape(15.dp),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = if (active) ADColors.Success else ADColors.Blue,
                    )
                }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text(automation.outcome, style = MaterialTheme.typography.labelLarge, color = ADColors.Blue)
                    Spacer(Modifier.height(3.dp))
                    Text(automation.summary, style = MaterialTheme.typography.bodyLarge)
                }
                ADStatusChip(
                    if (active) "On" else "Off",
                    if (active) ADStatusTone.SUCCESS else ADStatusTone.NEUTRAL,
                )
            }
        }

        ADCard {
            ADTaskMetric(Icons.Outlined.Lock, "Processing", automation.boundary)
            HorizontalDivider(Modifier.padding(start = 32.dp), color = ADColors.Separator)
            ADTaskMetric(Icons.Outlined.FolderOpen, "Saved as", automation.nativeOutput())
        }

        resultText?.let { message ->
            ADCard {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        if (lastSucceeded == false) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = if (lastSucceeded == false) ADColors.Warning else ADColors.Success,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        message,
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (active) {
            OutlinedButton(
                onClick = { execute(AssistantModeAction.STOP) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ADColors.Error),
            ) {
                Icon(Icons.Outlined.StopCircle, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Stop task")
            }
        } else {
            Button(
                onClick = { execute(AssistantModeAction.START) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Start task")
            }
        }

        Text(
            "You can also start or stop this task by voice from the glasses.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )
    }
}

@Composable
private fun ADTaskMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(Modifier.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(21.dp))
        Text(label, Modifier.padding(start = 11.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
    }
}

private fun ADAutomation.toAssistantMode(): AssistantMode = when (this) {
    ADAutomation.TRANSLATOR -> AssistantMode.TRANSLATOR
    ADAutomation.MEETING_NOTES -> AssistantMode.MEETING_NOTES
    ADAutomation.LIVE_CAPTIONS -> AssistantMode.LIVE_CAPTIONS
    ADAutomation.ERRAND_BRAIN -> AssistantMode.ERRAND_BRAIN
    ADAutomation.AUTO_DIARY -> AssistantMode.AUTO_DIARY
    ADAutomation.AUTO_AUDIO -> AssistantMode.AUTO_AUDIO
    ADAutomation.VISUAL_DIARY -> AssistantMode.VISUAL_DIARY
    ADAutomation.LOCAL_AGENT -> AssistantMode.LOCAL_AGENT
}

private fun ADAutomation.nativeOutput(): String = when (this) {
    ADAutomation.LOCAL_AGENT -> "Approved Android action"
    ADAutomation.MEETING_NOTES -> "Transcript and notes"
    ADAutomation.LIVE_CAPTIONS -> "Live captions"
    ADAutomation.TRANSLATOR -> "Translated speech"
    ADAutomation.ERRAND_BRAIN -> "Tasks and reminders"
    ADAutomation.AUTO_DIARY -> "Private daily summary"
    ADAutomation.AUTO_AUDIO -> "Audio and transcript"
    ADAutomation.VISUAL_DIARY -> "Visual timeline"
}

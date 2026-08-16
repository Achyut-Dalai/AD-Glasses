package com.fersaiyan.cyanbridge.ui.adglasses

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
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ADTasksScreen(
    activeShortcutTitle: String?,
    onTask: (ADAutomation) -> Unit,
) {
    val visibleTasks = ADAutomation.entries.filter { it.visibleInTasks }
    val activeTask = visibleTasks.firstOrNull { it.runtimeTitle == activeShortcutTitle }

    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "Tasks")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 30.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            activeTask?.let { task ->
                item(key = "active") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ADColors.SuccessSoft, RoundedCornerShape(16.dp))
                            .clickable { onTask(task) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).background(ADColors.Success, CircleShape))
                        Text(
                            task.title,
                            modifier = Modifier.padding(start = 10.dp).weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("Running", style = MaterialTheme.typography.labelMedium, color = ADColors.Success)
                    }
                }
            }

            item(key = "task-list") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ADColors.Surface, RoundedCornerShape(18.dp))
                        .padding(horizontal = 15.dp),
                ) {
                    visibleTasks.forEachIndexed { index, task ->
                        ADTaskRow(
                            task = task,
                            active = task.runtimeTitle == activeShortcutTitle,
                            onClick = { onTask(task) },
                        )
                        if (index != visibleTasks.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 48.dp),
                                color = ADColors.Separator,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ADTaskRow(
    task: ADAutomation,
    active: Boolean,
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
            modifier = Modifier
                .size(38.dp)
                .background(
                    if (active) ADColors.SuccessSoft else ADColors.SurfaceSubtle,
                    RoundedCornerShape(11.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                task.icon(),
                contentDescription = null,
                tint = if (active) ADColors.Success else ADColors.Ink,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (active) {
                    Text("On", style = MaterialTheme.typography.labelMedium, color = ADColors.Success)
                }
            }
            Spacer(Modifier.size(2.dp))
            Text(
                task.summary,
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = ADColors.Muted,
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun ADAutomation.icon(): ImageVector = when (this) {
    ADAutomation.MEETING_NOTES -> Icons.Outlined.Notes
    ADAutomation.TRANSLATOR -> Icons.Outlined.Translate
    ADAutomation.ERRAND_BRAIN -> Icons.Outlined.Checklist
    ADAutomation.AUTO_DIARY -> Icons.Outlined.Description
    ADAutomation.VISUAL_DIARY -> Icons.Outlined.Image
    ADAutomation.LOCAL_AGENT,
    ADAutomation.LIVE_CAPTIONS,
    ADAutomation.AUTO_AUDIO -> Icons.Outlined.Checklist
}

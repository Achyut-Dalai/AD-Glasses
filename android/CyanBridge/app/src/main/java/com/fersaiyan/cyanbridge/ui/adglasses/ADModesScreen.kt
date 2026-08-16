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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Things the glasses can do for the user, either started here or by voice. */
@Composable
internal fun ADModesScreen(
    activeShortcutTitle: String?,
    onMode: (ADAutomation) -> Unit,
) {
    val activeMode = ADAutomation.entries.firstOrNull { it.runtimeTitle == activeShortcutTitle }

    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "Modes")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = 30.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "Things glasses can do for me",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        "Start a mode here, or ask through the glasses. Active modes keep running on the phone in the background.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ADColors.Muted,
                    )
                }
            }

            activeShortcutTitle?.let {
                item {
                    ADCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(9.dp).background(ADColors.Success, CircleShape),
                            )
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(
                                    "Active now",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ADColors.Success,
                                )
                                Text(
                                    activeMode?.title ?: "Active mode",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            ADStatusChip("ON", ADStatusTone.SUCCESS, showCheck = true)
                        }
                    }
                }
            }

            items(ADAutomation.entries, key = { it.name }) { mode ->
                ADModeRow(
                    mode = mode,
                    active = mode.runtimeTitle == activeShortcutTitle,
                    onClick = { onMode(mode) },
                )
            }
        }
    }
}

@Composable
private fun ADModeRow(
    mode: ADAutomation,
    active: Boolean,
    onClick: () -> Unit,
) {
    val icon = mode.icon()
    ADCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (active) ADColors.SuccessSoft else ADColors.SurfaceSubtle,
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (active) ADColors.Success else ADColors.Ink,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        mode.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (active) ADStatusChip("ON", ADStatusTone.SUCCESS)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    mode.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ADColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    mode.outcome,
                    style = MaterialTheme.typography.labelMedium,
                    color = ADColors.Blue,
                )
            }
            Icon(
                Icons.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = ADColors.Muted,
            )
        }
    }
}

private fun ADAutomation.icon(): ImageVector = when (this) {
    ADAutomation.LOCAL_AGENT -> Icons.Outlined.AutoAwesome
    ADAutomation.MEETING_NOTES -> Icons.Outlined.Notes
    ADAutomation.LIVE_CAPTIONS -> Icons.Outlined.GraphicEq
    ADAutomation.TRANSLATOR -> Icons.Outlined.Translate
    ADAutomation.ERRAND_BRAIN -> Icons.Outlined.Checklist
    ADAutomation.AUTO_DIARY -> Icons.Outlined.Description
    ADAutomation.AUTO_AUDIO -> Icons.Outlined.Mic
    ADAutomation.VISUAL_DIARY -> Icons.Outlined.Image
}

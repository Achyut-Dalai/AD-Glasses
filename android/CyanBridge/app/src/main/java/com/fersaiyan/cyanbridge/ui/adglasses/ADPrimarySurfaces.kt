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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The phone is a continuation and review surface for an assistant that primarily lives
 * on the glasses. The shared session/orchestrator will replace the legacy chat callbacks
 * beneath this UI without changing the product model shown here.
 */
@Composable
internal fun ADConversationsScreen(host: ADHostActions) {
    var message by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "Conversations")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ADStatusChip("GLASSES FIRST", ADStatusTone.SUCCESS, showCheck = true)
                    Text(
                        text = "Pick up where your glasses left off.",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Your spoken and visual requests will live in one conversation. Use the phone for typing, long answers, links and history.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ADColors.Muted,
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ADConversationAction(
                        label = "Speak",
                        detail = "Ask through glasses",
                        icon = Icons.Outlined.Mic,
                        modifier = Modifier.weight(1f),
                        onClick = host.onVoiceQuestion,
                    )
                    ADConversationAction(
                        label = "See",
                        detail = "Ask what I see",
                        icon = Icons.Outlined.CameraAlt,
                        modifier = Modifier.weight(1f),
                        onClick = host.onImageQuestion,
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Continue on phone", style = MaterialTheme.typography.titleLarge)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ADColors.Surface, RoundedCornerShape(22.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BasicTextField(
                            value = message,
                            onValueChange = { message = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(82.dp),
                            singleLine = false,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = ADColors.Ink),
                            cursorBrush = SolidColor(ADColors.Ink),
                            decorationBox = { textField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 2.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.TopStart,
                                ) {
                                    if (message.isBlank()) {
                                        Text(
                                            "Ask anything, or continue your last conversation…",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = ADColors.Muted,
                                        )
                                    }
                                    textField()
                                }
                            },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Same AD, different screen",
                                style = MaterialTheme.typography.labelMedium,
                                color = ADColors.Muted,
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                onClick = {
                                    val prompt = message.trim()
                                    if (prompt.isEmpty()) host.onOpenChat()
                                    else {
                                        host.onOpenChatWithPrompt(prompt)
                                        message = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(ADColors.Ink, CircleShape),
                            ) {
                                Icon(
                                    Icons.Rounded.ArrowUpward,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                        }
                    }
                }
            }

            item {
                ADCard(modifier = Modifier.fillMaxWidth(), onClick = host.onOpenChat) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(ADColors.SurfaceSubtle, RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = ADColors.Ink,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                        Column(
                            modifier = Modifier
                                .padding(start = 13.dp)
                                .weight(1f),
                        ) {
                            Text("Conversation history", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Review saved chats and longer responses.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ADColors.Muted,
                            )
                        }
                        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ADConversationAction(
    label: String,
    detail: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = 116.dp)
            .background(ADColors.Surface, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(11.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Phone configuration for persistent/background wearable behaviours. */
@Composable
internal fun ADModesScreen(
    activeShortcutTitle: String?,
    onMode: (ADAutomation) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "Modes")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Set it once. Use it from your glasses.",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Configure ongoing behaviours here. AD can then start supported modes from voice without making you operate the phone.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ADColors.Muted,
                    )
                }
            }

            if (activeShortcutTitle != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ADColors.SuccessSoft, RoundedCornerShape(18.dp))
                            .padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(9.dp).background(ADColors.Success, CircleShape))
                        Column(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .weight(1f),
                        ) {
                            Text("Active now", style = MaterialTheme.typography.labelMedium, color = ADColors.Success)
                            Text(activeShortcutTitle, style = MaterialTheme.typography.titleMedium)
                        }
                        ADStatusChip("RUNNING", ADStatusTone.SUCCESS)
                    }
                }
            }

            item {
                Text("Available modes", style = MaterialTheme.typography.titleLarge)
            }

            items(ADAutomation.entries, key = { it.name }) { mode ->
                ADModeRow(
                    mode = mode,
                    active = mode.title == activeShortcutTitle,
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
    val icon = when (mode) {
        ADAutomation.LOCAL_AGENT -> Icons.Outlined.AutoAwesome
        ADAutomation.MEETING_NOTES -> Icons.Outlined.Notes
        ADAutomation.LIVE_CAPTIONS -> Icons.Outlined.GraphicEq
        ADAutomation.TRANSLATOR -> Icons.Outlined.Translate
        ADAutomation.ERRAND_BRAIN -> Icons.Outlined.Checklist
        ADAutomation.AUTO_DIARY -> Icons.Outlined.Description
        ADAutomation.AUTO_AUDIO -> Icons.Outlined.Mic
        ADAutomation.VISUAL_DIARY -> Icons.Outlined.Image
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ADColors.Surface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .padding(start = 13.dp, end = 8.dp)
                .weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mode.title, style = MaterialTheme.typography.titleMedium)
                if (active) {
                    Spacer(Modifier.size(8.dp))
                    ADStatusChip("ON", ADStatusTone.SUCCESS)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                mode.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = ADColors.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "${mode.outcome} · ${mode.boundary}",
                style = MaterialTheme.typography.labelMedium,
                color = ADColors.Muted,
            )
        }
        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted)
    }
}

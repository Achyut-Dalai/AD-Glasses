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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Phone-side continuation surface for the assistant that primarily lives on the glasses.
 *
 * This intentionally does not maintain a second assistant state. Until the shared
 * conversation/session layer lands, typed prompts and the history entry point continue
 * through the existing host chat callbacks.
 */
@Composable
internal fun ADConversationsScreen(host: ADHostActions) {
    var message by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "Conversations")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "AD is with you on the glasses",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Use the phone when you want to type, review longer answers, open links, or continue something in more detail.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ADColors.Muted,
                    )
                }
            }

            item {
                ADCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = host.onOpenChat,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(ADColors.BlueSoft, RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = ADColors.Blue,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column(
                            modifier = Modifier
                                .padding(start = 13.dp)
                                .weight(1f),
                        ) {
                            Text("Open conversation history", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Review and continue your saved conversations.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ADColors.Muted,
                            )
                        }
                        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted)
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ADConversationAction(
                        label = "Ask by voice",
                        detail = "Through your glasses",
                        icon = Icons.Outlined.Mic,
                        modifier = Modifier.weight(1f),
                        onClick = host.onVoiceQuestion,
                    )
                    ADConversationAction(
                        label = "Ask what I see",
                        detail = "Use glasses camera",
                        icon = Icons.Outlined.CameraAlt,
                        modifier = Modifier.weight(1f),
                        onClick = host.onImageQuestion,
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ADColors.Surface, RoundedCornerShape(20.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicTextField(
                        value = message,
                        onValueChange = { message = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        singleLine = false,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = ADColors.Ink),
                        cursorBrush = SolidColor(ADColors.Blue),
                        decorationBox = { textField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                contentAlignment = Alignment.TopStart,
                            ) {
                                if (message.isBlank()) {
                                    Text(
                                        "Continue from the phone…",
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
                            text = "Phone input",
                            style = MaterialTheme.typography.labelMedium,
                            color = ADColors.Muted,
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                val prompt = message.trim()
                                if (prompt.isEmpty()) {
                                    host.onOpenChat()
                                } else {
                                    host.onOpenChatWithPrompt(prompt)
                                    message = ""
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(ADColors.Blue, CircleShape),
                        ) {
                            Icon(
                                Icons.Rounded.ArrowUpward,
                                contentDescription = "Send",
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(21.dp),
                            )
                        }
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
            .background(ADColors.Surface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(ADColors.BlueSoft, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Blue, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1)
    }
}

/**
 * Configuration surface for persistent/background glasses behaviours.
 * Modes can still be entered from voice; this screen is for inspection and configuration.
 */
@Composable
internal fun ADModesScreen(
    activeShortcutTitle: String?,
    onMode: (ADAutomation) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "Modes")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Configure how your glasses help",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Set up ongoing behaviours here. You can still start or stop supported modes by speaking to AD on your glasses.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ADColors.Muted,
                    )
                }
            }

            if (activeShortcutTitle != null) {
                item {
                    ADCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
            }

            items(ADAutomation.entries, key = { it.name }) { mode ->
                ADModeCard(
                    mode = mode,
                    active = mode.title == activeShortcutTitle,
                    onClick = { onMode(mode) },
                )
            }
        }
    }
}

@Composable
private fun ADModeCard(
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

    ADCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (active) ADColors.SuccessSoft else ADColors.BlueSoft,
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (active) ADColors.Success else ADColors.Blue,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 13.dp, end = 8.dp)
                    .weight(1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        mode.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
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
                Spacer(Modifier.height(8.dp))
                Text(
                    "${mode.outcome} · ${mode.boundary}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ADColors.Muted,
                )
            }
            Icon(Icons.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted)
        }
    }
}

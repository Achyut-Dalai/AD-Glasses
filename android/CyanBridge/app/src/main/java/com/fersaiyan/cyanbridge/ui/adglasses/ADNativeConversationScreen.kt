package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.ai.orchestrator.AndroidAssistantCapabilityExecutor
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantConversationSession
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantInputSurface
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantOrchestrator
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantTurn
import com.fersaiyan.cyanbridge.chat.ChatStore
import com.fersaiyan.cyanbridge.shared.chat.ChatMessage
import com.fersaiyan.cyanbridge.shared.chat.ChatRole
import kotlinx.coroutines.launch

/** Compatibility adapter while MainActivity still owns the broad host contract. */
@Composable
internal fun ADNativeConversationScreen(host: ADHostActions) {
    ADNativeConversationScreen(
        onVoiceQuestion = host.onVoiceQuestion,
        onImageQuestion = host.onImageQuestion,
    )
}

/** Native phone continuation for the same durable conversation used by the glasses. */
@Composable
internal fun ADNativeConversationScreen(
    onVoiceQuestion: () -> Unit,
    onImageQuestion: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember(context) { AssistantConversationSession.get(context) }
    val orchestrator = remember(context) {
        AssistantOrchestrator(
            context = context,
            executor = AndroidAssistantCapabilityExecutor(context),
        )
    }

    var threadId by remember { mutableStateOf(session.activeThreadId()) }
    var messages by remember(threadId) { mutableStateOf(ChatStore.listMessages(threadId)) }
    var message by remember { mutableStateOf("") }
    var webSearch by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var pendingPrompt by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        messages = ChatStore.listMessages(threadId)
    }

    fun send() {
        val prompt = message.trim()
        if (prompt.isEmpty() || sending) return
        message = ""
        pendingPrompt = prompt
        sending = true
        errorText = null
        scope.launch {
            runCatching {
                orchestrator.handle(
                    turn = AssistantTurn(
                        text = prompt,
                        surface = AssistantInputSurface.PHONE_TEXT,
                        webRequested = if (webSearch) true else null,
                    ),
                    providerType = LocalAgentPrefs.getProviderType(context),
                )
            }.onFailure { error ->
                errorText = error.message ?: "Couldn’t finish that request."
            }
            pendingPrompt = null
            sending = false
            threadId = session.activeThreadId()
            refresh()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Conversations", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    if (!sending) {
                        threadId = orchestrator.startNewConversation()
                        messages = emptyList()
                        errorText = null
                    }
                },
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "New conversation")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty() && pendingPrompt == null) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Your conversations from the glasses will appear here.\nYou can also continue from the phone.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = ADColors.Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 34.dp),
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { chatMessage ->
                ADMessageBubble(chatMessage)
            }

            pendingPrompt?.let { prompt ->
                item(key = "pending-user") {
                    ADPendingUserBubble(prompt)
                }
                item(key = "pending-reply") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Box(
                            modifier = Modifier
                                .background(ADColors.Surface, RoundedCornerShape(18.dp))
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = ADColors.Ink,
                            )
                        }
                    }
                }
            }

            errorText?.let { error ->
                item(key = "error") {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ADColors.Error,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ADColors.Background)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ADColors.Surface, RoundedCornerShape(24.dp))
                    .padding(start = 15.dp, end = 8.dp, top = 12.dp, bottom = 7.dp),
            ) {
                BasicTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp, max = 132.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = ADColors.Ink),
                    cursorBrush = SolidColor(ADColors.Ink),
                    decorationBox = { textField ->
                        Box(contentAlignment = Alignment.TopStart) {
                            if (message.isBlank()) {
                                Text("Message", style = MaterialTheme.typography.bodyLarge, color = ADColors.Muted)
                            }
                            textField()
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onImageQuestion, enabled = !sending, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Outlined.CameraAlt, "Ask what I see", tint = ADColors.Muted, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onVoiceQuestion, enabled = !sending, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Outlined.Mic, "Voice", tint = ADColors.Muted, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { webSearch = !webSearch },
                        enabled = !sending,
                        modifier = Modifier
                            .size(38.dp)
                            .background(if (webSearch) ADColors.BlueSoft else Color.Transparent, CircleShape),
                    ) {
                        Icon(
                            Icons.Outlined.Public,
                            "Web Search",
                            tint = if (webSearch) ADColors.Blue else ADColors.Muted,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { send() },
                        enabled = message.isNotBlank() && !sending,
                        modifier = Modifier.size(40.dp).background(
                            if (message.isNotBlank() && !sending) ADColors.Ink else ADColors.SurfaceSubtle,
                            CircleShape,
                        ),
                    ) {
                        Icon(
                            Icons.Rounded.ArrowUpward,
                            contentDescription = "Send",
                            tint = if (message.isNotBlank() && !sending) Color.White else ADColors.Muted,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ADMessageBubble(message: ChatMessage) {
    val user = message.role == ChatRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (user) 0.82f else 0.94f)
                .background(
                    if (user) ADColors.Ink else Color.Transparent,
                    RoundedCornerShape(20.dp),
                )
                .padding(
                    horizontal = if (user) 15.dp else 2.dp,
                    vertical = if (user) 11.dp else 5.dp,
                ),
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = if (user) Color.White else ADColors.Ink,
            )
        }
    }
}

@Composable
private fun ADPendingUserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .background(ADColors.Ink, RoundedCornerShape(20.dp))
                .padding(horizontal = 15.dp, vertical = 11.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
    }
}

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Native phone continuation for the same durable conversation used by the glasses. */
@Composable
internal fun ADNativeConversationScreen(
    navigationRequest: ADNavigationRequest? = null,
    onNavigationRequestApplied: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
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

    val pendingAlreadyPersisted = pendingPrompt?.let { prompt ->
        messages.asReversed()
            .firstOrNull { it.role == ChatRole.USER }
            ?.content
            ?.trim() == prompt.trim()
    } == true

    fun refresh() {
        messages = ChatStore.listMessages(threadId)
    }

    fun send() {
        val prompt = message.trim()
        if (prompt.isEmpty() || sending) return
        val useWeb = webSearch
        message = ""
        webSearch = false
        pendingPrompt = prompt
        sending = true
        errorText = null
        scope.launch {
            runCatching {
                orchestrator.handle(
                    turn = AssistantTurn(
                        text = prompt,
                        surface = AssistantInputSurface.PHONE_TEXT,
                        webRequested = if (useWeb) true else null,
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

    LaunchedEffect(navigationRequest?.id) {
        val request = navigationRequest ?: return@LaunchedEffect
        request.threadId?.let(session::selectThread)
        val requestedThreadId = session.activeThreadId()
        threadId = requestedThreadId
        messages = ChatStore.listMessages(requestedThreadId)
        request.prefill?.takeIf { it.isNotBlank() }?.let { message = it }
        webSearch = request.webSearchRequested
        errorText = null
        onNavigationRequestApplied(request.id)
    }

    // Glasses-originated turns use the same durable session. Refresh while this surface is
    // visible so the phone stays a live review surface without requiring a reopen.
    LaunchedEffect(threadId) {
        while (isActive) {
            delay(CONVERSATION_REFRESH_MS)
            val activeThreadId = session.activeThreadId()
            if (activeThreadId != threadId) {
                threadId = activeThreadId
                break
            }
            val latest = ChatStore.listMessages(threadId)
            if (latest != messages) messages = latest
        }
    }

    LaunchedEffect(messages.size, pendingPrompt, pendingAlreadyPersisted, errorText) {
        val dynamicCount = messages.size +
            (if (pendingPrompt != null && !pendingAlreadyPersisted) 1 else 0) +
            (if (pendingPrompt != null) 1 else 0) +
            (if (errorText != null) 1 else 0)
        if (dynamicCount > 0) {
            runCatching { listState.animateScrollToItem(dynamicCount - 1) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Chats", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    if (!sending) {
                        threadId = orchestrator.startNewConversation()
                        messages = emptyList()
                        message = ""
                        webSearch = false
                        errorText = null
                    }
                },
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "New chat")
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 18.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            if (messages.isEmpty() && pendingPrompt == null) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 36.dp),
                        ) {
                            Text("Start a chat", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.size(7.dp))
                            Text(
                                "Messages from the glasses and phone stay together here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ADColors.Muted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { chatMessage ->
                ADMessageBubble(chatMessage)
            }

            pendingPrompt?.takeUnless { pendingAlreadyPersisted }?.let { prompt ->
                item(key = "pending-user") {
                    ADPendingUserBubble(prompt)
                }
            }

            if (pendingPrompt != null) {
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ADColors.Error,
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
            if (webSearch) {
                Text(
                    "Web Search",
                    style = MaterialTheme.typography.labelMedium,
                    color = ADColors.Blue,
                    modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ADColors.Surface, RoundedCornerShape(24.dp))
                    .padding(start = 16.dp, end = 7.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp, max = 128.dp).padding(vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = ADColors.Ink),
                    cursorBrush = SolidColor(ADColors.Ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    maxLines = 5,
                    decorationBox = { textField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (message.isBlank()) {
                                Text(
                                    if (webSearch) "Search the web" else "Message",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = ADColors.Muted,
                                )
                            }
                            textField()
                        }
                    },
                )
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
            ADConversationMessageBody(
                content = message.content,
                userMessage = user,
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
            ADConversationMessageBody(content = text, userMessage = true)
        }
    }
}

private const val CONVERSATION_REFRESH_MS = 1_250L

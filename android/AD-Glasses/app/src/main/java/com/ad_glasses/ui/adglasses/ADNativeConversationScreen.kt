package com.ad_glasses.ui.adglasses

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ad_glasses.agent.LocalAgentPrefs
import com.ad_glasses.ai.orchestrator.AndroidAssistantCapabilityExecutor
import com.ad_glasses.ai.orchestrator.AssistantConversationSession
import com.ad_glasses.ai.orchestrator.AssistantInputSurface
import com.ad_glasses.ai.orchestrator.AssistantOrchestrator
import com.ad_glasses.ai.orchestrator.AssistantTurn
import com.ad_glasses.audio.MeetingCapturePrefs
import com.ad_glasses.chat.ChatStore
import com.ad_glasses.localagent.AudioSessionCoordinator
import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatThread
import com.ad_glasses.shared.chat.ChatRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Native phone continuation for the same durable assistant session used by the glasses. */
@Composable
internal fun ADNativeConversationScreen(
    navigationRequest: ADNavigationRequest? = null,
    onNavigationRequestApplied: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val composerFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val session = remember(context) { AssistantConversationSession.get(context) }
    val internalProvider = LocalAgentPrefs.getProviderType(context)
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
    var lastFailedPrompt by remember { mutableStateOf<String?>(null) }
    var showConversationHistory by remember { mutableStateOf(false) }
    var conversations by remember { mutableStateOf(session.conversations()) }
    var renameTarget by remember { mutableStateOf<ChatThread?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ChatThread?>(null) }
    var clearAllRequested by remember { mutableStateOf(false) }
    var aiAudioActive by remember { mutableStateOf(AudioSessionCoordinator.isBusy()) }
    var recordingActive by remember { mutableStateOf(MeetingCapturePrefs.getState(context).isRecording) }

    val pendingAlreadyPersisted = pendingPrompt?.let { prompt ->
        messages.asReversed()
            .firstOrNull { it.role == ChatRole.USER }
            ?.content
            ?.trim() == prompt.trim()
    } == true

    fun refresh() {
        messages = ChatStore.listMessages(threadId)
        conversations = session.conversations()
    }

    fun refreshConversations() {
        conversations = session.conversations()
    }

    fun focusComposer() {
        composerFocusRequester.requestFocus()
        keyboardController?.show()
    }


    fun startNewPrompt() {
        if (sending) return
        val newThreadId = session.startNewConversation()
        threadId = newThreadId
        messages = emptyList()
        message = ""
        webSearch = false
        pendingPrompt = null
        errorText = null
        lastFailedPrompt = null
        showConversationHistory = false
        refreshConversations()
        scope.launch {
            delay(80)
            focusComposer()
        }
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
        lastFailedPrompt = null
        scope.launch {
            runCatching {
                orchestrator.handle(
                    turn = AssistantTurn(
                        text = prompt,
                        surface = AssistantInputSurface.PHONE_TEXT,
                        webRequested = useWeb,
                    ),
                    providerType = internalProvider,
                )
            }.onFailure { error ->
                errorText = error.message ?: "Couldn’t finish that request."
                lastFailedPrompt = prompt
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
        if (!request.prefill.isNullOrBlank() || request.webSearchRequested) {
            delay(90)
            focusComposer()
        }
    }

    // Glasses-originated turns use the same durable session. Refresh while this surface is
    // visible so the phone remains a live review surface without requiring a reopen.
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

    // These indicators are intentionally driven by real runtime state. Do not show a fake
    // listening animation when no audio session or recording is actually active.
    LaunchedEffect(Unit) {
        while (isActive) {
            aiAudioActive = AudioSessionCoordinator.isBusy()
            recordingActive = MeetingCapturePrefs.getState(context).isRecording
            delay(ACTIVITY_REFRESH_MS)
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "AI",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            if (showConversationHistory) {
                TextButton(onClick = { showConversationHistory = false }) {
                    Text("Done")
                }
            } else {
                TextButton(
                    onClick = {
                        refreshConversations()
                        showConversationHistory = true
                    },
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("History")
                }
            }
            TextButton(
                onClick = ::startNewPrompt,
                enabled = !sending,
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text("New")
            }
        }

        if (showConversationHistory) {
            ADConversationHistory(
                conversations = conversations,
                activeThreadId = threadId,
                onOpen = { selected ->
                    if (session.selectThread(selected.id)) {
                        threadId = session.activeThreadId()
                        refresh()
                        showConversationHistory = false
                    }
                },
                onRename = { target ->
                    renameTarget = target
                    renameText = target.title
                },
                onDelete = { target -> deleteTarget = target },
                onClearAll = { clearAllRequested = true },
            )
        } else {
            if (recordingActive || aiAudioActive) {
                ADLiveAudioState(recording = recordingActive)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 8.dp,
                    bottom = 14.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (messages.isEmpty() && pendingPrompt == null) {
                    item(key = "empty") {
                        ADConversationEmptyState()
                    }
                }

                items(messages, key = { it.id }) { chatMessage ->
                    ADConversationTurn(chatMessage)
                }

                pendingPrompt?.takeUnless { pendingAlreadyPersisted }?.let { prompt ->
                    item(key = "pending-user") {
                        ADUserTurn(prompt)
                    }
                }

                if (pendingPrompt != null) {
                    item(key = "pending-reply") {
                        ADAssistantThinking()
                    }
                }

                errorText?.let { error ->
                    item(key = "error") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ADColors.ErrorSoft, RoundedCornerShape(12.dp))
                                .clickable(enabled = lastFailedPrompt != null) {
                                    lastFailedPrompt?.let { failed ->
                                        message = failed
                                        errorText = null
                                        focusComposer()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ADColors.Error,
                            )
                            if (lastFailedPrompt != null) {
                                Text(
                                    "Tap to put the same request back in the composer. AD never switches providers automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ADColors.Error,
                                )
                            }
                        }
                    }
                }
            }

            ADConversationComposer(
                message = message,
                onMessageChange = { message = it },
                webSearch = webSearch,
                sending = sending,
                focusRequester = composerFocusRequester,
                onSend = ::send,
            )
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename conversation") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(80) },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.trim().isNotEmpty(),
                    onClick = {
                        session.renameConversation(target.id, renameText)
                        renameTarget = null
                        refreshConversations()
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete conversation?") },
            text = { Text("This removes the conversation and its messages from AD Glasses.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        threadId = session.deleteConversation(target.id)
                        messages = ChatStore.listMessages(threadId)
                        deleteTarget = null
                        refreshConversations()
                    },
                ) { Text("Delete", color = ADColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (clearAllRequested) {
        AlertDialog(
            onDismissRequest = { clearAllRequested = false },
            title = { Text("Clear AI conversations?") },
            text = { Text("All AD-owned conversation history will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        session.clearAllConversations()
                        threadId = session.startNewConversation()
                        messages = emptyList()
                        clearAllRequested = false
                        refreshConversations()
                        showConversationHistory = false
                    },
                ) { Text("Clear all", color = ADColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { clearAllRequested = false }) { Text("Cancel") }
            },
        )
    }
    }
}

@Composable
private fun ADLiveAudioState(recording: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 1.dp)
            .background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ADActivityWaveform(color = ADColors.Ink, compact = true)
        Column(Modifier.weight(1f)) {
            Text(
                if (recording) "Audio capture active" else "AI audio active",
                style = MaterialTheme.typography.labelLarge,
                color = ADColors.Ink,
            )
            Text(
                if (recording) "Recording is running in the background" else "Voice playback is active",
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
            )
        }
    }
}

@Composable
private fun ADConversationEmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 92.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ADGlassesMark(Modifier.size(38.dp))
            Spacer(Modifier.height(10.dp))
            Text("Start a conversation", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ADConversationHistory(
    conversations: List<ChatThread>,
    activeThreadId: String,
    onOpen: (ChatThread) -> Unit,
    onRename: (ChatThread) -> Unit,
    onDelete: (ChatThread) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (conversations.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No conversations yet", color = ADColors.Muted)
            }
            return@Column
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = 8.dp,
                bottom = 10.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(conversations, key = { it.id }) { conversation ->
                val preview = ChatStore.listMessages(conversation.id)
                    .lastOrNull()
                    ?.content
                    ?.replace("\n", " ")
                    ?.trim()
                    .orEmpty()
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (conversation.id == activeThreadId) ADColors.BlueSoft else ADColors.Surface,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { onOpen(conversation) }
                            .padding(start = 13.dp, top = 11.dp, bottom = 11.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                conversation.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = ADColors.Ink,
                                maxLines = 1,
                            )
                            if (preview.isNotBlank()) {
                                Text(
                                    preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ADColors.Muted,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                formatConversationTime(conversation.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = ADColors.Muted,
                            )
                        }
                        IconButton(onClick = { onRename(conversation) }) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "Rename ${conversation.title}",
                                tint = ADColors.Muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = { onDelete(conversation) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Delete ${conversation.title}",
                                tint = ADColors.Muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
        TextButton(
            onClick = onClearAll,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp),
        ) {
            Text("Clear all conversations", color = ADColors.Error)
        }
    }
}

private fun formatConversationTime(timestamp: Long): String =
    SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun ADConversationTurn(message: ChatMessage) {
    if (message.role == ChatRole.USER) {
        ADUserTurn(message.content)
    } else {
        ADAssistantTurn(message.content)
    }
}

@Composable
private fun ADUserTurn(content: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    ADColors.SurfaceSubtle,
                    RoundedCornerShape(
                        topStart = 17.dp,
                        topEnd = 17.dp,
                        bottomStart = 17.dp,
                        bottomEnd = 6.dp,
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            ADConversationMessageBody(content = content, userMessage = true)
        }
    }
}

@Composable
private fun ADAssistantTurn(content: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier.size(26.dp).background(ADColors.Surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ADGlassesMark(Modifier.size(18.dp))
        }
        Box(modifier = Modifier.weight(1f).padding(top = 1.dp, end = 3.dp)) {
            ADConversationMessageBody(content = content, userMessage = false)
        }
    }
}

@Composable
private fun ADAssistantThinking() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier.size(26.dp).background(ADColors.Surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ADGlassesMark(Modifier.size(18.dp))
        }
        ADActivityWaveform(color = ADColors.Ink, compact = true)
        Text("AI is working…", style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
    }
}

@Composable
private fun ADActivityWaveform(
    color: Color,
    compact: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "prompt-audio-wave")
    val first by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(560, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wave-1",
    )
    val second by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(640, delayMillis = 110, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wave-2",
    )
    val third by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, delayMillis = 190, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wave-3",
    )
    val fourth by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(610, delayMillis = 70, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wave-4",
    )
    val maxHeight = if (compact) 14f else 19f
    Row(
        modifier = Modifier.height(maxHeight.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
    ) {
        listOf(first, second, third, fourth).forEach { level ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((4f + (maxHeight - 4f) * level).dp)
                    .background(color, CircleShape),
            )
        }
    }
}

@Composable
private fun ADConversationComposer(
    message: String,
    onMessageChange: (String) -> Unit,
    webSearch: Boolean,
    sending: Boolean,
    focusRequester: FocusRequester,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ADColors.Background)
            .padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 6.dp),
    ) {
        if (webSearch) {
            Row(
                modifier = Modifier.padding(start = 8.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Outlined.Public, null, tint = ADColors.Ink, modifier = Modifier.size(14.dp))
                Text("Web search", style = MaterialTheme.typography.labelMedium, color = ADColors.Ink)
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ADColors.Surface,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 13.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp, max = 112.dp)
                        .focusRequester(focusRequester)
                        .padding(vertical = 6.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = ADColors.Ink),
                    cursorBrush = SolidColor(ADColors.Ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    maxLines = 5,
                    decorationBox = { textField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (message.isBlank()) {
                                Text(
                                    if (webSearch) "Search the web" else "Ask AD…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ADColors.Muted,
                                )
                            }
                            textField()
                        }
                    },
                )
                val sendEnabled = message.isNotBlank() && !sending
                IconButton(
                    onClick = onSend,
                    enabled = sendEnabled,
                    modifier = Modifier.size(38.dp).background(
                        if (sendEnabled) ADColors.BlueDeep else ADColors.SurfaceSubtle,
                        CircleShape,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.ArrowUpward,
                        contentDescription = "Send prompt",
                        tint = if (sendEnabled) Color.White else ADColors.Muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private const val CONVERSATION_REFRESH_MS = 1_250L
private const val ACTIVITY_REFRESH_MS = 350L

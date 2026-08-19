package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.ai.orchestrator.AndroidAssistantCapabilityExecutor
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantConversationSession
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantInputSurface
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantOrchestrator
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantTurn
import com.fersaiyan.cyanbridge.audio.MeetingCapturePrefs
import com.fersaiyan.cyanbridge.chat.ChatStore
import com.fersaiyan.cyanbridge.localagent.AudioSessionCoordinator
import com.fersaiyan.cyanbridge.shared.chat.ChatMessage
import com.fersaiyan.cyanbridge.shared.chat.ChatRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    }

    fun focusComposer() {
        composerFocusRequester.requestFocus()
        keyboardController?.show()
    }

    fun useSuggestion(prompt: String, requestWeb: Boolean = false) {
        message = prompt
        webSearch = requestWeb
        focusComposer()
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
        if (!request.prefill.isNullOrBlank() || request.webSearchRequested) {
            delay(90)
            focusComposer()
        }
    }

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
                .heightIn(min = 54.dp)
                .padding(start = 14.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Prompt", style = MaterialTheme.typography.titleLarge)
                Text(
                    "One conversation across phone and glasses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (messages.isNotEmpty() || pendingPrompt != null) {
                Surface(
                    onClick = ::startNewPrompt,
                    enabled = !sending,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("New", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (recordingActive || aiAudioActive) {
            ADLiveAudioState(recording = recordingActive)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = 6.dp,
                bottom = 14.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (messages.isEmpty() && pendingPrompt == null) {
                item(key = "empty") {
                    ADConversationEmptyState(onSuggestion = ::useSuggestion)
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
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = ADColors.ErrorSoft,
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ADColors.Error,
                        )
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

@Composable
private fun ADLiveAudioState(recording: Boolean) {
    Surface(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp).fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(13.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ADActivityWaveform(color = MaterialTheme.colorScheme.secondary, compact = true)
            Text(
                if (recording) "Recording active" else "AI audio active",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ADConversationEmptyState(
    onSuggestion: (String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(19.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ADGlassesMark(Modifier.size(width = 29.dp, height = 17.dp))
                    }
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text("Ask anything", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Type a question or start with a suggestion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    )
                }
            }
        }

        ADPromptSuggestion(
            text = "What did I capture today?",
            icon = Icons.Outlined.AutoAwesome,
            container = MaterialTheme.colorScheme.surface,
        ) { onSuggestion("What did I capture today?", false) }
        ADPromptSuggestion(
            text = "Search the web for something current",
            icon = Icons.Outlined.Public,
            container = MaterialTheme.colorScheme.secondaryContainer,
        ) { onSuggestion("Search the web for ", true) }
        ADPromptSuggestion(
            text = "Help me plan something",
            icon = Icons.Outlined.Lightbulb,
            container = MaterialTheme.colorScheme.tertiaryContainer,
        ) { onSuggestion("Help me plan ", false) }
    }
}

@Composable
private fun ADPromptSuggestion(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        }
    }
}

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
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth * 0.84f)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(
                        topStart = 17.dp,
                        topEnd = 17.dp,
                        bottomStart = 17.dp,
                        bottomEnd = 6.dp,
                    ),
                )
                .padding(horizontal = 13.dp, vertical = 9.dp),
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.size(25.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                ADGlassesMark(Modifier.size(width = 18.dp, height = 11.dp))
            }
        }
        Box(modifier = Modifier.weight(1f).padding(top = 1.dp, end = 2.dp)) {
            ADConversationMessageBody(content = content, userMessage = false)
        }
    }
}

@Composable
private fun ADAssistantThinking() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.size(25.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                ADGlassesMark(Modifier.size(width = 18.dp, height = 11.dp))
            }
        }
        ADActivityWaveform(color = MaterialTheme.colorScheme.primary, compact = true)
        Text("Working…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 7.dp),
    ) {
        if (webSearch) {
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = 6.dp, bottom = 5.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Outlined.Public, null, modifier = Modifier.size(13.dp))
                    Text("Web search", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        .heightIn(min = 36.dp, max = 110.dp)
                        .focusRequester(focusRequester)
                        .padding(vertical = 7.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    maxLines = 5,
                    decorationBox = { textField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (message.isBlank()) {
                                Text(
                                    if (webSearch) "Search the web" else "Ask AI…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    modifier = Modifier.size(36.dp).background(
                        if (sendEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.ArrowUpward,
                        contentDescription = "Send prompt",
                        tint = if (sendEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private const val CONVERSATION_REFRESH_MS = 1_250L
private const val ACTIVITY_REFRESH_MS = 350L

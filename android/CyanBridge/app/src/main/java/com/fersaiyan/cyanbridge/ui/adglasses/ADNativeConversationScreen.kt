package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PhotoLibrary
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
        messages.asReversed().firstOrNull { it.role == ChatRole.USER }?.content?.trim() == prompt.trim()
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
        if (dynamicCount > 0) runCatching { listState.animateScrollToItem(dynamicCount - 1) }
    }

    Column(Modifier.fillMaxSize()) {
        ADPromptHeader(
            hasConversation = messages.isNotEmpty() || pendingPrompt != null,
            sending = sending,
            onNew = ::startNewPrompt,
        )

        if (recordingActive || aiAudioActive) {
            ADLiveAudioState(recording = recordingActive)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 6.dp,
                bottom = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            if (messages.isEmpty() && pendingPrompt == null) {
                item(key = "empty") { ADConversationEmptyState(onSuggestion = ::useSuggestion) }
            }

            items(messages, key = { it.id }) { chatMessage -> ADConversationTurn(chatMessage) }

            pendingPrompt?.takeUnless { pendingAlreadyPersisted }?.let { prompt ->
                item(key = "pending-user") { ADUserTurn(prompt) }
            }

            if (pendingPrompt != null) {
                item(key = "pending-reply") { ADAssistantThinking() }
            }

            errorText?.let { error ->
                item(key = "error") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = ADColors.ErrorSoft,
                        shape = RoundedCornerShape(11.dp),
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ADColors.Error,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
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
private fun ADPromptHeader(
    hasConversation: Boolean,
    sending: Boolean,
    onNew: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(ADColors.Surface, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = ADColors.Ink,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text("PROMPT", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
            Text("Conversation", style = MaterialTheme.typography.titleLarge)
        }
        if (hasConversation) {
            Surface(
                onClick = onNew,
                enabled = !sending,
                shape = RoundedCornerShape(10.dp),
                color = ADColors.Surface,
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Rounded.Add, null, tint = if (sending) ADColors.Muted else ADColors.Ink, modifier = Modifier.size(14.dp))
                    Text("New", style = MaterialTheme.typography.labelMedium, color = if (sending) ADColors.Muted else ADColors.Ink)
                }
            }
        }
    }
}

@Composable
private fun ADLiveAudioState(recording: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(11.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(7.dp).background(if (recording) ADColors.Red else ADColors.Ink, CircleShape))
            ADActivityWaveform(color = if (recording) ADColors.Red else ADColors.Ink, compact = true)
            Column(Modifier.weight(1f)) {
                Text(if (recording) "Audio capture active" else "AI audio active", style = MaterialTheme.typography.labelLarge)
                Text(if (recording) "Recording in background" else "Voice playback active", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
            }
        }
    }
}

@Composable
private fun ADConversationEmptyState(onSuggestion: (String, Boolean) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(ADColors.Surface, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = ADColors.Red,
                modifier = Modifier.size(27.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Text("What do you want to know?", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.size(5.dp))
        Text(
            "Ask anything, use the web when freshness matters, or continue from your glasses.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ADPromptSuggestion(
                text = "What did I capture today?",
                icon = Icons.Outlined.PhotoLibrary,
            ) { onSuggestion("What did I capture today?", false) }
            ADPromptSuggestion(
                text = "Search the web for something current",
                icon = Icons.Outlined.Public,
            ) { onSuggestion("Search the web for ", true) }
            ADPromptSuggestion(
                text = "Help me plan something",
                icon = Icons.Outlined.Lightbulb,
            ) { onSuggestion("Help me plan ", false) }
        }
    }
}

@Composable
private fun ADPromptSuggestion(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        color = ADColors.Surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = ADColors.Ink, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 9.dp).weight(1f))
            Icon(Icons.Outlined.ArrowForward, null, tint = ADColors.Muted, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun ADConversationTurn(message: ChatMessage) {
    if (message.role == ChatRole.USER) ADUserTurn(message.content) else ADAssistantTurn(message.content)
}

@Composable
private fun ADUserTurn(content: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.widthIn(max = 306.dp),
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 4.dp),
            color = ADColors.Ink,
            contentColor = Color.Black,
        ) {
            Box(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
                ADConversationMessageBody(content = content, userMessage = true)
            }
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
            modifier = Modifier.size(28.dp).background(ADColors.Surface, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ADGlassesMark(Modifier.size(20.dp))
        }
        Box(modifier = Modifier.weight(1f).padding(top = 2.dp, end = 2.dp)) {
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
            modifier = Modifier.size(28.dp).background(ADColors.Surface, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ADGlassesMark(Modifier.size(20.dp))
        }
        ADActivityWaveform(color = ADColors.Ink, compact = true)
        Text("AI is working…", style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
    }
}

@Composable
private fun ADActivityWaveform(color: Color, compact: Boolean) {
    val transition = rememberInfiniteTransition(label = "prompt-audio-wave")
    val first by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(560, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "wave-1",
    )
    val second by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(animation = tween(640, delayMillis = 110, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "wave-2",
    )
    val third by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(animation = tween(520, delayMillis = 190, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "wave-3",
    )
    val fourth by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(animation = tween(610, delayMillis = 70, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "wave-4",
    )
    val maxHeight = if (compact) 12f else 16f
    Row(
        modifier = Modifier.height(maxHeight.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(first, second, third, fourth).forEach { level ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((3f + (maxHeight - 3f) * level).dp)
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
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 7.dp),
    ) {
        if (webSearch) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ADColors.SurfaceSubtle,
                modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.Public, null, tint = ADColors.Ink, modifier = Modifier.size(12.dp))
                    Text("Web search", style = MaterialTheme.typography.labelMedium, color = ADColors.Ink)
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ADColors.Surface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp, max = 104.dp)
                        .focusRequester(focusRequester)
                        .padding(vertical = 7.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = ADColors.Ink),
                    cursorBrush = SolidColor(ADColors.Red),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    maxLines = 5,
                    decorationBox = { textField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (message.isBlank()) {
                                Text(
                                    if (webSearch) "Search the web" else "Ask anything…",
                                    style = MaterialTheme.typography.bodyLarge,
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
                    modifier = Modifier.size(36.dp).background(
                        if (sendEnabled) ADColors.Red else ADColors.SurfaceSubtle,
                        CircleShape,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.ArrowUpward,
                        contentDescription = "Send prompt",
                        tint = if (sendEnabled) Color.White else ADColors.Muted,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}

private const val CONVERSATION_REFRESH_MS = 1_250L
private const val ACTIVITY_REFRESH_MS = 350L

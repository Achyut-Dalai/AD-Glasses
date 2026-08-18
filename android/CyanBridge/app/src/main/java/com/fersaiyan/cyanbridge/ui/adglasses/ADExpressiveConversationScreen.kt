package com.fersaiyan.cyanbridge.ui.adglasses

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
import androidx.compose.material.icons.outlined.Terminal
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.ai.orchestrator.AndroidAssistantCapabilityExecutor
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantConversationSession
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantInputSurface
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantOrchestrator
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantTurn
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantWebMode
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantWebModePreferences
import com.fersaiyan.cyanbridge.audio.MeetingCapturePrefs
import com.fersaiyan.cyanbridge.chat.ChatStore
import com.fersaiyan.cyanbridge.localagent.AudioSessionCoordinator
import com.fersaiyan.cyanbridge.shared.chat.ChatMessage
import com.fersaiyan.cyanbridge.shared.chat.ChatRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Phone continuation of the same durable assistant used by the glasses. */
@Composable
internal fun ADExpressiveConversationScreen(
    navigationRequest: ADNavigationRequest? = null,
    onNavigationRequestApplied: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
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
    var forceWebForTurn by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var pendingPrompt by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var aiAudioActive by remember { mutableStateOf(AudioSessionCoordinator.isBusy()) }
    var recordingActive by remember { mutableStateOf(MeetingCapturePrefs.getState(context).isRecording) }
    var globalWebMode by remember(context) { mutableStateOf(AssistantWebModePreferences.get(context)) }

    val pendingAlreadyPersisted = pendingPrompt?.let { prompt ->
        messages.asReversed().firstOrNull { it.role == ChatRole.USER }?.content?.trim() == prompt.trim()
    } == true

    fun refresh() {
        messages = ChatStore.listMessages(threadId)
        globalWebMode = AssistantWebModePreferences.get(context)
    }

    fun focusComposer() {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    fun startNewPrompt() {
        if (sending) return
        threadId = session.startNewConversation()
        messages = emptyList()
        message = ""
        forceWebForTurn = false
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
        val useWeb = forceWebForTurn
        message = ""
        forceWebForTurn = false
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
        threadId = session.activeThreadId()
        messages = ChatStore.listMessages(threadId)
        request.prefill?.takeIf { it.isNotBlank() }?.let { message = it }
        forceWebForTurn = request.webSearchRequested
        errorText = null
        onNavigationRequestApplied(request.id)
        if (!request.prefill.isNullOrBlank() || request.webSearchRequested) {
            delay(90)
            focusComposer()
        }
    }

    LaunchedEffect(threadId) {
        while (isActive) {
            delay(EXPRESSIVE_CONVERSATION_REFRESH_MS)
            val activeThread = session.activeThreadId()
            if (activeThread != threadId) {
                threadId = activeThread
                break
            }
            val latest = ChatStore.listMessages(threadId)
            if (latest != messages) messages = latest
            globalWebMode = AssistantWebModePreferences.get(context)
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            aiAudioActive = AudioSessionCoordinator.isBusy()
            recordingActive = MeetingCapturePrefs.getState(context).isRecording
            delay(EXPRESSIVE_ACTIVITY_REFRESH_MS)
        }
    }

    LaunchedEffect(messages.size, pendingPrompt, errorText) {
        val dynamicCount = messages.size +
            (if (pendingPrompt != null && !pendingAlreadyPersisted) 1 else 0) +
            (if (pendingPrompt != null) 1 else 0) +
            (if (errorText != null) 1 else 0)
        if (dynamicCount > 0) runCatching { listState.animateScrollToItem(dynamicCount - 1) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Prompt", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text("Same assistant as your glasses", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                    if (globalWebMode == AssistantWebMode.ON) {
                        ADStatusChip("WEB ON", ADStatusTone.INFO)
                    }
                }
            }
            if (messages.isNotEmpty() || pendingPrompt != null) {
                Surface(
                    onClick = ::startNewPrompt,
                    enabled = !sending,
                    shape = RoundedCornerShape(16.dp),
                    color = ADColors.Surface,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("New", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (recordingActive || aiAudioActive) {
            ADExpressiveLiveAudioState(recording = recordingActive)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 10.dp, 16.dp, 22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (messages.isEmpty() && pendingPrompt == null) {
                item(key = "empty") {
                    ADExpressiveConversationEmptyState(globalWebMode)
                }
            }

            items(messages, key = { it.id }) { chatMessage ->
                ADExpressiveConversationTurn(chatMessage)
            }

            pendingPrompt?.takeUnless { pendingAlreadyPersisted }?.let { prompt ->
                item(key = "pending-user") { ADExpressiveUserTurn(prompt) }
            }

            if (pendingPrompt != null) {
                item(key = "pending-reply") { ADExpressiveAssistantThinking() }
            }

            errorText?.let { error ->
                item(key = "error") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = ADColors.ErrorSoft,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(
                            error,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ADColors.Error,
                        )
                    }
                }
            }
        }

        ADExpressiveConversationComposer(
            message = message,
            onMessageChange = { message = it },
            forceWebForTurn = forceWebForTurn,
            onToggleWeb = { forceWebForTurn = !forceWebForTurn },
            sending = sending,
            focusRequester = focusRequester,
            onSend = ::send,
        )
    }
}

@Composable
private fun ADExpressiveConversationEmptyState(webMode: AssistantWebMode) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 46.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(66.dp),
            shape = RoundedCornerShape(22.dp),
            color = ADColors.Ink,
            contentColor = Color.White,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Continue the conversation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        Text(
            "Type here when the phone is the better surface. Voice and vision still belong on the glasses; this page keeps the full answer and context.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 26.dp),
        )
        Spacer(Modifier.height(18.dp))
        ADStatusChip(
            if (webMode == AssistantWebMode.ON) "WEB PREFERRED" else "WEB AUTO",
            ADStatusTone.NEUTRAL,
        )
    }
}

@Composable
private fun ADExpressiveLiveAudioState(recording: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(18.dp),
        color = ADColors.Surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ADExpressiveWaveform(color = ADColors.Ink)
            Column(Modifier.weight(1f)) {
                Text(
                    if (recording) "Audio capture is active" else "AI audio is active",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    if (recording) "Recording continues in the background" else "Voice playback is using the audio route",
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun ADExpressiveConversationTurn(message: ChatMessage) {
    if (message.role == ChatRole.USER) ADExpressiveUserTurn(message.content)
    else ADExpressiveAssistantTurn(message.content)
}

@Composable
private fun ADExpressiveUserTurn(content: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.widthIn(max = 336.dp),
            color = ADColors.SurfaceSubtle,
            shape = RoundedCornerShape(22.dp, 22.dp, 7.dp, 22.dp),
        ) {
            Box(Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
                ADConversationMessageBody(content = content, userMessage = true)
            }
        }
    }
}

@Composable
private fun ADExpressiveAssistantTurn(content: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = CircleShape,
            color = ADColors.Surface,
        ) {
            Box(contentAlignment = Alignment.Center) { ADGlassesMark(Modifier.size(20.dp)) }
        }
        Box(modifier = Modifier.weight(1f).padding(top = 3.dp, end = 4.dp)) {
            ADConversationMessageBody(content = content, userMessage = false)
        }
    }
}

@Composable
private fun ADExpressiveAssistantThinking() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(modifier = Modifier.size(30.dp), shape = CircleShape, color = ADColors.Surface) {
            Box(contentAlignment = Alignment.Center) { ADGlassesMark(Modifier.size(20.dp)) }
        }
        ADExpressiveWaveform(color = ADColors.Ink)
        Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
    }
}

@Composable
private fun ADExpressiveWaveform(color: Color) {
    val transition = rememberInfiniteTransition(label = "expressive-prompt-wave")
    val levels = listOf(
        transition.animateFloat(
            0.22f,
            1f,
            infiniteRepeatable(tween(560, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "wave-1",
        ).value,
        transition.animateFloat(
            0.3f,
            0.82f,
            infiniteRepeatable(tween(640, delayMillis = 110, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "wave-2",
        ).value,
        transition.animateFloat(
            0.18f,
            0.94f,
            infiniteRepeatable(tween(520, delayMillis = 190, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "wave-3",
        ).value,
        transition.animateFloat(
            0.28f,
            0.72f,
            infiniteRepeatable(tween(610, delayMillis = 70, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "wave-4",
        ).value,
    )
    Row(
        modifier = Modifier.height(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
    ) {
        levels.forEach { level ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((4f + 11f * level).dp)
                    .background(color, CircleShape),
            )
        }
    }
}

@Composable
private fun ADExpressiveConversationComposer(
    message: String,
    onMessageChange: (String) -> Unit,
    forceWebForTurn: Boolean,
    onToggleWeb: () -> Unit,
    sending: Boolean,
    focusRequester: FocusRequester,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ADColors.Background)
            .padding(start = 12.dp, end = 12.dp, top = 7.dp, bottom = 10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ADColors.Surface,
            shape = RoundedCornerShape(26.dp),
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(
                    onClick = onToggleWeb,
                    modifier = Modifier.size(40.dp).background(
                        if (forceWebForTurn) ADColors.Ink else ADColors.SurfaceSubtle,
                        CircleShape,
                    ),
                ) {
                    Icon(
                        Icons.Outlined.Public,
                        contentDescription = if (forceWebForTurn) "Web on for this prompt" else "Use web for this prompt",
                        tint = if (forceWebForTurn) Color.White else ADColors.Muted,
                        modifier = Modifier.size(19.dp),
                    )
                }
                BasicTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 128.dp)
                        .focusRequester(focusRequester)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = ADColors.Ink),
                    cursorBrush = SolidColor(ADColors.Ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    maxLines = 5,
                    decorationBox = { textField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (message.isBlank()) {
                                Text(
                                    if (forceWebForTurn) "Ask with web…" else "Ask AI…",
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
                    modifier = Modifier.size(40.dp).background(
                        if (sendEnabled) ADColors.Ink else ADColors.SurfaceSubtle,
                        CircleShape,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.ArrowUpward,
                        contentDescription = "Send prompt",
                        tint = if (sendEnabled) Color.White else ADColors.Muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private const val EXPRESSIVE_CONVERSATION_REFRESH_MS = 1_250L
private const val EXPRESSIVE_ACTIVITY_REFRESH_MS = 350L

package com.adglasses.app.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adglasses.app.AppGraph
import com.adglasses.app.core.model.ChatMessage
import com.adglasses.app.core.model.MessageRole
import com.adglasses.app.core.speech.GlassesSpeechStatus
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    padding: PaddingValues,
    vm: ADViewModel,
    openSettings: () -> Unit,
) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val assistantWorking by vm.assistantWorking.collectAsStateWithLifecycle()
    val glassesSpeech by vm.glassesSpeechStatus.collectAsStateWithLifecycle()
    val speaking by AppGraph.tts.speaking.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) vm.sendPhoneVoiceMessage(text)
        }
    }
    val startSystemVoice = {
        speechLauncher.launch(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask AD")
            },
        )
    }

    LaunchedEffect(messages.size, assistantWorking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex + if (assistantWorking) 1 else 0)
        }
    }

    Scaffold(
        modifier = Modifier.padding(padding),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Filled.History, contentDescription = "Conversation history")
                    }
                },
                actions = {
                    IconButton(onClick = vm::newConversation) {
                        Icon(Icons.Outlined.Edit, contentDescription = "New conversation")
                    }
                    IconButton(onClick = openSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            ADFloatingComposer(
                draft = draft,
                onDraft = { draft = it },
                send = {
                    if (draft.isNotBlank()) {
                        vm.sendMessage(draft)
                        draft = ""
                    }
                },
                voice = startSystemVoice,
                working = assistantWorking,
                speaking = speaking,
                glassesSpeech = glassesSpeech,
            )
        },
    ) { inner ->
        if (messages.isEmpty()) {
            AssistantWelcome(
                modifier = Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp),
                listening = glassesSpeech is GlassesSpeechStatus.Listening,
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item { ConversationHeader() }
                items(messages, key = { it.id }) { message -> ConversationBubble(message) }
                if (assistantWorking) item(key = "thinking") { AssistantThinking() }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun AssistantWelcome(modifier: Modifier, listening: Boolean) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
        ) {
            Box(
                Modifier
                    .background(
                        Brush.linearGradient(listOf(ADAccent.Indigo, ADAccent.Blue)),
                    )
                    .padding(20.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(190.dp)
                        .background(Color.White.copy(alpha = 0.11f), CircleShape),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(130.dp)
                        .background(ADAccent.Cyan.copy(alpha = 0.18f), CircleShape),
                )
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    AssistantAvatar(54.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            "Ask",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            "Talk it through, ask a question, or pick up where your glasses left off.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.82f),
                        )
                    }
                }
            }
        }
        AssistantSignalVisual(listening = listening)
    }
}

@Composable
private fun AssistantSignalVisual(listening: Boolean) {
    val transition = rememberInfiniteTransition(label = "assistant-signal")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (listening) 520 else 1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "signal-pulse",
    )
    val baseHeights = listOf(14f, 22f, 31f, 40f, 31f, 22f, 14f)

    Box(
        modifier = Modifier.fillMaxWidth().height(238.dp),
        contentAlignment = Alignment.Center,
    ) {
        listOf(210.dp, 164.dp, 118.dp).forEachIndexed { index, size ->
            Surface(
                modifier = Modifier.size(size),
                shape = CircleShape,
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    ADAccent.Indigo.copy(
                        alpha = (if (listening) 0.18f else 0.09f) + (pulse * 0.06f) - (index * 0.02f),
                    ),
                ),
            ) {}
        }
        Box(
            Modifier
                .size(118.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            ADAccent.Cyan.copy(alpha = if (listening) 0.22f else 0.12f),
                            ADAccent.Indigo.copy(alpha = if (listening) 0.20f else 0.10f),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )
        Row(
            modifier = Modifier.height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            baseHeights.forEachIndexed { index, resting ->
                val phase = if ((index % 2) == 0) pulse else 1f - pulse
                val height = if (listening) 9f + (phase * 34f) else resting - 4f + (phase * 9f)
                Box(
                    Modifier
                        .size(width = 5.dp, height = height.dp)
                        .background(
                            Brush.verticalGradient(listOf(ADAccent.Indigo, ADAccent.Cyan)),
                            RoundedCornerShape(100.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun AssistantAvatar(size: androidx.compose.ui.unit.Dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(0.75.dp, Color.White.copy(alpha = 0.22f)),
    ) {
        Box(
            modifier = Modifier.background(Brush.linearGradient(listOf(ADAccent.Indigo, ADAccent.Blue))),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().padding(2.dp).background(Color.White.copy(alpha = 0.07f), CircleShape))
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.38f),
            )
        }
    }
}

@Composable
private fun ConversationHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AssistantAvatar(34.dp)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("AD", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Current conversation",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConversationBubble(message: ChatMessage) {
    val assistant = message.role == MessageRole.Assistant
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (!assistant) Spacer(Modifier.weight(1f)) else AssistantAvatar(28.dp)
        Surface(
            modifier = Modifier.widthIn(max = 560.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (assistant) 5.dp else 18.dp,
                bottomEnd = if (assistant) 18.dp else 5.dp,
            ),
            color = if (assistant) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
        ) {
            Box(
                modifier = if (assistant) {
                    Modifier.padding(horizontal = 15.dp, vertical = 11.dp)
                } else {
                    Modifier
                        .background(Brush.linearGradient(listOf(ADAccent.Blue, ADAccent.Indigo)))
                        .padding(horizontal = 15.dp, vertical = 11.dp)
                },
            ) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (assistant) MaterialTheme.colorScheme.onSurface else Color.White,
                )
            }
        }
        if (assistant) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun AssistantThinking() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        AssistantAvatar(28.dp)
        CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 1.8.dp)
        Text(
            "Thinking",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ADFloatingComposer(
    draft: String,
    onDraft: (String) -> Unit,
    send: () -> Unit,
    voice: () -> Unit,
    working: Boolean,
    speaking: Boolean,
    glassesSpeech: GlassesSpeechStatus,
) {
    Column(
        modifier = Modifier.fillMaxWidth().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ADGlassSurface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(horizontal = 10.dp, vertical = 7.dp),
            cornerRadius = 24.dp,
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ComposerStatus(working, speaking, glassesSpeech)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = {}, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = "Add attachment")
                    }
                    TextField(
                        value = draft,
                        onValueChange = onDraft,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message AD") },
                        minLines = 1,
                        maxLines = 5,
                        shape = RoundedCornerShape(18.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                    when {
                        working || speaking -> IconButton(onClick = { AppGraph.tts.stop() }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Filled.Stop, contentDescription = "Stop response", tint = ADAccent.Red)
                        }
                        draft.isBlank() -> IconButton(onClick = voice, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Filled.Mic, contentDescription = "Start voice input")
                        }
                        else -> Surface(
                            onClick = send,
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = Color.Transparent,
                        ) {
                            Box(
                                Modifier.background(Brush.linearGradient(listOf(ADAccent.Blue, ADAccent.Indigo))),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Send message", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerStatus(
    working: Boolean,
    speaking: Boolean,
    glassesSpeech: GlassesSpeechStatus,
) {
    val text = when {
        glassesSpeech is GlassesSpeechStatus.Listening -> "Listening from glasses"
        glassesSpeech is GlassesSpeechStatus.Transcribing -> if (glassesSpeech.local) {
            "Transcribing glasses mic on device"
        } else {
            "Preparing glasses voice transcript"
        }
        glassesSpeech is GlassesSpeechStatus.Failed -> glassesSpeech.reason
        speaking -> "Speaking…"
        working -> "Thinking…"
        else -> null
    }
    if (text != null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (working || glassesSpeech is GlassesSpeechStatus.Transcribing) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.6.dp)
            }
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

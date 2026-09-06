package com.adglasses.app.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.adglasses.app.core.model.ChatMessage
import com.adglasses.app.core.model.MessageRole
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(padding: PaddingValues, vm: ADViewModel) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val assistantWorking by vm.assistantWorking.collectAsStateWithLifecycle()
    val aiConfiguration by vm.aiConfiguration.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var showAISettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) vm.sendPhoneVoiceMessage(text)
        }
    }

    LaunchedEffect(messages.size, assistantWorking) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex + if (assistantWorking) 1 else 0)
    }

    Scaffold(
        modifier = Modifier.padding(padding),
        topBar = {
            TopAppBar(
                title = { Text(if (messages.isEmpty()) "" else "AD") },
                actions = {
                    IconButton(onClick = { showAISettings = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Cloud AI settings")
                    }
                    IconButton(onClick = vm::newConversation) {
                        Icon(Icons.Outlined.Edit, contentDescription = "New conversation")
                    }
                },
            )
        },
        bottomBar = {
            AssistantComposer(
                draft = draft,
                onDraft = { draft = it },
                send = {
                    vm.sendMessage(draft)
                    draft = ""
                },
                voice = {
                    speechLauncher.launch(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask AD")
                        }
                    )
                },
            )
        },
    ) { inner ->
        if (messages.isEmpty()) {
            AssistantWelcome(
                modifier = Modifier.padding(inner),
                providerLabel = aiConfiguration.activeProfile?.let { "${it.provider.displayName} • ${it.model}" },
                configure = { showAISettings = true },
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Current conversation", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            aiConfiguration.activeProfile?.let { "${it.provider.displayName} • ${it.model}" }
                                ?: "Configure Cloud AI from the settings button",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(Modifier.padding(top = 14.dp))
                }
                items(messages, key = { it.id }) { message -> TranscriptMessage(message) }
                if (assistantWorking) item(key = "assistant-working") { AssistantThinking() }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    if (showAISettings) AISettingsDialog(vm = vm, dismiss = { showAISettings = false })
}

@Composable
private fun AssistantWelcome(
    modifier: Modifier = Modifier,
    providerLabel: String?,
    configure: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.background(Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF2563EB)))).padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = .17f)) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.padding(14.dp).size(28.dp))
                    }
                    Spacer(Modifier.height(62.dp))
                    Text("Ask", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Talk it through, ask a question, or pick up where your glasses left off.", color = Color.White.copy(alpha = .82f))
                    Text(
                        providerLabel ?: "Cloud AI not configured • tap to choose a provider",
                        color = Color.White.copy(alpha = .72f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        if (providerLabel == null) {
            Surface(
                onClick = configure,
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Configure Cloud AI", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.SemiBold)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .09f), modifier = Modifier.size(180.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                }
            }
        }
    }
}

@Composable
private fun AssistantThinking() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(8.dp).size(17.dp))
        }
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text("AD is thinking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TranscriptMessage(message: ChatMessage) {
    if (message.role == MessageRole.Assistant) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(8.dp).size(17.dp))
            }
            Column(Modifier.weight(1f).widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("AD", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(message.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Text("You", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp, bottom = 5.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = 560.dp),
            ) {
                Text(message.text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun AssistantComposer(draft: String, onDraft: (String) -> Unit, send: () -> Unit, voice: () -> Unit) {
    Surface(tonalElevation = 5.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = {}) { Icon(Icons.Filled.Add, contentDescription = "Add attachment") }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraft,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message AD") },
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
            )
            if (draft.isBlank()) {
                IconButton(onClick = voice) { Icon(Icons.Filled.Mic, contentDescription = "Voice input") }
            } else {
                FilledIconButton(onClick = send) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Send") }
            }
        }
    }
}

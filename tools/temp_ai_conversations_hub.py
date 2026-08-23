from pathlib import Path
import re

ROOT = Path('android/AD-Glasses/app/src/main/java/com/ad_glasses')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, found {count}')
    return text.replace(old, new, 1)

# Rename bottom tab from Chats to AI.
models = ROOT / 'ui/adglasses/ADGlassesModels.kt'
text = models.read_text()
text = replace_once(text, '    CHATS("Chats"),', '    AI("AI"),', 'ADTab Chats -> AI')
models.write_text(text)

# Replace old Prompt/Terminal tab glyph with an AI glyph.
components = ROOT / 'ui/adglasses/ADComponents.kt'
text = components.read_text()
text = replace_once(
    text,
    'import androidx.compose.material.icons.outlined.Terminal\n',
    'import androidx.compose.material.icons.rounded.AutoAwesome\n',
    'Terminal import',
)
text = replace_once(
    text,
    '                    ADTab.CHATS -> Icons.Outlined.Terminal',
    '                    ADTab.AI -> Icons.Rounded.AutoAwesome',
    'bottom nav AI icon',
)
components.write_text(text)

# Route conversations to the renamed AI tab.
app = ROOT / 'ui/adglasses/ADGlassesApp.kt'
text = app.read_text()
count = text.count('ADTab.CHATS')
if count != 2:
    raise SystemExit(f'ADGlassesApp: expected 2 ADTab.CHATS refs, found {count}')
text = text.replace('ADTab.CHATS', 'ADTab.AI')
app.write_text(text)

# Expose safe AD-owned conversation management on the session layer.
session = ROOT / 'ai/orchestrator/AssistantConversationSession.kt'
text = session.read_text()
text = replace_once(
    text,
    'import com.ad_glasses.shared.chat.ChatMessage\n',
    'import com.ad_glasses.shared.chat.ChatMessage\nimport com.ad_glasses.shared.chat.ChatThread\n',
    'ChatThread import',
)
anchor = '''    @Synchronized
    fun startNewConversation(): String {
        pruneExpiredConversations()
        return createAndActivate()
    }
'''
insert = anchor + '''
    /** List only conversations owned by the AD assistant, newest first. */
    @Synchronized
    fun conversations(): List<ChatThread> {
        pruneExpiredConversations()
        val managed = managedThreadIds()
        val conversations = ChatStore.listThreads()
            .filter { it.id in managed || it.title == AssistantConversationPolicy.THREAD_TITLE }
            .sortedByDescending { it.updatedAt }
        val legacyIds = conversations
            .asSequence()
            .filter { it.title == AssistantConversationPolicy.THREAD_TITLE }
            .mapTo(linkedSetOf()) { it.id }
        if (!managed.containsAll(legacyIds)) saveManagedThreadIds(managed + legacyIds)
        return conversations
    }

    /** Rename an AD-owned conversation without allowing unrelated ChatStore data to be claimed. */
    @Synchronized
    fun renameConversation(threadId: String, title: String): Boolean {
        val thread = ChatStore.getThread(threadId) ?: return false
        val managed = managedThreadIds()
        if (thread.id !in managed && thread.title != AssistantConversationPolicy.THREAD_TITLE) return false
        trackManagedThread(thread.id)
        return ChatStore.updateThreadTitle(thread.id, title)
    }

    /** Delete one AD-owned conversation and return the conversation that should become active. */
    @Synchronized
    fun deleteConversation(threadId: String): String {
        val thread = ChatStore.getThread(threadId)
        val managed = managedThreadIds()
        val owned = thread != null &&
            (thread.id in managed || thread.title == AssistantConversationPolicy.THREAD_TITLE)
        if (owned) {
            ChatStore.deleteThread(threadId)
            saveManagedThreadIds(managed - threadId)
        }
        if (prefs.getString(KEY_ACTIVE_THREAD_ID, null) == threadId) {
            prefs.edit().remove(KEY_ACTIVE_THREAD_ID).apply()
        }
        val next = conversations().firstOrNull()?.id
        if (next != null) {
            prefs.edit().putString(KEY_ACTIVE_THREAD_ID, next).apply()
            return next
        }
        return createAndActivate()
    }
'''
text = replace_once(text, anchor, insert, 'conversation management insertion')
session.write_text(text)

# Rework the native conversation surface into a clean AI hub with history management.
screen = ROOT / 'ui/adglasses/ADNativeConversationScreen.kt'
text = screen.read_text()

# Imports for the new management UI.
text = replace_once(
    text,
    'import androidx.compose.material.icons.outlined.ArrowForward\n',
    '',
    'remove ArrowForward import',
)
text = replace_once(
    text,
    'import androidx.compose.material.icons.outlined.ChatBubbleOutline\n',
    '',
    'remove ChatBubbleOutline import',
)
text = replace_once(
    text,
    'import androidx.compose.material.icons.outlined.Terminal\n',
    '',
    'remove Terminal import',
)
text = replace_once(
    text,
    'import androidx.compose.material.icons.rounded.Add\n',
    'import androidx.compose.material.icons.rounded.Add\nimport androidx.compose.material.icons.rounded.Delete\nimport androidx.compose.material.icons.rounded.Edit\nimport androidx.compose.material.icons.rounded.History\n',
    'management icon imports',
)
text = replace_once(
    text,
    'import androidx.compose.material3.Icon\n',
    'import androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.Icon\n',
    'AlertDialog import',
)
text = replace_once(
    text,
    'import androidx.compose.material3.MaterialTheme\n',
    'import androidx.compose.material3.MaterialTheme\nimport androidx.compose.material3.OutlinedTextField\n',
    'OutlinedTextField import',
)
text = replace_once(
    text,
    'import androidx.compose.material3.Text\n',
    'import androidx.compose.material3.Text\nimport androidx.compose.material3.TextButton\n',
    'TextButton import',
)
text = replace_once(
    text,
    'import com.ad_glasses.shared.chat.ChatMessage\n',
    'import com.ad_glasses.shared.chat.ChatMessage\nimport com.ad_glasses.shared.chat.ChatThread\n',
    'screen ChatThread import',
)
text = replace_once(
    text,
    'import kotlinx.coroutines.launch\n',
    'import kotlinx.coroutines.launch\nimport java.text.SimpleDateFormat\nimport java.util.Date\nimport java.util.Locale\n',
    'date imports',
)

# Add state for the history manager.
state_anchor = '''    var errorText by remember { mutableStateOf<String?>(null) }
    var lastFailedPrompt by remember { mutableStateOf<String?>(null) }
    var aiAudioActive by remember { mutableStateOf(AudioSessionCoordinator.isBusy()) }
'''
state_replacement = '''    var errorText by remember { mutableStateOf<String?>(null) }
    var lastFailedPrompt by remember { mutableStateOf<String?>(null) }
    var showConversationHistory by remember { mutableStateOf(false) }
    var conversations by remember { mutableStateOf(session.conversations()) }
    var renameTarget by remember { mutableStateOf<ChatThread?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ChatThread?>(null) }
    var clearAllRequested by remember { mutableStateOf(false) }
    var aiAudioActive by remember { mutableStateOf(AudioSessionCoordinator.isBusy()) }
'''
text = replace_once(text, state_anchor, state_replacement, 'history state')

# Refresh history whenever the active thread changes.
refresh_anchor = '''    fun refresh() {
        messages = ChatStore.listMessages(threadId)
    }
'''
refresh_replacement = '''    fun refresh() {
        messages = ChatStore.listMessages(threadId)
        conversations = session.conversations()
    }

    fun refreshConversations() {
        conversations = session.conversations()
    }
'''
text = replace_once(text, refresh_anchor, refresh_replacement, 'refresh history')

# Remove prompt suggestion plumbing completely.
text = re.sub(
    r'''\n    fun useSuggestion\(prompt: String, requestWeb: Boolean = false\) \{.*?\n    \}\n''',
    '\n',
    text,
    count=1,
    flags=re.S,
)

# New conversation should leave history and refresh its list.
old = '''        lastFailedPrompt = null
        scope.launch {
            delay(80)
            focusComposer()
        }
'''
new = '''        lastFailedPrompt = null
        showConversationHistory = false
        refreshConversations()
        scope.launch {
            delay(80)
            focusComposer()
        }
'''
text = replace_once(text, old, new, 'new conversation history reset')

# Replace the whole page body before helper composables.
body_start = text.index('    Column(Modifier.fillMaxSize()) {')
body_end = text.index('\n}\n\n@Composable\nprivate fun ADConversationRouteDisclosure', body_start)
new_body = '''    Column(Modifier.fillMaxSize()) {
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
'''
text = text[:body_start] + new_body + text[body_end + 2:]

# Remove the old provider disclosure composable.
text, count = re.subn(
    r'''\n@Composable\nprivate fun ADConversationRouteDisclosure\(.*?\n}\n\n@Composable\nprivate fun ADLiveAudioState''',
    '\n@Composable\nprivate fun ADLiveAudioState',
    text,
    count=1,
    flags=re.S,
)
if count != 1:
    raise SystemExit(f'provider disclosure removal: expected 1 match, found {count}')

# Replace prompt suggestions with minimal empty state and conversation manager.
start = text.index('@Composable\nprivate fun ADConversationEmptyState')
end = text.index('@Composable\nprivate fun ADConversationTurn', start)
manager_block = '''@Composable
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
                    ?.replace("\\n", " ")
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

'''
text = text[:start] + manager_block + text[end:]

# Sanity checks against the unwanted old surface.
for forbidden in [
    'Text("Chats"',
    'ADConversationRouteDisclosure(',
    'ADPromptSuggestion(',
    'What did I capture today?',
    'Help me plan something',
    'AD-owned ${internalProvider.label} conversation',
]:
    if forbidden in text:
        raise SystemExit(f'ADNativeConversationScreen still contains forbidden UI: {forbidden}')

screen.write_text(text)

print('AI conversation hub transformer completed')

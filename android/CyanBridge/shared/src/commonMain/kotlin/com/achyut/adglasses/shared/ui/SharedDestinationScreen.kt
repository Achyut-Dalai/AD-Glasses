package com.achyut.adglasses.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.achyut.adglasses.shared.ai.ChatMessage as AiChatMessage
import com.achyut.adglasses.shared.chat.ChatMessage
import com.achyut.adglasses.shared.chat.ChatRole
import com.achyut.adglasses.shared.chat.ChatThread
import com.achyut.adglasses.shared.chat.ChatThreadSummary
import com.achyut.adglasses.shared.chat.ChatThreadUiState
import com.achyut.adglasses.shared.chat.ChatAttachmentsUiState
import com.achyut.adglasses.shared.chat.ChatComposerUiState
import com.achyut.adglasses.shared.navigation.AppDestination
import com.achyut.adglasses.shared.persistence.ChatEntity
import com.achyut.adglasses.shared.persistence.ChatMessageEntity
import com.achyut.adglasses.shared.plugins.NativePluginCardData
import com.achyut.adglasses.shared.plugins.NativePluginIds
import com.achyut.adglasses.shared.plugins.PluginTimeWindow
import com.achyut.adglasses.shared.recordings.MeetingRecordingUiState
import com.achyut.adglasses.shared.recordings.RecordingItem
import com.achyut.adglasses.shared.recordings.SyncedMediaItem
import com.achyut.adglasses.shared.recordings.TranscriptionEngine
import com.achyut.adglasses.shared.settings.AgentProviderType
import com.achyut.adglasses.shared.settings.SettingsSection
import com.achyut.adglasses.shared.settings.MemoryPrivacyMode
import com.achyut.adglasses.shared.settings.MemorySourceType
import com.achyut.adglasses.shared.platform.ADGlassesServices
import com.achyut.adglasses.shared.platform.PlatformPreferences
import com.achyut.adglasses.shared.platform.createPlatformPreferences
import com.achyut.adglasses.shared.platform.platformCurrentTimeMillis
import com.achyut.adglasses.shared.ui.chat.ChatListScreen
import com.achyut.adglasses.shared.ui.chat.ChatThreadScreen
import com.achyut.adglasses.shared.ui.plugins.CommunityPluginsScreen
import com.achyut.adglasses.shared.ui.recordings.RecordingsScreen
import com.achyut.adglasses.shared.ui.recordings.SyncedMediaGalleryScreen
import com.achyut.adglasses.shared.ui.settings.SettingsScreenActions
import com.achyut.adglasses.shared.ui.settings.SettingsUiState
import com.achyut.adglasses.shared.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

/**
 * Shared top-level destinations used by the iOS KMP host.
 */
@Composable
fun SharedDestinationScreen(
    destination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    onOpenAppearance: () -> Unit = {},
) {
    when (destination) {
        AppDestination.CHATS -> SharedChatsDestination(onDestinationSelected)
        AppDestination.MEDIA -> SharedMediaDestination(onDestinationSelected)
        AppDestination.PLUGINS -> SharedPluginsDestination(onDestinationSelected)
        AppDestination.SETTINGS -> SharedSettingsDestination(
            onDestinationSelected = onDestinationSelected,
            onOpenAppearance = onOpenAppearance,
        )
        else -> Unit
    }
}

@Composable
private fun SharedChatsDestination(onDestinationSelected: (AppDestination) -> Unit) {
    val scope = rememberCoroutineScope()
    var threads by remember { mutableStateOf<List<ChatThreadSummary>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<ChatThreadSummary?>(null) }
    var selectedThreadId by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            if (ADGlassesServices.isInitialized()) {
                threads = ADGlassesServices.chatRepository.getAllChats()
                    .map { ChatThreadSummary(it.id, it.title, it.updatedAt) }
                    .sortedByDescending { it.updatedAtEpochMillis }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val selectedThread = threads.firstOrNull { it.id == selectedThreadId }
    if (selectedThread != null) {
        SharedChatThreadDestination(
            threadSummary = selectedThread,
            onBack = { selectedThreadId = null },
            onDestinationSelected = onDestinationSelected,
        )
        return
    }

    ChatListScreen(
        threads = threads,
        pendingDelete = pendingDelete,
        formatTimestamp = ::formatSharedTimestamp,
        onOpenThread = { selectedThreadId = it.id },
        onRequestDelete = { pendingDelete = it },
        onConfirmDelete = {
            val thread = pendingDelete
            pendingDelete = null
            if (thread != null) {
                scope.launch {
                    if (ADGlassesServices.isInitialized()) {
                        ADGlassesServices.chatRepository.deleteChat(thread.id)
                        refresh()
                    }
                }
            }
        },
        onDismissDelete = { pendingDelete = null },
        onNewChat = {
            val now = sharedNowMillis()
            val id = "ios-$now"
            scope.launch {
                if (ADGlassesServices.isInitialized()) {
                    ADGlassesServices.chatRepository.insertChat(
                        ChatEntity(
                            id = id,
                            title = "New chat",
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    refresh()
                    selectedThreadId = id
                }
            }
        },
        onChatAppearance = {},
        onDestinationSelected = onDestinationSelected,
    )
}

@Composable
private fun SharedChatThreadDestination(
    threadSummary: ChatThreadSummary,
    onBack: () -> Unit,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var messages by remember(threadSummary.id) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var composerText by remember(threadSummary.id) { mutableStateOf("") }
    var isThinking by remember(threadSummary.id) { mutableStateOf(false) }
    var statusText by remember(threadSummary.id) { mutableStateOf<String?>(null) }

    fun reloadMessages() {
        scope.launch {
            if (ADGlassesServices.isInitialized()) {
                messages = ADGlassesServices.chatRepository.getMessages(threadSummary.id)
                    .map(ChatMessageEntity::toSharedMessage)
            }
        }
    }

    LaunchedEffect(threadSummary.id) { reloadMessages() }

    ChatThreadScreen(
        state = ChatThreadUiState(
            thread = ChatThread(
                id = threadSummary.id,
                title = threadSummary.title,
                createdAt = 0L,
                updatedAt = threadSummary.updatedAtEpochMillis,
            ),
            messages = messages,
            composerText = composerText,
            isGenerating = isThinking,
            statusText = statusText,
        ),
        messages = messages,
        composer = ChatComposerUiState(isMediaEnabled = false),
        attachments = ChatAttachmentsUiState(),
        modelBadge = if (ADGlassesServices.isInitialized()) {
            ADGlassesServices.aiModelRegistry.getDefaultModelId()
        } else {
            null
        },
        dailySummaryProgress = null,
        dailyReviewQueueStatus = statusText,
        userBubbleColor = null,
        assistantBubbleColor = null,
        wallpaper = null,
        isThinking = isThinking,
        onOpenChatList = onBack,
        onChatAppearance = {},
        onComposerTextChanged = { composerText = it },
        onPrimaryAction = {
            val text = composerText.trim()
            if (text.isNotEmpty() && !isThinking) {
                composerText = ""
                scope.launch {
                    if (!ADGlassesServices.isInitialized()) return@launch
                    val now = sharedNowMillis()
                    isThinking = true
                    statusText = null
                    ADGlassesServices.chatRepository.insertMessage(
                        ChatMessageEntity(
                            id = "user-$now",
                            chatId = threadSummary.id,
                            role = "user",
                            content = text,
                            timestamp = now,
                        ),
                    )
                    val history = ADGlassesServices.chatRepository.getMessages(threadSummary.id)
                        .map { AiChatMessage(it.role, it.content) }
                    runCatching {
                        ADGlassesServices.chatAiService.chat(history).message
                    }.onSuccess { response ->
                        ADGlassesServices.chatRepository.insertMessage(
                            ChatMessageEntity(
                                id = "assistant-${sharedNowMillis()}",
                                chatId = threadSummary.id,
                                role = "assistant",
                                content = response.content,
                                timestamp = sharedNowMillis(),
                            ),
                        )
                    }.onFailure { error ->
                        statusText = error.message ?: "Chat request failed"
                    }
                    reloadMessages()
                    isThinking = false
                }
            }
        },
        onAttachImage = {},
        onRecordAudio = {},
        onClearAttachments = {},
        onDestinationSelected = { destination ->
            if (destination == AppDestination.CHATS) onBack() else onDestinationSelected(destination)
        },
    )
}

@Composable
private fun SharedMediaDestination(onDestinationSelected: (AppDestination) -> Unit) {
    val scope = rememberCoroutineScope()
    var mediaItems by remember { mutableStateOf<List<SyncedMediaItem>>(emptyList()) }
    var showGallery by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            if (ADGlassesServices.isInitialized()) {
                mediaItems = ADGlassesServices.mediaRecordRepository.getAll().map { record ->
                    SyncedMediaItem(
                        id = record.id.hashCode().toLong(),
                        displayName = record.filename,
                        contentUriString = record.filePath,
                        isVideo = record.mimeType.startsWith("video/"),
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    if (showGallery) {
        SyncedMediaGalleryScreen(
            mediaItems = mediaItems,
            isLoading = false,
            folderHint = "Files downloaded by AD Glasses",
            loadThumbnail = { _: String -> null },
            onNavigateBack = { showGallery = false },
            onRefresh = ::refresh,
            onOpenMedia = {},
            onShareItems = {},
            onDeleteItems = {},
        )
    } else {
        RecordingsScreen(
            sessions = emptyList<RecordingItem>(),
            isLoading = false,
            recentSyncedMedia = mediaItems.take(4),
            playingSessionId = null,
            transcribingSessionId = null,
            meetingRecording = MeetingRecordingUiState(),
            showEngineChooser = false,
            selectedEngine = TranscriptionEngine.MOONSHINE,
            transcriptionProgress = null,
            transcriptDialog = null,
            formatTimestamp = ::formatSharedTimestamp,
            loadThumbnail = { _: String -> null },
            onOpenSyncedMedia = { showGallery = true },
            onOpenSyncedMediaItem = { showGallery = true },
            onPlay = {},
            onTranscribe = {},
            onViewTranscript = {},
            onStopMeetingCapture = {},
            onEngineSelected = {},
            onConfirmEngine = {},
            onDismissEngineChooser = {},
            onDismissTranscript = {},
            onDestinationSelected = onDestinationSelected,
        )
    }
}

@Composable
private fun SharedPluginsDestination(onDestinationSelected: (AppDestination) -> Unit) {
    val nativePlugins = listOf(
        NativePluginCardData(
            id = NativePluginIds.LOCAL_AGENT,
            title = "Local Agent",
            description = "Phone accessibility automation requires the Android Local Agent runtime.",
            badge = "Android only",
            enabled = false,
            hasSettings = false,
            isAvailable = false,
        ),
        NativePluginCardData(
            id = NativePluginIds.AUTO_DIARY,
            title = "AutoDiary",
            description = "Screen capture and daily-memory automation is not available on iOS yet.",
            badge = "iOS pending",
            enabled = false,
            hasSettings = false,
            isAvailable = false,
        ),
        NativePluginCardData(
            id = NativePluginIds.AUTO_AUDIO,
            title = "Auto Audio",
            description = "Background glasses audio capture requires an iOS audio/BLE adapter.",
            badge = "iOS pending",
            enabled = false,
            hasSettings = false,
            isAvailable = false,
        ),
        NativePluginCardData(
            id = NativePluginIds.VISUAL_DIARY,
            title = "Visual Diary",
            description = "Periodic visual notes require iOS media transfer and background scheduling.",
            badge = "iOS pending",
            enabled = false,
            hasSettings = false,
            isAvailable = false,
        ),
    )

    CommunityPluginsScreen(
        plugins = emptyList(),
        selectedWindow = PluginTimeWindow.ALL_TIME,
        isRefreshing = false,
        onWindowSelected = {},
        onRefresh = {},
        onPublishPlugin = {},
        onDestinationSelected = onDestinationSelected,
        nativePlugins = nativePlugins,
        onToggleNativePlugin = { _, _ -> },
    )
}

@Composable
private fun SharedSettingsDestination(
    onDestinationSelected: (AppDestination) -> Unit,
    onOpenAppearance: () -> Unit,
) {
    var expandedSections by remember { mutableStateOf<Set<SettingsSection>>(emptySet()) }
    val preferences = remember { createPlatformPreferences(SHARED_SETTINGS_PREFS) }
    var settingsState by remember {
        mutableStateOf(loadSharedSettings(preferences))
    }
    val actions = remember(onDestinationSelected, onOpenAppearance) {
        SharedSettingsScreenActions(
            onDestinationSelected = onDestinationSelected,
            onOpenAppearance = onOpenAppearance,
            currentState = { settingsState },
            updateState = { next ->
                settingsState = next
                saveSharedSettings(preferences, next)
            },
        )
    }

    SettingsScreen(
        state = settingsState,
        expandedSections = expandedSections,
        onToggleSection = { section ->
            expandedSections = if (section in expandedSections) {
                expandedSections - section
            } else {
                expandedSections + section
            }
        },
        actions = actions,
    )
}

private class SharedSettingsScreenActions(
    private val onDestinationSelected: (AppDestination) -> Unit,
    private val onOpenAppearance: () -> Unit,
    private val currentState: () -> SettingsUiState,
    private val updateState: (SettingsUiState) -> Unit,
) : SettingsScreenActions {
    private fun update(transform: (SettingsUiState) -> SettingsUiState) {
        updateState(transform(currentState()))
    }

    override fun onDestinationSelected(destination: AppDestination) = onDestinationSelected.invoke(destination)
    override fun openAppearance() = onOpenAppearance.invoke()
    override fun openAppLanguageSelection() = Unit
    override fun setDefaultImageQuestion(question: String) = update { it.copy(defaultImageQuestion = question) }
    override fun resetDefaultImageQuestion() = update {
        it.copy(defaultImageQuestion = SettingsUiState().defaultImageQuestion)
    }
    override fun setMemoryMode(mode: MemoryPrivacyMode) = update { it.copy(memoryMode = mode) }
    override fun setMemorySync(source: MemorySourceType, enabled: Boolean) = update { state ->
        when (source) {
            MemorySourceType.EXPLICIT_USER_FACT -> state.copy(syncExplicit = enabled)
            MemorySourceType.AUTO_DAILY_FACT -> state.copy(syncDaily = enabled)
            MemorySourceType.SCREEN_OCR -> state.copy(syncOcr = enabled)
            MemorySourceType.DERIVED_SUMMARY -> state.copy(syncDerived = enabled)
            else -> state
        }
    }
    override fun setOcrRetentionDays(value: Int) = update { it.copy(ocrRetentionDays = value.coerceIn(1, 3650)) }
    override fun deletePassiveCapture() = Unit
    override fun lockVault() = update { it.copy(vaultLocked = true) }
    override fun unlockVault() = update { it.copy(vaultLocked = false) }
    override fun setVaultPassphrase() = Unit
    override fun clearVaultPassphrase() = Unit
    override fun resetVault() = update { it.copy(vaultLocked = false, vaultRequiresPassphrase = false) }
    override fun setTranscriptStorageEnabled(enabled: Boolean) = update { it.copy(transcriptStorageEnabled = enabled) }
    override fun setRedactNamesEnabled(enabled: Boolean) = update { it.copy(redactNamesEnabled = enabled) }
    override fun setIncludeFullTranscriptionEnabled(enabled: Boolean) = update { it.copy(includeFullTranscriptionInExports = enabled) }
    override fun exportLocalData() = Unit
    override fun importLocalData() = Unit
    override fun clearLocalData() = Unit
    override fun sendDebugLogs() = Unit
    override fun stopMeetingCapture() = Unit
    override fun setProviderType(type: AgentProviderType) = update { it.copy(providerType = type) }
    override fun openLocalModels() = Unit
}

private const val SHARED_SETTINGS_PREFS = "adglasses_shared_settings"

private fun loadSharedSettings(preferences: PlatformPreferences): SettingsUiState = SettingsUiState(
    providerType = AgentProviderType.valueOf(preferences.getString("provider_type", AgentProviderType.CLOUD_API.name)),
    memoryMode = MemoryPrivacyMode.fromRaw(preferences.getString("memory_mode", MemoryPrivacyMode.PRIVATE_LOCAL.name)),
    syncExplicit = preferences.getBoolean("sync_explicit", true),
    syncDaily = preferences.getBoolean("sync_daily", true),
    syncOcr = preferences.getBoolean("sync_ocr", false),
    syncDerived = preferences.getBoolean("sync_derived", false),
    ocrRetentionDays = preferences.getInt("ocr_retention_days", 7),
    vaultLocked = preferences.getBoolean("vault_locked", false),
    transcriptStorageEnabled = preferences.getBoolean("transcript_storage", false),
    redactNamesEnabled = preferences.getBoolean("redact_names", true),
    includeFullTranscriptionInExports = preferences.getBoolean("full_transcript_exports", false),
)

private fun saveSharedSettings(preferences: PlatformPreferences, state: SettingsUiState) {
    preferences.putString("provider_type", state.providerType.name)
    preferences.putString("memory_mode", state.memoryMode.name)
    preferences.putBoolean("sync_explicit", state.syncExplicit)
    preferences.putBoolean("sync_daily", state.syncDaily)
    preferences.putBoolean("sync_ocr", state.syncOcr)
    preferences.putBoolean("sync_derived", state.syncDerived)
    preferences.putInt("ocr_retention_days", state.ocrRetentionDays)
    preferences.putBoolean("vault_locked", state.vaultLocked)
    preferences.putBoolean("transcript_storage", state.transcriptStorageEnabled)
    preferences.putBoolean("redact_names", state.redactNamesEnabled)
    preferences.putBoolean("full_transcript_exports", state.includeFullTranscriptionInExports)
}

private fun ChatMessageEntity.toSharedMessage(): ChatMessage = ChatMessage(
    id = id,
    chatId = chatId,
    role = if (role.equals("user", ignoreCase = true)) ChatRole.USER else ChatRole.ASSISTANT,
    content = content,
    createdAt = timestamp,
)

private fun formatSharedTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Unknown time"
    val ageSeconds = ((platformCurrentTimeMillis() - timestamp) / 1000L).coerceAtLeast(0L)
    return when {
        ageSeconds < 60L -> "Just now"
        ageSeconds < 60L * 60L -> "${ageSeconds / 60L}m ago"
        ageSeconds < 24L * 60L * 60L -> "${ageSeconds / (60L * 60L)}h ago"
        else -> "${ageSeconds / (24L * 60L * 60L)}d ago"
    }
}

private fun sharedNowMillis(): Long = platformCurrentTimeMillis()

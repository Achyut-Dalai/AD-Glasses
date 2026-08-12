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
import com.achyut.adglasses.shared.navigation.closeSubscription
import com.achyut.adglasses.shared.navigation.openSubscription
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
import com.achyut.adglasses.shared.platform.CyanBridgeServices
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
import com.achyut.adglasses.shared.generated.resources.*
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

/**
 * Shared top-level destinations used by the iOS KMP host.
 *
 * Android keeps its mature Activity presenters for now. The iOS host uses this
 * route so the shared screens are real destinations rather than placeholders.
 * Platform repositories and AI services remain behind CyanBridgeServices.
 */
@Composable
fun SharedDestinationScreen(
    destination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    onOpenAppearance: () -> Unit = {},
) {
    var subscriptionRoute by remember(destination) {
    }

    when (destination) {
        AppDestination.CHATS -> SharedChatsDestination(onDestinationSelected)
        AppDestination.MEDIA -> SharedMediaDestination(onDestinationSelected)
        AppDestination.PLUGINS -> SharedPluginsDestination(onDestinationSelected)
        AppDestination.SETTINGS -> when (subscriptionRoute) {
                onDestinationSelected = onDestinationSelected,
                onOpenAppearance = onOpenAppearance,
                onOpenSubscription = {
                    subscriptionRoute = subscriptionRoute.openSubscription()
                },
            )
                initialState = proSubscriptionState,
                onBack = { subscriptionRoute = subscriptionRoute.closeSubscription() },
            )
        }
        AppDestination.GLASSES -> Unit
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun SharedChatsDestination(onDestinationSelected: (AppDestination) -> Unit) {
    val scope = rememberCoroutineScope()
    val newChatTitle = stringResource(Res.string.action_new_chat)
    val formatTimestamp = sharedTimestampFormatter()
    var threads by remember { mutableStateOf<List<ChatThreadSummary>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<ChatThreadSummary?>(null) }
    var selectedThreadId by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            if (CyanBridgeServices.isInitialized()) {
                threads = CyanBridgeServices.chatRepository.getAllChats()
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
         formatTimestamp = formatTimestamp,
        onOpenThread = { selectedThreadId = it.id },
        onRequestDelete = { pendingDelete = it },
        onConfirmDelete = {
            val thread = pendingDelete
            pendingDelete = null
            if (thread != null) {
                scope.launch {
                    if (CyanBridgeServices.isInitialized()) {
                        CyanBridgeServices.chatRepository.deleteChat(thread.id)
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
                if (CyanBridgeServices.isInitialized()) {
                    CyanBridgeServices.chatRepository.insertChat(
                        ChatEntity(
                            id = id,
                             title = newChatTitle,
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

@OptIn(ExperimentalResourceApi::class)
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
    val chatRequestFailed = stringResource(Res.string.chat_request_failed)

    fun reloadMessages() {
        scope.launch {
            if (CyanBridgeServices.isInitialized()) {
                messages = CyanBridgeServices.chatRepository.getMessages(threadSummary.id)
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
        modelBadge = if (CyanBridgeServices.isInitialized()) {
            CyanBridgeServices.aiModelRegistry.getDefaultModelId()
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
                    if (!CyanBridgeServices.isInitialized()) return@launch
                    val now = sharedNowMillis()
                    isThinking = true
                    statusText = null
                    CyanBridgeServices.chatRepository.insertMessage(
                        ChatMessageEntity(
                            id = "user-$now",
                            chatId = threadSummary.id,
                            role = "user",
                            content = text,
                            timestamp = now,
                        ),
                    )
                    val history = CyanBridgeServices.chatRepository.getMessages(threadSummary.id)
                        .map { AiChatMessage(it.role, it.content) }
                    runCatching {
                        CyanBridgeServices.chatAiService.chat(history).message
                    }.onSuccess { response ->
                        CyanBridgeServices.chatRepository.insertMessage(
                            ChatMessageEntity(
                                id = "assistant-${sharedNowMillis()}",
                                chatId = threadSummary.id,
                                role = "assistant",
                                content = response.content,
                                timestamp = sharedNowMillis(),
                            ),
                        )
                    }.onFailure { error ->
                         statusText = error.message ?: chatRequestFailed
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

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun SharedMediaDestination(onDestinationSelected: (AppDestination) -> Unit) {
    val scope = rememberCoroutineScope()
    val formatTimestamp = sharedTimestampFormatter()
    var mediaItems by remember { mutableStateOf<List<SyncedMediaItem>>(emptyList()) }
    var showGallery by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            if (CyanBridgeServices.isInitialized()) {
                mediaItems = CyanBridgeServices.mediaRecordRepository.getAll().map { record ->
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
             folderHint = stringResource(Res.string.media_folder_hint),
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
             formatTimestamp = formatTimestamp,
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

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun SharedPluginsDestination(onDestinationSelected: (AppDestination) -> Unit) {
    val nativePlugins = listOf(
        NativePluginCardData(
            id = NativePluginIds.LOCAL_AGENT,
             title = stringResource(Res.string.native_local_agent_title),
             description = stringResource(Res.string.native_local_agent_description),
             badge = stringResource(Res.string.native_android_only),
            enabled = false,
            hasSettings = false,
            isAvailable = false,
        ),
        NativePluginCardData(
            id = NativePluginIds.AUTO_DIARY,
             title = stringResource(Res.string.native_auto_diary_title),
             description = stringResource(Res.string.native_auto_diary_description),
             badge = stringResource(Res.string.native_ios_pending),
            enabled = false,
            hasSettings = false,
            isAvailable = false,
        ),
        NativePluginCardData(
            id = NativePluginIds.AUTO_AUDIO,
            title = stringResource(Res.string.native_auto_audio_title),
            description = stringResource(Res.string.native_auto_audio_description),
            badge = stringResource(Res.string.native_ios_pending),
            enabled = false,
            hasSettings = false,
            isAvailable = false,
        ),
        NativePluginCardData(
            id = NativePluginIds.VISUAL_DIARY,
            title = stringResource(Res.string.native_visual_diary_title),
            description = stringResource(Res.string.native_visual_diary_description),
            badge = stringResource(Res.string.native_ios_pending),
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
    onOpenSubscription: () -> Unit,
) {
    var expandedSections by remember { mutableStateOf<Set<SettingsSection>>(emptySet()) }
    val preferences = remember { createPlatformPreferences(SHARED_SETTINGS_PREFS) }
    var settingsState by remember {
        mutableStateOf(loadSharedSettings(preferences))
    }
    val actions = remember(onDestinationSelected, onOpenAppearance, onOpenSubscription) {
        SharedSettingsScreenActions(
            onDestinationSelected = onDestinationSelected,
            onOpenAppearance = onOpenAppearance,
            onOpenSubscription = onOpenSubscription,
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

@OptIn(ExperimentalResourceApi::class)
@Composable
    onBack: () -> Unit,
) {
    var state by remember(initialState) { mutableStateOf(initialState) }
    val unavailableSubscriptionStatus = stringResource(Res.string.shared_chat_unavailable)

        state = state.copy(
            status = onSubscriptionAction(action),
        )
    }

        state = state,
        onPlanSelected = { plan -> state = state.copy(selectedPlan = plan) },
        onCancelSubscription = {
            state = state.copy(
                 status = unavailableSubscriptionStatus,
            )
        },
        onBack = onBack,
    )
}

private class SharedSettingsScreenActions(
    private val onDestinationSelected: (AppDestination) -> Unit,
    private val onOpenAppearance: () -> Unit,
    private val onOpenSubscription: () -> Unit,
    private val currentState: () -> SettingsUiState,
    private val updateState: (SettingsUiState) -> Unit,
) : SettingsScreenActions {
    private fun update(transform: (SettingsUiState) -> SettingsUiState) {
        updateState(transform(currentState()))
    }

    override fun onDestinationSelected(destination: AppDestination) = onDestinationSelected.invoke(destination)
    override fun openAppearance() = onOpenAppearance.invoke()
    override fun openAppLanguageSelection() = Unit
    override fun openSubscription() = onOpenSubscription.invoke()
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

private const val SHARED_SETTINGS_PREFS = "cyanbridge_shared_settings"

private fun loadSharedSettings(preferences: PlatformPreferences): SettingsUiState = SettingsUiState(
    providerType = AgentProviderType.valueOf(preferences.getString("provider_type", AgentProviderType.CLOUD.name)),
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

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun sharedTimestampFormatter(): (Long) -> String {
    val unknown = stringResource(Res.string.time_unknown)
    val justNow = stringResource(Res.string.time_just_now)
    val minutesAgo = stringResource(Res.string.time_minutes_ago)
    val hoursAgo = stringResource(Res.string.time_hours_ago)
    val daysAgo = stringResource(Res.string.time_days_ago)
    return { timestamp ->
        formatSharedTimestamp(timestamp, unknown, justNow, minutesAgo, hoursAgo, daysAgo)
    }
}

private fun formatSharedTimestamp(
    timestamp: Long,
    unknown: String,
    justNow: String,
    minutesAgo: String,
    hoursAgo: String,
    daysAgo: String,
): String {
    if (timestamp <= 0L) return unknown
    val ageSeconds = ((platformCurrentTimeMillis() - timestamp) / 1000L).coerceAtLeast(0L)
    return when {
        ageSeconds < 60L -> justNow
        ageSeconds < 60L * 60L -> minutesAgo.replace("%1\$d", (ageSeconds / 60L).toString())
        ageSeconds < 24L * 60L * 60L -> hoursAgo.replace("%1\$d", (ageSeconds / (60L * 60L)).toString())
        else -> daysAgo.replace("%1\$d", (ageSeconds / (24L * 60L * 60L)).toString())
    }
}

private fun sharedNowMillis(): Long = platformCurrentTimeMillis()

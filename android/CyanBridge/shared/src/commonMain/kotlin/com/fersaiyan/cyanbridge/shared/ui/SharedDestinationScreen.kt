package com.fersaiyan.cyanbridge.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.fersaiyan.cyanbridge.shared.ai.ChatMessage as AiChatMessage
import com.fersaiyan.cyanbridge.shared.chat.ChatMessage
import com.fersaiyan.cyanbridge.shared.chat.ChatRole
import com.fersaiyan.cyanbridge.shared.chat.ChatThread
import com.fersaiyan.cyanbridge.shared.chat.ChatThreadSummary
import com.fersaiyan.cyanbridge.shared.chat.ChatThreadUiState
import com.fersaiyan.cyanbridge.shared.chat.ChatAttachmentsUiState
import com.fersaiyan.cyanbridge.shared.chat.ChatComposerUiState
import com.fersaiyan.cyanbridge.shared.billing.ProSubscriptionAction
import com.fersaiyan.cyanbridge.shared.billing.ProSubscriptionUiState
import com.fersaiyan.cyanbridge.shared.billing.unavailableProSubscriptionStatus
import com.fersaiyan.cyanbridge.shared.navigation.AppDestination
import com.fersaiyan.cyanbridge.shared.navigation.SharedSubscriptionRoute
import com.fersaiyan.cyanbridge.shared.navigation.closeSubscription
import com.fersaiyan.cyanbridge.shared.navigation.openSubscription
import com.fersaiyan.cyanbridge.shared.persistence.ChatEntity
import com.fersaiyan.cyanbridge.shared.persistence.ChatMessageEntity
import com.fersaiyan.cyanbridge.shared.plugins.NativePluginCardData
import com.fersaiyan.cyanbridge.shared.plugins.NativePluginIds
import com.fersaiyan.cyanbridge.shared.plugins.PluginTimeWindow
import com.fersaiyan.cyanbridge.shared.recordings.MeetingRecordingUiState
import com.fersaiyan.cyanbridge.shared.recordings.RecordingItem
import com.fersaiyan.cyanbridge.shared.recordings.SyncedMediaItem
import com.fersaiyan.cyanbridge.shared.recordings.TranscriptionEngine
import com.fersaiyan.cyanbridge.shared.settings.SettingsSection
import com.fersaiyan.cyanbridge.shared.settings.MemoryPrivacyMode
import com.fersaiyan.cyanbridge.shared.settings.MemorySourceType
import com.fersaiyan.cyanbridge.shared.platform.CyanBridgeServices
import com.fersaiyan.cyanbridge.shared.platform.PlatformPreferences
import com.fersaiyan.cyanbridge.shared.platform.createPlatformPreferences
import com.fersaiyan.cyanbridge.shared.platform.platformCurrentTimeMillis
import com.fersaiyan.cyanbridge.shared.ui.chat.ChatListScreen
import com.fersaiyan.cyanbridge.shared.ui.chat.ChatThreadScreen
import com.fersaiyan.cyanbridge.shared.ui.plugins.CommunityPluginsScreen
import com.fersaiyan.cyanbridge.shared.ui.pro.ProSubscriptionScreen
import com.fersaiyan.cyanbridge.shared.ui.recordings.RecordingsScreen
import com.fersaiyan.cyanbridge.shared.ui.recordings.SyncedMediaGalleryScreen
import com.fersaiyan.cyanbridge.shared.ui.settings.SettingsScreenActions
import com.fersaiyan.cyanbridge.shared.ui.settings.SettingsUiState
import com.fersaiyan.cyanbridge.shared.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

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
    proSubscriptionState: ProSubscriptionUiState = ProSubscriptionUiState(),
    onProSubscriptionAction: (ProSubscriptionAction) -> String = ::unavailableProSubscriptionStatus,
) {
    var subscriptionRoute by remember(destination) {
        mutableStateOf(SharedSubscriptionRoute.SETTINGS)
    }

    when (destination) {
        AppDestination.CHATS -> SharedChatsDestination(onDestinationSelected)
        AppDestination.MEDIA -> SharedMediaDestination(onDestinationSelected)
        AppDestination.PLUGINS -> SharedPluginsDestination(onDestinationSelected)
        AppDestination.SETTINGS -> when (subscriptionRoute) {
            SharedSubscriptionRoute.SETTINGS -> SharedSettingsDestination(
                onDestinationSelected = onDestinationSelected,
                onOpenAppearance = onOpenAppearance,
                onOpenSubscription = {
                    subscriptionRoute = subscriptionRoute.openSubscription()
                },
            )
            SharedSubscriptionRoute.PRO_SUBSCRIPTION -> SharedProSubscriptionDestination(
                initialState = proSubscriptionState,
                onSubscriptionAction = onProSubscriptionAction,
                onBack = { subscriptionRoute = subscriptionRoute.closeSubscription() },
            )
        }
        AppDestination.GLASSES -> Unit
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
        formatTimestamp = ::formatSharedTimestamp,
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
            folderHint = "Files downloaded by CyanBridge",
            loadThumbnail = { _: String -> null },
            onNavigateBack = { showGallery = false },
            onRefresh = ::refresh,
            onOpenMedia = {},
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

@Composable
private fun SharedProSubscriptionDestination(
    initialState: ProSubscriptionUiState,
    onSubscriptionAction: (ProSubscriptionAction) -> String,
    onBack: () -> Unit,
) {
    var state by remember(initialState) { mutableStateOf(initialState) }

    fun reportUnavailableAction(action: ProSubscriptionAction) {
        state = state.copy(
            status = onSubscriptionAction(action),
            checkoutPlan = null,
        )
    }

    ProSubscriptionScreen(
        state = state,
        onPlanSelected = { plan -> state = state.copy(selectedPlan = plan) },
        onSubscribeInApp = { reportUnavailableAction(ProSubscriptionAction.SUBSCRIBE) },
        onSubscribeOnWebsite = {
            if (state.webCheckoutAvailable) {
                state = state.copy(checkoutPlan = state.selectedPlan)
            } else {
                reportUnavailableAction(ProSubscriptionAction.SUBSCRIBE)
            }
        },
        onSecureCheckoutSelected = { reportUnavailableAction(ProSubscriptionAction.SUBSCRIBE) },
        onDismissSecureCheckout = { state = state.copy(checkoutPlan = null) },
        onDonate = { reportUnavailableAction(ProSubscriptionAction.DONATE) },
        onCancelSubscription = {
            state = state.copy(
                status = "Subscription management is unavailable on this host. No entitlement was changed.",
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
    override fun openVisionProfileSelection() = Unit
    override fun editVisionInstructions() = Unit
    override fun openSubscription() = onOpenSubscription.invoke()
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
}

private const val SHARED_SETTINGS_PREFS = "cyanbridge_shared_settings"

private fun loadSharedSettings(preferences: PlatformPreferences): SettingsUiState = SettingsUiState(
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

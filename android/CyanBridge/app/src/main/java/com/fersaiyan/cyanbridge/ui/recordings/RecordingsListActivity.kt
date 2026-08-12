package com.achyut.adglasses.ui.recordings

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.achyut.adglasses.MainActivity
import com.achyut.adglasses.R
import com.achyut.adglasses.agent.LocalModelsConfigureActivity
import com.achyut.adglasses.ai.transcription.DefaultTranscriptionService
import com.achyut.adglasses.ai.transcription.GemmaLiteRtTranscriptionProvider
import com.achyut.adglasses.ai.transcription.Mp4AudioChunker
import com.achyut.adglasses.ai.transcription.NoOpAudioChunker
import com.achyut.adglasses.ai.transcription.RetryPolicy
import com.achyut.adglasses.ai.transcription.RetryingTranscriptionProvider
import com.achyut.adglasses.ai.transcription.TranscriptionProgress
import com.achyut.adglasses.ai.transcription.TranscriptionResult
import com.achyut.adglasses.ai.transcription.TranscriptionService
import com.achyut.adglasses.ai.transcription.moonshine.MoonshineModelManager
import com.achyut.adglasses.ai.transcription.moonshine.MoonshineTranscriptionProvider
import com.achyut.adglasses.shared.recordings.MeetingRecordingUiState as SharedMeetingRecordingUiState
import com.achyut.adglasses.shared.recordings.RecordingItem
import com.achyut.adglasses.shared.recordings.SyncedMediaItem
import com.achyut.adglasses.shared.recordings.TranscriptDialogUiState as SharedTranscriptDialogUiState
import com.achyut.adglasses.shared.recordings.TranscriptionEngine
import com.achyut.adglasses.shared.recordings.TranscriptionProgressUiState as SharedTranscriptionProgressUiState
import com.achyut.adglasses.shared.settings.CaptureSource
import com.achyut.adglasses.audio.MeetingCapturePrefs
import com.achyut.adglasses.audio.MeetingCaptureService
import com.achyut.adglasses.shared.chat.ChatRole
import com.achyut.adglasses.chat.ChatStore
import com.achyut.adglasses.data.local.entity.CaptureSession
import com.achyut.adglasses.localagent.userfacts.TranscriptCandidateFactsAppender
import com.achyut.adglasses.localmodels.settings.LocalModelRuntime
import com.achyut.adglasses.localmodels.settings.LocalModelSettingsRepository
import com.achyut.adglasses.localmodels.storage.LocalModelStorageRepository
import com.achyut.adglasses.privacy.PrivacyPrefs
import com.achyut.adglasses.shared.navigation.AppDestination
import com.achyut.adglasses.shared.ui.recordings.RecordingsScreen
import com.achyut.adglasses.ui.ChatThreadActivity
import com.achyut.adglasses.ui.CommunityPluginsActivity
import com.achyut.adglasses.ui.MyApplication
import com.achyut.adglasses.ui.SettingsActivity
import com.achyut.adglasses.ui.appearance.AppearancePreferences
import com.achyut.adglasses.ui.appearance.rememberAppearanceSettings
import com.achyut.adglasses.ui.debug.DebugLogSupport
import com.achyut.adglasses.ui.theme.AdGlassesTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

class RecordingsListActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_TRANSCRIPTION_ENGINE = "recordings_transcription_engine"
        private const val KEY_TRANSCRIPTION_ENGINE = "engine"
    }

    private val uiScope = MainScope()
    private var sessionsJob: Job? = null
    private var recentMediaJob: Job? = null

    private var sessions by mutableStateOf<List<CaptureSession>>(emptyList())
    private var isLoadingSessions by mutableStateOf(true)
    private var recentSyncedMedia by mutableStateOf<List<SyncedMediaItem>>(emptyList())
    private var meetingRecording by mutableStateOf(SharedMeetingRecordingUiState())
    private var currentlyPlayingId by mutableStateOf<Long?>(null)
    private var transcribingId by mutableStateOf<Long?>(null)
    private var pendingTranscriptionSession by mutableStateOf<CaptureSession?>(null)
    private var selectedEngine by mutableStateOf(TranscriptionEngine.GEMMA)
    private var transcriptionProgress by mutableStateOf<SharedTranscriptionProgressUiState?>(null)
    private var transcriptDialog by mutableStateOf<SharedTranscriptDialogUiState?>(null)

    private var mediaPlayer: MediaPlayer? = null
    private val ephemeralTranscripts = mutableMapOf<Long, String>()
    private var meetingStateReceiverRegistered = false

    private val transcriptionPrefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_TRANSCRIPTION_ENGINE, MODE_PRIVATE)
    }

    private val meetingStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action != MeetingCaptureService.ACTION_STATE) return
            val source = intent.getStringExtra(MeetingCaptureService.EXTRA_SOURCE)
                ?.let { runCatching { CaptureSource.valueOf(it) }.getOrNull() }
            meetingRecording = SharedMeetingRecordingUiState(
                isRecording = intent.getBooleanExtra(MeetingCaptureService.EXTRA_IS_RECORDING, false),
                sourceLabel = source?.let { src ->
                    when (src) {
                        CaptureSource.BLUETOOTH_MIC -> "Bluetooth mic"
                        CaptureSource.PHONE_MIC -> "Phone mic"
                    }
                },
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncMeetingRecordingState()

        val appearancePreferences = AppearancePreferences(this)
        setContent {
            val appearance by rememberAppearanceSettings(appearancePreferences)
            AdGlassesTheme(appearance) {
                val recordingItems = sessions.map { it.toRecordingItem() }
                RecordingsScreen(
                    sessions = recordingItems,
                    isLoading = isLoadingSessions,
                    recentSyncedMedia = recentSyncedMedia,
                    playingSessionId = currentlyPlayingId,
                    transcribingSessionId = transcribingId,
                    meetingRecording = meetingRecording,
                    showEngineChooser = pendingTranscriptionSession != null,
                    selectedEngine = selectedEngine,
                    transcriptionProgress = transcriptionProgress,
                    transcriptDialog = transcriptDialog,
                    formatTimestamp = { ms -> java.text.DateFormat.getDateTimeInstance().format(java.util.Date(ms)) },
                    loadThumbnail = { uriString -> loadThumbnailForShared(uriString) },
                    onOpenSyncedMedia = {
                        startActivity(Intent(this, SyncedMediaGalleryActivity::class.java))
                    },
                    onOpenSyncedMediaItem = ::openSyncedMediaItem,
                    onPlay = { item -> sessions.firstOrNull { it.id == item.id }?.let(::onPlayClicked) },
                    onTranscribe = { item -> sessions.firstOrNull { it.id == item.id }?.let(::onTranscribeClicked) },
                    onViewTranscript = { item -> sessions.firstOrNull { it.id == item.id }?.let(::onViewTranscriptionClicked) },
                    onStopMeetingCapture = { MeetingCaptureService.stop(this) },
                    onEngineSelected = { selectedEngine = it },
                    onConfirmEngine = ::confirmTranscription,
                    onDismissEngineChooser = { pendingTranscriptionSession = null },
                    onDismissTranscript = { transcriptDialog = null },
                    onDestinationSelected = ::navigateTo,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerMeetingStateReceiver()
        syncMeetingRecordingState()

        sessionsJob?.cancel()
        isLoadingSessions = true
        sessionsJob = uiScope.launch {
            MyApplication.repository.getAllCaptureSessions().collect { captureSessions ->
                sessions = captureSessions
                isLoadingSessions = false
            }
        }
        loadRecentSyncedPhotos()
    }

    override fun onStop() {
        super.onStop()
        unregisterMeetingStateReceiver()
        sessionsJob?.cancel()
        sessionsJob = null
        recentMediaJob?.cancel()
        recentMediaJob = null
        stopPlayback()
    }

    override fun onDestroy() {
        uiScope.cancel()
        stopPlayback()
        super.onDestroy()
    }

    private fun registerMeetingStateReceiver() {
        if (meetingStateReceiverRegistered) return
        LocalBroadcastManager.getInstance(this).registerReceiver(
            meetingStateReceiver,
            IntentFilter(MeetingCaptureService.ACTION_STATE),
        )
        meetingStateReceiverRegistered = true
    }

    private fun unregisterMeetingStateReceiver() {
        if (!meetingStateReceiverRegistered) return
        LocalBroadcastManager.getInstance(this).unregisterReceiver(meetingStateReceiver)
        meetingStateReceiverRegistered = false
    }

    private fun syncMeetingRecordingState() {
        val state = MeetingCapturePrefs.getState(this)
        meetingRecording = SharedMeetingRecordingUiState(
            isRecording = state.isRecording,
            sourceLabel = state.source?.let { src ->
                when (src) {
                    CaptureSource.BLUETOOTH_MIC -> "Bluetooth mic"
                    CaptureSource.PHONE_MIC -> "Phone mic"
                }
            },
        )
    }

    private fun loadRecentSyncedPhotos() {
        recentMediaJob?.cancel()
        recentMediaJob = uiScope.launch {
            recentSyncedMedia = withContext(Dispatchers.IO) {
                SyncedMediaQuery.query(
                    context = this@RecordingsListActivity,
                    imagesOnly = true,
                    limit = 4,
                )
            }
        }
    }

    private fun openSyncedMediaItem(item: SyncedMediaItem) {
        val uri = android.net.Uri.parse(item.contentUriString)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, getString(R.string.synced_media_open_failed), Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateTo(destination: AppDestination) {
        val target = when (destination) {
            AppDestination.GLASSES -> Intent(this, MainActivity::class.java)
            AppDestination.CHATS -> buildRecentChatIntent()
            AppDestination.MEDIA -> return
            AppDestination.PLUGINS -> Intent(this, CommunityPluginsActivity::class.java)
            AppDestination.SETTINGS -> Intent(this, SettingsActivity::class.java)
        }
        target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(target)
    }

    private fun buildRecentChatIntent(): Intent {
        val last = ChatStore.listNonEmptyThreads().firstOrNull()
        val lastUserAt = last?.let { thread ->
            ChatStore.listMessages(thread.id)
                .lastOrNull { it.role == ChatRole.USER }
                ?.createdAt
        } ?: 0L
        val openChatId = last?.id?.takeIf {
            lastUserAt > 0L && System.currentTimeMillis() - lastUserAt < 30 * 60 * 1_000
        }
        return Intent(this, ChatThreadActivity::class.java).apply {
            if (openChatId != null) {
                putExtra(ChatThreadActivity.EXTRA_CHAT_ID, openChatId)
            }
        }
    }

    private fun onPlayClicked(session: CaptureSession) {
        val path = session.audioPath
        if (path.isBlank()) {
            Toast.makeText(this, "Missing audio path", Toast.LENGTH_SHORT).show()
            return
        }
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "Audio file not found", Toast.LENGTH_LONG).show()
            return
        }
        if (currentlyPlayingId == session.id) {
            stopPlayback()
            return
        }

        stopPlayback()
        val player = MediaPlayer()
        mediaPlayer = player
        currentlyPlayingId = session.id
        runCatching {
            player.setDataSource(path)
            player.setOnCompletionListener { stopPlayback() }
            player.prepare()
            player.start()
        }.onFailure {
            Toast.makeText(this, "Failed to play audio: ${it.message}", Toast.LENGTH_LONG).show()
            stopPlayback()
        }
    }

    private fun onTranscribeClicked(session: CaptureSession) {
        if (transcribingId != null) {
            Toast.makeText(this, "Already transcribing...", Toast.LENGTH_SHORT).show()
            return
        }
        val path = session.audioPath
        if (path.isBlank() || !File(path).exists()) {
            Toast.makeText(this, "Audio file not found", Toast.LENGTH_LONG).show()
            return
        }

        selectedEngine = transcriptionEngineFromWire(
            transcriptionPrefs.getString(KEY_TRANSCRIPTION_ENGINE, null),
        )
        pendingTranscriptionSession = session
    }

    private fun confirmTranscription() {
        val session = pendingTranscriptionSession ?: return
        pendingTranscriptionSession = null
        transcriptionPrefs.edit().putString(KEY_TRANSCRIPTION_ENGINE, selectedEngine.wire).apply()
        startTranscriptionWithEngine(session, selectedEngine)
    }

    private fun startTranscriptionWithEngine(session: CaptureSession, engine: TranscriptionEngine) {
        if (transcribingId != null) {
            Toast.makeText(this, "Already transcribing...", Toast.LENGTH_SHORT).show()
            return
        }
        if (engine == TranscriptionEngine.GEMMA && !isGemmaLiteRtReady()) {
            showGemmaRequiresLiteRtDialog()
            return
        }

        transcribingId = session.id
        transcriptionProgress = SharedTranscriptionProgressUiState(
            title = "Transcribing (${engine.title})",
            message = "Preparing...",
            progress = 0,
        )

        uiScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val provider: com.achyut.adglasses.ai.transcription.TranscriptionProvider
                    val chunker: com.achyut.adglasses.ai.transcription.AudioChunker

                    when (engine) {
                        TranscriptionEngine.GEMMA -> {
                            provider = RetryingTranscriptionProvider(
                                GemmaLiteRtTranscriptionProvider(applicationContext),
                                policy = RetryPolicy(maxAttempts = 1),
                            )
                            chunker = Mp4AudioChunker(applicationContext)
                        }

                        TranscriptionEngine.MOONSHINE -> {
                            val kind = MoonshineModelManager.chooseDefault(languageHint = null)
                            val modelDir = MoonshineModelManager.modelDir(applicationContext, kind)
                            if (!MoonshineModelManager.isInstalled(applicationContext, kind)) {
                                val approved = CompletableDeferred<Boolean>()
                                withContext(Dispatchers.Main) {
                                    AlertDialog.Builder(this@RecordingsListActivity)
                                        .setTitle("Download local Moonshine model?")
                                        .setMessage(
                                            "To transcribe with Moonshine local model, the app needs to download the model files once. Proceed?",
                                        )
                                        .setNegativeButton("Not now") { _, _ -> approved.complete(false) }
                                        .setPositiveButton("Download") { _, _ -> approved.complete(true) }
                                        .setCancelable(false)
                                        .show()
                                }
                                if (!approved.await()) {
                                    return@withContext TranscriptionResult.Failure(
                                        kind = TranscriptionResult.FailureKind.BAD_REQUEST,
                                        message = "Moonshine local model not installed",
                                        canRetry = true,
                                    )
                                }
                                MoonshineModelManager.installIfNeeded(applicationContext, kind) { update ->
                                    runOnUiThread {
                                        transcriptionProgress = SharedTranscriptionProgressUiState(
                                            title = "Transcribing (${engine.title})",
                                            message = update.message,
                                            progress = update.percent.coerceIn(0, 100),
                                        )
                                    }
                                }
                            }
                            provider = RetryingTranscriptionProvider(
                                MoonshineTranscriptionProvider(
                                    context = applicationContext,
                                    modelDir = modelDir,
                                    modelArch = kind.modelArch,
                                ),
                                policy = RetryPolicy(maxAttempts = 1),
                            )
                            chunker = NoOpAudioChunker()
                        }
                    }

                    val service: TranscriptionService = DefaultTranscriptionService(
                        context = applicationContext,
                        repository = MyApplication.repository,
                        provider = provider,
                        chunker = chunker,
                    )
                    val isGemma = engine == TranscriptionEngine.GEMMA
                    val isMoonshine = engine == TranscriptionEngine.MOONSHINE
                    service.transcribe(
                        session = session,
                        options = TranscriptionService.Options(
                            chunkDurationSec = if (isGemma) 45 else 60,
                        ),
                        onProgress = { progress ->
                            runOnUiThread {
                                transcriptionProgress = progress.toUiState(
                                    engine = engine,
                                    showIndeterminate =
                                        (isGemma || isMoonshine) &&
                                            progress.stage == TranscriptionProgress.Stage.TRANSCRIBING,
                                )
                            }
                        },
                    )
                }

                when (result) {
                    is TranscriptionResult.Success -> {
                        ephemeralTranscripts[session.id] = result.text
                        withContext(Dispatchers.IO) {
                            runCatching {
                                TranscriptCandidateFactsAppender.appendFromTranscript(
                                    context = applicationContext,
                                    session = session,
                                    transcript = result.text,
                                )
                            }
                        }
                        Toast.makeText(this@RecordingsListActivity, "Transcription complete", Toast.LENGTH_SHORT).show()
                    }

                    is TranscriptionResult.Failure -> {
                        Log.e("RecordingsListActivity", "Transcription failed: ${result.message}")
                        if (isGemmaLiteRtRequirementIssue(result.message)) {
                            showGemmaRequiresLiteRtDialog()
                        }
                        if (engine == TranscriptionEngine.GEMMA || DebugLogSupport.isLocalRuntimeIssue(result.message)) {
                            DebugLogSupport.showSupportOptionsDialog(
                                activity = this@RecordingsListActivity,
                                title = "Local runtime issue",
                                issueType = "Local runtime issue",
                                description = "Transcription failed while using a local runtime. Sending logs can help diagnose LiteRT or GPU issues.",
                                extraInfo = linkedMapOf(
                                    "screen" to "recordings",
                                    "transcription_engine" to engine.name,
                                ),
                            )
                        }
                        Toast.makeText(
                            this@RecordingsListActivity,
                            "Transcription failed: ${result.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            } catch (throwable: Throwable) {
                Log.e("RecordingsListActivity", "Transcription threw an exception", throwable)
                if (isGemmaLiteRtRequirementIssue(throwable.message)) {
                    showGemmaRequiresLiteRtDialog()
                }
                if (engine == TranscriptionEngine.GEMMA || DebugLogSupport.isLocalRuntimeIssue(throwable.message, throwable)) {
                    DebugLogSupport.showSupportOptionsDialog(
                        activity = this@RecordingsListActivity,
                        title = "Local runtime issue",
                        issueType = "Local runtime issue",
                        description = "Transcription crashed while using a local runtime. Sending logs can help diagnose LiteRT or GPU issues.",
                        extraInfo = linkedMapOf(
                            "screen" to "recordings",
                            "transcription_engine" to engine.name,
                        ),
                    )
                }
                Toast.makeText(
                    this@RecordingsListActivity,
                    "Transcription failed: ${throwable.message}",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                transcriptionProgress = null
                transcribingId = null
            }
        }
    }

    private fun onViewTranscriptionClicked(session: CaptureSession) {
        uiScope.launch {
            val record = withContext(Dispatchers.IO) {
                MyApplication.repository.getTranscriptionByCaptureSessionId(session.id)
            }
            val storedText = record?.transcriptText
            val text = storedText ?: ephemeralTranscripts[session.id]
            if (text.isNullOrBlank()) {
                Toast.makeText(this@RecordingsListActivity, "No transcription available yet", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val stored = !storedText.isNullOrBlank()
            val storageEnabled = PrivacyPrefs.isTranscriptStorageEnabled(applicationContext)
            transcriptDialog = SharedTranscriptDialogUiState(
                title = if (stored) "Transcription (stored)" else "Transcription",
                text = if (!stored && !storageEnabled) {
                    "(Transcript storage is OFF in Settings; this text may not be persisted.)\n\n$text"
                } else {
                    text
                },
            )
        }
    }

    private fun isGemmaLiteRtReady(): Boolean {
        val selectedModel = runCatching {
            LocalModelStorageRepository.resolveSelectedModel(applicationContext)
        }.getOrNull() ?: return false
        val settings = runCatching {
            LocalModelSettingsRepository.getForModel(applicationContext, selectedModel.id)
        }.getOrNull() ?: return false
        return settings.modelRuntime == LocalModelRuntime.LITERT
    }

    private fun isGemmaLiteRtRequirementIssue(message: String?): Boolean {
        val normalized = message?.trim()?.lowercase().orEmpty()
        return normalized.contains("gemma transcription requires local runtime = litert")
    }

    private fun showGemmaRequiresLiteRtDialog() {
        AlertDialog.Builder(this)
            .setTitle("Gemma Requires LiteRT")
            .setMessage("Gemma transcription only works with Local Runtime set to LiteRT. Open Local Models settings and switch the selected model runtime to LiteRT.")
            .setNegativeButton("Close", null)
            .setPositiveButton("Open model settings") { _, _ ->
                startActivity(Intent(this, LocalModelsConfigureActivity::class.java))
            }
            .show()
    }

    private fun stopPlayback() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        currentlyPlayingId = null
    }

    private fun transcriptionEngineFromWire(value: String?): TranscriptionEngine {
        return when (value?.trim()?.lowercase()) {
            "moonshot", "moonshine" -> TranscriptionEngine.MOONSHINE
            else -> TranscriptionEngine.GEMMA
        }
    }

    private val TranscriptionEngine.wire: String
        get() = when (this) {
            TranscriptionEngine.MOONSHINE -> "moonshine"
            TranscriptionEngine.GEMMA -> "gemma"
        }

    private val TranscriptionEngine.title: String
        get() = when (this) {
            TranscriptionEngine.MOONSHINE -> "Moonshine (local)"
            TranscriptionEngine.GEMMA -> "Gemma (LiteRT local)"
        }

    private fun TranscriptionProgress.toUiState(
        engine: TranscriptionEngine,
        showIndeterminate: Boolean,
    ): SharedTranscriptionProgressUiState {
        val detail = detail?.let { " · $it" }.orEmpty()
        val message = when {
            showIndeterminate && engine == TranscriptionEngine.GEMMA -> "Transcribing with Gemma...$detail"
            showIndeterminate && engine == TranscriptionEngine.MOONSHINE -> "Transcribing with Moonshine...$detail"
            else -> when (stage) {
                TranscriptionProgress.Stage.PREPARING -> "Preparing... $percent%$detail"
                TranscriptionProgress.Stage.CHUNKING -> "Chunking... $percent%$detail"
                TranscriptionProgress.Stage.TRANSCRIBING -> "Transcribing... $percent%$detail"
                TranscriptionProgress.Stage.MERGING -> "Merging... $percent%$detail"
                TranscriptionProgress.Stage.SAVING -> "Saving... $percent%$detail"
                TranscriptionProgress.Stage.DONE -> "Done"
            }
        }
        return SharedTranscriptionProgressUiState(
            title = "Transcribing (${engine.title})",
            message = message,
            progress = if (showIndeterminate) null else percent.coerceIn(0, 100),
        )
    }

    private suspend fun loadThumbnailForShared(uriString: String): androidx.compose.ui.graphics.ImageBitmap? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val uri = android.net.Uri.parse(uriString)
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentResolver.loadThumbnail(uri, android.util.Size(256, 256), null)
                } else {
                    val inputStream = contentResolver.openInputStream(uri)
                    inputStream?.use { android.graphics.BitmapFactory.decodeStream(it) }
                }
                bitmap?.asImageBitmap()
            }.getOrNull()
        }
    }
}

internal fun CaptureSession.toRecordingItem(): RecordingItem {
    val timestamp = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(startedAt))
    val titlePrefix = if (captureSource == GLASSES_SYNC_CAPTURE_SOURCE) "Glasses audio" else "Meeting"
    val metadata = buildString {
        append("${durationSec}s")
        if (captureSource.isNotBlank()) append(" · $captureSource")
        if (deviceClass.isNotBlank()) append(" · $deviceClass")
    }
    return RecordingItem(
        id = id,
        title = "$titlePrefix · $timestamp",
        metadata = metadata,
        stopReason = stopReason,
        durationSec = durationSec,
        captureSource = captureSource,
        deviceClass = deviceClass,
        startedAt = startedAt,
    )
}

private const val GLASSES_SYNC_CAPTURE_SOURCE = "GLASSES_SYNC_P2P"

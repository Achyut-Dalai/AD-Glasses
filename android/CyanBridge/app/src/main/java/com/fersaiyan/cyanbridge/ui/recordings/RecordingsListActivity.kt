package com.fersaiyan.cyanbridge.ui.recordings

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.MediaPlayer
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
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.agent.LocalModelsConfigureActivity
import com.fersaiyan.cyanbridge.ai.transcription.DefaultTranscriptionService
import com.fersaiyan.cyanbridge.ai.transcription.GemmaLiteRtTranscriptionProvider
import com.fersaiyan.cyanbridge.ai.transcription.Mp4AudioChunker
import com.fersaiyan.cyanbridge.ai.transcription.NoOpAudioChunker
import com.fersaiyan.cyanbridge.ai.transcription.RetryPolicy
import com.fersaiyan.cyanbridge.ai.transcription.RetryingTranscriptionProvider
import com.fersaiyan.cyanbridge.ai.transcription.TranscriptionProgress
import com.fersaiyan.cyanbridge.ai.transcription.TranscriptionResult
import com.fersaiyan.cyanbridge.ai.transcription.TranscriptionService
import com.fersaiyan.cyanbridge.ai.transcription.moonshine.MoonshineModelManager
import com.fersaiyan.cyanbridge.ai.transcription.moonshine.MoonshineTranscriptionProvider
import com.fersaiyan.cyanbridge.audio.CaptureSource
import com.fersaiyan.cyanbridge.audio.MeetingCapturePrefs
import com.fersaiyan.cyanbridge.audio.MeetingCaptureService
import com.fersaiyan.cyanbridge.chat.ChatRole
import com.fersaiyan.cyanbridge.chat.ChatStore
import com.fersaiyan.cyanbridge.data.local.entity.CaptureSession
import com.fersaiyan.cyanbridge.localagent.userfacts.TranscriptCandidateFactsAppender
import com.fersaiyan.cyanbridge.localmodels.settings.LocalModelRuntime
import com.fersaiyan.cyanbridge.localmodels.settings.LocalModelSettingsRepository
import com.fersaiyan.cyanbridge.localmodels.storage.LocalModelStorageRepository
import com.fersaiyan.cyanbridge.privacy.PrivacyPrefs
import com.fersaiyan.cyanbridge.shared.navigation.AppDestination
import com.fersaiyan.cyanbridge.ui.ChatThreadActivity
import com.fersaiyan.cyanbridge.ui.CommunityPluginsActivity
import com.fersaiyan.cyanbridge.ui.MyApplication
import com.fersaiyan.cyanbridge.ui.SettingsActivity
import com.fersaiyan.cyanbridge.ui.appearance.AppearancePreferences
import com.fersaiyan.cyanbridge.ui.appearance.rememberAppearanceSettings
import com.fersaiyan.cyanbridge.ui.debug.DebugLogSupport
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var meetingRecording by mutableStateOf(MeetingRecordingUiState())
    private var currentlyPlayingId by mutableStateOf<Long?>(null)
    private var transcribingId by mutableStateOf<Long?>(null)
    private var pendingTranscriptionSession by mutableStateOf<CaptureSession?>(null)
    private var selectedEngine by mutableStateOf(TranscriptionEngine.GEMMA)
    private var transcriptionProgress by mutableStateOf<TranscriptionProgressUiState?>(null)
    private var transcriptDialog by mutableStateOf<TranscriptDialogUiState?>(null)

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
            meetingRecording = MeetingRecordingUiState(
                isRecording = intent.getBooleanExtra(MeetingCaptureService.EXTRA_IS_RECORDING, false),
                source = source,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncMeetingRecordingState()

        val appearancePreferences = AppearancePreferences(this)
        setContent {
            val appearance by rememberAppearanceSettings(appearancePreferences)
            CyanBridgeTheme(appearance) {
                RecordingsScreen(
                    sessions = sessions,
                    isLoading = isLoadingSessions,
                    recentSyncedMedia = recentSyncedMedia,
                    playingSessionId = currentlyPlayingId,
                    transcribingSessionId = transcribingId,
                    meetingRecording = meetingRecording,
                    showEngineChooser = pendingTranscriptionSession != null,
                    selectedEngine = selectedEngine,
                    transcriptionProgress = transcriptionProgress,
                    transcriptDialog = transcriptDialog,
                    onOpenSyncedMedia = {
                        startActivity(Intent(this, SyncedMediaGalleryActivity::class.java))
                    },
                    onOpenSyncedMediaItem = ::openSyncedMediaItem,
                    onPlay = ::onPlayClicked,
                    onTranscribe = ::onTranscribeClicked,
                    onViewTranscript = ::onViewTranscriptionClicked,
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
        meetingRecording = MeetingRecordingUiState(
            isRecording = state.isRecording,
            source = state.source,
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
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.contentUri, "image/*")
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
        transcriptionProgress = TranscriptionProgressUiState(
            title = "Transcribing (${engine.title})",
            message = "Preparing...",
            progress = 0,
        )

        uiScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val provider: com.fersaiyan.cyanbridge.ai.transcription.TranscriptionProvider
                    val chunker: com.fersaiyan.cyanbridge.ai.transcription.AudioChunker

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
                                        transcriptionProgress = TranscriptionProgressUiState(
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
            transcriptDialog = TranscriptDialogUiState(
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
    ): TranscriptionProgressUiState {
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
        return TranscriptionProgressUiState(
            title = "Transcribing (${engine.title})",
            message = message,
            progress = if (showIndeterminate) null else percent.coerceIn(0, 100),
        )
    }
}

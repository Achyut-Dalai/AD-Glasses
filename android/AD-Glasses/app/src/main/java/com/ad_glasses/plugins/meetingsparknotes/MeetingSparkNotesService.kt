package com.ad_glasses.plugins.meetingsparknotes

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.ad_glasses.ai.router.CliRelayClient
import com.ad_glasses.bridge.core.DisplayCommand
import com.ad_glasses.bridge.core.GlassesBridge
import com.ad_glasses.plugins.PluginVoiceRecognizer
import com.ad_glasses.plugins.startPluginVoiceForeground
import com.ad_glasses.plugins.startPluginVoiceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures a live meeting transcript with Android speech recognition, using a connected Bluetooth
 * glasses microphone when Android exposes one, then turns that transcript into meeting notes.
 */
class MeetingSparkNotesService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val meetingStore = MeetingSparkNotesStore()
    private val transcriptLines = mutableListOf<String>()
    private val transcriptLock = Any()
    private val isSummarizing = AtomicBoolean(false)
    private lateinit var voiceRecognizer: PluginVoiceRecognizer

    override fun onCreate() {
        super.onCreate()
        MeetingSparkNotesNotificationHelper.ensureChannel(this)
        meetingStore.load(this)
        voiceRecognizer = PluginVoiceRecognizer(
            context = this,
            languageTag = null,
            onPartialText = { partial ->
                MeetingSparkNotesNotificationHelper.updateNotification(
                    this,
                    "Capturing: ${partial.take(NOTIFICATION_TEXT_LIMIT)}",
                )
            },
            onFinalText = ::appendTranscriptLine,
            onError = { message ->
                Log.w(TAG, message)
                MeetingSparkNotesNotificationHelper.updateNotification(this, message)
            },
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMeetingCapture()
            ACTION_STOP -> stopMeetingCapture()
            ACTION_SUMMARIZE -> summarizeCurrentMeeting()
            null -> if (MeetingSparkNotesPreferences.isEnabled(this)) startMeetingCapture() else stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        voiceRecognizer.stop()
        MeetingSparkNotesPreferences.setEnabled(this, false)
        scope.cancel()
        super.onDestroy()
    }

    private fun startMeetingCapture() {
        if (!startPluginVoiceForeground(
                service = this,
                notificationId = MeetingSparkNotesNotificationHelper.NOTIFICATION_ID,
                notification = MeetingSparkNotesNotificationHelper.buildNotification(
                    this,
                    "Starting live meeting capture...",
                ),
            )
        ) {
            MeetingSparkNotesPreferences.setEnabled(this, false)
            Log.w(TAG, "Missing microphone or notification permission")
            stopSelf()
            return
        }

        synchronized(transcriptLock) { transcriptLines.clear() }
        if (!voiceRecognizer.start()) {
            MeetingSparkNotesPreferences.setEnabled(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        MeetingSparkNotesPreferences.setEnabled(this, true)
        MeetingSparkNotesNotificationHelper.updateNotification(
            this,
            "Listening for meeting speech...",
        )
    }

    private fun appendTranscriptLine(text: String) {
        synchronized(transcriptLock) {
            if (transcriptLines.lastOrNull() == text) return
            transcriptLines += text
        }
        MeetingSparkNotesNotificationHelper.updateNotification(
            this,
            "Captured: ${text.take(NOTIFICATION_TEXT_LIMIT)}",
        )
    }

    private fun stopMeetingCapture() {
        MeetingSparkNotesPreferences.setEnabled(this, false)
        voiceRecognizer.stop()
        summarizeTranscript(currentTranscript())
    }

    private fun summarizeCurrentMeeting() {
        summarizeTranscript(currentTranscript())
    }

    private fun currentTranscript(): String = synchronized(transcriptLock) {
        transcriptLines.joinToString(separator = "\n")
    }

    private fun summarizeTranscript(transcript: String) {
        if (transcript.isBlank()) {
            MeetingSparkNotesNotificationHelper.updateNotification(this, "No speech was captured")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (!isSummarizing.compareAndSet(false, true)) return

        MeetingSparkNotesNotificationHelper.updateNotification(this, "Creating meeting summary...")
        scope.launch {
            val summary = generateSummary(transcript)
            if (summary != null) {
                meetingStore.addSummary(summary, MeetingSparkNotesPreferences.getMaxHistory(this@MeetingSparkNotesService))
                meetingStore.persist(this@MeetingSparkNotesService, MeetingSparkNotesPreferences.getMaxHistory(this@MeetingSparkNotesService))
                MeetingSparkNotesNotificationHelper.updateNotification(
                    this@MeetingSparkNotesService,
                    "Summary ready: ${summary.title.take(NOTIFICATION_TEXT_LIMIT)}",
                )
                GlassesBridge.showCard(DisplayCommand.Card(summary.title, summary.summary))
            } else {
                MeetingSparkNotesNotificationHelper.updateNotification(
                    this@MeetingSparkNotesService,
                    "Meeting summary failed. Check your AI connection.",
                )
            }
            isSummarizing.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun generateSummary(transcript: String): MeetingSummary? {
        val style = MeetingSparkNotesPreferences.getSummaryStyle(this)
        val includeParticipants = MeetingSparkNotesPreferences.isIncludeParticipants(this)
        val includeActionItems = MeetingSparkNotesPreferences.isIncludeActionItems(this)
        val customPrompt = MeetingSparkNotesPreferences.getCustomPrompt(this)
        val prompt = buildString {
            append("Create a $style meeting summary from the transcript below. ")
            if (includeParticipants) append("Identify speakers or participants only when the transcript supports it. ")
            if (includeActionItems) append("Include a clearly labeled action-items list. ")
            if (customPrompt.isNotBlank()) append("Additional instructions: $customPrompt\n")
            append("Transcript:\n$transcript")
        }
        val result = CliRelayClient.chat(
            context = this,
            chatId = "meeting_spark_notes_${System.currentTimeMillis()}",
            prompt = prompt,
            messages = listOf(mapOf("role" to "user", "content" to prompt)),
            modelOverride = MeetingSparkNotesPreferences.getCloudModelId(this),
        )
        return result.fold(
            onSuccess = { response ->
                MeetingSummary(
                    id = UUID.randomUUID().toString(),
                    timestampMs = System.currentTimeMillis(),
                    title = extractTitle(response),
                    summary = response,
                    actionItems = extractActionItems(response),
                    participants = extractParticipants(response),
                    durationMinutes = 0,
                    audioPath = null,
                )
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to generate meeting summary", error)
                null
            },
        )
    }

    private fun extractTitle(response: String): String =
        response.lineSequence().firstOrNull { it.isNotBlank() }?.take(100) ?: "Meeting Summary"

    private fun extractActionItems(response: String): List<String> {
        val actionItems = mutableListOf<String>()
        var inActionSection = false
        for (line in response.lineSequence()) {
            if (line.contains("action item", ignoreCase = true) ||
                line.contains("to-do", ignoreCase = true) ||
                line.contains("task:", ignoreCase = true)
            ) {
                inActionSection = true
            } else if (inActionSection && line.startsWith("- ")) {
                actionItems += line.removePrefix("- ").trim()
            } else if (inActionSection && line.isBlank()) {
                inActionSection = false
            }
        }
        return actionItems
    }

    private fun extractParticipants(response: String): List<String> =
        response.lineSequence()
            .filter {
                it.contains("participant", ignoreCase = true) ||
                    it.contains("attendee", ignoreCase = true) ||
                    it.contains("speaker", ignoreCase = true)
            }
            .flatMap { it.split(",", "and", "&").asSequence() }
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.contains("participant", ignoreCase = true) }
            .distinct()
            .toList()

    companion object {
        private const val TAG = "MeetingSparkNotes"
        private const val NOTIFICATION_TEXT_LIMIT = 100

        const val ACTION_START = "com.ad_glasses.ACTION_START_MEETING"
        const val ACTION_STOP = "com.ad_glasses.ACTION_STOP_MEETING"
        const val ACTION_SUMMARIZE = "com.ad_glasses.ACTION_SUMMARIZE_MEETING"

        fun start(context: Context) {
            startPluginVoiceService(
                context,
                Intent(context, MeetingSparkNotesService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MeetingSparkNotesService::class.java).setAction(ACTION_STOP),
            )
        }

        fun summarize(context: Context) {
            context.startService(
                Intent(context, MeetingSparkNotesService::class.java).setAction(ACTION_SUMMARIZE),
            )
        }
    }
}

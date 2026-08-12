package com.achyut.adglasses.ui.debug

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.achyut.adglasses.ai.transcription.ChunkingTranscriptionService
import com.achyut.adglasses.ai.transcription.TranscriptionEndpointPrefs
import com.achyut.adglasses.ai.transcription.TranscriptionEvent
import com.achyut.adglasses.ai.transcription.TranscriptionRequest
import com.achyut.adglasses.ai.transcription.backend.FakeTranscriptionBackend
import com.achyut.adglasses.ai.transcription.backend.HttpTranscriptionBackend
import com.achyut.adglasses.ai.transcription.backend.TranscriptionBackend
import com.achyut.adglasses.ai.transcription.storage.RoomTranscriptStore
import com.achyut.adglasses.data.local.entity.CaptureSession
import com.achyut.adglasses.privacy.PrivacyPrefs
import com.achyut.adglasses.ui.MyApplication
import com.achyut.adglasses.ui.appearance.AppearancePreferences
import com.achyut.adglasses.shared.ui.debug.TranscriptionDebugScreen
import com.achyut.adglasses.ui.appearance.rememberAppearanceSettings
import com.achyut.adglasses.ui.theme.CyanBridgeTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

/**
 * Minimal manual POC entry point for Chapter 6.
 *
 * Launch via:
 *  adb shell am start -n com.achyut.adglasses/.ui.debug.TranscriptionDebugActivity
 */
class TranscriptionDebugActivity : AppCompatActivity() {

    private val scope = MainScope()

    private var latestSession: CaptureSession? = null
    private var endpointUrl by mutableStateOf("")
    private var apiKey by mutableStateOf("")
    private var useHttp by mutableStateOf(false)
    private var transcriptStorageEnabled by mutableStateOf(false)
    private var latestSessionInfo by mutableStateOf("(no session loaded)")
    private var isTranscribing by mutableStateOf(false)
    private var transcriptionProgressPercent by mutableStateOf(0)
    private var progressText by mutableStateOf("")
    private var persistedText by mutableStateOf("")
    private var output by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        endpointUrl = TranscriptionEndpointPrefs.getEndpointUrl(this).orEmpty()
        apiKey = TranscriptionEndpointPrefs.getApiKey(this).orEmpty()
        transcriptStorageEnabled = PrivacyPrefs.isTranscriptStorageEnabled(this)
        val appearancePreferences = AppearancePreferences(this)
        setContent {
            val appearance by rememberAppearanceSettings(appearancePreferences)
            CyanBridgeTheme(appearance) {
                TranscriptionDebugScreen(
                    endpointUrl = endpointUrl,
                    apiKey = apiKey,
                    useHttp = useHttp,
                    transcriptStorageEnabled = transcriptStorageEnabled,
                    latestSessionInfo = latestSessionInfo,
                    isTranscribing = isTranscribing,
                    progress = transcriptionProgressPercent,
                    progressText = progressText,
                    persistedText = persistedText,
                    output = output,
                    onEndpointUrlChange = { endpointUrl = it },
                    onApiKeyChange = { apiKey = it },
                    onUseHttpChange = { useHttp = it },
                    onStorageEnabledChange = {
                        transcriptStorageEnabled = it
                        PrivacyPrefs.setTranscriptStorageEnabled(this, it)
                    },
                    onSaveEndpoint = ::saveEndpointConfig,
                    onLoadLatest = ::loadLatestSession,
                    onTranscribe = ::startTranscription,
                )
            }
        }

        loadLatestSession()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadLatestSession() {
        scope.launch {
            val sessions = MyApplication.database.captureSessionDao().getAllSessions().first()
            latestSession = sessions.firstOrNull()

            val s = latestSession
            latestSessionInfo = if (s == null) {
                "No capture sessions found yet. Record a meeting first."
            } else {
                "Latest session id=${s.id}\nstartedAt=${s.startedAt}\ndurationSec=${s.durationSec}\naudioPath=${s.audioPath}"
            }
        }
    }

    private fun startTranscription() {
        val session = latestSession
        if (session == null) {
            Toast.makeText(this, "No capture session available", Toast.LENGTH_SHORT).show()
            return
        }

        val audioFile = File(session.audioPath)
        if (!audioFile.exists()) {
            Toast.makeText(this, "Audio file missing: ${session.audioPath}", Toast.LENGTH_LONG).show()
            return
        }

        val backend: TranscriptionBackend = if (useHttp) {
            HttpTranscriptionBackend(endpointUrl = endpointUrl.trim(), apiKey = apiKey.trim().takeUnless { it.isNullOrBlank() })
        } else {
            FakeTranscriptionBackend(fixedText = null)
        }

        val store = RoomTranscriptStore(
            context = applicationContext,
            dao = MyApplication.database.captureTranscriptDao(),
        )

        val service = ChunkingTranscriptionService(
            backend = backend,
            transcriptStore = store,
        )

        transcriptionProgressPercent = 0
        progressText = "Starting…"
        output = ""
        persistedText = ""
        isTranscribing = true

        scope.launch {
            service.transcribe(
                TranscriptionRequest(
                    audioFile = audioFile,
                    captureSessionId = session.id,
                    languageHint = null,
                )
            ).collect { event ->
                when (event) {
                    is TranscriptionEvent.Started -> {
                        progressText = "Started (${event.totalChunks} chunks) provider=${event.provider}"
                    }

                    is TranscriptionEvent.Progress -> {
                        transcriptionProgressPercent = event.percent
                        progressText = "${event.percent}% — ${event.message}"
                    }

                    is TranscriptionEvent.Completed -> {
                        transcriptionProgressPercent = 100
                        progressText = "Done (provider=${event.provider})"
                        output = event.transcript
                        persistedText = if (event.persisted) {
                            "Transcript persisted to DB (capture_transcripts)."
                        } else {
                            "Transcript NOT persisted (storage disabled or missing session id)."
                        }
                    }

                    is TranscriptionEvent.Failed -> {
                        progressText = "Failed: ${event.error.debugMessage}"
                        persistedText = if (event.canRetry) "Retryable" else "Not retryable"
                    }
                }
                isTranscribing = false
            }
        }
    }

    private fun saveEndpointConfig() {
        TranscriptionEndpointPrefs.setEndpointUrl(this, endpointUrl)
        TranscriptionEndpointPrefs.setApiKey(this, apiKey)
        Toast.makeText(this, "Saved endpoint config", Toast.LENGTH_SHORT).show()
    }
}

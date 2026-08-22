package com.ad_glasses.ai.live

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ad_glasses.ai.vision.ImageQuestionPreferences
import com.ad_glasses.ai.vision.ImageQuestionPromptResolver
import com.ad_glasses.ai.vision.ImageQuestionRoute
import com.ad_glasses.ui.localization.AppLanguagePreferences
import com.ad_glasses.ui.setThemedComposeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Visible, activity-scoped Gemini Live preview. It never records after Stop or background pause. */
class GeminiLiveActivity : AppCompatActivity(), GeminiLiveClient.Listener {
    private lateinit var client: GeminiLiveClient
    private var statusText by mutableStateOf("Ready")
    private var elapsedText by mutableStateOf("Session not running")
    private var networkText by mutableStateOf("Network: checking")
    private var indicatorsText by mutableStateOf("Microphone: off   Glasses camera: off")
    private var sessionActive by mutableStateOf(false)
    private var startedAtMs = 0L
    private var liveListening = false
    private var hardwareImageButtonRegistered = false
    private val hardwareImageCaptureInProgress = AtomicBoolean(false)
    private val hardwareImageButtonHandler: () -> Unit = { captureHardwareImageQuestion() }

    private val ticker = object : Runnable {
        override fun run() {
            if (startedAtMs > 0L) {
                val seconds = (System.currentTimeMillis() - startedAtMs) / 1_000L
                elapsedText = "Session ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
                window.decorView.postDelayed(this, 1_000L)
            }
        }
    }

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startLive() else Toast.makeText(this, "Microphone permission is required for Gemini Live", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = GeminiLiveClient(this, this)
        setThemedComposeContent {
            GeminiLivePreviewScreen(
                status = statusText,
                elapsed = elapsedText,
                network = networkText,
                indicators = indicatorsText,
                active = sessionActive,
                onBack = ::finish,
                onStart = ::explainAndRequestMicrophone,
                onStop = { client.stop() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        client.resumeAfterForeground()
    }

    override fun onPostResume() {
        super.onPostResume()
        updateHardwareImageButtonRouting()
    }

    override fun onPause() {
        unregisterHardwareImageButton()
        client.pauseForBackground()
        super.onPause()
    }

    override fun onDestroy() {
        window.decorView.removeCallbacks(ticker)
        unregisterHardwareImageButton()
        client.close()
        super.onDestroy()
    }

    private fun explainAndRequestMicrophone() {
        AlertDialog.Builder(this)
            .setTitle("Gemini Live preview")
            .setMessage("Gemini Live listens through your microphone and sends live audio to Google. While this screen is open, press the glasses AI photo button to deliberately send its thumbnail to Gemini. This preview requires network access.")
            .setPositiveButton("Continue") { _, _ ->
                if (hasPermission(Manifest.permission.RECORD_AUDIO)) startLive()
                else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startLive() {
        val language = AppLanguagePreferences.selected(this).languageTag
            .ifBlank { Locale.getDefault().toLanguageTag() }
        val defaultImageQuestion = ImageQuestionPromptResolver.resolve(
            settings = ImageQuestionPreferences.get(this),
            userQuestion = null,
        ).forRoute(ImageQuestionRoute.CLOUD_API)
        client.start(language, defaultImageQuestion)
    }

    private fun captureHardwareImageQuestion() {
        if (!hardwareImageCaptureInProgress.compareAndSet(false, true)) return
        statusText = "Receiving glasses AI photo"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { GeminiLiveGlassesImageCapture().captureFromHardwareButton() }
            }
            result
                .onSuccess { image ->
                    client.sendImage(image)
                    indicatorsText = "Microphone: on   Glasses AI photo: thumbnail sent"
                    statusText = "Image sent to Gemini Live"
                }
                .onFailure { error ->
                    Toast.makeText(this@GeminiLiveActivity, error.message ?: "Glasses image capture failed", Toast.LENGTH_LONG).show()
                }
            hardwareImageCaptureInProgress.set(false)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onStateChanged(state: GeminiLiveState, detail: String) {
        runOnUiThread {
            statusText = detail.ifBlank { state.name.lowercase().replaceFirstChar(Char::uppercase) }
            val listening = state == GeminiLiveState.LISTENING
            liveListening = listening
            updateHardwareImageButtonRouting()
            if (listening && startedAtMs == 0L) {
                startedAtMs = System.currentTimeMillis()
                window.decorView.post(ticker)
            }
            if (state == GeminiLiveState.STOPPED || state == GeminiLiveState.ERROR) {
                startedAtMs = 0L
                window.decorView.removeCallbacks(ticker)
                elapsedText = "Session not running"
            }
            indicatorsText = if (listening) {
                "Gemini Live is listening   Microphone: on   Glasses AI photo: ready"
            } else {
                "Microphone: off   Glasses camera: off"
            }
            sessionActive = listening || state == GeminiLiveState.CONNECTING || state == GeminiLiveState.RECONNECTING
        }
    }

    override fun onInterrupted() {
        runOnUiThread { statusText = "Gemini was interrupted. Listening for you." }
    }

    override fun onNetworkChanged(available: Boolean) {
        runOnUiThread { networkText = if (available) "Network: connected" else "Network: lost, reconnecting when available" }
    }

    private fun updateHardwareImageButtonRouting() {
        val shouldRegister = liveListening && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        if (shouldRegister && !hardwareImageButtonRegistered) {
            GeminiLiveImageButtonRouter.register(hardwareImageButtonHandler)
            hardwareImageButtonRegistered = true
        } else if (!shouldRegister) {
            unregisterHardwareImageButton()
        }
    }

    private fun unregisterHardwareImageButton() {
        if (!hardwareImageButtonRegistered) return
        GeminiLiveImageButtonRouter.unregister(hardwareImageButtonHandler)
        hardwareImageButtonRegistered = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeminiLivePreviewScreen(
    status: String,
    elapsed: String,
    network: String,
    indicators: String,
    active: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gemini Live preview") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(status, style = MaterialTheme.typography.headlineSmall)
            Text(elapsed, style = MaterialTheme.typography.bodyLarge)
            Text(network, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(indicators, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (active) {
                    OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                        Text("Stop")
                    }
                } else {
                    Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                        Text("Start Gemini Live")
                    }
                }
            }
        }
    }
}

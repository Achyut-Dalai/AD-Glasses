package com.achyut.adglasses.ai.live

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.achyut.adglasses.R
import com.achyut.adglasses.agent.AiPrefs
import com.achyut.adglasses.ai.vision.ImageQuestionPreferences
import com.achyut.adglasses.ai.vision.ImageQuestionPromptResolver
import com.achyut.adglasses.ai.vision.ImageQuestionRoute
import com.achyut.adglasses.ui.localization.AppLanguagePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Visible, activity-scoped Gemini Live preview. It never records after Stop or background pause. */
class GeminiLiveActivity : AppCompatActivity(), GeminiLiveClient.Listener {
    private lateinit var client: GeminiLiveClient
    private lateinit var status: TextView
    private lateinit var elapsed: TextView
    private lateinit var network: TextView
    private lateinit var indicators: TextView
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private var startedAtMs = 0L
    private var liveListening = false
    private var hardwareImageButtonRegistered = false
    private val hardwareImageCaptureInProgress = AtomicBoolean(false)
    private val hardwareImageButtonHandler: () -> Unit = { captureHardwareImageQuestion() }

    private val ticker = object : Runnable {
        override fun run() {
            if (startedAtMs > 0L) {
                val seconds = (System.currentTimeMillis() - startedAtMs) / 1_000L
                elapsed.text = "Session ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
                elapsed.postDelayed(this, 1_000L)
            }
        }
    }

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startLive() else Toast.makeText(this, "Microphone permission is required for Gemini Live", Toast.LENGTH_LONG).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gemini_live)
        status = findViewById(R.id.gemini_live_status)
        elapsed = findViewById(R.id.gemini_live_elapsed)
        network = findViewById(R.id.gemini_live_network)
        indicators = findViewById(R.id.gemini_live_indicators)
        startButton = findViewById(R.id.gemini_live_start)
        stopButton = findViewById(R.id.gemini_live_stop)
        client = GeminiLiveClient(this, this)

        startButton.setOnClickListener { explainAndRequestMicrophone() }
        stopButton.setOnClickListener { client.stop() }
        setControls(false)
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
        elapsed.removeCallbacks(ticker)
        unregisterHardwareImageButton()
        client.close()
        super.onDestroy()
    }

    private fun explainAndRequestMicrophone() {
        if (!hasPaidPlan()) {
            Toast.makeText(this, "Gemini Live requires an active paid Pro plan and network access.", Toast.LENGTH_LONG).show()
            return
        }
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
        ).forRoute(ImageQuestionRoute.PRO_RELAY)
        client.start(language, defaultImageQuestion)
    }

    private fun captureHardwareImageQuestion() {
        if (!hardwareImageCaptureInProgress.compareAndSet(false, true)) return
        status.text = "Receiving glasses AI photo"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { GeminiLiveGlassesImageCapture().captureFromHardwareButton() }
            }
            result
                .onSuccess { image ->
                    client.sendImage(image)
                    indicators.text = "Microphone: on   Glasses AI photo: thumbnail sent"
                    status.text = "Image sent to Gemini Live"
                }
                .onFailure { error ->
                    Toast.makeText(this@GeminiLiveActivity, error.message ?: "Glasses image capture failed", Toast.LENGTH_LONG).show()
                }
            hardwareImageCaptureInProgress.set(false)
        }
    }

    private fun hasPaidPlan(): Boolean {
        return true
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onStateChanged(state: GeminiLiveState, detail: String) {
        runOnUiThread {
            status.text = detail.ifBlank { state.name.lowercase().replaceFirstChar(Char::uppercase) }
            val listening = state == GeminiLiveState.LISTENING
            liveListening = listening
            updateHardwareImageButtonRouting()
            if (listening && startedAtMs == 0L) {
                startedAtMs = System.currentTimeMillis()
                elapsed.post(ticker)
            }
            if (state == GeminiLiveState.STOPPED || state == GeminiLiveState.ERROR) {
                startedAtMs = 0L
                elapsed.removeCallbacks(ticker)
                elapsed.text = "Session not running"
            }
            indicators.text = if (listening) "Gemini Live is listening   Microphone: on   Glasses AI photo: ready" else "Microphone: off   Glasses camera: off"
            setControls(listening || state == GeminiLiveState.CONNECTING || state == GeminiLiveState.RECONNECTING)
        }
    }

    override fun onInterrupted() {
        runOnUiThread { status.text = "Gemini was interrupted. Listening for you." }
    }

    override fun onNetworkChanged(available: Boolean) {
        runOnUiThread { network.text = if (available) "Network: connected" else "Network: lost, reconnecting when available" }
    }

    private fun setControls(active: Boolean) {
        startButton.visibility = if (active) View.GONE else View.VISIBLE
        stopButton.visibility = if (active) View.VISIBLE else View.GONE
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

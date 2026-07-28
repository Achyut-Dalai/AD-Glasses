package com.fersaiyan.cyanbridge.ai.live

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
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.agent.ProSubscriptionPrefs
import com.fersaiyan.cyanbridge.ai.vision.ImageQuestionPreferences
import com.fersaiyan.cyanbridge.ai.vision.ImageQuestionPromptResolver
import com.fersaiyan.cyanbridge.ai.vision.ImageQuestionRoute
import com.fersaiyan.cyanbridge.ui.localization.AppLanguagePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Visible, activity-scoped Gemini Live preview. It never records after Stop or background pause. */
class GeminiLiveActivity : AppCompatActivity(), GeminiLiveClient.Listener {
    private lateinit var client: GeminiLiveClient
    private lateinit var status: TextView
    private lateinit var elapsed: TextView
    private lateinit var network: TextView
    private lateinit var indicators: TextView
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var lookButton: MaterialButton
    private var startedAtMs = 0L

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
        lookButton = findViewById(R.id.gemini_live_look)
        client = GeminiLiveClient(this, this)

        startButton.setOnClickListener { explainAndRequestMicrophone() }
        stopButton.setOnClickListener { client.stop() }
        lookButton.setOnClickListener { captureLookAtThis() }
        setControls(false)
    }

    override fun onResume() {
        super.onResume()
        client.resumeAfterForeground()
    }

    override fun onPause() {
        client.pauseForBackground()
        super.onPause()
    }

    override fun onDestroy() {
        elapsed.removeCallbacks(ticker)
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
            .setMessage("Gemini Live listens through your microphone and sends live audio to Google. Look at this deliberately sends a glasses thumbnail at your configured image-question quality. This preview requires network access.")
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

    private fun captureLookAtThis() {
        val quality = ImageQuestionPreferences.thumbnailQuality(this)
        lookButton.isEnabled = false
        status.text = "Capturing ${quality.label} glasses thumbnail"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { GeminiLiveGlassesImageCapture().capture(quality) }
            }
            result
                .onSuccess { image ->
                    client.sendImage(image)
                    indicators.text = "Microphone: on   Glasses camera: ${quality.label} thumbnail sent"
                    status.text = "Image sent to Gemini Live"
                }
                .onFailure { error ->
                    Toast.makeText(this@GeminiLiveActivity, error.message ?: "Glasses image capture failed", Toast.LENGTH_LONG).show()
                }
            lookButton.isEnabled = true
        }
    }

    private fun hasPaidPlan(): Boolean {
        return ProSubscriptionPrefs.isActiveLocally(this) &&
            ProSubscriptionPrefs.getPlan(this).lowercase() in setOf("cheap", "standard", "max")
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onStateChanged(state: GeminiLiveState, detail: String) {
        runOnUiThread {
            status.text = detail.ifBlank { state.name.lowercase().replaceFirstChar(Char::uppercase) }
            val listening = state == GeminiLiveState.LISTENING
            if (listening && startedAtMs == 0L) {
                startedAtMs = System.currentTimeMillis()
                elapsed.post(ticker)
            }
            if (state == GeminiLiveState.STOPPED || state == GeminiLiveState.ERROR) {
                startedAtMs = 0L
                elapsed.removeCallbacks(ticker)
                elapsed.text = "Session not running"
            }
            indicators.text = if (listening) "Gemini Live is listening   Microphone: on   Glasses camera: ready" else "Microphone: off   Glasses camera: off"
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
        lookButton.isEnabled = active
        lookButton.alpha = if (active) 1f else 0.5f
    }
}

package com.fersaiyan.cyanbridge.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * System-role integration for AD Glasses.
 *
 * The glasses wake word remains the product's primary entry point. The Android Assistant role
 * gives AD a first-class, screen-off system integration point without handing requests to a
 * consumer assistant app.
 */
object ADAssistantRole {
    data class State(
        val available: Boolean,
        val held: Boolean,
    )

    fun state(context: Context): State {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return State(false, false)
        val roles = context.getSystemService(RoleManager::class.java) ?: return State(false, false)
        val available = roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
        return State(
            available = available,
            held = available && roles.isRoleHeld(RoleManager.ROLE_ASSISTANT),
        )
    }

    fun requestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roles = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) return null
        return roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
    }
}

/** Kept intentionally light; actual AI/audio work belongs to a bounded AD session. */
class ADVoiceInteractionService : VoiceInteractionService()

/** Creates the headless voice-interaction session owned by AD. */
class ADVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        ADVoiceInteractionSession(this)
}

/**
 * A displayless system-assistant session. The product does not show a generic assistant overlay;
 * audio, camera context and replies are routed by the glasses runtime.
 */
class ADVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    override fun onCreate() {
        super.onCreate()
        setUiEnabled(false)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        setUiEnabled(false)
        setKeepAwake(true)
        super.onShow(args, showFlags)
    }

    override fun onHide() {
        setKeepAwake(false)
        super.onHide()
    }
}

/**
 * Required recognition endpoint for the Assistant role metadata.
 *
 * Glasses audio does not enter through Android SpeechRecognizer, so this endpoint deliberately
 * refuses phone-microphone recognition for now instead of opening a second microphone pipeline.
 * A bounded AD recognizer can be connected here later without changing inference providers.
 */
class ADRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        listener.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onStopListening(listener: Callback) = Unit

    override fun onCancel(listener: Callback) = Unit
}

package com.fersaiyan.cyanbridge.media.autocapture

import android.content.Context

/**
 * Compile-time bridge for the inherited MainActivity host while automatic background audio
 * capture is retired. All reads are disabled and all writes are no-ops, so the removed feature
 * cannot be re-enabled by stale host code or persisted preferences.
 */
@Deprecated("Automatic background audio capture has been removed")
object AutoAudioCapturePrefs {
    fun isEnabled(context: Context): Boolean = false
    fun setEnabled(context: Context, enabled: Boolean) = Unit
    fun getSuccessfulLoops(context: Context): Int = 0
    fun incrementSuccessfulLoops(context: Context): Int = 0
    fun resetSuccessfulLoops(context: Context) = Unit
    fun getLoopsPerSync(context: Context): Int = 1
    fun setLoopsPerSync(context: Context, loops: Int) = Unit
    fun isVisualNotesEnabled(context: Context): Boolean = false
    fun setVisualNotesEnabled(context: Context, enabled: Boolean) = Unit
    fun isSpeechExtendEnabled(context: Context): Boolean = false
    fun setSpeechExtendEnabled(context: Context, enabled: Boolean) = Unit
    fun isPausedForMeeting(context: Context): Boolean = false
    fun setPausedForMeeting(context: Context, paused: Boolean) = Unit
    fun isPausedForVideo(context: Context): Boolean = false
    fun setPausedForVideo(context: Context, paused: Boolean) = Unit
    fun getPauseUntilMs(context: Context): Long = 0L
    fun pauseForMs(context: Context, durationMs: Long) = Unit
    fun clearPauseUntil(context: Context) = Unit
    fun shouldPauseNow(context: Context): Boolean = false
    fun getLastPauseReason(context: Context): String = ""
    fun setLastPauseReason(context: Context, reason: String) = Unit
    fun clearLastPauseReason(context: Context) = Unit
}

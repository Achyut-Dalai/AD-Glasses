package com.ad_glasses.ai.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.ad_glasses.R

/** Plays packaged assistant listening cues directly; no platform speech-engine earcon registration involved. */
object AssistantListeningCuePlayer {
    private val lock = Any()
    private var player: MediaPlayer? = null

    fun play(
        context: Context,
        phoneRoute: Boolean,
        onDone: (() -> Unit)? = null,
    ) {
        stop()
        val resourceId = if (phoneRoute) R.raw.ad_listening_cue_phone else R.raw.ad_listening_cue
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val created = runCatching {
            MediaPlayer.create(
                context.applicationContext,
                resourceId,
                attributes,
                0,
            )
        }.getOrNull()

        if (created == null) {
            onDone?.invoke()
            return
        }

        synchronized(lock) {
            player = created
        }
        created.setOnCompletionListener { completed ->
            releaseIfCurrent(completed)
            onDone?.invoke()
        }
        created.setOnErrorListener { failed, _, _ ->
            releaseIfCurrent(failed)
            onDone?.invoke()
            true
        }
        runCatching { created.start() }
            .onFailure {
                releaseIfCurrent(created)
                onDone?.invoke()
            }
    }

    fun stop() {
        val current = synchronized(lock) {
            player.also { player = null }
        } ?: return
        runCatching { current.stop() }
        runCatching { current.release() }
    }

    private fun releaseIfCurrent(candidate: MediaPlayer) {
        val shouldRelease = synchronized(lock) {
            if (player === candidate) {
                player = null
                true
            } else {
                false
            }
        }
        if (shouldRelease) {
            runCatching { candidate.release() }
        }
    }
}

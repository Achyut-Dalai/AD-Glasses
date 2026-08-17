package com.fersaiyan.cyanbridge.ui.reactnative

import android.media.MediaPlayer
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.io.File

/** Small native audio primitive so Library playback stays inside the React product shell. */
class ADMediaPlaybackModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    private var player: MediaPlayer? = null
    private var playingId: String? = null
    private var listenerCount = 0

    override fun getName(): String = "ADMediaPlayback"

    @ReactMethod
    fun addListener(eventName: String) {
        listenerCount += 1
    }

    @ReactMethod
    fun removeListeners(count: Double) {
        listenerCount = (listenerCount - count.toInt()).coerceAtLeast(0)
    }

    @ReactMethod
    fun toggle(id: String, path: String, promise: Promise) {
        if (playingId == id && player?.isPlaying == true) {
            stopInternal()
            promise.resolve(stateMap())
            return
        }

        val file = File(path)
        if (!file.isFile) {
            promise.reject("E_AUDIO_MISSING", "That recording is no longer available on this phone")
            return
        }

        stopInternal(emit = false)
        runCatching {
            MediaPlayer().also { next ->
                next.setDataSource(path)
                next.setOnCompletionListener {
                    stopInternal()
                }
                next.setOnErrorListener { _, _, _ ->
                    stopInternal()
                    true
                }
                next.prepare()
                next.start()
                player = next
                playingId = id
            }
        }.onSuccess {
            emitState()
            promise.resolve(stateMap())
        }.onFailure { error ->
            stopInternal()
            promise.reject("E_AUDIO_PLAYBACK", error.message ?: "Could not play that recording", error)
        }
    }

    @ReactMethod
    fun stop() {
        stopInternal()
    }

    @ReactMethod
    fun getState(promise: Promise) {
        promise.resolve(stateMap())
    }

    override fun invalidate() {
        stopInternal(emit = false)
        super.invalidate()
    }

    private fun stopInternal(emit: Boolean = true) {
        val current = player
        player = null
        playingId = null
        if (current != null) {
            runCatching { if (current.isPlaying) current.stop() }
            runCatching { current.reset() }
            runCatching { current.release() }
        }
        if (emit) emitState()
    }

    private fun stateMap() = Arguments.createMap().apply {
        val active = player?.isPlaying == true
        putBoolean("playing", active)
        if (active && playingId != null) putString("id", playingId)
    }

    private fun emitState() {
        if (listenerCount <= 0 || !reactContext.hasActiveReactInstance()) return
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(EVENT_STATE, stateMap())
    }

    private companion object {
        const val EVENT_STATE = "adPlaybackState"
    }
}

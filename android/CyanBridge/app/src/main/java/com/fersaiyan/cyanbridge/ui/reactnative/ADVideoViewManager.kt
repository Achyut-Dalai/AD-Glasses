package com.fersaiyan.cyanbridge.ui.reactnative

import android.content.Context
import android.net.Uri
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

/** Media3-backed video surface that stays inside the React Native Library experience. */
class ADVideoPlayerView(context: Context) : FrameLayout(context) {
    private val player = ExoPlayer.Builder(context).build()
    private val playerView = PlayerView(context).apply {
        useController = true
        this.player = this@ADVideoPlayerView.player
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }
    private var currentUri: String? = null

    init {
        addView(playerView)
    }

    fun setSource(raw: String?) {
        val source = raw?.trim().orEmpty()
        if (source == currentUri) return
        currentUri = source.takeIf { it.isNotBlank() }
        player.stop()
        player.clearMediaItems()
        if (source.isBlank()) return
        player.setMediaItem(MediaItem.fromUri(Uri.parse(source)))
        player.prepare()
    }

    fun setAutoPlay(enabled: Boolean) {
        player.playWhenReady = enabled
    }

    fun release() {
        playerView.player = null
        player.release()
    }
}

class ADVideoViewManager(
    @Suppress("UNUSED_PARAMETER") reactContext: ReactApplicationContext,
) : SimpleViewManager<ADVideoPlayerView>() {
    override fun getName(): String = "ADVideoView"

    override fun createViewInstance(reactContext: ThemedReactContext): ADVideoPlayerView =
        ADVideoPlayerView(reactContext)

    @ReactProp(name = "uri")
    fun setUri(view: ADVideoPlayerView, uri: String?) {
        view.setSource(uri)
    }

    @ReactProp(name = "autoPlay", defaultBoolean = false)
    fun setAutoPlay(view: ADVideoPlayerView, enabled: Boolean) {
        view.setAutoPlay(enabled)
    }

    override fun onDropViewInstance(view: ADVideoPlayerView) {
        view.release()
        super.onDropViewInstance(view)
    }
}

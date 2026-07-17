package com.fersaiyan.cyanbridge.ui.recordings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.media.SyncedMediaFolder
import com.fersaiyan.cyanbridge.shared.recordings.SyncedMediaItem
import com.fersaiyan.cyanbridge.shared.ui.recordings.SyncedMediaGalleryScreen
import com.fersaiyan.cyanbridge.ui.appearance.AppearancePreferences
import com.fersaiyan.cyanbridge.ui.appearance.rememberAppearanceSettings
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncedMediaGalleryActivity : AppCompatActivity() {

    private val uiScope = MainScope()
    private var loadJob: Job? = null
    private var mediaItems by mutableStateOf<List<SyncedMediaItem>>(emptyList())
    private var isLoading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appearancePreferences = AppearancePreferences(this)
        setContent {
            val appearance by rememberAppearanceSettings(appearancePreferences)
            CyanBridgeTheme(appearance) {
                SyncedMediaGalleryScreen(
                    mediaItems = mediaItems,
                    isLoading = isLoading,
                    folderHint = getString(R.string.synced_media_folder_hint, SyncedMediaFolder.relativePath),
                    loadThumbnail = { contentUriString -> loadThumbnail(contentUriString) },
                    onNavigateBack = ::finish,
                    onRefresh = ::loadSyncedMedia,
                    onOpenMedia = ::openMediaItem,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        loadSyncedMedia()
    }

    override fun onStop() {
        loadJob?.cancel()
        loadJob = null
        super.onStop()
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    private fun loadSyncedMedia() {
        loadJob?.cancel()
        isLoading = true
        loadJob = uiScope.launch {
            mediaItems = withContext(Dispatchers.IO) {
                SyncedMediaQuery.query(this@SyncedMediaGalleryActivity)
            }
            isLoading = false
        }
    }

    private suspend fun loadThumbnail(contentUriString: String): ImageBitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.loadThumbnail(
                    android.net.Uri.parse(contentUriString),
                    Size(420, 420),
                    null,
                )
            }.getOrNull()?.asImageBitmap()
        }
    }

    private fun openMediaItem(item: SyncedMediaItem) {
        val uri = android.net.Uri.parse(item.contentUriString)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (item.isVideo) "video/*" else "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, getString(R.string.synced_media_open_failed), Toast.LENGTH_SHORT).show()
            }
    }
}

package com.fersaiyan.cyanbridge.ui.recordings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.databinding.ActivitySyncedMediaGalleryBinding
import com.fersaiyan.cyanbridge.media.SyncedMediaFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncedMediaGalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySyncedMediaGalleryBinding
    private lateinit var adapter: SyncedMediaAdapter

    private val uiScope = MainScope()
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncedMediaGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.tvFolderHint.text = getString(
            R.string.synced_media_folder_hint,
            SyncedMediaFolder.relativePath
        )

        adapter = SyncedMediaAdapter(
            context = this,
            onItemClick = ::openMediaItem,
        )

        binding.recyclerSyncedMedia.layoutManager = GridLayoutManager(this, 4)
        binding.recyclerSyncedMedia.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        loadSyncedMedia()
    }

    override fun onStop() {
        super.onStop()
        loadJob?.cancel()
        loadJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
        adapter.release()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadSyncedMedia() {
        loadJob?.cancel()
        binding.progressLoading.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE

        loadJob = uiScope.launch {
            val mediaItems = withContext(Dispatchers.IO) {
                SyncedMediaQuery.query(this@SyncedMediaGalleryActivity)
            }

            adapter.submitList(mediaItems)
            binding.progressLoading.visibility = View.GONE
            binding.emptyState.visibility = if (mediaItems.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openMediaItem(item: SyncedMediaItem) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.contentUri, if (item.isVideo) "video/*" else "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, getString(R.string.synced_media_open_failed), Toast.LENGTH_SHORT)
                    .show()
            }
    }

}

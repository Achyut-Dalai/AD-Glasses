package com.fersaiyan.cyanbridge.ui.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fersaiyan.cyanbridge.shared.ui.notes.NoteDetailScreen
import com.fersaiyan.cyanbridge.ui.MyApplication
import com.fersaiyan.cyanbridge.ui.appearance.AppearancePreferences
import com.fersaiyan.cyanbridge.ui.appearance.rememberAppearanceSettings
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NoteDetailActivity : AppCompatActivity() {

    private val uiScope = MainScope()
    private var title by mutableStateOf("Note")
    private var summary by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appearancePreferences = AppearancePreferences(this)
        setContent {
            val appearance by rememberAppearanceSettings(appearancePreferences)
            CyanBridgeTheme(appearance) {
                NoteDetailScreen(
                    title = title,
                    summary = summary,
                    onCopy = { copyToClipboard(summary) },
                    onShare = { shareText(summary) },
                    onBack = ::finish,
                )
            }
        }

        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId <= 0L) {
            finish()
            return
        }

        uiScope.launch {
            val note = MyApplication.notesRepository.getNoteById(noteId)
            if (note == null) {
                Toast.makeText(this@NoteDetailActivity, "Note not found", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            title = note.title
            summary = note.summary
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
    }

    private fun copyToClipboard(text: String) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("note", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareText(text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(sendIntent, "Share note"))
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }
}

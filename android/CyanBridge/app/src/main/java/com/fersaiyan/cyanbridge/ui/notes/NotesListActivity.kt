package com.fersaiyan.cyanbridge.ui.notes

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fersaiyan.cyanbridge.data.local.entity.Note
import com.fersaiyan.cyanbridge.ui.MyApplication
import com.fersaiyan.cyanbridge.ui.appearance.AppearancePreferences
import com.fersaiyan.cyanbridge.ui.appearance.rememberAppearanceSettings
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class NotesListActivity : AppCompatActivity() {

    private var notes by mutableStateOf<List<Note>>(emptyList())
    private var showCreateDialog by mutableStateOf(false)

    private val uiScope = MainScope()
    private var notesJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appearancePreferences = AppearancePreferences(this)
        setContent {
            val appearance by rememberAppearanceSettings(appearancePreferences)
            CyanBridgeTheme(appearance) {
                NotesListScreen(
                    notes = notes,
                    showCreateDialog = showCreateDialog,
                    onOpenNote = { note ->
                        startActivity(Intent(this, NoteDetailActivity::class.java).apply {
                            putExtra(NoteDetailActivity.EXTRA_NOTE_ID, note.id)
                        })
                    },
                    onShowCreateDialog = { showCreateDialog = true },
                    onDismissCreateDialog = { showCreateDialog = false },
                    onCreateFromTranscript = ::createFromTranscript,
                    onBack = ::finish,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        notesJob?.cancel()
        notesJob = uiScope.launch {
            MyApplication.notesRepository.getAllNotes().collect { notes ->
                this@NotesListActivity.notes = notes
            }
        }
    }

    override fun onStop() {
        super.onStop()
        notesJob?.cancel()
        notesJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
    }

    private fun createFromTranscript(title: String, transcript: String) {
        val cleanTranscript = transcript.trim()
        if (cleanTranscript.isBlank()) {
            Toast.makeText(this, "Transcript is empty", Toast.LENGTH_SHORT).show()
            return
        }
        uiScope.launch {
            try {
                val id = MyApplication.notesRepository.createFromTranscript(
                    transcript = cleanTranscript,
                    hintTitle = title.trim().takeIf { it.isNotBlank() },
                    deviceClass = null,
                    durationSec = null,
                    tagsCsv = null,
                    storeTranscript = true,
                )
                showCreateDialog = false
                startActivity(Intent(this@NotesListActivity, NoteDetailActivity::class.java).apply {
                    putExtra(NoteDetailActivity.EXTRA_NOTE_ID, id)
                })
            } catch (t: Throwable) {
                Toast.makeText(this@NotesListActivity, "Failed to create note: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

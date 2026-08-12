package com.achyut.adglasses.notes

import com.achyut.adglasses.data.local.dao.NoteDao
import com.achyut.adglasses.data.local.entity.Note
import com.achyut.adglasses.shared.notes.SummarizationRequest
import com.achyut.adglasses.shared.notes.SummarizationService
import com.achyut.adglasses.shared.notes.SummaryMarkdownFormatter
import kotlinx.coroutines.flow.Flow

/**
 * Room-backed notes repository for Chapter 7 meeting notes.
 *
 * `createFromTranscript` is the main orchestration entrypoint:
 * transcript -> structured summary -> deterministic markdown -> persisted note.
 */
class RoomNotesRepository(
    private val noteDao: NoteDao,
    private val summarizationService: SummarizationService,
) : NotesRepository {

    override fun getAllNotes(): Flow<List<Note>> = noteDao.getAllNotes()

    override suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)

    override suspend fun createFromTranscript(
        transcript: String,
        hintTitle: String?,
        deviceClass: String?,
        durationSec: Long?,
        tagsCsv: String?,
        storeTranscript: Boolean,
    ): Long {
        val structured = summarizationService.summarize(
            SummarizationRequest(transcript = transcript, hintTitle = hintTitle)
        )
        // Keep output stable across summarizer implementations.
        val formatted = SummaryMarkdownFormatter.format(structured)
        val now = System.currentTimeMillis()

        val note = Note(
            title = structured.title,
            summary = formatted,
            transcript = if (storeTranscript) transcript else null,
            redactedTranscript = null,
            createdAt = now,
            updatedAt = now,
            durationSec = durationSec,
            deviceClass = deviceClass,
            tags = tagsCsv,
        )

        return noteDao.insertNote(note)
    }
}

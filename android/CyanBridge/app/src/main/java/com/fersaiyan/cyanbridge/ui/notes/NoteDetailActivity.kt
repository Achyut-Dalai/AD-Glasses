package com.fersaiyan.cyanbridge.ui.notes

/**
 * Compatibility component-name token for inherited note-detail intents.
 *
 * Note presentation now lives in the AD Glasses Compose library route. The
 * historical note-id extra is retained for callers that still build this intent.
 */
class NoteDetailActivity private constructor() {
    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }
}

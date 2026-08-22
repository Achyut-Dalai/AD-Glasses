package com.ad_glasses.notes

import android.content.Context
import com.ad_glasses.ui.MyApplication

/**
 * Minimal service locator so unit tests can override NotesRepository.
 */
object NotesServiceLocator {
    @Volatile
    var overrideRepository: NotesRepository? = null

    fun notesRepository(context: Context): NotesRepository {
        return overrideRepository ?: MyApplication.notesRepository
    }
}

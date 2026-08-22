package com.ad_glasses.ai.transcription.storage

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ad_glasses.data.local.AppDatabase
import com.ad_glasses.privacy.PrivacyPrefs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTranscriptStoreAndroidTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun maybePersist_respectsPrivacyToggle_andWritesToCaptureTranscripts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PrivacyPrefs.setTranscriptStorageEnabled(context, true)

        val store = RoomTranscriptStore(context = context, dao = db.captureTranscriptDao())
        val ok = store.maybePersist(
            captureSessionId = 1L,
            provider = "fake",
            language = "en",
            transcript = "hello",
        )

        assertTrue(ok)
        val row = db.captureTranscriptDao().getForSession(1L)
        assertNotNull(row)
        assertEquals("hello", row?.transcript)
        assertEquals("fake", row?.provider)
    }
}

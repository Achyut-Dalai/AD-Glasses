package com.fersaiyan.cyanbridge.ui.recordings

import android.net.Uri
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fersaiyan.cyanbridge.data.local.entity.CaptureSession
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecordingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersCaptureActionsAndRoutesCallbacks() {
        val session = CaptureSession(
            id = 7L,
            startedAt = 1_700_000_000_000L,
            endedAt = 1_700_000_060_000L,
            durationSec = 60L,
            deviceClass = "HEY_CYAN",
            captureSource = "PHONE_MIC",
            audioPath = "/tmp/recording.m4a",
            timerDurationSec = null,
            stopReason = null,
            error = null,
        )
        var playedSessionId: Long? = null
        var transcribedSessionId: Long? = null
        var openedMedia = 0

        composeRule.setContent {
            CyanBridgeTheme {
                RecordingsScreen(
                    sessions = listOf(session),
                    isLoading = false,
                    recentSyncedMedia = emptyList(),
                    playingSessionId = null,
                    transcribingSessionId = null,
                    meetingRecording = MeetingRecordingUiState(),
                    showEngineChooser = false,
                    selectedEngine = TranscriptionEngine.GEMMA,
                    transcriptionProgress = null,
                    transcriptDialog = null,
                    onOpenSyncedMedia = { openedMedia += 1 },
                    onOpenSyncedMediaItem = {},
                    onPlay = { playedSessionId = it.id },
                    onTranscribe = { transcribedSessionId = it.id },
                    onViewTranscript = {},
                    onStopMeetingCapture = {},
                    onEngineSelected = {},
                    onConfirmEngine = {},
                    onDismissEngineChooser = {},
                    onDismissTranscript = {},
                    onDestinationSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Meeting captures").assertTextContains("Meeting captures")
        composeRule.onNodeWithContentDescription("Play recording").performClick()
        composeRule.onNodeWithText("Transcribe").performClick()
        composeRule.onNodeWithText("Open synced media").performClick()

        composeRule.runOnIdle {
            assertEquals(7L, playedSessionId)
            assertEquals(7L, transcribedSessionId)
            assertEquals(1, openedMedia)
        }
    }

    @Test
    fun showsFourRecentPhotosInOneFullWidthRow() {
        val recentMedia = List(4) { index ->
            SyncedMediaItem(
                id = index.toLong(),
                contentUri = Uri.parse("content://cyanbridge/media/$index"),
                displayName = "photo_$index.jpg",
                mimeType = "image/jpeg",
                isVideo = false,
                takenAtMs = index.toLong(),
            )
        }
        var openedId: Long? = null

        composeRule.setContent {
            CyanBridgeTheme {
                RecordingsScreen(
                    sessions = emptyList(),
                    isLoading = false,
                    recentSyncedMedia = recentMedia,
                    playingSessionId = null,
                    transcribingSessionId = null,
                    meetingRecording = MeetingRecordingUiState(),
                    showEngineChooser = false,
                    selectedEngine = TranscriptionEngine.GEMMA,
                    transcriptionProgress = null,
                    transcriptDialog = null,
                    onOpenSyncedMedia = {},
                    onOpenSyncedMediaItem = { openedId = it.id },
                    onPlay = {},
                    onTranscribe = {},
                    onViewTranscript = {},
                    onStopMeetingCapture = {},
                    onEngineSelected = {},
                    onConfirmEngine = {},
                    onDismissEngineChooser = {},
                    onDismissTranscript = {},
                    onDestinationSelected = {},
                )
            }
        }

        composeRule.onNodeWithTag("recent_synced_media").assertExists()
        composeRule.onNodeWithTag("recent_synced_media_3").performClick()

        composeRule.runOnIdle {
            assertEquals(3L, openedId)
        }
    }
}

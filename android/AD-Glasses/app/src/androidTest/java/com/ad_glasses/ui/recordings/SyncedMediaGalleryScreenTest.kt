package com.ad_glasses.ui.recordings

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ad_glasses.shared.ui.recordings.SyncedMediaGalleryScreen
import com.ad_glasses.ui.theme.ADGlassesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SyncedMediaGalleryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsEmptyStateAndRefreshesThroughCallback() {
        var refreshCount = 0

        composeRule.setContent {
            ADGlassesTheme {
                SyncedMediaGalleryScreen(
                    mediaItems = emptyList(),
                    isLoading = false,
                    folderHint = "Saved in DCIM/AD-Glasses",
                    loadThumbnail = { null },
                    onNavigateBack = {},
                    onRefresh = { refreshCount += 1 },
                    onOpenMedia = {},
                    onShareItems = {},
                    onDeleteItems = {},
                )
            }
        }

        composeRule.onNodeWithText("No synced photos or videos yet.")
            .assertTextContains("No synced photos or videos yet.")
        composeRule.onNodeWithContentDescription("Refresh synced media").performClick()

        composeRule.runOnIdle {
            assertEquals(1, refreshCount)
        }
    }
}

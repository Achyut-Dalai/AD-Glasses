package com.achyut.adglasses.ui.recordings

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.achyut.adglasses.shared.ui.recordings.SyncedMediaGalleryScreen
import com.achyut.adglasses.ui.theme.AdGlassesTheme
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
            AdGlassesTheme {
                SyncedMediaGalleryScreen(
                    mediaItems = emptyList(),
                    isLoading = false,
                    folderHint = "Saved in DCIM/CyanBridge",
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

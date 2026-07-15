package com.fersaiyan.cyanbridge.ui.recordings

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
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
            CyanBridgeTheme {
                SyncedMediaGalleryScreen(
                    mediaItems = emptyList(),
                    isLoading = false,
                    folderHint = "Saved in DCIM/CyanBridge",
                    onNavigateBack = {},
                    onRefresh = { refreshCount += 1 },
                    onOpenMedia = {},
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

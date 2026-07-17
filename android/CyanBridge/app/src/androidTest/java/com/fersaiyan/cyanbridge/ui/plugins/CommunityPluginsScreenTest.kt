package com.fersaiyan.cyanbridge.ui.plugins

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.fersaiyan.cyanbridge.shared.plugins.CommunityPluginCardData
import com.fersaiyan.cyanbridge.shared.plugins.NativePluginCardData
import com.fersaiyan.cyanbridge.shared.plugins.PluginTimeWindow
import com.fersaiyan.cyanbridge.shared.ui.plugins.CommunityPluginsScreen
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CommunityPluginsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun filtersPluginsAndRoutesTaskerSetupActions() {
        var selectedWindow by mutableStateOf(PluginTimeWindow.ALL_TIME)
        var taskerStoreActions = 0
        var publishActions = 0
        val plugin = CommunityPluginCardData(
            title = "Meeting Spark Notes",
            author = "cyanlabs",
            description = "Builds concise action summaries.",
            badge = "Productivity",
            downloadsAll = 100,
            downloadsMonthly = 20,
            downloadsWeekly = 5,
            votesAll = 50,
            votesMonthly = 10,
            votesWeekly = 2,
            trendAll = 70,
            trendMonthly = 80,
            trendWeekly = 90,
        )

        composeRule.setContent {
            CyanBridgeTheme {
                CommunityPluginsScreen(
                    plugins = listOf(plugin),
                    selectedWindow = selectedWindow,
                    imageAutomationEnabled = false,
                    showImageAutomationBanner = true,
                    isRefreshing = false,
                    onWindowSelected = { selectedWindow = it },
                    onRefresh = {},
                    onDismissImageAutomationBanner = {},
                    onOpenTaskerStore = { taskerStoreActions += 1 },
                    onOpenTaskerNet = {},
                    onPublishPlugin = { publishActions += 1 },
                    onDestinationSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Community Plugins").assertExists()
        composeRule.onNodeWithTag("image_automation_banner").assertExists()
        composeRule.onNodeWithContentDescription("Publish plugin").performClick()
        composeRule.onNodeWithText("Weekly").performClick()
        composeRule.onNodeWithText("Download plugin").performClick()
        composeRule.onNodeWithText("Get Tasker").performClick()
        composeRule.onNodeWithTag("community_plugins_list")
            .performScrollToNode(hasText("1. Meeting Spark Notes"))
        composeRule.onNodeWithText("1. Meeting Spark Notes").assertExists()

        composeRule.runOnIdle {
            assertEquals(PluginTimeWindow.WEEKLY, selectedWindow)
            assertEquals(1, taskerStoreActions)
            assertEquals(1, publishActions)
        }
    }

    @Test
    fun nativePluginCardsRenderWithSettingsButton() {
        var settingsOpened = false
        var toggledValue: Boolean? = null
        val nativePlugin = NativePluginCardData(
            id = "walking_aid",
            title = "Walking Aid",
            description = "Scene description for navigation.",
            badge = "Accessibility",
            enabled = false,
            hasSettings = true,
        )
        composeRule.setContent {
            CyanBridgeTheme {
                CommunityPluginsScreen(
                    plugins = emptyList(),
                    selectedWindow = PluginTimeWindow.ALL_TIME,
                    imageAutomationEnabled = false,
                    showImageAutomationBanner = false,
                    isRefreshing = false,
                    nativePlugins = listOf(nativePlugin),
                    onOpenNativePluginSettings = { settingsOpened = true },
                    onToggleNativePlugin = { _, enabled -> toggledValue = enabled },
                    onWindowSelected = {},
                    onRefresh = {},
                    onDismissImageAutomationBanner = {},
                    onOpenTaskerStore = {},
                    onOpenTaskerNet = {},
                    onPublishPlugin = {},
                    onDestinationSelected = {},
                )
            }
        }
        composeRule.onNodeWithTag("native_plugin_card_walking_aid").assertExists()
        composeRule.onNodeWithText("Walking Aid").assertExists()
        composeRule.runOnIdle {
            assertEquals(false, settingsOpened)
        }
    }
}

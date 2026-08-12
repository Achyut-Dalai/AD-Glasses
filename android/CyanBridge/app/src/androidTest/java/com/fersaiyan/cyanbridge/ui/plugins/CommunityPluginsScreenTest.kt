package com.achyut.adglasses.ui.plugins

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.achyut.adglasses.shared.plugins.CommunityPluginCardData
import com.achyut.adglasses.shared.plugins.NativePluginCardData
import com.achyut.adglasses.shared.plugins.PluginTimeWindow
import com.achyut.adglasses.shared.ui.plugins.CommunityPluginsScreen
import com.achyut.adglasses.ui.theme.CyanBridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CommunityPluginsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersServerTaskerPluginAndRoutesItsInstallAction() {
        var selectedWindow by mutableStateOf(PluginTimeWindow.ALL_TIME)
        var taskerActions = 0
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
            taskerNetLink = "https://taskernet.com/example",
        )

        composeRule.setContent {
            CyanBridgeTheme {
                CommunityPluginsScreen(
                    plugins = listOf(plugin),
                    selectedWindow = selectedWindow,
                    isRefreshing = false,
                    onWindowSelected = { selectedWindow = it },
                    onRefresh = {},
                    onOpenCommunityPlugin = { taskerActions += 1 },
                    onPublishPlugin = { publishActions += 1 },
                    onDestinationSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Community Plugins").assertExists()
        composeRule.onAllNodesWithText("Image Questions Automation").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Publish plugin").performClick()
        composeRule.onNodeWithText("Weekly").performClick()
        composeRule.onAllNodesWithText("Open in Tasker")[0].performClick()
        composeRule.onNodeWithTag("community_plugins_list")
            .performScrollToNode(hasText("1. Meeting Spark Notes"))
        composeRule.onAllNodesWithText("1. Meeting Spark Notes")[0].assertExists()

        composeRule.runOnIdle {
            assertEquals(PluginTimeWindow.WEEKLY, selectedWindow)
            assertEquals(1, taskerActions)
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
                    isRefreshing = false,
                    nativePlugins = listOf(nativePlugin),
                    onOpenNativePluginSettings = { settingsOpened = true },
                    onToggleNativePlugin = { _, enabled -> toggledValue = enabled },
                    onWindowSelected = {},
                    onRefresh = {},
                    onPublishPlugin = {},
                    onDestinationSelected = {},
                )
            }
        }
        composeRule.onNodeWithTag("native_plugin_card_walking_aid").assertExists()
        composeRule.onNodeWithText("Walking Aid").assertExists()
        composeRule.onNodeWithContentDescription("Walking Aid settings").performClick()
        composeRule.runOnIdle {
            assertEquals(true, settingsOpened)
        }
    }

    @Test
    fun downloadUrlOnlyPluginHasAnAction() {
        var opened = false
        val plugin = CommunityPluginCardData(
            title = "Downloaded Workflow",
            author = "cyanlabs",
            description = "A plugin distributed as a direct download.",
            badge = "Other",
            downloadsAll = 1,
            downloadsMonthly = 1,
            downloadsWeekly = 1,
            votesAll = 1,
            votesMonthly = 1,
            votesWeekly = 1,
            trendAll = 1,
            trendMonthly = 1,
            trendWeekly = 1,
            downloadUrl = "https://example.com/plugin.apk",
        )

        composeRule.setContent {
            CyanBridgeTheme {
                CommunityPluginsScreen(
                    plugins = listOf(plugin),
                    selectedWindow = PluginTimeWindow.ALL_TIME,
                    isRefreshing = false,
                    onWindowSelected = {},
                    onRefresh = {},
                    onOpenCommunityPlugin = { opened = true },
                    onPublishPlugin = {},
                    onDestinationSelected = {},
                )
            }
        }

        composeRule.onAllNodesWithText("Download plugin")[0].performClick()
        composeRule.runOnIdle {
            assertEquals(true, opened)
        }
    }
}

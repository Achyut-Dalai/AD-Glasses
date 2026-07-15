package com.fersaiyan.cyanbridge.shared.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PluginsPresentationTest {
    @Test
    fun pluginMetricsFollowTheSelectedTimeWindow() {
        val plugin = CommunityPluginCardData(
            title = "Meeting helper",
            author = "cyanlabs",
            description = "Creates concise meeting follow-ups.",
            badge = "Productivity",
            downloadsAll = 100,
            downloadsMonthly = 20,
            downloadsWeekly = 5,
            votesAll = 30,
            votesMonthly = 8,
            votesWeekly = 2,
            trendAll = 70,
            trendMonthly = 80,
            trendWeekly = 90,
        )

        assertEquals(100, plugin.downloads(PluginTimeWindow.ALL_TIME))
        assertEquals(8, plugin.votes(PluginTimeWindow.MONTHLY))
        assertEquals(90, plugin.trend(PluginTimeWindow.WEEKLY))
    }

    @Test
    fun publishFormStartsEmptyAndNotSubmitting() {
        val state = PublishPluginUiState()

        assertEquals("", state.title)
        assertEquals("", state.taskerNetLink)
        assertFalse(state.isSubmitting)
        assertEquals("Other", CommunityPluginCatalog.categories.last())
    }
}

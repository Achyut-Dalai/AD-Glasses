package com.fersaiyan.cyanbridge.shared.navigation

import com.fersaiyan.cyanbridge.shared.icons.AppIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NavigationModelsTest {

    @Test
    fun allDestinationsHaveLabels() {
        AppDestination.entries.forEach { destination ->
            assertNotNull(destination.label)
            assertTrue(destination.label.isNotBlank())
        }
    }

    @Test
    fun allDestinationsHaveIcons() {
        AppDestination.entries.forEach { destination ->
            assertNotNull(destination.icon)
        }
    }

    @Test
    fun labelsMatchExpectedValues() {
        assertEquals("Glasses", AppDestination.GLASSES.label)
        assertEquals("Chats", AppDestination.CHATS.label)
        assertEquals("Media", AppDestination.MEDIA.label)
        assertEquals("Plugins", AppDestination.PLUGINS.label)
        assertEquals("Settings", AppDestination.SETTINGS.label)
    }

    @Test
    fun iconsMatchExpectedValues() {
        assertEquals(AppIcon.Glasses, AppDestination.GLASSES.icon)
        assertEquals(AppIcon.Chat, AppDestination.CHATS.icon)
        assertEquals(AppIcon.Recordings, AppDestination.MEDIA.icon)
        assertEquals(AppIcon.Plugins, AppDestination.PLUGINS.icon)
        assertEquals(AppIcon.Settings, AppDestination.SETTINGS.icon)
    }

    @Test
    fun exactlyFiveDestinations() {
        assertEquals(5, AppDestination.entries.size)
    }

    private fun assertTrue(condition: Boolean) {
        kotlin.test.assertTrue(condition)
    }
}

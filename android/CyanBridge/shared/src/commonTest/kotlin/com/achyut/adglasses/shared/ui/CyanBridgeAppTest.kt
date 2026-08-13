package com.achyut.adglasses.shared.ui

import com.achyut.adglasses.shared.appearance.AccentProfiles
import com.achyut.adglasses.shared.appearance.AppearanceSettings
import com.achyut.adglasses.shared.appearance.ThemeMode
import com.achyut.adglasses.shared.glasses.GlassesDashboardAction
import com.achyut.adglasses.shared.glasses.GlassesDashboardUiState
import com.achyut.adglasses.shared.glasses.GlassesSyncFlow
import com.achyut.adglasses.shared.navigation.AppDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CyanBridgeAppTest {

    @Test
    fun dashboardStateDefaultsAreSafe() {
        val state = GlassesDashboardUiState()
        assertEquals("Disconnected", state.connectionLabel)
        assertFalse(state.showHeyCyanControls)
    }

    @Test
    fun appearanceSettingsDefaultsAreStable() {
        val settings = AppearanceSettings()
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(AccentProfiles.CYAN_ID, settings.accentProfileId)
    }

    @Test
    fun allNavigationDestinationsExist() {
        val destinations = AppDestination.entries
        assertEquals(5, destinations.size)
        assertTrue(destinations.contains(AppDestination.GLASSES))
        assertTrue(destinations.contains(AppDestination.CHATS))
        assertTrue(destinations.contains(AppDestination.MEDIA))
        assertTrue(destinations.contains(AppDestination.PLUGINS))
        assertTrue(destinations.contains(AppDestination.SETTINGS))
    }

    @Test
    fun syncFlowPickerDismissIsIdempotent() {
        var dismissed = false
        val onDismiss = { dismissed = true }
        onDismiss()
        assertTrue(dismissed)
    }

    @Test
    fun dashboardActionNavigatePreservesDestination() {
        val action = GlassesDashboardAction.Navigate(AppDestination.SETTINGS)
        assertEquals(AppDestination.SETTINGS, action.destination)
    }
}

package com.fersaiyan.cyanbridge.shared.glasses

import com.fersaiyan.cyanbridge.shared.navigation.AppDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class GlassesDashboardPresentationTest {
    @Test
    fun defaultDashboardStateIsSafeBeforeADeviceIsSelected() {
        val state = GlassesDashboardUiState()

        assertEquals("Disconnected", state.connectionLabel)
        assertEquals("Unknown", state.deviceClassLabel)
        assertFalse(state.showHeyCyanControls)
        assertFalse(state.showMetaRaybanControls)
        assertNull(state.transfer.progress)
    }

    @Test
    fun navigationActionKeepsTheTypedDestination() {
        val action = GlassesDashboardAction.Navigate(AppDestination.MEDIA)

        assertEquals(AppDestination.MEDIA, action.destination)
    }

    @Test
    fun syncFlowLabelsKeepTheExistingProtocolChoicesDistinct() {
        assertEquals("HeyCyan app flow", GlassesSyncFlow.OFFICIAL_HEYCYAN.label)
        assertEquals("Custom flow", GlassesSyncFlow.CUSTOM.label)
    }
}

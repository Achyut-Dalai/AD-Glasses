package com.ad_glasses.shared.ui.glasses

import com.ad_glasses.shared.glasses.GlassesDashboardAction
import com.ad_glasses.shared.glasses.GlassesDashboardUiState
import com.ad_glasses.shared.glasses.GlassesSyncFlow
import com.ad_glasses.shared.glasses.GlassesTransferUiState
import com.ad_glasses.shared.glasses.MetaRaybanUiState
import com.ad_glasses.shared.glasses.OtaSectionUiState
import com.ad_glasses.shared.navigation.AppDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class GlassesDashboardScreenTest {

    @Test
    fun defaultStateShowsDisconnected() {
        val state = GlassesDashboardUiState()
        assertEquals("Disconnected", state.connectionLabel)
        assertEquals("Unknown", state.deviceClassLabel)
        assertFalse(state.showHeyCyanControls)
        assertFalse(state.showMetaRaybanControls)
        assertNull(state.transfer.progress)
    }

    @Test
    fun connectedStatePreservesLabels() {
        val state = GlassesDashboardUiState(
            connectionLabel = "Connected: AD Glasses V2",
            deviceClassLabel = "HeyCyan Smart Glasses",
        )
        assertEquals("Connected: AD Glasses V2", state.connectionLabel)
        assertEquals("HeyCyan Smart Glasses", state.deviceClassLabel)
    }

    @Test
    fun transferProgressReflectsSyncState() {
        val state = GlassesDashboardUiState(
            transfer = GlassesTransferUiState(progress = 0.42f),
        )
        assertEquals(0.42f, state.transfer.progress)
    }

    @Test
    fun otaStateReflectsIdleByDefault() {
        val state = GlassesDashboardUiState()
        assertNull(state.ota.progress)
    }

    @Test
    fun navigateActionPreservesTypedDestination() {
        val action = GlassesDashboardAction.Navigate(AppDestination.MEDIA)
        assertEquals(AppDestination.MEDIA, action.destination)
    }

    @Test
    fun syncFlowLabelsDistinguishProtocols() {
        assertEquals("HeyCyan app flow", GlassesSyncFlow.OFFICIAL_HEYCYAN.label)
        assertEquals("Custom flow", GlassesSyncFlow.CUSTOM.label)
    }
}

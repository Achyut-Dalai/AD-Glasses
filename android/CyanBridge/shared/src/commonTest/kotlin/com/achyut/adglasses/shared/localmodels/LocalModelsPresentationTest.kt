package com.achyut.adglasses.shared.localmodels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LocalModelsPresentationTest {
    @Test
    fun catalogActionRetainsThePlatformNeutralModelId() {
        val action = LocalModelsAction.DownloadCatalogModel("qwen2.5-0.5b-instruct-q4")

        assertEquals("qwen2.5-0.5b-instruct-q4", action.id)
    }

    @Test
    fun localModelsStateKeepsOptionalSectionsCollapsedByDefault() {
        val state = LocalModelsConfigureUiState()

        assertFalse(state.catalogExpanded)
        assertFalse(state.remoteServerExpanded)
        assertFalse(state.studioBridgeExpanded)
        assertFalse(state.generationSettingsExpanded)
    }
}

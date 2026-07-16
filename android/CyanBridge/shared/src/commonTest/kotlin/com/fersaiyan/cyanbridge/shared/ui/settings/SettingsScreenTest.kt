package com.fersaiyan.cyanbridge.shared.ui.settings

import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType
import com.fersaiyan.cyanbridge.shared.settings.CaptureSource
import com.fersaiyan.cyanbridge.shared.settings.MemoryPrivacyMode
import com.fersaiyan.cyanbridge.shared.settings.MemorySourceType
import com.fersaiyan.cyanbridge.shared.settings.SettingsSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsScreenTest {

    @Test
    fun defaultUiStateUsesPrivacyFirstDefaults() {
        val state = SettingsUiState()
        assertEquals(false, state.isProSubscribed)
        assertEquals(AgentProviderType.PRO_SUBSCRIPTION, state.providerType)
        assertEquals(false, state.localAgentAutomationEnabled)
        assertEquals(true, state.localAgentRequireConfirmation)
        assertEquals(MemoryPrivacyMode.PRIVATE_LOCAL, state.memoryMode)
        assertEquals(true, state.redactNamesEnabled)
        assertEquals(false, state.transcriptStorageEnabled)
    }

    @Test
    fun allSettingsSectionsExist() {
        val sections = SettingsSection.entries
        assertTrue(sections.contains(SettingsSection.AI_AUTOMATION))
        assertTrue(sections.contains(SettingsSection.LOCAL_AGENT))
        assertTrue(sections.contains(SettingsSection.MEMORY_PRIVACY))
        assertTrue(sections.contains(SettingsSection.TRANSCRIPTS))
        assertTrue(sections.contains(SettingsSection.DATA))
        assertTrue(sections.contains(SettingsSection.AGENT))
        assertTrue(sections.contains(SettingsSection.SUPPORT))
        assertTrue(sections.contains(SettingsSection.FAQ))
    }

    @Test
    fun agentProviderTypesCoverAllOptions() {
        val types = AgentProviderType.entries
        assertTrue(types.contains(AgentProviderType.PRO_SUBSCRIPTION))
        assertTrue(types.contains(AgentProviderType.LOCAL_MODEL))
        assertTrue(types.contains(AgentProviderType.REMOTE_SERVER))
        assertTrue(types.contains(AgentProviderType.STUDIO_BRIDGE))
    }

    @Test
    fun captureSourceIncludesAllOptions() {
        val sources = CaptureSource.entries
        assertTrue(sources.contains(CaptureSource.BLUETOOTH_MIC))
        assertTrue(sources.contains(CaptureSource.PHONE_MIC))
    }

    @Test
    fun memoryPrivacyModeCoversSpectrum() {
        val modes = MemoryPrivacyMode.entries
        assertTrue(modes.contains(MemoryPrivacyMode.PRIVATE_LOCAL))
        assertTrue(modes.contains(MemoryPrivacyMode.CLOUD_SYNC))
    }

    @Test
    fun memorySourceTypeCoversAllInputs() {
        val types = MemorySourceType.entries
        assertTrue(types.contains(MemorySourceType.EXPLICIT))
        assertTrue(types.contains(MemorySourceType.DAILY))
        assertTrue(types.contains(MemorySourceType.OCR))
        assertTrue(types.contains(MemorySourceType.DERIVED))
    }

    @Test
    fun stateCopyPreservesUnrelatedFields() {
        val original = SettingsUiState(
            isProSubscribed = true,
            proPlan = "Max",
            localAgentMaxSteps = 12,
        )
        val updated = original.copy(memoryMode = MemoryPrivacyMode.CLOUD_SYNC)
        assertEquals(true, updated.isProSubscribed)
        assertEquals("Max", updated.proPlan)
        assertEquals(12, updated.localAgentMaxSteps)
        assertEquals(MemoryPrivacyMode.CLOUD_SYNC, updated.memoryMode)
    }
}

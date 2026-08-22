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
        assertEquals(false, state.cloudConfigured)
        assertEquals(MemoryPrivacyMode.PRIVATE_LOCAL, state.memoryMode)
        assertEquals(true, state.redactNamesEnabled)
        assertEquals(false, state.transcriptStorageEnabled)
    }

    @Test
    fun allSettingsSectionsExist() {
        val sections = SettingsSection.entries
        assertTrue(sections.contains(SettingsSection.AI_AUTOMATION))
        assertTrue(sections.contains(SettingsSection.MEMORY_PRIVACY))
        assertTrue(sections.contains(SettingsSection.TRANSCRIPTS))
        assertTrue(sections.contains(SettingsSection.DATA))
        assertTrue(sections.contains(SettingsSection.SUPPORT))
        assertTrue(sections.contains(SettingsSection.FAQ))
    }

    @Test
    fun agentProviderTypesCoverAllOptions() {
        val types = AgentProviderType.entries
        assertTrue(types.contains(AgentProviderType.CLOUD_AI))
        assertTrue(types.contains(AgentProviderType.LOCAL_AGENT))
        assertEquals(2, types.size)
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
        assertTrue(modes.contains(MemoryPrivacyMode.ENCRYPTED_SYNC))
        assertTrue(modes.contains(MemoryPrivacyMode.FAST_CLOUD_MEMORY))
        assertTrue(modes.contains(MemoryPrivacyMode.CONFIDENTIAL_CLOUD_BETA))
    }

    @Test
    fun memorySourceTypeCoversAllInputs() {
        val types = MemorySourceType.entries
        assertTrue(types.contains(MemorySourceType.EXPLICIT_USER_FACT))
        assertTrue(types.contains(MemorySourceType.AUTO_DAILY_FACT))
        assertTrue(types.contains(MemorySourceType.SCREEN_OCR))
        assertTrue(types.contains(MemorySourceType.DERIVED_SUMMARY))
        assertTrue(types.contains(MemorySourceType.IMPORTED_TEXT))
        assertTrue(types.contains(MemorySourceType.SYSTEM_NOTE))
    }

    @Test
    fun stateCopyPreservesUnrelatedFields() {
        val original = SettingsUiState(
            cloudConfigured = true,
            cloudSummary = "https://relay.example",
            ocrRetentionDays = 12,
        )
        val updated = original.copy(memoryMode = MemoryPrivacyMode.ENCRYPTED_SYNC)
        assertEquals(true, updated.cloudConfigured)
        assertEquals("https://relay.example", updated.cloudSummary)
        assertEquals(12, updated.ocrRetentionDays)
        assertEquals(MemoryPrivacyMode.ENCRYPTED_SYNC, updated.memoryMode)
    }
}

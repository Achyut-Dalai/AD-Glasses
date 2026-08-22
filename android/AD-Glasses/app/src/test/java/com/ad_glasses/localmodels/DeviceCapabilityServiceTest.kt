package com.ad_glasses.localmodels

import com.ad_glasses.localmodels.catalog.LocalModelCatalogRepository
import com.ad_glasses.localmodels.device.DeviceCapabilityService
import com.ad_glasses.localmodels.device.DeviceSnapshot
import com.ad_glasses.localmodels.settings.LocalModelPerformanceProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCapabilityServiceTest {
    @Test
    fun unsupported_abi_is_blocked() {
        val entry = LocalModelCatalogRepository.findById("qwen2.5-0.5b-instruct-q4")!!
        val snapshot = DeviceSnapshot(
            primaryAbi = "armeabi-v7a",
            supportedAbis = listOf("armeabi-v7a"),
            totalRamBytes = 8L * 1024L * 1024L * 1024L,
            freeStorageBytes = 8L * 1024L * 1024L * 1024L,
            cpuCoreCount = 8,
        )

        val result = DeviceCapabilityService.assess(snapshot, entry, requireDownloadHeadroom = true)
        assertFalse(result.supported)
        assertTrue(result.blockers.isNotEmpty())
    }

    @Test
    fun low_storage_is_blocked_for_download() {
        val entry = LocalModelCatalogRepository.findById("qwen2.5-1.5b-instruct-q4")!!
        val snapshot = DeviceSnapshot(
            primaryAbi = "arm64-v8a",
            supportedAbis = listOf("arm64-v8a"),
            totalRamBytes = 12L * 1024L * 1024L * 1024L,
            freeStorageBytes = 400L * 1024L * 1024L,
            cpuCoreCount = 8,
        )

        val result = DeviceCapabilityService.assess(snapshot, entry, requireDownloadHeadroom = true)
        assertFalse(result.supported)
    }

    @Test
    fun insufficient_ram_is_blocked_before_download() {
        val entry = LocalModelCatalogRepository.findById("qwen2.5-1.5b-instruct-q4")!!
        val snapshot = DeviceSnapshot(
            primaryAbi = "arm64-v8a",
            supportedAbis = listOf("arm64-v8a"),
            totalRamBytes = 6L * 1024L * 1024L * 1024L,
            freeStorageBytes = 8L * 1024L * 1024L * 1024L,
            cpuCoreCount = 8,
        )

        val result = DeviceCapabilityService.assess(snapshot, entry, requireDownloadHeadroom = true)
        assertFalse(result.supported)
        assertFalse(result.ramSuitable)
        assertTrue(result.blockers.any { it.contains("RAM unsuitable") })
    }

    @Test
    fun profile_recommendation_prefers_fast_on_small_ram() {
        val entry = LocalModelCatalogRepository.findById("qwen2.5-1.5b-instruct-q4")!!
        val snapshot = DeviceSnapshot(
            primaryAbi = "arm64-v8a",
            supportedAbis = listOf("arm64-v8a"),
            totalRamBytes = 4L * 1024L * 1024L * 1024L,
            freeStorageBytes = 8L * 1024L * 1024L * 1024L,
            cpuCoreCount = 6,
        )

        val profile = DeviceCapabilityService.recommendProfile(snapshot, entry)
        assertTrue(profile == LocalModelPerformanceProfile.FAST)
    }

    @Test
    fun qwen_half_billion_is_allowed_on_three_gib_phone() {
        val entry = LocalModelCatalogRepository.findById("qwen2.5-0.5b-instruct-q4")!!
        val snapshot = DeviceSnapshot(
            primaryAbi = "arm64-v8a",
            supportedAbis = listOf("arm64-v8a"),
            totalRamBytes = 3L * 1024L * 1024L * 1024L,
            freeStorageBytes = 4L * 1024L * 1024L * 1024L,
            cpuCoreCount = 8,
        )

        assertTrue(DeviceCapabilityService.assess(snapshot, entry, requireDownloadHeadroom = true).supported)
        assertEquals(3.0, DeviceCapabilityService.recommendedMinRamGbForModelBytes(420_000_000L), 0.0)
    }
}

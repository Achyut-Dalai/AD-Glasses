package com.fersaiyan.cyanbridge.localmodels.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fersaiyan.cyanbridge.localmodels.catalog.LocalModelCatalogRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalModelSettingsRepositoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPreferences()
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun firstQwenInstallPersistsFastDeviceRecommendation() {
        val entry = LocalModelCatalogRepository.findById("qwen2.5-0.5b-instruct-q4")!!

        val settings = LocalModelSettingsRepository.initializeCatalogDefaultsIfMissing(
            context = context,
            entry = entry,
            profile = LocalModelPerformanceProfile.FAST,
        )

        assertEquals(LocalModelPerformanceProfile.FAST, settings.profile)
        assertEquals(512, settings.maxTokens)
        assertEquals(2048, settings.contextSize)
        assertEquals(
            LocalModelPerformanceProfile.FAST,
            LocalModelSettingsRepository.getForModel(context, entry.id).profile,
        )
    }

    @Test
    fun deviceRecommendationDoesNotOverwriteUserSettings() {
        val entry = LocalModelCatalogRepository.findById("qwen2.5-0.5b-instruct-q4")!!
        val custom = LocalGenerationSettings.defaultsFor(
            entry,
            LocalModelPerformanceProfile.BALANCED,
        ).copy(maxTokens = 333)
        LocalModelSettingsRepository.saveForModel(context, entry.id, custom)

        val resolved = LocalModelSettingsRepository.initializeCatalogDefaultsIfMissing(
            context = context,
            entry = entry,
            profile = LocalModelPerformanceProfile.FAST,
        )

        assertEquals(LocalModelPerformanceProfile.BALANCED, resolved.profile)
        assertEquals(333, resolved.maxTokens)
    }

    private fun clearPreferences() {
        context.getSharedPreferences("local_model_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}

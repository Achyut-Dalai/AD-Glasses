package com.ad_glasses.localmodels

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.ad_glasses.localmodels.settings.LocalGenerationSettings
import com.ad_glasses.localmodels.settings.LocalModelPerformanceProfile
import com.ad_glasses.localmodels.settings.LocalModelRuntime
import com.ad_glasses.localmodels.settings.LocalModelSettingsRepository
import com.ad_glasses.localmodels.storage.InstalledLocalModel
import com.ad_glasses.localmodels.storage.LocalModelFileFormat
import com.ad_glasses.localmodels.storage.LocalModelStorageRepository
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalModelStorageRepositoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearState()
        LocalModelStorageRepository.ensureDirs(context)
        ShadowStatFs.registerStats(
            LocalModelStorageRepository.baseDir(context),
            1_000_000,
            1_000_000,
            1_000_000,
        )
    }

    @After
    fun tearDown() {
        ShadowStatFs.reset()
        clearState()
    }

    @Test
    fun central_import_records_display_name_format_and_selection() {
        val source = File(context.cacheDir, "My Qwen.gguf")
        source.writeBytes(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte(), 1))

        val imported = LocalModelStorageRepository.importModelFromUri(context, Uri.fromFile(source))

        assertEquals("My Qwen", imported.displayName)
        assertEquals(LocalModelFileFormat.GGUF, imported.format)
        assertEquals(imported.id, LocalModelStorageRepository.getSelectedModelId(context))
        assertTrue(File(imported.absolutePath).isFile)
        assertEquals(LocalModelFileFormat.GGUF, LocalModelStorageRepository.getInstalled(context, imported.id)?.format)
    }

    @Test
    fun invalid_import_cleans_partial_and_final_files() {
        val source = File(context.cacheDir, "fake.gguf")
        source.writeText("not a model")

        val error = runCatching {
            LocalModelStorageRepository.importModelFromUri(context, Uri.fromFile(source))
        }.exceptionOrNull()

        assertTrue(error?.message?.contains("valid GGUF") == true)
        assertTrue(LocalModelStorageRepository.listInstalled(context).isEmpty())
        val managedFiles = LocalModelStorageRepository.modelsDir(context).listFiles().orEmpty()
        assertFalse(managedFiles.any { it.name.endsWith(".part") })
        assertTrue(managedFiles.isEmpty())
    }

    @Test
    fun cleanup_removes_invalid_legacy_registry_entry_and_app_owned_file() {
        val invalid = File(LocalModelStorageRepository.modelsDir(context), "selected-image.png")
        invalid.writeBytes(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()))
        LocalModelStorageRepository.persistInstalled(
            context,
            InstalledLocalModel(
                id = "legacy-png",
                catalogId = null,
                displayName = "selected-image",
                fileName = invalid.name,
                absolutePath = invalid.absolutePath,
                sizeBytes = invalid.length(),
                sha256 = null,
                quantization = null,
                promptTemplateId = null,
                sourceUrl = null,
                licenseTermsNote = null,
                importedAtMs = 1L,
            ),
        )
        LocalModelStorageRepository.setSelectedModelId(context, "legacy-png")

        assertEquals(1, LocalModelStorageRepository.cleanupMissingModels(context))
        assertTrue(LocalModelStorageRepository.listInstalled(context).isEmpty())
        assertFalse(invalid.exists())
        assertEquals(null, LocalModelStorageRepository.getSelectedModelId(context))
    }

    @Test
    fun cleanup_removes_invalid_orphan_left_by_legacy_import() {
        val invalid = File(LocalModelStorageRepository.modelsDir(context), "image_20.gguf")
        invalid.writeBytes(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()))

        assertEquals(1, LocalModelStorageRepository.cleanupMissingModels(context))
        assertFalse(invalid.exists())
        assertTrue(LocalModelStorageRepository.listInstalled(context).isEmpty())
    }

    @Test
    fun storage_preflight_reserves_space_and_rejects_known_oversize_source() {
        val reserve = 64L
        assertTrue(LocalModelStorageRepository.hasImportStorageHeadroom(1_024L, 900L, reserve))
        assertFalse(LocalModelStorageRepository.hasImportStorageHeadroom(1_024L, 961L, reserve))
        assertFalse(LocalModelStorageRepository.hasImportStorageHeadroom(64L, null, reserve))
    }

    @Test
    fun settings_force_litert_package_to_litert_runtime() {
        val file = File(context.cacheDir, "gemma.task")
        file.outputStream().use { output ->
            output.write(byteArrayOf(1, 2, 3, 4))
            output.write(ByteArray(1_048_576))
        }
        val model = LocalModelStorageRepository.importModelFromUri(context, Uri.fromFile(file))
        val incompatible = LocalGenerationSettings.defaultsFor(null, LocalModelPerformanceProfile.BALANCED)
            .copy(modelRuntime = LocalModelRuntime.LLAMA_CPP)

        LocalModelSettingsRepository.saveForModel(context, model.id, incompatible)

        assertEquals(LocalModelRuntime.LITERT, LocalModelSettingsRepository.getForModel(context, model.id).modelRuntime)
    }

    private fun clearState() {
        context.getSharedPreferences("local_models_registry", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("local_model_settings", Context.MODE_PRIVATE).edit().clear().commit()
        LocalModelStorageRepository.baseDir(context).deleteRecursively()
        context.cacheDir.listFiles()
            ?.filter { it.name == "My Qwen.gguf" || it.name == "fake.gguf" || it.name == "gemma.task" }
            ?.forEach(File::delete)
    }
}

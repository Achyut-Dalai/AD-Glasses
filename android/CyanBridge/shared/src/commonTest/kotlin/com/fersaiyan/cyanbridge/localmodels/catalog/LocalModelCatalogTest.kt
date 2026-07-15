package com.fersaiyan.cyanbridge.localmodels.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalModelCatalogTest {
    @Test
    fun curatedCatalogUsesStableUniqueAndDownloadableEntries() {
        val models = LocalModelCatalogRepository.curatedModels

        assertEquals(models.size, models.map(LocalModelCatalogEntry::id).toSet().size)
        assertTrue(models.all { entry ->
            entry.id.isNotBlank() &&
                entry.expectedFilename.isNotBlank() &&
                entry.sourceUrl?.startsWith("https://") == true &&
                entry.sizeBytes > 0 &&
                entry.contextSizeDefault > 0 &&
                entry.minRamGb > 0.0 &&
                entry.minStorageGb > 0.0
        })
    }

    @Test
    fun lookupRejectsBlankIdsAndFindsKnownEntries() {
        assertNull(LocalModelCatalogRepository.findById(null))
        assertNull(LocalModelCatalogRepository.findById("  "))
        assertEquals(
            "qwen2.5-0.5b-instruct-q4",
            LocalModelCatalogRepository.findById("qwen2.5-0.5b-instruct-q4")?.id,
        )
    }
}

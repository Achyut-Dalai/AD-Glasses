package com.fersaiyan.cyanbridge.localmodels

import com.fersaiyan.cyanbridge.localmodels.storage.LocalModelFileUtils
import com.fersaiyan.cyanbridge.localmodels.storage.LocalModelFileFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class LocalModelFileUtilsTest {
    @Test
    fun sanitize_filename_normalizes_extension_and_chars() {
        val clean = LocalModelFileUtils.sanitizeFileName(" qwen 2.5@mobile ")
        assertTrue(clean.endsWith(".gguf"))
        assertFalse(clean.contains(" "))
        assertFalse(clean.contains("@"))
    }

    @Test
    fun gguf_header_detection_works() {
        val tmp = File.createTempFile("local-model", ".gguf")
        tmp.writeBytes(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte(), 1, 2))
        assertTrue(LocalModelFileUtils.isGgufFile(tmp))
        tmp.delete()
    }

    @Test
    fun sha256_is_stable() {
        val tmp = File.createTempFile("local-model", ".gguf")
        tmp.writeText("abc")
        val first = LocalModelFileUtils.sha256Hex(tmp)
        val second = LocalModelFileUtils.sha256Hex(tmp)
        assertEquals(first, second)
        tmp.delete()
    }

    @Test
    fun supported_extensions_map_to_their_actual_runtime_format() {
        assertEquals(LocalModelFileFormat.GGUF, LocalModelFileUtils.formatFromFileName("Qwen.GGUF"))
        assertEquals(LocalModelFileFormat.LITERT, LocalModelFileUtils.formatFromFileName("gemma.litertlm"))
        assertEquals(LocalModelFileFormat.LITERT, LocalModelFileUtils.formatFromFileName("gemma.task"))
        assertEquals(null, LocalModelFileUtils.formatFromFileName("model.bin"))
        assertFalse(LocalModelFileUtils.hasSupportedModelExtension("model.gguf.zip"))
    }

    @Test
    fun limited_copy_removes_partial_file_when_source_exceeds_budget() {
        val dir = Files.createTempDirectory("local-model-copy-").toFile()
        val target = File(dir, "oversize.gguf.part")

        val error = runCatching {
            LocalModelFileUtils.copyToFileWithLimit(
                input = ByteArrayInputStream(ByteArray(128)),
                target = target,
                maxBytes = 64,
            )
        }.exceptionOrNull()

        assertTrue(error?.message?.contains("exceeds available storage") == true)
        assertFalse(target.exists())
        dir.deleteRecursively()
    }
}

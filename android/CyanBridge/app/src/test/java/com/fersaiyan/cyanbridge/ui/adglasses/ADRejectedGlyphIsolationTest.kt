package com.fersaiyan.cyanbridge.ui.adglasses

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ADRejectedGlyphIsolationTest {

    @Test
    fun rejectedGlassesGlyphIsNotUsedByProductScreens() {
        val uiDir = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses")
        assertTrue(uiDir.isDirectory)

        uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "ADExpressiveIcons.kt" }
            .forEach { file ->
                assertFalse(
                    "${file.name} must not render the rejected glasses glyph",
                    file.readText().contains("ADGlyph.DEVICE"),
                )
            }
    }

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromRepoRoot = File("android/CyanBridge/app", relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot
        return direct
    }
}

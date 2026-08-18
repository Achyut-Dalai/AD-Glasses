package com.fersaiyan.cyanbridge.ui.adglasses

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ADArchitectureBoundaryTest {
    @Test
    fun composeProductSurfaceDoesNotDependOnMainActivity() {
        val uiDir = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses")
        assertTrue(uiDir.isDirectory)

        uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val source = file.readText()
                assertFalse(
                    "${file.name} must use runtime contracts/controllers instead of MainActivity",
                    source.contains("import com.fersaiyan.cyanbridge.MainActivity"),
                )
            }
    }

    @Test
    fun hiddenMainActivityXmlAndLegacyComposeHostStayDeleted() {
        assertFalse(sourceFile("src/main/res/layout/acitivyt_main.xml").exists())
        assertFalse(
            sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/ComposeLegacyAdapterHost.kt").exists(),
        )
        assertTrue(
            sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/ComposeActivityHost.kt").isFile,
        )
    }

    @Test
    fun nonVisualMainActivityBridgeCannotGrowIntoAViewHierarchy() {
        val controller = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/databinding/AcitivytMainBinding.kt",
        ).readText()

        assertTrue(controller.contains("No XML is inflated and no hidden View hierarchy is created"))
        assertFalse(controller.contains("class ControlSlot : View"))
        assertFalse(controller.contains("FrameLayout("))
        assertFalse(controller.contains("LinearLayout("))
        assertFalse(controller.contains("ComposeView("))
    }

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromRepoRoot = File("android/CyanBridge/app", relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot
        return direct
    }
}

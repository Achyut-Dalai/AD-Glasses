package com.ad_glasses.ui.adglasses

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ADArchitectureBoundaryTest {
    @Test
    fun composeProductSurfaceDoesNotDependOnMainActivity() {
        val uiDir = sourceFile("src/main/java/com/ad_glasses/ui/adglasses")
        assertTrue(uiDir.isDirectory)

        uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val source = file.readText()
                assertFalse(
                    "${file.name} must use runtime contracts/controllers instead of MainActivity",
                    source.contains("import com.ad_glasses.MainActivity"),
                )
            }
    }

    @Test
    fun hiddenMainActivityXmlAndLegacyComposeHostStayDeleted() {
        assertFalse(sourceFile("src/main/res/layout/acitivyt_main.xml").exists())
        assertFalse(sourceFile("src/main/res/layout").exists())
        assertFalse(
            sourceFile("src/main/java/com/ad_glasses/ui/ComposeLegacyAdapterHost.kt").exists(),
        )
        assertTrue(
            sourceFile("src/main/java/com/ad_glasses/ui/ComposeActivityHost.kt").isFile,
        )

        val gradle = sourceFile("build.gradle").readText()
        assertTrue(gradle.contains("viewBinding = false"))
        assertTrue(gradle.contains("compose = true"))
    }

    @Test
    fun nonVisualMainActivityBridgeCannotGrowIntoAViewHierarchy() {
        val controller = sourceFile(
            "src/main/java/com/ad_glasses/databinding/AcitivytMainBinding.kt",
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
        val fromRepoRoot = File("android/AD-Glasses/app", relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot
        return direct
    }
}

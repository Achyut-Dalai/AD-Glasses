package com.fersaiyan.cyanbridge.ui.adglasses

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Product-level guardrails for AD Glasses UI.
 *
 * Legacy Activities may remain in the repository while backend/runtime migration is
 * ongoing, but native AD surfaces must not navigate to them directly. Likewise,
 * generic product surfaces must not be renamed around whichever hardware family is
 * primary today.
 */
class ADProductSurfaceIsolationTest {

    @Test
    fun nativeAdUiDoesNotReferenceLegacyActivities() {
        val uiDir = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses")
        assertTrue("AD UI source directory should exist", uiDir.isDirectory)

        val forbidden = listOf(
            "ChatListActivity",
            "ChatThreadActivity",
            "SettingsActivity",
            "CommunityPluginsActivity",
            "PublishPluginActivity",
            "NotesListActivity",
            "RecordingsListActivity",
            "SyncedMediaGalleryActivity",
            "AppDestination.",
        )

        uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val source = file.readText()
                forbidden.forEach { token ->
                    assertFalse(
                        "${file.name} must not directly reference legacy route token $token",
                        source.contains(token),
                    )
                }
            }
    }

    @Test
    fun genericDeviceSurfacesDoNotHardcodeCurrentHardwareBrand() {
        listOf(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesPairingScreen.kt",
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesDeviceCenterScreen.kt",
        ).forEach { path ->
            val source = sourceFile(path).readText()
            assertFalse("$path should stay hardware-neutral", source.contains("HeyCyan"))
        }
    }

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct

        // Some IDE/test runners use the repository root rather than the :app module.
        val fromRepoRoot = File("android/CyanBridge/app", relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot

        return direct
    }
}

package com.fersaiyan.cyanbridge.ui.adglasses

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Verifies the installed AD Glasses product surface, not just navigation intent. */
class ADProductSurfaceIsolationTest {

    @Test
    fun replacedLegacyActivitiesAreNotInstalledComponents() {
        val manifest = sourceFile("src/main/AndroidManifest.xml").readText()
        val replacedActivities = listOf(
            ".ui.ChatListActivity",
            ".ui.ChatThreadActivity",
            ".ui.SettingsActivity",
            ".ui.appearance.AppearanceActivity",
            ".ui.CommunityPluginsActivity",
            ".ui.PublishPluginActivity",
            ".ui.notes.NotesListActivity",
            ".ui.notes.NoteDetailActivity",
            ".ui.recordings.RecordingsListActivity",
            ".ui.recordings.SyncedMediaGalleryActivity",
        )

        replacedActivities.forEach { activity ->
            assertFalse(
                "$activity has an AD-native replacement and must not be registered in the APK",
                manifest.contains("android:name=\"$activity\""),
            )
        }
    }

    @Test
    fun nativeAdUiDoesNotImportReplacedActivitiesOrSharedNavigation() {
        val uiDir = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses")
        assertTrue("AD UI source directory should exist", uiDir.isDirectory)

        val forbiddenImports = listOf(
            "import com.fersaiyan.cyanbridge.ui.ChatListActivity",
            "import com.fersaiyan.cyanbridge.ui.ChatThreadActivity",
            "import com.fersaiyan.cyanbridge.ui.SettingsActivity",
            "import com.fersaiyan.cyanbridge.ui.CommunityPluginsActivity",
            "import com.fersaiyan.cyanbridge.ui.PublishPluginActivity",
            "import com.fersaiyan.cyanbridge.ui.notes.NotesListActivity",
            "import com.fersaiyan.cyanbridge.ui.recordings.RecordingsListActivity",
            "import com.fersaiyan.cyanbridge.ui.recordings.SyncedMediaGalleryActivity",
            "import com.fersaiyan.cyanbridge.shared.navigation.AppDestination",
        )

        uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val source = file.readText()
                forbiddenImports.forEach { token ->
                    assertFalse(
                        "${file.name} must not import replaced product route $token",
                        source.contains(token),
                    )
                }
            }
    }

    @Test
    fun adNavigationRootDoesNotUseCompatibilityEscapeCallbacksOrOldSurfaces() {
        val source = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesApp.kt",
        ).readText()

        listOf(
            "host.onOpenChat",
            "host.onOpenChatWithPrompt",
            "host.onOpenPhotos",
            "host.onOpenMedia",
            "host.onOpenNotes",
            "host.onOpenLegacySettings",
            "host.onOpenAutomationSettings",
            "ADDeviceCenterScreen(",
            "ADSettingsScreen(",
            "ADAiServicesScreen(",
            "ADAdvancedCenterScreen(",
        ).forEach { token ->
            assertFalse("AD navigation root must not use compatibility token $token", source.contains(token))
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

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
            ".ui.BatteryOptimizationGuideActivity",
            ".ui.OnboardingFeatureActivity",
        )

        replacedActivities.forEach { activity ->
            assertFalse(
                "$activity has an AD-native replacement and must not be registered in the APK",
                manifest.contains("android:name=\"$activity\""),
            )
        }
    }

    @Test
    fun obsoleteOnboardingSourceIsDeleted() {
        val uiDir = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui")
        assertFalse(File(uiDir, "BatteryOptimizationGuideActivity.kt").exists())
        assertFalse(File(uiDir, "OnboardingFeatureActivity.kt").exists())
        assertTrue(File(uiDir, "WelcomeActivity.kt").isFile)
    }

    @Test
    fun obsoleteAdUiBundlesAreDeletedButDeviceToolsRemainNative() {
        val uiDir = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses")
        assertFalse(File(uiDir, "ADMainScreens.kt").exists())
        assertFalse(File(uiDir, "ADDetailScreens.kt").exists())
        assertFalse(File(uiDir, "ADModeNativeModel.kt").exists())

        assertTrue(File(uiDir, "ADHomeSurface.kt").isFile)
        assertTrue(File(uiDir, "ADNativeConversationScreen.kt").isFile)
        assertTrue(File(uiDir, "ADNativeLibraryScreens.kt").isFile)
        assertTrue(File(uiDir, "ADModesScreen.kt").isFile)
        assertTrue(File(uiDir, "ADSyncScreen.kt").isFile)
        assertTrue(File(uiDir, "ADFirmwareScreen.kt").isFile)
    }

    @Test
    fun deviceCenterKeepsSyncFirmwareAndDiagnosticsAsStableDestinations() {
        val deviceCenter = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesDeviceCenterScreen.kt",
        ).readText()
        val app = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesApp.kt",
        ).readText()

        assertTrue(deviceCenter.contains("title = \"Sync media\""))
        assertTrue(deviceCenter.contains("title = \"Firmware\""))
        assertTrue(deviceCenter.contains("title = \"Advanced\""))
        assertTrue(app.contains("ADRoute.SYNC -> ADSyncScreen"))
        assertTrue(app.contains("ADRoute.FIRMWARE -> ADFirmwareScreen"))
    }

    @Test
    fun builtProductKeepsAdGlassesIdentity() {
        val strings = sourceFile("src/main/res/values/strings.xml").readText()
        val gradle = sourceFile("build.gradle").readText()
        val manifest = sourceFile("src/main/AndroidManifest.xml").readText()

        assertTrue(strings.contains("<string name=\"app_name\">AD Glasses</string>"))
        assertFalse("System-facing strings must not expose CyanBridge branding", strings.contains("CyanBridge"))
        assertTrue(
            "APK artifact should use the AD Glasses product name",
            gradle.contains("outputFileName = \"AD-Glasses.apk\""),
        )
        assertTrue(manifest.contains("android:label=\"AD Glasses notification access\""))
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

        val firmware = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADFirmwareScreen.kt",
        ).readText()
        assertFalse(
            "Firmware UI copy should stay hardware-neutral even while the current adapter is HeyCyan",
            firmware.contains("\"HeyCyan"),
        )
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

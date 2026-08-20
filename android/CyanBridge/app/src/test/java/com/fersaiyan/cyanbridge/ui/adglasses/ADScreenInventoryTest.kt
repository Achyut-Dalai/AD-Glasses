package com.fersaiyan.cyanbridge.ui.adglasses

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ADScreenInventoryTest {

    @Test
    fun primaryTabsStayFocusedOnGlassesFirstProduct() {
        assertEquals(listOf("Home", "Prompt", "AI", "Library"), ADTab.entries.map { it.label })
    }

    @Test
    fun currentRoutesRemainComplete() {
        assertEquals(
            setOf(
                "MAIN", "DEVICE_CENTER", "SYNC", "SETTINGS", "AI_RELAY", "AI_LOCAL",
                "AI_ASSISTANT_APPS", "PRIVACY", "STORAGE", "LANGUAGE", "PERMISSIONS",
                "ADVANCED", "ABOUT", "FIRMWARE", "LIBRARY_CAPTURES", "LIBRARY_RECORDINGS",
                "LIBRARY_NOTES",
            ),
            ADRoute.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun routeTableUsesNativeAdScreensForEveryProductDestination() {
        val app = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesApp.kt").readText()
        val requiredMappings = listOf(
            "ADTab.HOME -> ADHomeSurface(",
            "ADTab.CHATS -> ADNativeConversationScreen(",
            "ADTab.AI -> ADNativeAiScreen(",
            "ADTab.LIBRARY -> ADExpressiveLibraryHome(",
            "ADRoute.DEVICE_CENTER -> ADGlassesDeviceCenterScreen(",
            "ADRoute.SYNC -> ADSyncScreen(",
            "ADRoute.SETTINGS -> ADNativeSettingsHubScreen(",
            "ADRoute.AI_RELAY -> ADNativeRelaySettingsScreen(",
            "ADRoute.AI_LOCAL -> ADNativeLocalAiSettingsScreen(",
            "ADRoute.AI_ASSISTANT_APPS -> ADAssistantAppsScreen(",
            "ADRoute.PRIVACY -> ADPrivacyCenterScreenRefined(",
            "ADRoute.STORAGE -> ADStorageScreenRefined(",
            "ADRoute.LANGUAGE -> ADLanguageScreen(",
            "ADRoute.PERMISSIONS -> ADPermissionsScreen(",
            "ADRoute.ADVANCED -> ADAdvancedScreen(",
            "ADRoute.ABOUT -> ADMinimalAboutScreen(",
            "ADRoute.FIRMWARE -> ADFirmwareScreen(",
            "ADRoute.LIBRARY_CAPTURES -> ADNativeCapturesScreen(",
            "ADRoute.LIBRARY_RECORDINGS -> ADNativeRecordingsScreen(",
            "ADRoute.LIBRARY_NOTES -> ADNativeNotesScreen(",
        )
        requiredMappings.forEach { mapping -> assertTrue("Missing native AD route mapping: $mapping", app.contains(mapping)) }
    }

    @Test
    fun launcherUsesUploadedBrandArtworkOnDarkAdaptiveBackground() {
        val drawable = sourceFile("src/main/res/drawable")
        val drawableNoDpi = sourceFile("src/main/res/drawable-nodpi")
        val uploadedBrand = File(drawableNoDpi, "ad_codex_brand.png")
        val adaptiveForeground = File(drawable, "ad_glasses_adaptive_foreground.xml")
        val adaptiveBackground = File(drawable, "ad_glasses_adaptive_background.xml")
        val launcher = sourceFile("src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        val roundLauncher = sourceFile("src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")

        assertTrue("Uploaded AD brand artwork must exist", uploadedBrand.isFile && uploadedBrand.length() > 0L)
        assertTrue("Adaptive foreground must exist", adaptiveForeground.isFile)
        assertTrue("Adaptive background must exist", adaptiveBackground.isFile)
        assertTrue(adaptiveForeground.readText().contains("@drawable/ad_codex_brand"))
        assertTrue(adaptiveBackground.readText().contains("#171717"))

        listOf(launcher, roundLauncher).forEach { file ->
            val xml = file.readText()
            assertTrue(xml.contains("@drawable/ad_glasses_adaptive_background"))
            assertTrue(xml.contains("@drawable/ad_glasses_adaptive_foreground"))
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

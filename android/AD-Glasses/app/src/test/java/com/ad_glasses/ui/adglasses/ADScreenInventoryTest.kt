package com.ad_glasses.ui.adglasses

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ADScreenInventoryTest {

    @Test
    fun primaryTabsAreHomeAiAndLibrary() {
        assertEquals(
            listOf("Home", "AI", "Library"),
            ADTab.entries.map { it.label },
        )
        assertTrue(ADTab.entries.any { it.name == "AI" })
    }

    @Test
    fun currentRoutesRemainComplete() {
        assertEquals(
            setOf(
                "MAIN",
                "DEVICE_CENTER",
                "SYNC",
                "SETTINGS",
                "AI_CLOUD",
                "AI_LOCAL",
                "PRIVACY",
                "STORAGE",
                "LANGUAGE",
                "PERMISSIONS",
                "ABOUT",
                "FIRMWARE",
                "LIBRARY_CAPTURES",
                "LIBRARY_RECORDINGS",
                "LIBRARY_NOTES",
            ),
            ADRoute.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun routeTableUsesNativeAdScreensForEveryProductDestination() {
        val app = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADGlassesApp.kt",
        ).readText()

        val requiredMappings = listOf(
            "ADTab.HOME -> ADHomeSurface(",
            "ADTab.AI -> ADNativeConversationScreen(",
            "ADTab.LIBRARY -> ADExpressiveLibraryHome(",
            "ADRoute.DEVICE_CENTER -> ADGlassesDeviceCenterScreen(",
            "ADRoute.SYNC -> ADSyncScreen(",
            "ADRoute.SETTINGS -> ADNativeSettingsHubScreen(",
            "ADRoute.AI_CLOUD -> ADNativeCloudAiSettingsScreen(",
            "ADRoute.AI_LOCAL -> ADNativeLocalAiSettingsScreen(",
            "ADRoute.PRIVACY -> ADPrivacyCenterScreen(",
            "ADRoute.STORAGE -> ADStorageScreen(",
            "ADRoute.LANGUAGE -> ADLanguageScreen(",
            "ADRoute.PERMISSIONS -> ADPermissionsScreen(",
            "ADRoute.ABOUT -> ADMinimalAboutScreen(",
            "ADRoute.FIRMWARE -> ADFirmwareScreen(",
            "ADRoute.LIBRARY_CAPTURES -> ADNativeCapturesScreen(",
            "ADRoute.LIBRARY_RECORDINGS -> ADNativeRecordingsScreen(",
            "ADRoute.LIBRARY_NOTES -> ADNativeNotesScreen(",
        )

        requiredMappings.forEach { mapping ->
            assertTrue("Missing native AD route mapping: $mapping", app.contains(mapping))
        }
        assertTrue(app.contains("ADTab.AI"))
    }

    @Test
    fun deviceCenterOwnsAiConfigurationInsteadOfCapabilitiesList() {
        val deviceCenter = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADGlassesDeviceCenterScreen.kt",
        ).readText()
        val ai = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADNativeAiScreen.kt",
        ).readText()

        assertTrue(deviceCenter.contains("ADSectionTitle(\"AI\")"))
        assertTrue(deviceCenter.contains("ADDeviceAiSection("))
        assertFalse(deviceCenter.contains("ADSectionTitle(\"Capabilities\")"))
        assertFalse(deviceCenter.contains("ADDeviceCapability("))
        assertTrue(ai.contains("internal fun ADDeviceAiSection("))
        assertFalse(ai.contains("internal fun ADNativeAiScreen("))
    }

    @Test
    fun adLauncherAndHeroAssetsExistAndAdaptiveIconsUseThem() {
        val drawableNoDpi = sourceFile("src/main/res/drawable-nodpi")
        val drawable = sourceFile("src/main/res/drawable")
        val icon = File(drawableNoDpi, "ad_glasses_icon_source.png")
        val adaptiveForegroundImage = File(drawableNoDpi, "ad_glasses_adaptive_foreground_v2.png")
        val hero = File(drawableNoDpi, "ad_glasses_hero_v4.png")
        val adaptiveForeground = File(drawable, "ad_glasses_adaptive_foreground.xml")
        val adaptiveBackground = File(drawable, "ad_glasses_adaptive_background.xml")
        val launcher = sourceFile("src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        val roundLauncher = sourceFile("src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")

        assertTrue("AD Glasses icon source must exist", icon.isFile && icon.length() > 0L)
        assertTrue("AD Glasses adaptive foreground image must exist", adaptiveForegroundImage.isFile && adaptiveForegroundImage.length() > 0L)
        assertTrue("AD Glasses current hero art must exist", hero.isFile && hero.length() > 0L)
        assertTrue("Adaptive foreground wrapper must exist", adaptiveForeground.isFile)
        assertTrue("Adaptive background must exist", adaptiveBackground.isFile)

        val foregroundXml = adaptiveForeground.readText()
        assertTrue(foregroundXml.contains("@drawable/ad_glasses_adaptive_foreground_v2"))

        listOf(launcher, roundLauncher).forEach { file ->
            val xml = file.readText()
            assertTrue(xml.contains("@drawable/ad_glasses_adaptive_background"))
            assertTrue(xml.contains("@drawable/ad_glasses_adaptive_foreground"))
        }
    }

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct

        val fromRepoRoot = File("android/AD-Glasses/app", relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot

        return direct
    }
}

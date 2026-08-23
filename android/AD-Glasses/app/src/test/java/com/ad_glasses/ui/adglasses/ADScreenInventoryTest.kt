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
        assertTrue(app.contains("ADExternalDestination.AI -> routeStack = listOf(ADRoute.MAIN, ADRoute.DEVICE_CENTER)"))
    }

    @Test
    fun deviceCenterOwnsCloudAiConfigurationWithoutASeparateAiSettingsPage() {
        val deviceCenter = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADGlassesDeviceCenterScreen.kt",
        ).readText()
        val aiSectionFile = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADDeviceAiSection.kt",
        )
        val aiSection = aiSectionFile.readText()

        assertTrue(deviceCenter.contains("ADSectionTitle(\"AI\")"))
        assertTrue(deviceCenter.contains("ADDeviceAiSection("))
        assertFalse(deviceCenter.contains("ADSectionTitle(\"Capabilities\")"))
        assertTrue(aiSectionFile.isFile)
        assertTrue(aiSection.contains("internal fun ADDeviceAiSection("))
        assertTrue(aiSection.contains("AiProviderPrefs.getActiveProfile(context)"))
        assertTrue(aiSection.contains("Cloud AI profiles"))
        assertTrue(aiSection.contains("Add an API profile"))
        assertTrue(aiSection.contains("Icons.Outlined.Cloud"))
        assertTrue(aiSection.contains("tint = Color.Black"))
        assertFalse(aiSection.contains("LOCAL_MODELS"))
        assertFalse(aiSection.contains("LOCAL_AGENT"))
        assertFalse(aiSection.contains("title = \"Local\""))
        assertFalse(sourceFile("src/main/java/com/ad_glasses/ui/adglasses/ADNativeAiScreen.kt").exists())
    }

    @Test
    fun currentLauncherHeroAndLensAssetsExistWhileSupersededGenerationsDoNot() {
        val drawableNoDpi = sourceFile("src/main/res/drawable-nodpi")
        val drawable = sourceFile("src/main/res/drawable")
        val retiredIconSource = File(drawableNoDpi, "ad_glasses_icon_source.png")
        val adaptiveForegroundImage = File(drawableNoDpi, "ad_glasses_adaptive_foreground_v2.png")
        val hero = File(drawableNoDpi, "ad_glasses_hero_v4.png")
        val lens = File(drawableNoDpi, "ad_lens_shutter.png")
        val adaptiveForeground = File(drawable, "ad_glasses_adaptive_foreground.xml")
        val adaptiveBackground = File(drawable, "ad_glasses_adaptive_background.xml")
        val launcher = sourceFile("src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        val roundLauncher = sourceFile("src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")

        assertFalse("Retired AD Glasses icon source must stay removed", retiredIconSource.exists())
        assertTrue("AD Glasses adaptive foreground image must exist", adaptiveForegroundImage.isFile && adaptiveForegroundImage.length() > 0L)
        assertTrue("AD Glasses current hero art must exist", hero.isFile && hero.length() > 0L)
        assertTrue("Lens image must exist", lens.isFile && lens.length() > 0L)
        assertFalse(File(drawableNoDpi, "ad_glasses_hero_v2.png").exists())
        assertFalse(File(drawableNoDpi, "ad_glasses_hero_v3.png").exists())
        assertTrue("Adaptive foreground wrapper must exist", adaptiveForeground.isFile)
        assertTrue("Adaptive background must exist", adaptiveBackground.isFile)

        val foregroundXml = adaptiveForeground.readText()
        assertTrue(foregroundXml.contains("@drawable/ad_glasses_adaptive_foreground_v2"))

        listOf(launcher, roundLauncher).forEach { file ->
            val xml = file.readText()
            assertTrue(xml.contains("@drawable/ad_glasses_adaptive_background"))
            assertTrue(xml.contains("@drawable/ad_glasses_adaptive_foreground"))
        }

        listOf("mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi")
            .forEach { folder ->
                assertFalse(sourceFile("src/main/res/$folder/ic_launcher_foreground.png").exists())
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

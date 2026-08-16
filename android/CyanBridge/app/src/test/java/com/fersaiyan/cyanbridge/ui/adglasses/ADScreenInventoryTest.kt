package com.fersaiyan.cyanbridge.ui.adglasses

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ADScreenInventoryTest {

    @Test
    fun primaryTabsStayFocusedOnGlassesFirstProduct() {
        assertEquals(
            listOf("Home", "Chats", "AI", "Library"),
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
                "AI_RELAY",
                "AI_LOCAL",
                "AI_ASSISTANT_APPS",
                "PRIVACY",
                "STORAGE",
                "LANGUAGE",
                "PERMISSIONS",
                "ADVANCED",
                "ABOUT",
                "FIRMWARE",
                "TASK_DETAIL",
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
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesApp.kt",
        ).readText()

        val requiredMappings = listOf(
            "ADTab.HOME -> ADHomeSurface(",
            "ADTab.CHATS -> ADNativeConversationScreen(",
            "ADTab.AI -> ADNativeAiScreen(",
            "ADTab.LIBRARY -> ADNativeLibraryScreen(",
            "ADRoute.DEVICE_CENTER -> ADGlassesDeviceCenterScreen(",
            "ADRoute.SYNC -> ADSyncScreen(",
            "ADRoute.SETTINGS -> ADNativeSettingsHubScreen(",
            "ADRoute.AI_RELAY -> ADNativeRelaySettingsScreen(",
            "ADRoute.AI_LOCAL -> ADNativeLocalAiSettingsScreen(",
            "ADRoute.AI_ASSISTANT_APPS -> ADAssistantAppsScreen(",
            "ADRoute.PRIVACY -> ADPrivacyCenterScreen(",
            "ADRoute.STORAGE -> ADStorageScreen(",
            "ADRoute.LANGUAGE -> ADLanguageScreen(",
            "ADRoute.PERMISSIONS -> ADPermissionsScreen(",
            "ADRoute.ADVANCED -> ADAdvancedScreen(",
            "ADRoute.ABOUT -> ADAboutScreen(",
            "ADRoute.FIRMWARE -> ADFirmwareScreen(",
            "ADRoute.TASK_DETAIL -> ADNativeTaskDetailScreen(",
            "ADRoute.LIBRARY_CAPTURES -> ADNativeCapturesScreen(",
            "ADRoute.LIBRARY_RECORDINGS -> ADNativeRecordingsScreen(",
            "ADRoute.LIBRARY_NOTES -> ADNativeNotesScreen(",
        )

        requiredMappings.forEach { mapping ->
            assertTrue("Missing native AD route mapping: $mapping", app.contains(mapping))
        }
    }

    @Test
    fun adLauncherAndHeroAssetsExistAndAdaptiveIconsUseThem() {
        val drawableNoDpi = sourceFile("src/main/res/drawable-nodpi")
        val drawable = sourceFile("src/main/res/drawable")
        val icon = File(drawableNoDpi, "ad_glasses_icon_source.png")
        val hero = File(drawableNoDpi, "ad_glasses_hero_v4.png")
        val adaptiveForeground = File(drawable, "ad_glasses_adaptive_foreground.xml")
        val adaptiveBackground = File(drawable, "ad_glasses_adaptive_background.xml")
        val launcher = sourceFile("src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        val roundLauncher = sourceFile("src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")

        assertTrue("AD Glasses icon source must exist", icon.isFile && icon.length() > 0L)
        assertTrue("AD Glasses current hero art must exist", hero.isFile && hero.length() > 0L)
        assertTrue("Adaptive foreground must exist", adaptiveForeground.isFile)
        assertTrue("Adaptive background must exist", adaptiveBackground.isFile)

        val foregroundXml = adaptiveForeground.readText()
        assertTrue(foregroundXml.contains("@drawable/ad_glasses_icon_source"))

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

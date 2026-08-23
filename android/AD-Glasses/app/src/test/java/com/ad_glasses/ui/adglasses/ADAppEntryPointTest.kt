package com.ad_glasses.ui.adglasses

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ADAppEntryPointTest {

    @Test
    fun mainActivityRendersAdGlassesComposeShell() {
        val mainActivity = sourceFile(
            "src/main/java/com/ad_glasses/MainActivity.kt",
        ).readText()

        assertTrue(mainActivity.contains("setContent {"))
        assertTrue(mainActivity.contains("ADGlassesApp("))
        assertFalse(mainActivity.contains("ReactActivity"))
        assertFalse(mainActivity.contains("ReactRootView"))
    }

    @Test
    fun nativeRouteRootOwnsNavigationWithoutLaunchingLegacyActivities() {
        val adApp = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADGlassesApp.kt",
        ).readText()

        assertTrue(adApp.contains("ADRoute.DEVICE_CENTER -> ADGlassesDeviceCenterScreen("))
        assertTrue(adApp.contains("ADExternalDestination.AI -> {"))
        assertTrue(adApp.contains("selectedTab = ADTab.AI"))
        assertTrue(adApp.contains("ADTab.AI -> ADNativeConversationScreen("))
        assertFalse(adApp.contains("SettingsActivity::class.java"))
        assertFalse(adApp.contains("ChatListActivity::class.java"))
        assertFalse(adApp.contains("ChatThreadActivity::class.java"))
        assertFalse(adApp.contains("CommunityPluginsActivity::class.java"))
    }

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct

        val fromRepoRoot = File("android/AD-Glasses/app", relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot

        return direct
    }
}

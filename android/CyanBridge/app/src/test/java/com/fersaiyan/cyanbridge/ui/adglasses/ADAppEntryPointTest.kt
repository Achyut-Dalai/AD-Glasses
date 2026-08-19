package com.fersaiyan.cyanbridge.ui.adglasses

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ADAppEntryPointTest {

    @Test
    fun mainActivityRendersAdGlassesShellNotInheritedProductShell() {
        val mainActivity = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/MainActivity.kt",
        ).readText()

        assertTrue(
            "MainActivity must render the AD Glasses product shell",
            mainActivity.contains("ADGlassesApp("),
        )
        assertFalse(
            "The inherited CyanBridge product shell must not be rendered",
            mainActivity.contains("CyanBridgeApp("),
        )
    }

    @Test
    fun nativeRouteRootDoesNotOpenLegacyProductSettingsActivities() {
        val adApp = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesApp.kt",
        ).readText()

        assertFalse(adApp.contains("onOpenAutomationSettings"))
        assertFalse(adApp.contains("SettingsActivity::class.java"))
        assertTrue("System locale settings remain a supported Android handoff", adApp.contains("Settings.ACTION_APP_LOCALE_SETTINGS"))
    }

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct

        val fromRepoRoot = File("android/CyanBridge/app", relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot

        return direct
    }
}

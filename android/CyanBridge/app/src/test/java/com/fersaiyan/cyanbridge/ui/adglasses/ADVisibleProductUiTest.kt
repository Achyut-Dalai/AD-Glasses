package com.fersaiyan.cyanbridge.ui.adglasses

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-level guardrails for the React Native product migration. */
class ADVisibleProductUiTest {

    @Test
    fun welcomeKeepsOnlyTheStatementGlassesAndActions() {
        val welcome = rnFile("src/screens/WelcomeScreen.tsx").readText()
        assertTrue(welcome.contains("YOUR GLASSES"))
        assertTrue(welcome.contains("YOUR AI"))
        assertTrue(welcome.contains("YOUR DATA"))
        assertTrue(welcome.contains("GlassesImage"))
        assertTrue(welcome.contains("Connect glasses"))
        assertTrue(welcome.contains("Continue without glasses"))
        assertFalse(welcome.contains("AD GLASSES"))
    }

    @Test
    fun aiUsesCurrentCapabilityLanguageAndCronIdentity() {
        val ai = rnFile("src/screens/MainScreens.tsx").readText()
        assertTrue(ai.contains("DayNote', detail: 'Daily moments, distilled'"))
        assertTrue(ai.contains("Cron', detail: 'Recurring scheduled work', icon: 'repeat'"))
        assertTrue(ai.contains("Automation', detail: 'Apps & Android actions'"))
        assertFalse(ai.contains("Setup required"))
    }

    @Test
    fun settingsUsesTheProductGlassesImageForDeviceIdentity() {
        val details = rnFile("src/screens/DetailScreens.tsx").readText()
        assertTrue(details.contains("settingsGlasses}><GlassesImage"))
        assertTrue(details.contains("<GlassesImage height={170}"))
        assertTrue(details.contains("Version alpha"))
    }

    @Test
    fun productShellCoversEveryActiveRoute() {
        val app = rnFile("src/App.tsx").readText()
        listOf(
            "welcome", "home", "prompt", "ai", "library", "settings", "device",
            "pairing", "sync", "relay", "local-ai", "assistant-apps", "privacy",
            "storage", "language", "permissions", "advanced", "about", "firmware",
            "capability", "captures", "recordings", "notes",
        ).forEach { route -> assertTrue("RN shell must render $route", app.contains("case '$route'")) }
    }

    @Test
    fun motionHonorsStaggerAndReducedMotion() {
        val components = rnFile("src/design/components.tsx").readText()
        assertTrue(components.contains("AccessibilityInfo.isReduceMotionEnabled"))
        assertTrue(components.contains("withDelay(delay"))
        assertTrue(components.contains("withSpring(0.982"))
    }

    @Test
    fun nativeBridgeReusesTheExistingGlassesRuntime() {
        val bridge = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/reactnative/ADGlassesBridgeModule.kt").readText()
        assertTrue(bridge.contains("GlassesDashboardAction.CapturePhoto"))
        assertTrue(bridge.contains("GlassesDashboardAction.StartSync"))
        assertTrue(bridge.contains("AssistantOrchestrator"))
        assertTrue(bridge.contains("fun sendPrompt"))
        assertTrue(bridge.contains("SyncedMediaQuery.query"))
        assertTrue(bridge.contains("getAllCaptureSessions().first()"))
        assertTrue(bridge.contains("notesRepository.getAllNotes().first()"))
    }

    @Test
    fun reactNativeFoundationUsesNewArchitectureAndAlphaProductVersion() {
        val pkg = rnFile("package.json").readText()
        val properties = rnFile("gradle.properties").readText()
        val gradle = sourceFile("build.gradle").readText()
        assertTrue(pkg.contains("\"react-native\": \"0.86.2\""))
        assertTrue(pkg.contains("react-native-reanimated"))
        assertTrue(properties.contains("newArchEnabled=true"))
        assertTrue(properties.contains("hermesEnabled=true"))
        assertTrue(gradle.contains("versionName = \"alpha\""))
    }

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromRepoRoot = File("android/CyanBridge/app", relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot
        return direct
    }

    private fun rnFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromAppModule = File("../$relativePath")
        if (fromAppModule.exists()) return fromAppModule
        val fromRepoRoot = File("android/CyanBridge/$relativePath")
        if (fromRepoRoot.exists()) return fromRepoRoot
        return direct
    }
}

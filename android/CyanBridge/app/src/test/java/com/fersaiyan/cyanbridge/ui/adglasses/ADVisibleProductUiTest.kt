package com.fersaiyan.cyanbridge.ui.adglasses

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-level guardrails for the Compose-only AD Glasses product UI. */
class ADVisibleProductUiTest {

    @Test
    fun composeShellOwnsVisibleProductNavigation() {
        val app = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesApp.kt").readText()
        assertTrue(app.contains("fun ADGlassesApp("))
        listOf(
            "ADHomeSurface(",
            "ADNativeConversationScreen(",
            "ADNativeAiScreen(",
            "ADExpressiveLibraryHome(",
            "ADNativeSettingsHubScreen(",
            "ADGlassesDeviceCenterScreen(",
            "ADNativeCapturesScreen(",
            "ADNativeRecordingsScreen(",
            "ADNativeNotesScreen(",
        ).forEach { screen -> assertTrue("Compose shell must render $screen", app.contains(screen)) }
        assertFalse(app.contains("ADNativeCapabilityDetailScreen("))
    }

    @Test
    fun mainActivityMountsComposeInsteadOfReactNative() {
        val main = appFile("src/main/java/com/fersaiyan/cyanbridge/MainActivity.kt").readText()
        assertTrue(main.contains("setContent {"))
        assertTrue(main.contains("ADGlassesApp("))
        assertFalse(main.contains("ReactActivity"))
        assertFalse(main.contains("ReactRootView"))
        assertFalse(main.contains("com.facebook.react"))
    }

    @Test
    fun pairingScreenIsComposeOwned() {
        val pairingActivity = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/DeviceBindActivity.kt").readText()
        assertTrue(pairingActivity.contains("ADGlassesPairingScreen"))
        assertTrue(pairingActivity.contains("setContent"))
        assertFalse(pairingActivity.contains("setContentView(R.layout.activity_device_bind)"))
    }

    @Test
    fun aiPageKeepsApprovedPersistentControls() {
        val ai = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeAiScreen.kt").readText()
        assertTrue(ai.contains("AssistantCapability.VISUAL_DIARY"))
        assertTrue(ai.contains("AssistantCapability.AUTO_DIARY"))
        assertTrue(ai.contains("AssistantCapability.LOCAL_AGENT"))
        assertTrue(ai.contains("\"Timeline\""))
        assertTrue(ai.contains("\"Diary\""))
        assertTrue(ai.contains("\"ANSWER WITH\""))
        assertTrue(ai.contains("ADAiProviderPill"))
        assertFalse(ai.contains("R.drawable.ad_codex_ai"))
        assertFalse(ai.contains("AI that feels like yours"))
        assertFalse(ai.contains("selectedName"))
        assertFalse(ai.contains("Switch("))
        assertFalse(ai.contains("AssistantCapability.TRANSLATOR"))
        assertFalse(ai.contains("AssistantCapability.MEETING_NOTES"))
    }

    @Test
    fun homeStartsCoreActionsWithMatrixCardsAndNoGeneratedActionArt() {
        val home = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADHomeSurface.kt").readText()
        assertTrue(home.contains("onClick = host.onVoiceQuestion"))
        assertTrue(home.contains("onClick = host.onCapturePhoto"))
        assertTrue(home.contains("onClick = host.onToggleVideo"))
        assertTrue(home.contains("onClick = host.onImageQuestion"))
        assertTrue(home.contains("toggleCapability(AssistantCapability.TRANSLATOR)"))
        assertTrue(home.contains("toggleCapability(AssistantCapability.MEETING_NOTES)"))
        listOf("Ask AI", "Photo", "Video", "Translate", "Soundbites", "Audio", "Lens")
            .forEach { label -> assertTrue(home.contains("\"$label\"")) }
        assertTrue(home.contains("ADHomeActionCard("))
        assertTrue(home.contains("ADHeroSignalMatrix("))
        assertTrue(home.contains("ADTechFontFamily"))
        assertTrue(home.contains("R.drawable.ad_glasses_hero_v4"))
        assertFalse(home.contains("ADGlyphMatrixCard("))
        assertFalse(home.contains("R.drawable.ad_codex_ask"))
        assertFalse(home.contains("R.drawable.ad_codex_video"))
        assertFalse(home.contains("R.drawable.ad_codex_language"))
        assertFalse(home.contains("R.drawable.ad_codex_audio"))
    }

    @Test
    fun retiredReactAndLegacyScreenArtifactsRemainAbsent() {
        listOf(
            cyanBridgeFile("package.json"),
            cyanBridgeFile("package-lock.json"),
            cyanBridgeFile("metro.config.js"),
            cyanBridgeFile("src/App.tsx"),
            appFile("src/main/java/com/fersaiyan/cyanbridge/ui/reactnative"),
            appFile("src/main/res/layout/activity_chat_list.xml"),
            appFile("src/main/res/layout/activity_chat_thread.xml"),
            appFile("src/main/res/layout/activity_welcome.xml"),
            appFile("src/main/res/layout/activity_device_bind.xml"),
        ).forEach { artifact -> assertFalse("Retired artifact must stay removed: ${artifact.path}", artifact.exists()) }
    }

    private fun appFile(relativePath: String): File = firstExisting(
        File(relativePath),
        File("android/CyanBridge/app", relativePath),
    )

    private fun cyanBridgeFile(relativePath: String): File = firstExisting(
        File("../$relativePath"),
        File("android/CyanBridge", relativePath),
    )

    private fun firstExisting(vararg candidates: File): File =
        candidates.firstOrNull { it.exists() } ?: candidates.first()
}

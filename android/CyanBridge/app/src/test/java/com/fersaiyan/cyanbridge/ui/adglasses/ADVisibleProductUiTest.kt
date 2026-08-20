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
            "ADNativeCapturesScreen(",
            "ADNativeRecordingsScreen(",
            "ADNativeNotesScreen(",
        ).forEach { screen -> assertTrue("Compose shell must render $screen", app.contains(screen)) }
        assertFalse(app.contains("ADGlassesDeviceCenterScreen("))
        assertFalse(app.contains("ADNativeCapabilityDetailScreen("))
        assertFalse(appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesDeviceCenterScreen.kt").exists())
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
    fun pairingScreenIsComposeOwnedAndUsesProductBackdrop() {
        val pairingActivity = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/DeviceBindActivity.kt").readText()
        val pairing = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesPairingScreen.kt").readText()
        assertTrue(pairingActivity.contains("ADGlassesPairingScreen"))
        assertTrue(pairingActivity.contains("setContent"))
        assertFalse(pairingActivity.contains("setContentView(R.layout.activity_device_bind)"))
        assertTrue(pairing.contains("ADWallpaperBackground {"))
        assertTrue(pairing.contains("Icons.Outlined.Bluetooth"))
        assertTrue(pairing.contains("ADGlyph.NEXT"))
        assertFalse(pairing.contains("ADGlyph.DEVICE"))
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
    fun homeKeepsSettingsThenCompactLensCaptureAndHeroOpensSettings() {
        val home = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADHomeSurface.kt").readText()
        val app = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesApp.kt").readText()
        assertTrue(home.contains("onClick = host.onVoiceQuestion"))
        assertTrue(home.contains("onPhoto = host.onCapturePhoto"))
        assertTrue(home.contains("onVideo = host.onToggleVideo"))
        assertTrue(home.contains("ADTopBar(showBrand = false, showSettings = true, onSettings = onOpenSettings)"))
        assertTrue(home.contains("item { ADLensCard(onClick = host.onImageQuestion) }"))
        assertTrue(home.contains("ADSectionTitle(\"CAPTURE\")"))
        assertTrue(home.indexOf("ADTopBar(showBrand = false") < home.indexOf("ADLensCard(onClick = host.onImageQuestion)"))
        assertTrue(home.indexOf("ADLensCard(onClick = host.onImageQuestion)") < home.indexOf("ADSectionTitle(\"CAPTURE\")"))
        assertTrue(home.indexOf("ADSectionTitle(\"CAPTURE\")") < home.indexOf("ADLargeGlassesHero("))
        assertTrue(home.contains("toggleCapability(AssistantCapability.TRANSLATOR)"))
        assertTrue(home.contains("toggleCapability(AssistantCapability.MEETING_NOTES)"))
        listOf("ASSISTANT", "THINK   ASK", "PHOTO", "VIDEO", "Translate", "Record", "Soundbites", "AUDIO", "CAPTURE")
            .forEach { label -> assertTrue(home.contains("\"$label\"")) }

        assertFalse(home.contains("Text(\"AD GLASSES\""))
        assertFalse(home.contains("Text(\"Ask AI\""))
        assertFalse(home.contains("Text(\"Voice\""))
        assertTrue(home.contains("ADLargeGlassesHero("))
        assertTrue(home.contains("onOpenSettings = onOpenSettings"))
        assertTrue(home.contains("onClick = onOpenSettings"))
        assertTrue(app.contains("onOpenSettings = { navigateTo(ADRoute.SETTINGS) }"))
        assertTrue(home.contains("R.drawable.ad_glasses_hero_v4"))
        assertTrue(home.contains(".height(184.dp)"))
        assertTrue(home.contains(".heightIn(min = 122.dp)"))
        assertTrue(home.contains("ADLensShutterArtwork(Modifier.weight(0.94f).height(96.dp))"))
        assertTrue(home.contains("Canvas(Modifier.fillMaxSize().padding(14.dp))"))
        assertTrue(home.contains("ADLensShutterArtwork("))
        assertTrue(home.contains("ADCameraArtwork("))
        assertTrue(home.contains("heightIn(min = 154.dp)"))
        assertTrue(home.contains("Modifier.fillMaxWidth().height(76.dp)"))
        assertTrue(home.contains("ADHomeMiniPill(\"PHOTO\""))
        assertTrue(home.contains("ADHomeMiniPill(\"VIDEO\""))
        assertTrue(home.contains("fontFamily = ADTechFontFamily"))
        assertTrue(home.contains("letterSpacing = 0.75.sp"))
        assertFalse(home.contains("Icons.Outlined.CameraAlt"))
        assertFalse(home.contains("Text(\"Camera\""))
        assertFalse(home.contains("ADGlassesDeviceCard("))
        assertFalse(home.contains("ADLensMatrixAction("))
        assertFalse(home.contains("LENS MATRIX / V1"))
        assertFalse(home.contains("SEE   CAPTURE   ASK"))
        assertFalse(home.contains("\"01110\""))
        assertFalse(home.contains("activeCell"))
        assertFalse(home.contains("R.drawable.ad_codex_ask"))
        assertFalse(home.contains("R.drawable.ad_codex_video"))
        assertFalse(home.contains("R.drawable.ad_codex_language"))
        assertFalse(home.contains("R.drawable.ad_codex_audio"))
    }

    @Test
    fun settingsOwnsFirmwareAndDropsRedundantHeaderText() {
        val settings = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeSettingsHubScreen.kt").readText()
        val app = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesApp.kt").readText()
        assertTrue(settings.contains("ADPageLayout(onBack = onBack)"))
        assertFalse(settings.contains("ADScreenIntro(eyebrow = \"SYSTEM\", title = \"Settings\")"))
        assertTrue(settings.contains("ADSectionTitle(\"System\")"))
        assertTrue(settings.contains("glyph = ADGlyph.FIRMWARE"))
        assertTrue(settings.contains("title = \"Firmware\""))
        assertTrue(app.contains("onFirmware = { navigateTo(ADRoute.FIRMWARE) }"))
        assertFalse(settings.contains("onDevice:"))
    }

    @Test
    fun selectedPromptNavigationAndSystemIconsUseThePreservedMatrixDesigns() {
        val components = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADComponents.kt").readText()
        val glyphs = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADExpressiveIcons.kt").readText()
        val settings = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeSettingsHubScreen.kt").readText()
        val productSettings = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADProductSettingsScreens.kt").readText()
        val firmware = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADFirmwareScreen.kt").readText()
        val notes = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeLibraryScreens.kt").readText()
        val prompt = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeConversationScreen.kt").readText()

        assertTrue(components.contains("Icons.Outlined.ChatBubbleOutline"))
        assertTrue(components.contains("ADGlyph.BACK"))
        assertTrue(components.contains("ADGlyph.NEXT"))
        assertTrue(components.contains("Icons.Rounded.Settings"))
        assertFalse(components.contains("Icons.AutoMirrored.Rounded.ArrowBack"))
        assertFalse(components.contains("Icons.AutoMirrored.Rounded.KeyboardArrowRight"))

        listOf("ADGlyph.PROMPT", "ADGlyph.PRIVACY", "ADGlyph.PERMISSIONS", "ADGlyph.FIRMWARE", "ADGlyph.BACK", "ADGlyph.NEXT")
            .forEach { selected -> assertTrue(glyphs.contains(selected)) }
        assertTrue(glyphs.contains("selectedMatrixPattern"))
        assertTrue(settings.contains("ADGlyph.PRIVACY"))
        assertTrue(settings.contains("ADGlyph.PERMISSIONS"))
        assertTrue(settings.contains("ADGlyph.FIRMWARE"))
        assertTrue(productSettings.contains("ADGlyph.PERMISSIONS"))
        assertTrue(firmware.contains("ADGlyph.FIRMWARE"))
        assertTrue(notes.contains("ADGlyph.PROMPT"))
        assertTrue(prompt.contains("ADGlyph.PROMPT"))

        assertTrue(settings.contains("Icons.Outlined.Language"))
        assertTrue(productSettings.contains("Icons.Outlined.Language"))
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

package com.fersaiyan.cyanbridge.ui.adglasses

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ADVisibleProductUiTest {

    private val visibleSurfacePaths = listOf(
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADComponents.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADHomeSurface.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeConversationScreen.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADConversationRichContent.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeLibraryScreens.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesDeviceCenterScreen.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesPairingScreen.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeSettingsHubScreen.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADWelcomeScreen.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADSyncScreen.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADFirmwareScreen.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADAdvancedScreen.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeModeDetailScreen.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeAiScreen.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADAssistantAppsScreen.kt",
    )

    @Test
    fun visibleScreensDoNotExposeUpstreamProductOrPluginNames() {
        val forbiddenStringLiterals = listOf(
            "\"Ask AD",
            "\"AD can do",
            "\"AD AI",
            "\"CyanBridge",
            "\"Meeting Spark Notes",
            "\"Live Caption Relay",
            "\"Hands-Free Translator",
            "\"Errand Brain",
            "\"Auto Diary",
            "\"Auto Audio",
            "Things glasses can do for me",
        )

        visibleSurfacePaths.forEach { path ->
            val source = sourceFile(path).readText()
            forbiddenStringLiterals.forEach { forbidden ->
                assertFalse(
                    "$path must not expose visible legacy/upstream copy beginning with $forbidden",
                    source.contains(forbidden),
                )
            }
        }
    }

    @Test
    fun promptUsesTerminalIconAndHomeDoesNotDuplicatePromptNavigation() {
        val components = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADComponents.kt",
        ).readText()
        val models = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesModels.kt",
        ).readText()
        val home = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADHomeSurface.kt",
        ).readText()

        assertTrue(models.contains("CHATS(\"Prompt\")"))
        assertTrue(components.contains("ADTab.CHATS -> Icons.Outlined.Terminal"))
        assertFalse(home.contains("title = \"Conversations\""))
        assertFalse(home.contains("title = \"Chats\""))
        assertFalse(home.contains("onOpenConversations"))
    }

    @Test
    fun capabilitiesUseMonochromeEditorialToggleControls() {
        val models = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesModels.kt",
        ).readText()
        val ai = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeAiScreen.kt",
        ).readText()
        val details = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeModeDetailScreen.kt",
        ).readText()

        assertFalse(models.contains("TASKS(\"Tasks\")"))
        assertTrue(ai.contains("Text(\"Capabilities\""))
        assertTrue(details.contains("Switch("))
        assertTrue(details.contains("automation.capabilityIcon()"))
        assertTrue(details.contains("ADCapabilityDetailRow("))
        assertTrue(details.contains("ADColors.SurfaceSubtle"))
        assertFalse(details.contains("capabilityPalette()"))
        assertFalse(details.contains("ADCapabilityPalette"))
        assertTrue(details.contains("\"DETAILS\""))
        assertFalse(details.contains("OutlinedButton("))
        assertFalse(details.contains("Button("))
        assertFalse(details.contains("Start task"))
        assertFalse(details.contains("Stop task"))
    }

    @Test
    fun promptComposerSupportsFreshSessionsAndRealActivityMotion() {
        val prompt = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeConversationScreen.kt",
        ).readText()

        assertFalse(prompt.contains("Icons.Outlined.CameraAlt"))
        assertFalse(prompt.contains("Icons.Outlined.Mic"))
        assertFalse(prompt.contains("New chat"))
        assertTrue(prompt.contains("Icons.Outlined.Terminal"))
        assertTrue(prompt.contains("session.startNewConversation()"))
        assertTrue(prompt.contains("Icons.Rounded.Add"))
        assertTrue(prompt.contains("pendingAlreadyPersisted"))
        assertTrue(prompt.contains("KeyboardActions(onSend = { onSend() })"))
        assertTrue(prompt.contains("What do you want to know?"))
        assertTrue(prompt.contains("Ask AI…"))
        assertTrue(prompt.contains("ADActivityWaveform"))
        assertTrue(prompt.contains("AudioSessionCoordinator.isBusy()"))
        assertTrue(prompt.contains("MeetingCapturePrefs.getState(context).isRecording"))
        assertTrue(prompt.contains("ADColors.SurfaceSubtle"))
        assertTrue(prompt.contains("ADAssistantTurn"))
    }

    @Test
    fun capabilityProductNamesStayBroadWhileRuntimeIdsRemainCompatible() {
        val models = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesModels.kt",
        ).readText()
        val ai = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeAiScreen.kt",
        ).readText()

        assertTrue(models.contains("\"Soundbites\""))
        assertTrue(models.contains("\"DayNote\""))
        assertTrue(models.contains("\"Timeline\""))
        assertTrue(models.contains("\"Automation\""))
        assertTrue(models.contains("\"Cron\""))
        assertTrue(models.contains("\"Meeting Spark Notes\""))
        assertTrue(models.contains("\"Auto Diary\""))
        assertTrue(models.contains("\"Visual Diary\""))
        assertTrue(models.contains("\"Errand Brain\""))
        assertTrue(ai.contains("ADAutomation.MEETING_NOTES.title"))
        assertTrue(ai.contains("ADAutomation.AUTO_DIARY.title"))
        assertTrue(ai.contains("ADAutomation.VISUAL_DIARY.title"))
        assertTrue(ai.contains("ADAutomation.ERRAND_BRAIN.title"))
        assertTrue(ai.contains("ADAutomation.LOCAL_AGENT.title"))
        assertTrue(ai.contains("Icons.Outlined.AutoStories"))
        assertTrue(ai.contains("Icons.Outlined.Timeline"))
        assertTrue(ai.contains("Icons.Outlined.Schedule"))
        assertTrue(ai.contains("Icons.Outlined.Bolt"))
    }

    @Test
    fun welcomeIsPosterLikeAndContainsOnlyTheProductStatement() {
        val welcome = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADWelcomeScreen.kt",
        ).readText()

        assertTrue(welcome.contains("YOUR GLASSES\\nYOUR AI\\nYOUR DATA"))
        assertTrue(welcome.contains("alpha(0.16f)"))
        assertTrue(welcome.contains("AD GLASSES"))
        assertTrue(welcome.contains("Connect glasses"))
        assertTrue(welcome.contains("Continue without glasses"))
        assertFalse(welcome.contains("A private brain for the glasses you wear."))
        assertFalse(welcome.contains("Connect when you are ready."))
    }

    @Test
    fun productChromeUsesMonochromePalette() {
        val theme = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesTheme.kt",
        ).readText()
        val details = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeModeDetailScreen.kt",
        ).readText()

        assertTrue(theme.contains("val Blue = Color(0xFF2C2C2E)"))
        assertTrue(theme.contains("val BlueDeep = Color(0xFF111113)"))
        assertTrue(theme.contains("val BlueSoft = Color(0xFFEAEAED)"))
        assertFalse(details.contains("0xFF6D5C82"))
        assertFalse(details.contains("0xFF7A654B"))
        assertFalse(details.contains("0xFF4F6E66"))
    }

    @Test
    fun pairingUsesSafeDrawingAndDetectedIdentityWithoutManualSelectionFallback() {
        val pairing = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesPairingScreen.kt",
        ).readText()

        assertTrue(pairing.contains("WindowInsets.safeDrawing"))
        assertTrue(pairing.contains("val deviceClass = device.detectedClass"))
        assertTrue(pairing.contains("Looking for nearby glasses"))
        assertFalse(pairing.contains("HeyCyan"))
        assertFalse(pairing.contains("effectiveSelectedClass()"))
        assertFalse(pairing.contains("ModalBottomSheet"))
        assertFalse(pairing.contains("onSelectedClassChange"))
    }

    @Test
    fun aiIsAFirstClassTabAndSettingsHubDoesNotDuplicateIt() {
        val ai = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeAiScreen.kt",
        ).readText()
        val settings = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeSettingsHubScreen.kt",
        ).readText()

        assertTrue(ai.contains("ADAiSection(\"Default AI\")"))
        assertTrue(ai.contains("\"Gemini\""))
        assertTrue(ai.contains("\"OpenAI / Codex\""))
        assertTrue(ai.contains("\"Local AI\""))
        assertTrue(ai.contains("\"Assistant apps\""))
        assertFalse(ai.contains("ADTopBar(title = \"AI\")"))
        assertFalse(settings.contains("AI and web"))
        assertFalse(settings.contains("Routing"))
    }

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct

        val fromRepoRoot = File("android/CyanBridge/app", relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot

        return direct
    }
}

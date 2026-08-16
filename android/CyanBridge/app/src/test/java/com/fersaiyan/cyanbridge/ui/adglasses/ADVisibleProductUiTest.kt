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
    fun chatsUseConversationIconAndHomeDoesNotDuplicateChatNavigation() {
        val components = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADComponents.kt",
        ).readText()
        val home = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADHomeSurface.kt",
        ).readText()

        assertTrue(components.contains("ADTab.CHATS -> Icons.Rounded.Forum"))
        assertFalse(home.contains("title = \"Conversations\""))
        assertFalse(home.contains("title = \"Chats\""))
        assertFalse(home.contains("onOpenConversations"))
    }

    @Test
    fun tasksTabIsGoneAndCapabilitiesLiveInAi() {
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
        assertFalse(details.contains("Start mode"))
        assertFalse(details.contains("Stop mode"))
        assertFalse(details.contains("Start task"))
        assertFalse(details.contains("Stop task"))
    }

    @Test
    fun chatsComposerIsTextFirstAndDedupesPersistedPendingTurn() {
        val chats = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeConversationScreen.kt",
        ).readText()

        assertFalse(chats.contains("Icons.Outlined.CameraAlt"))
        assertFalse(chats.contains("Icons.Outlined.Mic"))
        assertTrue(chats.contains("pendingAlreadyPersisted"))
        assertTrue(chats.contains("KeyboardActions(onSend = { send() })"))
        assertTrue(chats.contains("ADColors.BlueSoft"))
        assertTrue(chats.contains("ADAssistantTurn"))
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

        assertTrue(ai.contains("ADAiSection(title = \"Default AI\")"))
        assertTrue(ai.contains("title = \"Gemini\""))
        assertTrue(ai.contains("title = \"OpenAI / Codex\""))
        assertTrue(ai.contains("title = \"Local AI\""))
        assertTrue(ai.contains("title = \"Assistant apps\""))
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

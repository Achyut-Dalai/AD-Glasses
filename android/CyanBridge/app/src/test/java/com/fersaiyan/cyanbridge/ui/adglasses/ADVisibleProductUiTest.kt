package com.fersaiyan.cyanbridge.ui.adglasses

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ADVisibleProductUiTest {

    private val visibleSurfacePaths = listOf(
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADComponents.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADHomeSurface.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADModesScreen.kt",
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
    fun chatsUseChatIconAndHomeDoesNotDuplicateChatNavigation() {
        val components = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADComponents.kt",
        ).readText()
        val home = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADHomeSurface.kt",
        ).readText()

        assertTrue(components.contains("ADTab.CHATS -> Icons.Rounded.ChatBubble"))
        assertFalse(home.contains("title = \"Conversations\""))
        assertFalse(home.contains("title = \"Chats\""))
        assertFalse(home.contains("onOpenConversations"))
    }

    @Test
    fun tasksStayCompactAndExcludeRetiredFeatures() {
        val tasks = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADModesScreen.kt",
        ).readText()
        val details = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeModeDetailScreen.kt",
        ).readText()

        assertTrue(tasks.contains("ADTopBar(title = \"Tasks\")"))
        assertTrue(tasks.contains("filter { it.visibleInTasks }"))
        assertFalse(tasks.contains("Things glasses can do for me"))
        assertFalse(details.contains("Start mode"))
        assertFalse(details.contains("Stop mode"))
        assertTrue(details.contains("Start task"))
        assertTrue(details.contains("Stop task"))
    }

    @Test
    fun chatsComposerIsTextFirstAndDedupesPersistedPendingTurn() {
        val chats = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeConversationScreen.kt",
        ).readText()

        assertFalse(chats.contains("Icons.Outlined.CameraAlt"))
        assertFalse(chats.contains("Icons.Outlined.Mic"))
        assertFalse(chats.contains("Icons.Outlined.Public"))
        assertTrue(chats.contains("pendingAlreadyPersisted"))
        assertTrue(chats.contains("KeyboardActions(onSend = { send() })"))
    }

    @Test
    fun pairingUsesSafeDrawingAndDetectedIdentityWithoutManualSelectionFallback() {
        val pairing = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesPairingScreen.kt",
        ).readText()

        assertTrue(pairing.contains("WindowInsets.safeDrawing"))
        assertTrue(pairing.contains("val deviceClass = device.detectedClass"))
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

        assertTrue(ai.contains("Text(\"Default AI\""))
        assertTrue(ai.contains("title = \"Gemini\""))
        assertTrue(ai.contains("title = \"OpenAI / Codex\""))
        assertTrue(ai.contains("title = \"Local AI\""))
        assertTrue(ai.contains("title = \"Web Search\""))
        assertTrue(ai.contains("title = \"Phone control\""))
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

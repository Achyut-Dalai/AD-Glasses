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
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADProductSettingsScreens.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADWelcomeScreen.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADSyncScreen.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADFirmwareScreen.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADAdvancedScreen.kt",
        "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeModeDetailScreen.kt",
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
    fun conversationsUseConversationIconRatherThanAssistantPersonaIcon() {
        val components = sourceFile(visibleSurfacePaths.first()).readText()
        val home = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADHomeSurface.kt",
        ).readText()

        assertTrue(components.contains("ADTab.ASSISTANT -> Icons.Rounded.ChatBubble"))
        assertTrue(home.contains("icon = Icons.Outlined.ChatBubble"))
        assertFalse(components.contains("ADTab.ASSISTANT -> Icons.Rounded.SmartToy"))
    }

    @Test
    fun pairingDisplaysDetectedIdentityWithoutManualSelectionFallback() {
        val pairing = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesPairingScreen.kt",
        ).readText()

        assertTrue(pairing.contains("val deviceClass = device.detectedClass"))
        assertFalse(pairing.contains("effectiveSelectedClass()"))
        assertFalse(pairing.contains("ModalBottomSheet"))
        assertFalse(pairing.contains("onSelectedClassChange"))
    }

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct

        val fromRepoRoot = File("android/CyanBridge/app", relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot

        return direct
    }
}

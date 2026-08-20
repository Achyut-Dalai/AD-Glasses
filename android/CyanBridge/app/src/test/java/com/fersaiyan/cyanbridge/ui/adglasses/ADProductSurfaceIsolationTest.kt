package com.fersaiyan.cyanbridge.ui.adglasses

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Product-level guardrails for the AD Glasses Compose surface. */
class ADProductSurfaceIsolationTest {

    @Test
    fun primaryTabsStayHomePromptAiLibrary() {
        val models = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesModels.kt").readText()
        val enumBody = Regex("enum class ADTab\\(val label: String\\) \\{([\\s\\S]*?)\\n}")
            .find(models)?.groupValues?.get(1).orEmpty()
        val labels = Regex("\\w+\\(\"([^\"]+)\"\\)").findAll(enumBody).map { it.groupValues[1] }.toList()
        assertEquals(listOf("Home", "Prompt", "AI", "Library"), labels)
    }

    @Test
    fun homeUsesAiStyleCardsMatrixGlyphsAndOnlyTheRealHeroImage() {
        val home = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADHomeSurface.kt").readText()
        val glyphs = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADExpressiveIcons.kt").readText()

        listOf("Ask AI", "Photo", "Video", "Translate", "Soundbites", "Audio", "Lens")
            .forEach { label -> assertTrue("Home should keep $label", home.contains("\"$label\"")) }
        listOf("Voice question", "Capture", "Record", "Live speech", "Speech notes", "Look at it. Ask about it.")
            .forEach { detail -> assertTrue("Home should use compact AI-style supporting copy", home.contains("\"$detail\"")) }
        assertTrue(home.contains("ADHomeActionCard("))
        assertTrue(home.contains("ADHeroSignalMatrix("))
        assertTrue(home.contains("ADTechFontFamily"))
        assertTrue(home.contains("R.drawable.ad_glasses_hero_v4"))
        assertFalse(home.contains("ADGlyphMatrixCard("))
        assertFalse(home.contains("R.drawable.ad_codex_ask"))
        assertFalse(home.contains("R.drawable.ad_codex_video"))
        assertFalse(home.contains("R.drawable.ad_codex_language"))
        assertFalse(home.contains("R.drawable.ad_codex_audio"))

        assertTrue(glyphs.contains("7×7 matrix"))
        assertTrue(glyphs.contains("rememberInfiniteTransition"))
        assertTrue(glyphs.contains("ADGlyph.SETTINGS"))
        assertTrue(glyphs.contains("ADGlyph.CHECK"))
        assertTrue(glyphs.contains("ADGlyph.NEXT"))
    }

    @Test
    fun aiPageIsLockedToTheApprovedSourceBlob() {
        val ai = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeAiScreen.kt")
        assertEquals(
            "AI page is locked. Deliberate AI redesigns must explicitly update this guard.",
            "a22475ebd1601af3e739e9dc5d52cc064ff7ebce",
            gitBlobSha(ai),
        )
        val source = ai.readText()
        assertTrue(source.contains("\"ANSWER WITH\""))
        assertTrue(source.contains("\"Timeline\""))
        assertTrue(source.contains("\"Diary\""))
        assertTrue(source.contains("\"Automation\""))
        assertFalse(source.contains("R.drawable.ad_codex_ai"))
        assertFalse(source.contains("AI that feels like yours"))
        assertFalse(source.contains("selectedName"))
    }

    @Test
    fun uploadedBackgroundReplacesGeneratedWallpaperPresets() {
        val appearance = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADAppearance.kt").readText()
        val app = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesApp.kt").readText()
        val settings = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeSettingsHubScreen.kt").readText()
        val background = sourceFile("src/main/res/drawable-nodpi/ad_user_background.jpeg")

        assertTrue(background.isFile && background.length() > 0L)
        assertTrue(appearance.contains("R.drawable.ad_user_background"))
        assertTrue(appearance.contains("ContentScale.Crop"))
        assertFalse(appearance.contains("ADWallpaperStyle"))
        assertFalse(appearance.contains("DOT_GRID"))
        assertFalse(appearance.contains("ORBIT"))
        assertFalse(appearance.contains("LINES"))
        assertTrue(app.contains("ADWallpaperBackground {"))
        assertFalse(app.contains("ADAppearancePrefs"))
        assertFalse(app.contains("var wallpaper"))
        assertFalse(settings.contains("ADWallpaperPicker"))
        assertFalse(settings.contains("ADSectionTitle(\"Wallpaper\")"))
    }

    @Test
    fun uploadedLogoOwnsBrandMarksAndMatrixNavigation() {
        val components = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADComponents.kt").readText()
        val logo = sourceFile("src/main/res/drawable-nodpi/ad_user_app_icon.webp")

        assertTrue(logo.isFile && logo.length() > 0L)
        assertTrue(components.contains("R.drawable.ad_user_app_icon"))
        assertTrue(components.contains("ADGlyph.BACK"))
        assertTrue(components.contains("ADGlyph.SETTINGS"))
        assertTrue(components.contains("ADGlyph.AI"))
        assertTrue(components.contains("ADGlyph.NEXT"))
        assertFalse(components.contains("Icons.Outlined.AutoAwesome"))
        assertFalse(components.contains("text = \"AD\""))
    }

    @Test
    fun promptBehaviorAndDeviceGlyphRemovalStayProtected() {
        val prompt = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeConversationScreen.kt").readText()
        val pairing = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesPairingScreen.kt").readText()
        val deviceCenter = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesDeviceCenterScreen.kt").readText()

        assertTrue(prompt.contains("What do you want to know?"))
        assertTrue(prompt.contains("Ask anything…"))
        assertTrue(prompt.contains("session.startNewConversation()"))
        assertTrue(prompt.contains("ADActivityWaveform"))
        assertFalse(pairing.contains("ADGlyph.DEVICE"))
        assertFalse(deviceCenter.contains("ADGlyph.DEVICE"))
        assertTrue(deviceCenter.contains("\"Sync media\""))
        assertTrue(deviceCenter.contains("\"Firmware\""))
    }

    @Test
    fun builtProductKeepsAdGlassesIdentityAndAlphaVersion() {
        val strings = sourceFile("src/main/res/values/strings.xml").readText()
        val gradle = sourceFile("build.gradle").readText()
        assertTrue(strings.contains("<string name=\"app_name\">AD Glasses</string>"))
        assertTrue(gradle.contains("versionName = \"alpha\""))
        assertTrue(gradle.contains("outputFileName.set(\"AD-Glasses.apk\")"))
    }

    private fun gitBlobSha(file: File): String {
        val bytes = file.readBytes()
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromRepoRoot = File("android/CyanBridge/app", relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot
        return direct
    }
}

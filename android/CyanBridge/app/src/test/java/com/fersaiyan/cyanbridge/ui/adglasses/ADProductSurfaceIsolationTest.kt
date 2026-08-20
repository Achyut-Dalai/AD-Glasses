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
    fun homeUsesLargeHeroLensMatrixAndConfigurationStyleActions() {
        val home = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADHomeSurface.kt").readText()

        listOf("Ask AI", "Photo", "Video", "Translate", "Soundbites", "Audio")
            .forEach { label -> assertTrue("Home should keep $label", home.contains("\"$label\"")) }
        assertTrue(home.contains("ADLargeGlassesHero("))
        assertTrue(home.contains(".height(184.dp)"))
        assertTrue(home.contains("R.drawable.ad_glasses_hero_v4"))
        assertTrue(home.contains("ADLensMatrixAction("))
        assertTrue(home.contains("LENS MATRIX / V1"))
        assertTrue(home.contains("SEE   CAPTURE   ASK"))
        assertTrue(home.contains("rememberInfiniteTransition"))
        assertFalse(home.contains("ADGlyphMatrixFeature("))
        assertFalse(home.contains("GLYPH MATRIX / 01"))
        assertFalse(home.contains("ADLensAction("))

        assertTrue(home.contains("Icons.Outlined.GraphicEq"))
        assertTrue(home.contains("shape = RoundedCornerShape(9.dp)"))
        assertTrue(home.contains("color = ADColors.SurfaceSubtle"))
        assertTrue(home.contains("style = MaterialTheme.typography.labelLarge"))

        listOf("Voice question", "Live speech", "Speech notes", "Look at it. Ask about it.")
            .forEach { unwanted -> assertFalse("Home should avoid explanatory action subtext", home.contains("\"$unwanted\"")) }
        assertFalse(home.contains("R.drawable.ad_codex_ask"))
        assertFalse(home.contains("R.drawable.ad_codex_video"))
        assertFalse(home.contains("R.drawable.ad_codex_language"))
        assertFalse(home.contains("R.drawable.ad_codex_audio"))
    }

    @Test
    fun selectedMatrixGlyphsAreRestoredWithoutMakingEveryIconMatrix() {
        val glyphs = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADExpressiveIcons.kt").readText()

        listOf("ADGlyph.PROMPT", "ADGlyph.PRIVACY", "ADGlyph.PERMISSIONS", "ADGlyph.FIRMWARE", "ADGlyph.BACK", "ADGlyph.NEXT")
            .forEach { glyph -> assertTrue("Selected matrix glyph must stay restored: $glyph", glyphs.contains(glyph)) }
        assertTrue(glyphs.contains("selectedMatrixPattern"))
        assertTrue(glyphs.contains("0111110\", \"1000001\", \"1010101"))
        assertTrue(glyphs.contains("rememberInfiniteTransition"))
        assertFalse(glyphs.contains("ADGlyph.SETTINGS"))
        assertFalse(glyphs.contains("ADGlyph.LANGUAGE -> listOf"))
        assertFalse(glyphs.contains("ADGlyph.AUDIO -> listOf"))
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
    fun greyWallpaperAndAlternateLogoRemainAuthoritative() {
        val appearance = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADAppearance.kt").readText()
        val app = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesApp.kt").readText()
        val settings = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeSettingsHubScreen.kt").readText()
        val pairing = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesPairingScreen.kt").readText()
        val components = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADComponents.kt").readText()
        val grey = sourceFile("src/main/res/drawable-nodpi/ad_wallpaper_grey.jpg")
        val v2 = sourceFile("src/main/res/drawable-nodpi/ad_wallpaper_v2.jpeg")
        val abstract = sourceFile("src/main/res/drawable-nodpi/ad_wallpaper_abstract.jpeg")
        val oldBackground = sourceFile("src/main/res/drawable-nodpi/ad_user_background.jpeg")
        val logo = sourceFile("src/main/res/drawable-nodpi/ad_user_app_icon.jpg")
        val oldLogo = sourceFile("src/main/res/drawable-nodpi/ad_user_app_icon.webp")

        assertTrue(grey.isFile && grey.length() > 0L)
        assertFalse(v2.exists())
        assertFalse(abstract.exists())
        assertFalse(oldBackground.exists())
        assertTrue(logo.isFile && logo.length() > 0L)
        assertFalse(oldLogo.exists())
        assertTrue(appearance.contains("enum class ADWallpaperStyle"))
        assertTrue(appearance.contains("R.drawable.ad_wallpaper_grey"))
        assertFalse(appearance.contains("R.drawable.ad_wallpaper_v2"))
        assertFalse(appearance.contains("R.drawable.ad_wallpaper_abstract"))
        assertTrue(appearance.contains("ADWallpaperPreferences"))
        assertTrue(appearance.contains("ContentScale.Crop"))
        assertTrue(app.contains("ADWallpaperBackground {"))
        assertTrue(pairing.contains("ADWallpaperBackground {"))
        assertTrue(settings.contains("ADWallpaperPicker()"))
        assertTrue(components.contains("R.drawable.ad_user_app_icon"))
    }

    @Test
    fun preservedIconsStayPreservedWhileLanguageAndAudioKeepTheirNewTreatment() {
        val components = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADComponents.kt").readText()
        val settings = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeSettingsHubScreen.kt").readText()
        val productSettings = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADProductSettingsScreens.kt").readText()
        val firmware = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADFirmwareScreen.kt").readText()
        val libraryHome = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADExpressiveLibraryHome.kt").readText()
        val libraryScreens = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeLibraryScreens.kt").readText()
        val prompt = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeConversationScreen.kt").readText()

        assertTrue(components.contains("ADGlyph.BACK"))
        assertTrue(components.contains("ADGlyph.NEXT"))
        assertTrue(components.contains("Icons.Outlined.ChatBubbleOutline"))
        assertFalse(components.contains("Icons.AutoMirrored.Rounded.ArrowBack"))
        assertFalse(components.contains("Icons.AutoMirrored.Rounded.KeyboardArrowRight"))

        assertTrue(settings.contains("ADGlyph.PRIVACY"))
        assertTrue(settings.contains("ADGlyph.PERMISSIONS"))
        assertTrue(settings.contains("ADGlyph.NEXT"))
        assertTrue(productSettings.contains("ADGlyph.PERMISSIONS"))
        assertTrue(firmware.contains("ADGlyph.FIRMWARE"))
        assertTrue(libraryHome.contains("glyph = ADGlyph.PROMPT"))
        assertTrue(libraryScreens.contains("ADGlyph.PROMPT"))
        assertTrue(prompt.contains("ADGlyph.PROMPT"))

        assertTrue(settings.contains("Icons.Outlined.Language"))
        assertTrue(settings.contains("ADSettingsIconTile("))
        assertTrue(productSettings.contains("Icons.Outlined.Language"))
        assertFalse(productSettings.contains("R.drawable.ad_codex_language"))
    }

    @Test
    fun pageLayoutForcesReadableDarkThemeContent() {
        val page = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADPageLayout.kt").readText()
        assertTrue(page.contains("LocalContentColor provides ADColors.Ink"))
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

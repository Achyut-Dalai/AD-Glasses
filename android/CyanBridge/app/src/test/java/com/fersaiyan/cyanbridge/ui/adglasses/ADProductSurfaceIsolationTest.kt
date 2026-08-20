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
    fun homeUsesMatchingAskPillAndLiveTranslationHero() {
        val home = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADHomeSurface.kt").readText()

        listOf("ASK AI", "LIVE TRANSLATION", "PHOTO", "VIDEO", "Translate", "Record", "Soundbites", "AUDIO", "CAPTURE")
            .forEach { label -> assertTrue("Home should keep $label", home.contains("\"$label\"")) }
        assertTrue(home.contains("ADTopBar(showBrand = false, showSettings = true, onSettings = onOpenSettings)"))
        assertFalse(home.contains("Text(\"AD GLASSES\""))
        assertFalse(home.contains("\"ASSISTANT\""))
        assertFalse(home.contains("\"THINK   ASK\""))

        val topBarIndex = home.indexOf("ADTopBar(showBrand = false")
        val lensIndex = home.indexOf("item { ADLensCard")
        val captureIndex = home.indexOf("ADSectionTitle(\"CAPTURE\")")
        val cameraIndex = home.indexOf("ADCameraSkillCard(")
        val askIndex = home.indexOf("ADAskSkillCard(")
        val deviceIndex = home.indexOf("ADLargeGlassesHero(")
        val audioIndex = home.indexOf("ADSectionTitle(\"AUDIO\")")
        listOf(topBarIndex, lensIndex, captureIndex, cameraIndex, askIndex, deviceIndex, audioIndex)
            .forEach { index -> assertTrue("Home hierarchy element missing", index >= 0) }
        assertTrue(topBarIndex < lensIndex)
        assertTrue(lensIndex < captureIndex)
        assertTrue(captureIndex < cameraIndex)
        assertTrue(captureIndex < askIndex)
        assertTrue(cameraIndex < deviceIndex)
        assertTrue(askIndex < deviceIndex)
        assertTrue(deviceIndex < audioIndex)

        assertTrue(home.contains("ADHomeMiniPill(\"PHOTO\", Modifier.weight(1f), onPhoto)"))
        assertTrue(home.contains("ADHomeMiniPill(\"VIDEO\", Modifier.weight(1f), onVideo)"))
        assertTrue(home.contains("ADHomeMiniPill(\"ASK AI\", Modifier.fillMaxWidth(), onClick)"))
        assertTrue(home.contains("translationActive = translateActive"))
        assertTrue(home.contains("onLiveTranslation = ::startLiveTranslation"))
        assertTrue(home.contains("onClick = onLiveTranslation"))
        assertTrue(home.contains("AssistantCapabilityCommand(AssistantCapability.TRANSLATOR, AssistantCapabilityAction.START)"))
        assertTrue(home.contains("R.drawable.ad_glasses_hero_v4"))
        assertTrue(home.contains(".height(184.dp)"))
        assertTrue(home.contains(".heightIn(min = 122.dp)"))
        assertTrue(home.contains("ADLensShutterArtwork(Modifier.weight(0.94f).height(96.dp))"))
        assertTrue(home.contains("Canvas(Modifier.fillMaxSize().padding(14.dp))"))
        assertTrue(home.contains("ADLensShutterArtwork("))
        assertTrue(home.contains("lens-focus-pulse"))
        assertTrue(home.contains("ADCameraArtwork("))
        assertTrue(home.contains("Modifier.fillMaxWidth().height(76.dp)"))
        assertTrue(home.contains("heightIn(min = 154.dp)"))
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

        listOf("Voice question", "Live speech", "Speech notes", "Look at it. Ask about it.")
            .forEach { unwanted -> assertFalse("Home should avoid explanatory action subtext", home.contains("\"$unwanted\"")) }
        assertFalse(home.contains("R.drawable.ad_codex_ask"))
        assertFalse(home.contains("R.drawable.ad_codex_video"))
        assertFalse(home.contains("R.drawable.ad_codex_language"))
        assertFalse(home.contains("R.drawable.ad_codex_audio"))
    }

    @Test
    fun settingsOwnsFirmwareAndDeviceCenterIsGone() {
        val models = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesModels.kt").readText()
        val app = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesApp.kt").readText()
        val settings = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeSettingsHubScreen.kt").readText()
        val deviceCenter = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesDeviceCenterScreen.kt")

        assertFalse(models.contains("DEVICE_CENTER"))
        assertFalse(app.contains("ADRoute.DEVICE_CENTER"))
        assertFalse(app.contains("ADGlassesDeviceCenterScreen("))
        assertFalse(deviceCenter.exists())
        assertTrue(settings.contains("ADPageLayout(onBack = onBack)"))
        assertFalse(settings.contains("ADScreenIntro(eyebrow = \"SYSTEM\", title = \"Settings\")"))
        assertTrue(settings.contains("ADSectionTitle(\"System\")"))
        assertTrue(settings.contains("ADGlyph.FIRMWARE"))
        assertTrue(settings.contains("title = \"Firmware\""))
        assertTrue(app.contains("onFirmware = { navigateTo(ADRoute.FIRMWARE) }"))
        assertFalse(settings.contains("onDevice:"))
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
    fun greyWallpaperLastIconAndLeanArtworkSetRemainAuthoritative() {
        val appearance = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADAppearance.kt").readText()
        val app = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesApp.kt").readText()
        val settings = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeSettingsHubScreen.kt").readText()
        val pairing = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesPairingScreen.kt").readText()
        val components = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADComponents.kt").readText()
        val home = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADHomeSurface.kt").readText()
        val welcome = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADWelcomeScreen.kt").readText()
        val drawableNoDpi = sourceFile("src/main/res/drawable-nodpi")
        val grey = File(drawableNoDpi, "ad_wallpaper_grey.jpg")
        val v2 = File(drawableNoDpi, "ad_wallpaper_v2.jpeg")
        val abstract = File(drawableNoDpi, "ad_wallpaper_abstract.jpeg")
        val lastIcon = File(drawableNoDpi, "ad_user_app_icon.png")
        val oldJpgIcon = File(drawableNoDpi, "ad_user_app_icon.jpg")
        val oldWebpIcon = File(drawableNoDpi, "ad_user_app_icon.webp")
        val heroV2 = File(drawableNoDpi, "ad_glasses_hero_v2.png")
        val heroV3 = File(drawableNoDpi, "ad_glasses_hero_v3.png")
        val heroV4 = File(drawableNoDpi, "ad_glasses_hero_v4.png")
        val iconSource = File(drawableNoDpi, "ad_glasses_icon_source.png")

        assertTrue(grey.isFile && grey.length() > 0L)
        assertFalse(v2.exists())
        assertFalse(abstract.exists())
        assertTrue(lastIcon.isFile && lastIcon.length() > 0L)
        assertFalse(oldJpgIcon.exists())
        assertFalse(oldWebpIcon.exists())
        assertFalse(heroV2.exists())
        assertFalse(heroV3.exists())
        assertTrue(heroV4.isFile && heroV4.length() > 0L)
        assertFalse(iconSource.exists())
        listOf(
            "ad_codex_ai.png", "ad_codex_ask.png", "ad_codex_audio.png", "ad_codex_automation.png",
            "ad_codex_brand.png", "ad_codex_language.png", "ad_codex_video.png",
        ).forEach { name -> assertFalse("Unused generated artwork must be removed: $name", File(drawableNoDpi, name).exists()) }

        assertTrue(appearance.contains("enum class ADWallpaperStyle"))
        assertTrue(appearance.contains("R.drawable.ad_wallpaper_grey"))
        assertFalse(appearance.contains("R.drawable.ad_wallpaper_v2"))
        assertFalse(appearance.contains("R.drawable.ad_wallpaper_abstract"))
        assertTrue(appearance.contains("ADWallpaperPreferences"))
        assertTrue(app.contains("ADWallpaperBackground {"))
        assertTrue(pairing.contains("ADWallpaperBackground {"))
        assertTrue(settings.contains("ADWallpaperPicker()"))
        assertTrue(components.contains("R.drawable.ad_user_app_icon"))
        assertTrue(home.contains("R.drawable.ad_glasses_hero_v4"))
        assertTrue(welcome.contains("R.drawable.ad_glasses_hero_v4"))
        assertFalse(settings.contains("R.drawable.ad_glasses_hero_v4"))
        assertFalse(settings.contains("painterResource"))
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
        assertTrue(settings.contains("ADGlyph.FIRMWARE"))
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
        assertTrue(page.contains("title: String? = null"))
    }

    @Test
    fun promptBehaviorAndDeviceGlyphRemovalStayProtected() {
        val prompt = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeConversationScreen.kt").readText()
        val pairing = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesPairingScreen.kt").readText()

        assertTrue(prompt.contains("What do you want to know?"))
        assertTrue(prompt.contains("Ask anything…"))
        assertTrue(prompt.contains("session.startNewConversation()"))
        assertTrue(prompt.contains("ADActivityWaveform"))
        assertFalse(pairing.contains("ADGlyph.DEVICE"))
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

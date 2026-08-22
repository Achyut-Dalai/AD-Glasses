package com.ad_glasses.ui.adglasses

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the installed AD Glasses product surface, not just navigation intent. */
class ADProductSurfaceIsolationTest {

    @Test
    fun replacedLegacyPageNamesAreAliasesNotLegacyActivities() {
        val manifest = sourceFile("src/main/AndroidManifest.xml").readText()
        val aliases = mapOf(
            ".ui.ChatListActivity" to ".ui.adglasses.ADConversationsRedirectActivity",
            ".ui.ChatThreadActivity" to ".ui.adglasses.ADConversationsRedirectActivity",
            ".ui.SettingsActivity" to ".ui.adglasses.ADSettingsRedirectActivity",
            ".ui.appearance.AppearanceActivity" to ".ui.adglasses.ADSettingsRedirectActivity",
            ".ui.CommunityPluginsActivity" to ".ui.adglasses.ADAiRedirectActivity",
            ".ui.PublishPluginActivity" to ".ui.adglasses.ADAiRedirectActivity",
            ".ui.notes.NotesListActivity" to ".ui.adglasses.ADNotesRedirectActivity",
            ".ui.notes.NoteDetailActivity" to ".ui.adglasses.ADNotesRedirectActivity",
            ".ui.recordings.RecordingsListActivity" to ".ui.adglasses.ADRecordingsRedirectActivity",
            ".ui.recordings.SyncedMediaGalleryActivity" to ".ui.adglasses.ADCapturesRedirectActivity",
        )

        aliases.forEach { (legacyName, target) ->
            val legacyActivity = Regex(
                """<activity\s+[^>]*android:name\s*=\s*\"${Regex.escape(legacyName)}\"""",
            )
            assertFalse(
                "$legacyName must never be registered as its old Activity UI",
                legacyActivity.containsMatchIn(manifest),
            )

            val alias = Regex(
                """<activity-alias\s+[^>]*android:name\s*=\s*\"${Regex.escape(legacyName)}\"[^>]*android:targetActivity\s*=\s*\"${Regex.escape(target)}\"""",
            )
            assertTrue(
                "$legacyName should resolve only through native AD redirect $target",
                alias.containsMatchIn(manifest),
            )
        }
    }

    @Test
    fun removedOnboardingActivitiesAreNotInstalledComponents() {
        val manifest = sourceFile("src/main/AndroidManifest.xml").readText()
        listOf(
            ".ui.BatteryOptimizationGuideActivity",
            ".ui.OnboardingFeatureActivity",
        ).forEach { removed -> assertFalse(manifest.contains("android:name=\"$removed\"")) }
    }

    @Test
    fun obsoleteOnboardingSourceIsDeleted() {
        val uiDir = sourceFile("src/main/java/com/ad_glasses/ui")
        assertFalse(File(uiDir, "BatteryOptimizationGuideActivity.kt").exists())
        assertFalse(File(uiDir, "OnboardingFeatureActivity.kt").exists())
        assertTrue(File(uiDir, "WelcomeActivity.kt").isFile)
    }

    @Test
    fun focusedAdUiFilesMatchCurrentProductSurface() {
        val uiDir = sourceFile("src/main/java/com/ad_glasses/ui/adglasses")
        listOf(
            "ADMainScreens.kt",
            "ADDetailScreens.kt",
            "ADModeNativeModel.kt",
            "ADPrimarySurfaces.kt",
            "ADModesScreen.kt",
            "ADNativeModeDetailScreen.kt",
            "ADLegacyRouteRedirectActivity.kt",
            "ADNativeCapabilityDetailScreen.kt",
        ).forEach { obsolete ->
            assertFalse("$obsolete is not part of the current AD product surface", File(uiDir, obsolete).exists())
        }

        assertTrue(File(uiDir, "ADHomeSurface.kt").isFile)
        assertTrue(File(uiDir, "ADNativeConversationScreen.kt").isFile)
        assertTrue(File(uiDir, "ADNativeLibraryScreens.kt").isFile)
        assertTrue(File(uiDir, "ADExpressiveLibraryHome.kt").isFile)
        assertTrue(File(uiDir, "ADNativeAiScreen.kt").isFile)
        assertTrue(File(uiDir, "ADAssistantAppsScreen.kt").isFile)
        assertTrue(File(uiDir, "ADNativeSettingsHubScreen.kt").isFile)
        assertTrue(File(uiDir, "ADSyncScreen.kt").isFile)
        assertTrue(File(uiDir, "ADFirmwareScreen.kt").isFile)
        assertTrue(File(uiDir, "ADRouteRedirectActivity.kt").isFile)
    }

    @Test
    fun primaryTabsAreExactlyHomePromptAiLibraryInThatOrder() {
        val models = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADGlassesModels.kt",
        ).readText()
        val enumBody = Regex("enum class ADTab\\(val label: String\\) \\{([\\s\\S]*?)\\n}")
            .find(models)?.groupValues?.get(1).orEmpty()

        val labels = Regex("\\w+\\(\"([^\"]+)\"\\)")
            .findAll(enumBody)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(listOf("Home", "Prompt", "AI", "Library"), labels)
        assertFalse(models.contains("TASKS(\"Tasks\")"))
    }

    @Test
    fun homeOwnsEverydayCapabilitiesWhileAiKeepsPersistentControls() {
        val home = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADHomeSurface.kt",
        ).readText()
        val ai = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADNativeAiScreen.kt",
        ).readText()
        val library = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADExpressiveLibraryHome.kt",
        ).readText()
        val app = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADGlassesApp.kt",
        ).readText()

        listOf("Ask AI", "Photo", "Video", "Translate", "Soundbites", "Audio", "Lens")
            .forEach { label -> assertTrue("Home should surface $label", home.contains("\"$label\"")) }
        assertTrue(home.contains("AssistantCapability.TRANSLATOR"))
        assertTrue(home.contains("AssistantCapability.MEETING_NOTES"))
        assertTrue(home.contains("onClick = host.onImageQuestion"))
        assertFalse(home.contains("Smart Lens"))
        assertFalse(home.contains("Search Web"))
        assertFalse(home.contains("ADHomeLink("))

        assertTrue(ai.contains("\"Timeline\""))
        assertTrue(ai.contains("\"Diary\""))
        assertTrue(ai.contains("\"Automation\""))
        assertTrue(ai.contains("ADAiProviderPill"))
        assertFalse(ai.contains("Switch("))
        assertFalse(ai.contains("\"DayNote\""))
        assertFalse("Translate belongs on Home, not AI", ai.contains("\"Translate\""))
        assertFalse("Soundbites belongs on Home, not AI", ai.contains("\"Soundbites\""))
        assertFalse("Cron is retired", ai.contains("\"Cron\""))
        assertFalse(ai.contains("\"Capabilities\""))
        assertFalse(ai.contains("\"Modes\""))
        assertTrue(ai.contains("\"Assistant apps\""))

        assertTrue(library.contains("ADLibraryDestinationRow("))
        assertFalse(library.contains("ON THIS PHONE"))
        assertFalse(library.contains("compact = true"))

        assertTrue(app.contains("ADTab.LIBRARY -> ADExpressiveLibraryHome("))
        assertTrue(app.contains("ADRoute.AI_ASSISTANT_APPS -> ADAssistantAppsScreen"))
        assertFalse(app.contains("ADRoute.CAPABILITY_DETAIL"))
        assertFalse(app.contains("ADNativeCapabilityDetailScreen("))
    }

    @Test
    fun capabilityNavigationDoesNotUseRetiredModesRoute() {
        val redirects = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADRouteRedirectActivity.kt",
        ).readText()
        val manifest = sourceFile("src/main/AndroidManifest.xml").readText()

        assertTrue(redirects.contains("ADExternalDestination.AI"))
        assertTrue(redirects.contains("abstract class ADRouteRedirectActivity"))
        assertTrue(redirects.contains("class ADAiRedirectActivity"))
        assertFalse(redirects.contains("ADExternalDestination.MODES"))
        assertFalse(redirects.contains("class ADModesRedirectActivity"))
        assertFalse(redirects.contains("class ADLegacyRouteRedirectActivity"))
        assertFalse(manifest.contains("ADModesRedirectActivity"))
    }

    @Test
    fun capabilityCommandArchitectureDoesNotUseRetiredModeTypesOrCron() {
        val orchestratorDir = sourceFile("src/main/java/com/ad_glasses/ai/orchestrator")
        val router = File(orchestratorDir, "AssistantCapabilityCommandRouter.kt").readText()
        val executor = File(orchestratorDir, "AndroidCapabilityCommandExecutor.kt").readText()

        assertFalse(File(orchestratorDir, "AssistantModeCommandRouter.kt").exists())
        assertFalse(File(orchestratorDir, "AndroidModeCommandExecutor.kt").exists())
        assertFalse(router.contains("ERRAND_BRAIN"))
        assertFalse(router.contains("\\b(cron|errand"))
        assertFalse(executor.contains("ErrandBrainService"))
        assertFalse(executor.contains("ErrandBrainPreferences"))
    }

    @Test
    fun pairingCopyIsHardwareNeutralAndSafeInsetAware() {
        val pairing = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADGlassesPairingScreen.kt",
        ).readText()
        assertTrue(pairing.contains("Looking for nearby glasses"))
        assertTrue(pairing.contains("WindowInsets.safeDrawing"))
        assertFalse("Pairing presentation must not hard-code current hardware", pairing.contains("HeyCyan"))
    }

    @Test
    fun promptUsesFreshSessionsAndRealActivityIndicators() {
        val prompt = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADNativeConversationScreen.kt",
        ).readText()
        val rich = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADConversationRichContent.kt",
        ).readText()

        assertTrue(prompt.contains("Icons.Outlined.Terminal"))
        assertTrue(prompt.contains("ADColors.SurfaceSubtle"))
        assertTrue(prompt.contains("ADAssistantTurn"))
        assertTrue(prompt.contains("What do you want to know?"))
        assertTrue(prompt.contains("Ask AI…"))
        assertTrue(prompt.contains("session.startNewConversation()"))
        assertTrue(prompt.contains("ADActivityWaveform"))
        assertTrue(prompt.contains("AudioSessionCoordinator.isBusy()"))
        assertTrue(prompt.contains("MeetingCapturePrefs.getState(context).isRecording"))
        assertFalse(prompt.contains("New chat"))
        assertTrue(rich.contains("ADConversationLinkKind.IMAGE"))
        assertTrue(rich.contains("ADConversationLinkKind.VIDEO"))
        assertTrue(rich.contains("ADConversationLinkKind.AUDIO"))
        assertTrue(rich.contains("ADConversationLinkKind.DOCUMENT"))
    }

    @Test
    fun inlineCapabilityControlsReplaceGenericDetailPageAndStatusText() {
        val uiDir = sourceFile("src/main/java/com/ad_glasses/ui/adglasses")
        val home = File(uiDir, "ADHomeSurface.kt").readText()
        val ai = File(uiDir, "ADNativeAiScreen.kt").readText()

        assertFalse(File(uiDir, "ADNativeCapabilityDetailScreen.kt").exists())
        assertTrue(home.contains("toggleCapability(AssistantCapability.TRANSLATOR)"))
        assertTrue(home.contains("toggleCapability(AssistantCapability.MEETING_NOTES)"))
        assertTrue(ai.contains("AssistantCapability.VISUAL_DIARY"))
        assertTrue(ai.contains("AssistantCapability.AUTO_DIARY"))
        assertTrue(ai.contains("AssistantCapability.LOCAL_AGENT"))
        assertTrue(ai.contains("ADAiCapabilityCard("))
        assertTrue(ai.contains("ADAiCapabilityRow("))
        assertFalse(ai.contains("Switch("))
        assertFalse(ai.contains("ADStatusChip("))
        assertFalse(home.contains("ADStatusChip(\"OFF\""))
        assertFalse(home.contains("ADStatusChip(\"ON\""))
    }

    @Test
    fun welcomeUsesSeparateProductStatementAndHeroStage() {
        val welcome = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADWelcomeScreen.kt",
        ).readText()
        assertTrue(welcome.contains("text = \"YOUR GLASSES\""))
        assertTrue(welcome.contains("text = \"YOUR AI\""))
        assertTrue(welcome.contains("text = \"YOUR DATA\""))
        assertTrue(welcome.contains("R.drawable.ad_glasses_hero_v4"))
        assertTrue(welcome.contains("RoundedCornerShape(28.dp)"))
    }

    @Test
    fun automationStaysProductFacingWhileCronIsRetired() {
        val models = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADGlassesModels.kt",
        ).readText()
        val strings = sourceFile("src/main/res/values/strings.xml").readText()
        val prefs = sourceFile("src/main/java/com/ad_glasses/ui/CommunityPluginPrefs.kt").readText()
        val service = sourceFile(
            "src/main/java/com/ad_glasses/plugins/errandbrain/ErrandBrainService.kt",
        ).readText()

        assertTrue(models.contains("\"Automation\""))
        assertTrue(models.contains("\"Local Agent\""))
        assertFalse(models.contains("ERRAND_BRAIN(\n        \"Cron\""))
        assertTrue(models.contains("@Deprecated(\"Cron is removed from the AD Glasses product\")"))
        assertTrue(strings.contains("<string name=\"local_agent_accessibility_service_label\">Glasses automation</string>"))
        assertTrue(prefs.contains("if (pluginId == NativePluginIds.ERRAND_BRAIN) return false"))
        assertTrue(service.contains("START_NOT_STICKY"))
        assertFalse(service.contains("SpeechRecognizer"))
    }

    @Test
    fun deviceCenterKeepsSyncFirmwareAndDiagnosticsAsStableDestinations() {
        val deviceCenter = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADGlassesDeviceCenterScreen.kt",
        ).readText()
        val app = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADGlassesApp.kt",
        ).readText()

        assertTrue(deviceCenter.contains("title = \"Sync media\""))
        assertTrue(deviceCenter.contains("title = \"Firmware\""))
        assertTrue(deviceCenter.contains("title = \"Advanced\""))
        assertTrue(app.contains("ADRoute.SYNC -> ADSyncScreen"))
        assertTrue(app.contains("ADRoute.FIRMWARE -> ADFirmwareScreen"))
    }

    @Test
    fun builtProductKeepsAdGlassesIdentityAndAlphaVersion() {
        val strings = sourceFile("src/main/res/values/strings.xml").readText()
        val gradle = sourceFile("build.gradle").readText()
        val manifest = sourceFile("src/main/AndroidManifest.xml").readText()

        assertTrue(strings.contains("<string name=\"app_name\">AD Glasses</string>"))
        assertFalse("System-facing strings must not expose ADGlasses branding", strings.contains("ADGlasses"))
        assertTrue(gradle.contains("versionName = \"alpha\""))
        assertTrue(gradle.contains("outputFileName.set(\"AD-Glasses.apk\")"))
        assertTrue(manifest.contains("android:label=\"AD Glasses notification access\""))
    }

    @Test
    fun nativeAdUiDoesNotImportReplacedProductActivitiesOrSharedNavigation() {
        val uiDir = sourceFile("src/main/java/com/ad_glasses/ui/adglasses")
        assertTrue("AD UI source directory should exist", uiDir.isDirectory)

        val forbiddenImports = listOf(
            "import com.ad_glasses.MainActivity",
            "import com.ad_glasses.ui.ChatListActivity",
            "import com.ad_glasses.ui.ChatThreadActivity",
            "import com.ad_glasses.ui.SettingsActivity",
            "import com.ad_glasses.ui.CommunityPluginsActivity",
            "import com.ad_glasses.ui.PublishPluginActivity",
            "import com.ad_glasses.ui.notes.NotesListActivity",
            "import com.ad_glasses.ui.recordings.RecordingsListActivity",
            "import com.ad_glasses.ui.recordings.SyncedMediaGalleryActivity",
            "import com.ad_glasses.shared.navigation.AppDestination",
        )

        uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val source = file.readText()
                forbiddenImports.forEach { token ->
                    assertFalse("${file.name} must not import replaced product route $token", source.contains(token))
                }
            }
    }

    @Test
    fun adNavigationRootDoesNotUseCompatibilityEscapeCallbacksOrOldSurfaces() {
        val source = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADGlassesApp.kt",
        ).readText()

        listOf(
            "host.onOpenChat",
            "host.onOpenChatWithPrompt",
            "host.onOpenPhotos",
            "host.onOpenMedia",
            "host.onOpenNotes",
            "host.onOpenLegacySettings",
            "host.onOpenAutomationSettings",
            "ADTasksScreen(",
            "ADDeviceCenterScreen(",
            "ADSettingsScreen(",
            "ADAiServicesScreen(",
            "ADAdvancedCenterScreen(",
        ).forEach { token ->
            assertFalse("AD navigation root must not use compatibility token $token", source.contains(token))
        }
    }

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct

        val fromRepoRoot = File("android/AD-Glasses/app", relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot

        return direct
    }
}

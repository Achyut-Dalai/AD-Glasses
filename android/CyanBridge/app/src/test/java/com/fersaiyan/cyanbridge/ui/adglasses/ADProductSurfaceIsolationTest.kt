package com.fersaiyan.cyanbridge.ui.adglasses

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
        val uiDir = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui")
        assertFalse(File(uiDir, "BatteryOptimizationGuideActivity.kt").exists())
        assertFalse(File(uiDir, "OnboardingFeatureActivity.kt").exists())
        assertTrue(File(uiDir, "WelcomeActivity.kt").isFile)
    }

    @Test
    fun obsoleteAdUiBundlesAndTasksPageStayDeleted() {
        val uiDir = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses")
        listOf(
            "ADMainScreens.kt",
            "ADDetailScreens.kt",
            "ADModeNativeModel.kt",
            "ADPrimarySurfaces.kt",
            "ADModesScreen.kt",
            "ADNativeModeDetailScreen.kt",
        ).forEach { obsolete ->
            assertFalse("$obsolete is not part of the current AD product surface", File(uiDir, obsolete).exists())
        }

        assertTrue(File(uiDir, "ADHomeSurface.kt").isFile)
        assertTrue(File(uiDir, "ADNativeConversationScreen.kt").isFile)
        assertTrue(File(uiDir, "ADNativeLibraryScreens.kt").isFile)
        assertTrue(File(uiDir, "ADNativeAiScreen.kt").isFile)
        assertTrue(File(uiDir, "ADNativeCapabilityDetailScreen.kt").isFile)
        assertTrue(File(uiDir, "ADAssistantAppsScreen.kt").isFile)
        assertTrue(File(uiDir, "ADNativeSettingsHubScreen.kt").isFile)
        assertTrue(File(uiDir, "ADSyncScreen.kt").isFile)
        assertTrue(File(uiDir, "ADFirmwareScreen.kt").isFile)
    }

    @Test
    fun primaryTabsAreExactlyHomePromptAiLibraryInThatOrder() {
        val models = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesModels.kt",
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
    fun aiUsesCurrentProductNamesWithoutCapabilitiesHeading() {
        val ai = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeAiScreen.kt",
        ).readText()
        val models = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesModels.kt",
        ).readText()
        val app = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesApp.kt",
        ).readText()

        assertTrue("AI should surface Translate", ai.contains("\"Translate\""))
        assertFalse("AI should not show a redundant Capabilities heading", ai.contains("\"Capabilities\""))
        assertFalse("AI should not show the retired Modes heading", ai.contains("\"Modes\""))
        assertTrue(ai.contains("ADAutomation.MEETING_NOTES.title"))
        assertTrue(ai.contains("ADAutomation.AUTO_DIARY.title"))
        assertTrue(ai.contains("ADAutomation.VISUAL_DIARY.title"))
        assertTrue(ai.contains("ADAutomation.ERRAND_BRAIN.title"))
        assertTrue(ai.contains("ADAutomation.LOCAL_AGENT.title"))
        assertTrue(models.contains("\"Soundbites\""))
        assertTrue(models.contains("\"DayNote\""))
        assertTrue(models.contains("\"Timeline\""))
        assertTrue(models.contains("\"Cron\""))
        assertTrue(models.contains("\"Automation\""))
        assertTrue(ai.contains("\"Apps & Android actions\""))
        assertFalse(ai.contains("\"Setup required\""))
        assertTrue(ai.contains("Icons.Outlined.EventRepeat"))
        assertTrue(ai.contains("\"Assistant apps\""))
        assertFalse(ai.contains("ADTopBar(title = \"AI\")"))
        assertTrue(app.contains("ADRoute.AI_ASSISTANT_APPS -> ADAssistantAppsScreen"))
        assertTrue(app.contains("ADRoute.CAPABILITY_DETAIL -> ADNativeCapabilityDetailScreen"))
        assertFalse(app.contains("ADRoute.TASK_DETAIL"))
        assertFalse(app.contains("ADTasksScreen("))
    }

    @Test
    fun capabilityNavigationDoesNotUseRetiredModesRoute() {
        val redirects = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADLegacyRouteRedirectActivity.kt",
        ).readText()
        val manifest = sourceFile("src/main/AndroidManifest.xml").readText()

        assertTrue(redirects.contains("ADExternalDestination.AI"))
        assertTrue(redirects.contains("class ADAiRedirectActivity"))
        assertFalse(redirects.contains("ADExternalDestination.MODES"))
        assertFalse(redirects.contains("class ADModesRedirectActivity"))
        assertFalse(manifest.contains("ADModesRedirectActivity"))
    }

    @Test
    fun capabilityCommandArchitectureDoesNotUseRetiredModeTypes() {
        val orchestratorDir = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ai/orchestrator")
        assertFalse(File(orchestratorDir, "AssistantModeCommandRouter.kt").exists())
        assertFalse(File(orchestratorDir, "AndroidModeCommandExecutor.kt").exists())
        assertTrue(File(orchestratorDir, "AssistantCapabilityCommandRouter.kt").isFile)
        assertTrue(File(orchestratorDir, "AndroidCapabilityCommandExecutor.kt").isFile)
    }

    @Test
    fun pairingCopyIsHardwareNeutralAndSafeInsetAware() {
        val pairing = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesPairingScreen.kt",
        ).readText()
        assertTrue(pairing.contains("Looking for nearby glasses"))
        assertTrue(pairing.contains("WindowInsets.safeDrawing"))
        assertFalse("Pairing presentation must not hard-code current hardware", pairing.contains("HeyCyan"))
    }

    @Test
    fun promptUsesFreshSessionsAndRealActivityIndicators() {
        val prompt = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeConversationScreen.kt",
        ).readText()
        val rich = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADConversationRichContent.kt",
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
        assertFalse(prompt.contains("Icons.Outlined.CameraAlt"))
        assertFalse(prompt.contains("Icons.Outlined.Mic"))
        assertTrue(rich.contains("ADConversationLinkKind.IMAGE"))
        assertTrue(rich.contains("ADConversationLinkKind.VIDEO"))
        assertTrue(rich.contains("ADConversationLinkKind.AUDIO"))
        assertTrue(rich.contains("ADConversationLinkKind.DOCUMENT"))
    }

    @Test
    fun capabilityDetailUsesMonochromeEditorialToggleInsteadOfStartStopButtons() {
        val detail = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeCapabilityDetailScreen.kt",
        ).readText()
        assertTrue(detail.contains("Switch("))
        assertTrue(detail.contains("automation.capabilityIcon()"))
        assertTrue(detail.contains("ADCapabilityDetailRow("))
        assertTrue(detail.contains("ADColors.SurfaceSubtle"))
        assertTrue(detail.contains("Icons.Outlined.EventRepeat"))
        assertTrue(detail.contains("isExclusiveVoiceCapability"))
        assertTrue(detail.contains("will switch live listening"))
        assertFalse(detail.contains("capabilityPalette()"))
        assertFalse(detail.contains("ADCapabilityPalette"))
        assertFalse(detail.contains("OutlinedButton("))
        assertFalse(detail.contains("Button("))
        assertFalse(detail.contains("\"Start task\""))
        assertFalse(detail.contains("\"Stop task\""))
        assertFalse(detail.contains("this task by voice"))
    }

    @Test
    fun welcomeUsesSeparateProductStatementAndHeroStage() {
        val welcome = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADWelcomeScreen.kt",
        ).readText()
        assertTrue(welcome.contains("text = \"YOUR GLASSES\""))
        assertTrue(welcome.contains("text = \"YOUR AI\""))
        assertTrue(welcome.contains("text = \"YOUR DATA\""))
        assertTrue(welcome.contains("R.drawable.ad_glasses_hero_v4"))
        assertTrue(welcome.contains("RoundedCornerShape(28.dp)"))
        assertFalse(welcome.contains("alpha(0.16f)"))
        assertFalse(welcome.contains("A private brain for the glasses you wear."))
        assertFalse(welcome.contains("Connect when you are ready."))
    }

    @Test
    fun automationUsesProductFacingLabelsWhileRuntimeIdentifiersStayCompatible() {
        val models = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesModels.kt",
        ).readText()
        val strings = sourceFile("src/main/res/values/strings.xml").readText()
        val manifest = sourceFile("src/main/AndroidManifest.xml").readText()

        assertTrue(models.contains("\"Automation\""))
        assertTrue(models.contains("\"Cron\""))
        assertTrue(models.contains("\"Local Agent\""))
        assertTrue(models.contains("\"Errand Brain\""))
        assertTrue(strings.contains("<string name=\"local_agent_accessibility_service_label\">Glasses automation</string>"))
        assertTrue(manifest.contains("android:label=\"Automation settings\""))
        assertTrue(manifest.contains("android:label=\"Cron settings\""))
        assertTrue(manifest.contains("android:label=\"DayNote settings\""))
        assertTrue(manifest.contains("android:label=\"Timeline settings\""))
    }

    @Test
    fun deviceCenterKeepsSyncFirmwareAndDiagnosticsAsStableDestinations() {
        val deviceCenter = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesDeviceCenterScreen.kt",
        ).readText()
        val app = sourceFile(
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesApp.kt",
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
        assertFalse("System-facing strings must not expose CyanBridge branding", strings.contains("CyanBridge"))
        assertTrue(gradle.contains("versionName = \"alpha\""))
        assertTrue(
            "APK artifact should use the AD Glasses product name",
            gradle.contains("outputFileName = \"AD-Glasses.apk\""),
        )
        assertTrue(manifest.contains("android:label=\"AD Glasses notification access\""))
    }

    @Test
    fun nativeAdUiDoesNotImportReplacedProductActivitiesOrSharedNavigation() {
        val uiDir = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses")
        assertTrue("AD UI source directory should exist", uiDir.isDirectory)

        val forbiddenImports = listOf(
            "import com.fersaiyan.cyanbridge.ui.ChatListActivity",
            "import com.fersaiyan.cyanbridge.ui.ChatThreadActivity",
            "import com.fersaiyan.cyanbridge.ui.SettingsActivity",
            "import com.fersaiyan.cyanbridge.ui.CommunityPluginsActivity",
            "import com.fersaiyan.cyanbridge.ui.PublishPluginActivity",
            "import com.fersaiyan.cyanbridge.ui.notes.NotesListActivity",
            "import com.fersaiyan.cyanbridge.ui.recordings.RecordingsListActivity",
            "import com.fersaiyan.cyanbridge.ui.recordings.SyncedMediaGalleryActivity",
            "import com.fersaiyan.cyanbridge.shared.navigation.AppDestination",
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
            "src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesApp.kt",
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

        val fromRepoRoot = File("android/CyanBridge/app", relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot

        return direct
    }
}

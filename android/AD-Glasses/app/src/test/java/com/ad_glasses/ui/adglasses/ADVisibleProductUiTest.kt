package com.ad_glasses.ui.adglasses

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-level guardrails for the Compose-only AD Glasses product UI. */
class ADVisibleProductUiTest {

    @Test
    fun composeShellOwnsVisibleProductNavigation() {
        val app = appFile("src/main/java/com/ad_glasses/ui/adglasses/ADGlassesApp.kt").readText()
        assertTrue(app.contains("fun ADGlassesApp("))
        listOf(
            "ADHomeSurface(",
            "ADNativeConversationScreen(",
            "ADExpressiveLibraryHome(",
            "ADNativeSettingsHubScreen(",
            "ADGlassesDeviceCenterScreen(",
            "ADNativeCapturesScreen(",
            "ADNativeRecordingsScreen(",
            "ADNativeNotesScreen(",
        ).forEach { screen -> assertTrue("Compose shell must render $screen", app.contains(screen)) }
        assertTrue(app.contains("ADTab.AI"))
        assertFalse(app.contains("ADNativeAiScreen("))
    }

    @Test
    fun aiConversationHubIsMinimalAndManageable() {
        val screen = appFile("src/main/java/com/ad_glasses/ui/adglasses/ADNativeConversationScreen.kt").readText()
        val components = appFile("src/main/java/com/ad_glasses/ui/adglasses/ADComponents.kt").readText()

        assertTrue(screen.contains("Text("AI""))
        assertTrue(screen.contains("ADConversationHistory("))
        assertTrue(screen.contains("Rename conversation"))
        assertTrue(screen.contains("Delete conversation?"))
        assertTrue(screen.contains("Clear AI conversations?"))
        assertTrue(screen.contains("session.startNewConversation()"))
        assertFalse(screen.contains("ADConversationRouteDisclosure("))
        assertFalse(screen.contains("ADPromptSuggestion("))
        assertFalse(screen.contains("What did I capture today?"))
        assertFalse(screen.contains("AD-owned ${internalProvider.label} conversation"))
        assertTrue(components.contains("ADTab.AI -> Icons.Rounded.AutoAwesome"))
        assertFalse(components.contains("Icons.Outlined.Terminal"))
    }

    @Test
    fun mainActivityMountsComposeInsteadOfReactNative() {
        val main = appFile("src/main/java/com/ad_glasses/MainActivity.kt").readText()
        assertTrue(main.contains("setContent {"))
        assertTrue(main.contains("ADGlassesApp("))
        assertFalse(main.contains("ReactActivity"))
        assertFalse(main.contains("ReactRootView"))
        assertFalse(main.contains("com.facebook.react"))
    }

    @Test
    fun reactNativeArtifactsRemainAbsent() {
        listOf(
            ADGlassesFile("package.json"),
            ADGlassesFile("package-lock.json"),
            ADGlassesFile("metro.config.js"),
            ADGlassesFile("metro.config.cjs"),
            ADGlassesFile("src/App.tsx"),
            ADGlassesFile("src/screens"),
            ADGlassesFile("src/navigation"),
            appFile("src/main/java/com/ad_glasses/ui/reactnative"),
        ).forEach { artifact -> assertFalse("React Native artifact must stay removed: ${artifact.path}", artifact.exists()) }
    }

    @Test
    fun inheritedActivityNamesRouteIntoComposeRedirects() {
        val manifest = appFile("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:name=\".ui.ChatListActivity\" android:targetActivity=\".ui.adglasses.ADConversationsRedirectActivity\""))
        assertTrue(manifest.contains("android:name=\".ui.SettingsActivity\" android:targetActivity=\".ui.adglasses.ADSettingsRedirectActivity\""))
        assertTrue(manifest.contains("android:name=\".ui.recordings.SyncedMediaGalleryActivity\" android:targetActivity=\".ui.adglasses.ADCapturesRedirectActivity\""))
        assertTrue(manifest.contains("android:name=\".ui.notes.NotesListActivity\" android:targetActivity=\".ui.adglasses.ADNotesRedirectActivity\""))
    }

    @Test
    fun retiredLegacyScreenLayoutsRemainAbsent() {
        listOf(
            "activity_chat_list.xml",
            "activity_chat_thread.xml",
            "activity_community_plugins.xml",
            "activity_note_detail.xml",
            "activity_notes_list.xml",
            "activity_publish_plugin.xml",
            "activity_recordings_list.xml",
            "activity_synced_media_gallery.xml",
            "activity_welcome.xml",
            "activity_device_bind.xml",
        ).forEach { layout ->
            val file = appFile("src/main/res/layout/$layout")
            assertFalse("Retired legacy layout must stay removed: $layout", file.exists())
        }
    }

    @Test
    fun pairingScreenIsComposeOwned() {
        val pairingActivity = appFile("src/main/java/com/ad_glasses/ui/DeviceBindActivity.kt").readText()
        assertTrue(pairingActivity.contains("ADGlassesPairingScreen"))
        assertTrue(pairingActivity.contains("setContent"))
        assertFalse(pairingActivity.contains("setContentView(R.layout.activity_device_bind)"))
    }

    @Test
    fun deviceCenterOwnsCloudAndLocalAiConfiguration() {
        val deviceCenter = appFile("src/main/java/com/ad_glasses/ui/adglasses/ADGlassesDeviceCenterScreen.kt").readText()
        val ai = appFile("src/main/java/com/ad_glasses/ui/adglasses/ADNativeAiScreen.kt").readText()

        assertTrue(deviceCenter.contains("ADSectionTitle(\"AI\")"))
        assertTrue(deviceCenter.contains("ADDeviceAiSection("))
        assertFalse(deviceCenter.contains("ADSectionTitle(\"Capabilities\")"))
        assertTrue(ai.contains("ADAiProviderPill(\"Cloud\""))
        assertTrue(ai.contains("ADAiProviderPill(\"Local\""))
        assertTrue(ai.contains("title = \"Cloud\""))
        assertTrue(ai.contains("title = \"Local\""))
        assertTrue(ai.contains("tint = Color.Black"))
        assertFalse(ai.contains("DayNote"))
        assertFalse(ai.contains("Visual Diary"))
        assertFalse(ai.contains("Timeline"))
    }

    @Test
    fun homeStartsCoreGlassesActionsAndOwnsLiveTranslateAndSoundbites() {
        val home = appFile("src/main/java/com/ad_glasses/ui/adglasses/ADHomeSurface.kt").readText()
        assertTrue(home.contains("onClick = host.onVoiceQuestion"))
        assertTrue(home.contains("onClick = host.onCapturePhoto"))
        assertTrue(home.contains("onClick = host.onToggleVideo"))
        assertTrue(home.contains("onClick = host.onImageQuestion"))
        assertTrue(home.contains("toggleCapability(AssistantCapability.TRANSLATOR)"))
        assertTrue(home.contains("toggleCapability(AssistantCapability.MEETING_NOTES)"))
        listOf("Ask AI", "Photo", "Video", "Translate", "Soundbites", "Audio", "Lens")
            .forEach { label -> assertTrue(home.contains("\"$label\"")) }
        assertFalse(home.contains("Search Web"))
        assertFalse(home.contains("Smart Lens"))
    }

    @Test
    fun recordingMapperRemainsIndependentOfRetiredActivity() {
        val mapper = appFile("src/main/java/com/ad_glasses/ui/recordings/RecordingItemMapper.kt").readText()
        assertTrue(mapper.contains("fun CaptureSession.toRecordingItem"))
        assertFalse(mapper.contains("class RecordingsListActivity"))
    }

    private fun appFile(relativePath: String): File = firstExisting(
        File(relativePath),
        File("android/AD-Glasses/app", relativePath),
    )

    private fun ADGlassesFile(relativePath: String): File = firstExisting(
        File("../$relativePath"),
        File("android/AD-Glasses", relativePath),
    )

    private fun firstExisting(vararg candidates: File): File =
        candidates.firstOrNull { it.exists() } ?: candidates.first()
}

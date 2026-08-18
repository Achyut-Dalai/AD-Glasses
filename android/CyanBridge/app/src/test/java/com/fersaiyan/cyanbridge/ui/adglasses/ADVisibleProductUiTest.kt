package com.fersaiyan.cyanbridge.ui.adglasses

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-level guardrails for the Compose-only AD Glasses product UI. */
class ADVisibleProductUiTest {

    @Test
    fun composeShellOwnsVisibleProductNavigation() {
        val app = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADGlassesApp.kt").readText()
        assertTrue(app.contains("fun ADGlassesApp("))
        listOf(
            "ADHomeSurface(",
            "ADNativeConversationScreen(",
            "ADNativeAiScreen(",
            "ADExpressiveLibraryHome(",
            "ADNativeSettingsHubScreen(",
            "ADGlassesDeviceCenterScreen(",
            "ADNativeCapturesScreen(",
            "ADNativeRecordingsScreen(",
            "ADNativeNotesScreen(",
        ).forEach { screen -> assertTrue("Compose shell must render $screen", app.contains(screen)) }
        assertFalse(app.contains("ADNativeCapabilityDetailScreen("))
    }

    @Test
    fun mainActivityMountsComposeInsteadOfReactNative() {
        val main = appFile("src/main/java/com/fersaiyan/cyanbridge/MainActivity.kt").readText()
        assertTrue(main.contains("setContent {"))
        assertTrue(main.contains("ADGlassesApp("))
        assertFalse(main.contains("ReactActivity"))
        assertFalse(main.contains("ReactRootView"))
        assertFalse(main.contains("com.facebook.react"))
    }

    @Test
    fun reactNativeArtifactsRemainAbsent() {
        listOf(
            cyanBridgeFile("package.json"),
            cyanBridgeFile("package-lock.json"),
            cyanBridgeFile("metro.config.js"),
            cyanBridgeFile("metro.config.cjs"),
            cyanBridgeFile("src/App.tsx"),
            cyanBridgeFile("src/screens"),
            cyanBridgeFile("src/navigation"),
            appFile("src/main/java/com/fersaiyan/cyanbridge/ui/reactnative"),
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
        val pairingActivity = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/DeviceBindActivity.kt").readText()
        assertTrue(pairingActivity.contains("ADGlassesPairingScreen"))
        assertTrue(pairingActivity.contains("setContent"))
        assertFalse(pairingActivity.contains("setContentView(R.layout.activity_device_bind)"))
    }

    @Test
    fun aiPageKeepsOnlyPersistentCapabilitiesAndConfiguration() {
        val ai = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADNativeAiScreen.kt").readText()
        assertFalse(ai.contains("Text(\"Capabilities\""))
        assertTrue(ai.contains("AssistantCapability.VISUAL_DIARY"))
        assertTrue(ai.contains("AssistantCapability.AUTO_DIARY"))
        assertTrue(ai.contains("AssistantCapability.LOCAL_AGENT"))
        assertTrue(ai.contains("Switch("))
        assertFalse(ai.contains("AssistantCapability.TRANSLATOR"))
        assertFalse(ai.contains("AssistantCapability.MEETING_NOTES"))
        assertFalse(ai.contains("ERRAND_BRAIN"))
        assertFalse(ai.contains("\"OFF\""))
    }

    @Test
    fun homeStartsCoreGlassesActionsAndOwnsLiveTranslateAndSoundbites() {
        val home = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/adglasses/ADHomeSurface.kt").readText()
        assertTrue(home.contains("onClick = host.onVoiceQuestion"))
        assertTrue(home.contains("onClick = host.onCapturePhoto"))
        assertTrue(home.contains("onClick = host.onToggleVideo"))
        assertTrue(home.contains("onClick = host.onImageQuestion"))
        assertTrue(home.contains("toggleCapability(AssistantCapability.TRANSLATOR)"))
        assertTrue(home.contains("toggleCapability(AssistantCapability.MEETING_NOTES)"))
        listOf("Ask AI", "Photo", "Video", "Translate", "Soundbites", "Audio", "Smart Lens")
            .forEach { label -> assertTrue(home.contains("\"$label\"")) }
        assertFalse(home.contains("Search Web"))
    }

    @Test
    fun recordingMapperRemainsIndependentOfRetiredActivity() {
        val mapper = appFile("src/main/java/com/fersaiyan/cyanbridge/ui/recordings/RecordingItemMapper.kt").readText()
        assertTrue(mapper.contains("fun CaptureSession.toRecordingItem"))
        assertFalse(mapper.contains("class RecordingsListActivity"))
    }

    private fun appFile(relativePath: String): File = firstExisting(
        File(relativePath),
        File("android/CyanBridge/app", relativePath),
    )

    private fun cyanBridgeFile(relativePath: String): File = firstExisting(
        File("../$relativePath"),
        File("android/CyanBridge", relativePath),
    )

    private fun firstExisting(vararg candidates: File): File =
        candidates.firstOrNull { it.exists() } ?: candidates.first()
}

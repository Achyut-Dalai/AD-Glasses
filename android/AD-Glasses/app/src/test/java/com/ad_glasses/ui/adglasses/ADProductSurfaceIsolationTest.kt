package com.ad_glasses.ui.adglasses

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the currently shipped AD Glasses product surface and removed-feature boundaries. */
class ADProductSurfaceIsolationTest {

    @Test
    fun inheritedActivityNamesRemainRedirectAliasesOnly() {
        val manifest = sourceFile("src/main/AndroidManifest.xml").readText()
        val aliases = mapOf(
            ".ui.ChatListActivity" to ".ui.adglasses.ADConversationsRedirectActivity",
            ".ui.ChatThreadActivity" to ".ui.adglasses.ADConversationsRedirectActivity",
            ".ui.SettingsActivity" to ".ui.adglasses.ADSettingsRedirectActivity",
            ".ui.CommunityPluginsActivity" to ".ui.adglasses.ADAiRedirectActivity",
            ".ui.notes.NotesListActivity" to ".ui.adglasses.ADNotesRedirectActivity",
            ".ui.recordings.RecordingsListActivity" to ".ui.adglasses.ADRecordingsRedirectActivity",
            ".ui.recordings.SyncedMediaGalleryActivity" to ".ui.adglasses.ADCapturesRedirectActivity",
        )

        aliases.forEach { (legacyName, target) ->
            assertFalse(
                Regex("""<activity\s+[^>]*android:name\s*=\s*\"${Regex.escape(legacyName)}\"""")
                    .containsMatchIn(manifest),
            )
            assertTrue(
                Regex(
                    """<activity-alias\s+[^>]*android:name\s*=\s*\"${Regex.escape(legacyName)}\"[^>]*android:targetActivity\s*=\s*\"${Regex.escape(target)}\"""",
                ).containsMatchIn(manifest),
            )
        }
    }

    @Test
    fun primaryTabsAreExactlyHomeChatsLibrary() {
        assertEquals(listOf("Home", "Chats", "Library"), ADTab.entries.map { it.label })
        assertFalse(ADTab.entries.any { it.name == "AI" })
    }

    @Test
    fun aiLivesInsideDeviceCenterAndExternalAiRoutesThere() {
        val app = sourceFile("src/main/java/com/ad_glasses/ui/adglasses/ADGlassesApp.kt").readText()
        val deviceCenter = sourceFile(
            "src/main/java/com/ad_glasses/ui/adglasses/ADGlassesDeviceCenterScreen.kt",
        ).readText()
        val ai = sourceFile("src/main/java/com/ad_glasses/ui/adglasses/ADNativeAiScreen.kt").readText()

        assertTrue(app.contains("ADExternalDestination.AI -> routeStack = listOf(ADRoute.MAIN, ADRoute.DEVICE_CENTER)"))
        assertFalse(app.contains("ADTab.AI"))
        assertTrue(deviceCenter.contains("ADSectionTitle(\"AI\")"))
        assertTrue(deviceCenter.contains("ADDeviceAiSection("))
        assertFalse(deviceCenter.contains("ADSectionTitle(\"Capabilities\")"))
        assertFalse(deviceCenter.contains("ADDeviceCapability("))
        assertTrue(ai.contains("internal fun ADDeviceAiSection("))
        assertFalse(ai.contains("internal fun ADNativeAiScreen("))
    }

    @Test
    fun aiSurfaceKeepsOnlyCloudAndLocalRouting() {
        val ai = sourceFile("src/main/java/com/ad_glasses/ui/adglasses/ADNativeAiScreen.kt").readText()

        assertTrue(ai.contains("AiProviderType.CLOUD_API"))
        assertTrue(ai.contains("AiProviderType.LOCAL_MODELS"))
        assertTrue(ai.contains("AgentProviderType.CLOUD_AI"))
        assertTrue(ai.contains("AgentProviderType.LOCAL_AGENT"))
        assertTrue(ai.contains("ADAiProviderPill(\"Cloud\""))
        assertTrue(ai.contains("ADAiProviderPill(\"Local\""))
        assertTrue(ai.contains("tint = Color.Black"))
        listOf("DayNote", "AutoDiary", "Visual Diary", "Timeline", "ChatGPT app", "Gemini app")
            .forEach { removed -> assertFalse("Removed AI surface must not contain $removed", ai.contains(removed)) }
    }

    @Test
    fun timelineAndDiaryImplementationsAndManifestComponentsStayRemoved() {
        val appRoot = sourceFile("src/main")
        val manifest = sourceFile("src/main/AndroidManifest.xml").readText()
        val pluginRoot = File(appRoot, "java/com/ad_glasses/plugins")

        assertFalse(File(pluginRoot, "autodiary").exists())
        assertFalse(File(pluginRoot, "visualdiary").exists())
        listOf("AutoDiary", "VisualDiary", "DayNote").forEach { token ->
            assertFalse("Manifest must not register removed $token components", manifest.contains(token))
        }
    }

    @Test
    fun mainActivityDoesNotRestartOrRequestPermissionsForRemovedMemoryFeatures() {
        val main = sourceFile("src/main/java/com/ad_glasses/MainActivity.kt").readText()

        listOf(
            "AutoDiaryService",
            "AutoDiarySettingsActivity",
            "VisualDiaryService",
            "VisualDiaryPreferences",
            "VisualDiarySettingsActivity",
            "NativePluginIds.AUTO_DIARY",
            "NativePluginIds.VISUAL_DIARY",
        ).forEach { removed -> assertFalse("MainActivity must not reference $removed", main.contains(removed)) }
        assertTrue(main.contains("val needsAccessibility = LocalAgentPlugin.isEnabled(this)"))
    }

    @Test
    fun capabilityCommandArchitectureDoesNotExposeRemovedDiaryOrTimeline() {
        val orchestratorDir = sourceFile("src/main/java/com/ad_glasses/ai/orchestrator")
        val router = File(orchestratorDir, "AssistantCapabilityCommandRouter.kt").readText()
        val executor = File(orchestratorDir, "AndroidCapabilityCommandExecutor.kt").readText()

        listOf("AUTO_DIARY", "VISUAL_DIARY", "DayNote", "Visual Diary")
            .forEach { removed ->
                assertFalse(router.contains(removed))
                assertFalse(executor.contains(removed))
            }
    }

    @Test
    fun homeKeepsEverydayCaptureTranslateAndSoundbiteActions() {
        val home = sourceFile("src/main/java/com/ad_glasses/ui/adglasses/ADHomeSurface.kt").readText()

        listOf("Ask AI", "Photo", "Video", "Translate", "Soundbites", "Audio", "Lens")
            .forEach { label -> assertTrue("Home should surface $label", home.contains("\"$label\"")) }
        assertTrue(home.contains("AssistantCapability.TRANSLATOR"))
        assertTrue(home.contains("AssistantCapability.MEETING_NOTES"))
        assertTrue(home.contains("onClick = host.onImageQuestion"))
    }

    @Test
    fun chatPersistenceHasProcessSafeRoomFailureBoundary() {
        val chatStore = sourceFile("src/main/java/com/ad_glasses/chat/ChatStore.kt").readText()

        assertTrue(chatStore.contains("private var persistenceAvailable = true"))
        assertTrue(chatStore.contains("private fun repositoryOrNull()"))
        assertTrue(chatStore.contains("private fun <T> withRepository"))
        assertTrue(chatStore.contains(".onFailure { persistenceAvailable = false }"))
        assertTrue(chatStore.contains("threads.clear()"))
        assertTrue(chatStore.contains("messagesByChatId.clear()"))
    }

    @Test
    fun builtProductKeepsAdGlassesIdentity() {
        val strings = sourceFile("src/main/res/values/strings.xml").readText()
        val gradle = sourceFile("build.gradle").readText()

        assertTrue(strings.contains("<string name=\"app_name\">AD Glasses</string>"))
        assertTrue(gradle.contains("outputFileName.set(\"AD-Glasses.apk\")"))
        assertFalse(strings.contains("CyanBridge"))
        assertFalse(strings.contains("Fersaiyan"))
    }

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct

        val fromRepoRoot = File("android/AD-Glasses/app", relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot

        return direct
    }
}

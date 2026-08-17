package com.fersaiyan.cyanbridge.ui.adglasses

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-level guardrails for the React Native product migration. */
class ADVisibleProductUiTest {

    @Test
    fun welcomeKeepsOnlyTheStatementGlassesAndActions() {
        val welcome = rnFile("src/screens/WelcomeScreen.tsx").readText()
        assertTrue(welcome.contains("YOUR GLASSES"))
        assertTrue(welcome.contains("YOUR AI"))
        assertTrue(welcome.contains("YOUR DATA"))
        assertTrue(welcome.contains("GlassesImage"))
        assertTrue(welcome.contains("Connect glasses"))
        assertTrue(welcome.contains("Continue without glasses"))
        assertFalse(welcome.contains("AD GLASSES"))
    }

    @Test
    fun primaryNavigationIsControlAiAndMemoryNotChat() {
        val routes = rnFile("src/navigation/routes.ts").readText()
        val components = rnFile("src/design/components.tsx").readText()
        assertTrue(routes.contains("export type RootTab = 'home' | 'ai' | 'library'"))
        assertTrue(routes.contains("rootTabs: RootTab[] = ['home', 'ai', 'library']"))
        assertTrue(routes.contains("| 'prompt'")) // hidden artifact-context route remains available
        assertFalse(components.contains("route: 'prompt', label:"))
        assertFalse(components.contains("label: 'Prompt'"))
    }

    @Test
    fun homeStartsGlassesActionsWithoutOpeningChat() {
        val home = rnFile("src/screens/MainScreens.tsx").readText()
        assertTrue(home.contains("title=\"Ask\" detail=\"Voice question\" onPress={() => ADNative.action('voiceQuestion')}"))
        assertTrue(home.contains("title=\"What I see\" detail=\"Ask with vision\" onPress={() => ADNative.action('imageQuestion')}"))
        assertTrue(home.contains("title=\"Snap\" detail=\"Capture a photo\" onPress={() => ADNative.action('capturePhoto')}"))
        assertFalse(home.contains("title=\"Search web\""))
        assertFalse(home.contains("navigate('prompt', {web: true})"))
    }

    @Test
    fun aiIsAdFirstAndKeepsCurrentCapabilityLanguage() {
        val ai = rnFile("src/screens/AIScreen.tsx").readText()
        assertTrue(ai.contains("AD is the assistant. Gemini, local models and Android tools work behind it."))
        assertTrue(ai.contains("Gemini Live"))
        assertTrue(ai.contains("Gemini + Search"))
        assertTrue(ai.contains("Moonshine + Local AI"))
        assertTrue(ai.contains("Background / Tasker"))
        assertTrue(ai.contains("Accessibility fallback"))
        assertTrue(ai.contains("direct AD integration, not the Gemini app UI"))
        assertTrue(ai.contains("DayNote', detail: 'Daily moments, distilled'"))
        assertTrue(ai.contains("Cron', detail: 'Recurring scheduled work', icon: 'repeat'"))
        assertTrue(ai.contains("Automation', detail: 'Apps & Android actions'"))
        assertFalse(ai.contains("title=\"System assistants\""))
    }

    @Test
    fun adCanHoldTheAndroidAssistantRoleWithoutBecomingAVisibleAssistantUi() {
        val manifest = projectFile("assistant-role/src/main/AndroidManifest.xml").readText()
        val metadata = projectFile("assistant-role/src/main/res/xml/ad_voice_interaction_service.xml").readText()
        val role = projectFile("assistant-role/src/main/java/com/fersaiyan/cyanbridge/assistant/ADAssistantRole.kt").readText()
        assertTrue(manifest.contains("android.service.voice.VoiceInteractionService"))
        assertTrue(manifest.contains("android.permission.BIND_VOICE_INTERACTION"))
        assertTrue(manifest.contains("android.voice_interaction"))
        assertTrue(metadata.contains("android:supportsLaunchVoiceAssistFromKeyguard=\"true\""))
        assertTrue(role.contains("RoleManager.ROLE_ASSISTANT"))
        assertTrue(role.contains("setUiEnabled(false)"))
        assertTrue(role.contains("The glasses wake word remains the product's primary entry point"))
    }

    @Test
    fun taskerIsBackgroundExecutionAndAccessibilityIsExplicitFallback() {
        val routing = sourceFile("src/main/java/com/fersaiyan/cyanbridge/automation/AutomationRoutePrefs.kt").readText()
        val executor = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ai/orchestrator/AndroidAssistantCapabilityExecutor.kt").readText()
        val broadcaster = sourceFile("src/main/java/com/fersaiyan/cyanbridge/automation/AutomationEventBroadcaster.kt").readText()
        assertTrue(routing.contains("null,\n            \"\" -> AutomationExecutor.TASKER"))
        assertTrue(executor.contains("AutomationExecutor.TASKER"))
        assertTrue(executor.contains("AutomationEventBroadcaster.sendPhoneAction"))
        assertTrue(executor.contains("AutomationExecutor.ACCESSIBILITY"))
        assertTrue(broadcaster.contains("com.fersaiyan.cyanbridge.AUTOMATION_EVENT"))
        assertTrue(broadcaster.contains("setPackage(TASKER_PACKAGE)"))
    }

    @Test
    fun legacyTaskerVoicePolicyNoLongerRequiresAnUnlockedAssistantUi() {
        val policy = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ai/image/ExternalAssistantAutomationCapability.kt").readText()
        val prefs = sourceFile("src/main/java/com/fersaiyan/cyanbridge/agent/LocalAgentPrefs.kt").readText()
        assertTrue(policy.contains("Voice Tasker handoff is a background broadcast path"))
        assertTrue(policy.contains("!capability.taskerInstalled"))
        assertFalse(policy.substringAfter("fun voiceBlockingReason").substringBefore("fun imageBlockingReason").contains("phoneLocked"))
        assertFalse(policy.substringAfter("fun voiceBlockingReason").substringBefore("fun imageBlockingReason").contains("autoInput"))
        assertTrue(prefs.contains("AgentProviderType.TASKER.name -> AgentProviderType.TASKER"))
    }

    @Test
    fun geminiLiveAlreadyHasDirectAudioVisionAndSessionResumptionPrimitives() {
        val live = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ai/live/GeminiLiveClient.kt").readText()
        assertTrue(live.contains("Direct Gemini Live WebSocket client"))
        assertTrue(live.contains("fun offerGlassesPcm"))
        assertTrue(live.contains("fun sendImage"))
        assertTrue(live.contains("responseModalities"))
        assertTrue(live.contains("sessionResumption"))
        assertTrue(live.contains("AudioTrack.Builder"))
    }

    @Test
    fun moonshineIsPartOfThePrivateOfflineSpeechLane() {
        val gradle = sourceFile("build.gradle").readText()
        val runtime = projectFile("AD_ASSISTANT_RUNTIME.md").readText()
        assertTrue(gradle.contains("implementation project(\":moonshine-voice\")"))
        assertTrue(runtime.contains("Moonshine/local speech is the private/offline transcription lane"))
        assertTrue(runtime.contains("The phone display stays off throughout the normal path."))
    }

    @Test
    fun productShellCoversEveryActiveRoute() {
        val app = rnFile("src/App.tsx").readText()
        listOf(
            "welcome", "home", "prompt", "ai", "library", "settings", "device",
            "pairing", "sync", "relay", "local-ai", "assistant-apps", "privacy",
            "storage", "language", "permissions", "advanced", "about", "firmware",
            "capability", "captures", "recordings", "notes",
        ).forEach { route -> assertTrue("RN shell must render $route", app.contains("case '$route'")) }
    }

    @Test
    fun motionHonorsStaggerAndReducedMotion() {
        val components = rnFile("src/design/components.tsx").readText()
        assertTrue(components.contains("AccessibilityInfo.isReduceMotionEnabled"))
        assertTrue(components.contains("withDelay(delay"))
        assertTrue(components.contains("withSpring(0.982"))
    }

    @Test
    fun nativeBridgeReusesTheExistingGlassesRuntime() {
        val bridge = sourceFile("src/main/java/com/fersaiyan/cyanbridge/ui/reactnative/ADGlassesBridgeModule.kt").readText()
        assertTrue(bridge.contains("GlassesDashboardAction.CapturePhoto"))
        assertTrue(bridge.contains("GlassesDashboardAction.TestVoiceQuestion"))
        assertTrue(bridge.contains("GlassesDashboardAction.TestImageQuestion"))
        assertTrue(bridge.contains("AssistantOrchestrator"))
        assertTrue(bridge.contains("fun sendPrompt"))
        assertTrue(bridge.contains("SyncedMediaQuery.query"))
        assertTrue(bridge.contains("getAllCaptureSessions().first()"))
        assertTrue(bridge.contains("notesRepository.getAllNotes().first()"))
    }

    @Test
    fun reactNativeFoundationUsesNewArchitectureAndAlphaProductVersion() {
        val pkg = rnFile("package.json").readText()
        val properties = rnFile("gradle.properties").readText()
        val gradle = sourceFile("build.gradle").readText()
        assertTrue(pkg.contains("\"react-native\": \"0.86.2\""))
        assertTrue(pkg.contains("react-native-reanimated"))
        assertTrue(properties.contains("newArchEnabled=true"))
        assertTrue(properties.contains("hermesEnabled=true"))
        assertTrue(gradle.contains("versionName = \"alpha\""))
    }

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromRepoRoot = File("android/CyanBridge/app", relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot
        return direct
    }

    private fun rnFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromAppModule = File("../$relativePath")
        if (fromAppModule.exists()) return fromAppModule
        val fromRepoRoot = File("android/CyanBridge/$relativePath")
        if (fromRepoRoot.exists()) return fromRepoRoot
        return direct
    }

    private fun projectFile(relativePath: String): File {
        val fromAppModule = File("../$relativePath")
        if (fromAppModule.exists()) return fromAppModule
        val fromRepoRoot = File("android/CyanBridge/$relativePath")
        if (fromRepoRoot.exists()) return fromRepoRoot
        return File(relativePath)
    }
}

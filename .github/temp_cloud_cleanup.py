from pathlib import Path
import re
import shutil

ROOT = Path('.')


def text(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, value: str) -> None:
    (ROOT / path).write_text(value)


def exact(path: str, old: str, new: str, expected: int = 1) -> None:
    value = text(path)
    count = value.count(old)
    if count != expected:
        raise RuntimeError(f'{path}: expected {expected} exact matches, found {count}: {old[:80]!r}')
    write(path, value.replace(old, new))


def sub(path: str, pattern: str, repl: str, expected: int = 1, flags: int = re.S) -> None:
    value = text(path)
    updated, count = re.subn(pattern, repl, value, flags=flags)
    if count != expected:
        raise RuntimeError(f'{path}: expected {expected} regex matches, found {count}: {pattern[:100]!r}')
    write(path, updated)


def drop_line(path: str, line: str, expected: int = 1) -> None:
    exact(path, line + '\n', '', expected)


def remove_path(path: str) -> None:
    target = ROOT / path
    if target.is_dir():
        shutil.rmtree(target)
    elif target.exists():
        target.unlink()


# ---------------------------------------------------------------------------
# MainActivity: remove local-model selection, fallback, streaming and routing.
# ---------------------------------------------------------------------------
main = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/MainActivity.kt'
for line in [
    'import com.ad_glasses.localmodels.provider.LocalModelsProvider',
    'import com.ad_glasses.localmodels.provider.localModelRequestCompatibilityIssue',
    'import com.ad_glasses.localmodels.tts.StreamingSpeechSessionManager',
    'import com.ad_glasses.localmodels.settings.LocalModelRuntime',
    'import com.ad_glasses.localmodels.settings.LocalModelSettingsRepository',
    'import com.ad_glasses.localmodels.storage.LocalModelStorageRepository',
]:
    drop_line(main, line)

sub(
    main,
    r'''\n    private val localSpeechSessionManager by lazy \{\n        StreamingSpeechSessionManager\.getInstance\(applicationContext\)\n    \}\n''',
    '\n',
)

sub(
    main,
    r'''    private fun imageQueryUnsupportedReasonForCurrentSelection\(\): String\? \{[\s\S]*?\n    \}\n\n\n\n\n    private fun refreshAiQueryButtonsState''',
    '''    private fun imageQueryUnsupportedReasonForCurrentSelection(): String? {\n        if (isMeizuMyvuSelected()) {\n            return "Image questions are unavailable for MYVU because its current transport does not expose camera capture."\n        }\n        return null\n    }\n\n    private fun refreshAiQueryButtonsState''',
)

sub(
    main,
    r'''        return when \(providerType\) \{[\s\S]*?        \}\.trim\(\)\n    \}\n\n    private fun validateSelectedLocalModelForChosenProvider\(imageRequested: Boolean\): String\? \{[\s\S]*?\n    \}\n\n    private fun resolveImageQuestionPrompt''',
    '''        return ApiTokenClient.chat(\n            context = this,\n            messages = messages,\n            imagePaths = imagePaths,\n            audioPath = audioPath,\n        ).getOrElse {\n            "Cloud AI unavailable (${it.message ?: it::class.java.simpleName})."\n        }.trim()\n    }\n\n    private fun resolveImageQuestionPrompt''',
)

sub(
    main,
    r'''    private fun triggerMemoryAwareImageQuery\([\s\S]*?\n    private fun beginAiQuestionForegroundWork\(''',
    '''    private fun triggerMemoryAwareImageQuery(\n        imagePath: String,\n        providerType: AgentProviderType,\n        resolvedPrompt: ResolvedImageQuestionPrompt,\n        onReplySpoken: (() -> Unit)? = null,\n    ) {\n        Log.i("AIHijack", "Running Cloud image query: $imagePath")\n\n        val onSpeechCompleted: () -> Unit = {\n            finishAiQuestionForegroundWork()\n            onReplySpoken?.invoke()\n        }\n\n        lifecycleScope.launch(Dispatchers.IO) {\n            try {\n                val routePrompt = resolvedPrompt.forRoute(ImageQuestionRoute.CLOUD_API)\n                val systemContext = buildCompactMemoryAwareSystemPrompt(\n                    queryText = routePrompt,\n                    date = todayDateString(),\n                )\n                val finalReply = try {\n                    AssistantOrchestrator(\n                        context = this@MainActivity,\n                        executor = AndroidAssistantCapabilityExecutor(this@MainActivity),\n                    ).handle(\n                        turn = AssistantTurn(\n                            text = routePrompt,\n                            surface = AssistantInputSurface.GLASSES_VISION,\n                            imagePath = imagePath,\n                            contextText = systemContext,\n                            webRequested = false,\n                        ),\n                        providerType = AgentProviderType.CLOUD_AI,\n                    ).let { result -> result.spokenText.trim().ifBlank { result.richText.trim() } }\n                } catch (error: CancellationException) {\n                    throw error\n                } catch (error: Exception) {\n                    Log.e("AIHijack", "Cloud image query failed", error)\n                    runOnUiThread {\n                        Toast.makeText(\n                            this@MainActivity,\n                            "Vision error: ${(error.message ?: "unknown error").take(100)}",\n                            Toast.LENGTH_LONG,\n                        ).show()\n                    }\n                    "I couldn't analyze that image with the active Cloud AI profile."\n                }\n\n                val replyToSpeak = finalReply.ifBlank {\n                    "I couldn't generate an answer for that image. Please try again."\n                }\n                Log.i("AIHijack", "Cloud image query completed replyLength=${replyToSpeak.length}")\n                runOnUiThread {\n                    speakVision(replyToSpeak, onDone = onSpeechCompleted)\n                }\n            } catch (error: CancellationException) {\n                finishAiQuestionForegroundWork()\n                throw error\n            } catch (error: Exception) {\n                Log.e("AIHijack", "Image query pipeline failed", error)\n                finishAiQuestionForegroundWork()\n                runOnUiThread {\n                    Toast.makeText(\n                        this@MainActivity,\n                        "Image question failed: ${error.message ?: "unknown error"}",\n                        Toast.LENGTH_LONG,\n                    ).show()\n                }\n            } finally {\n                imageQueryInProgress.set(false)\n            }\n        }\n    }\n\n    private fun beginAiQuestionForegroundWork(''',
)

exact(main, '        cancelLocalStreamingSpeech("new voice query")\n', '')
sub(
    main,
    r'''    private fun triggerAssistantVoiceQuery\(\) \{[\s\S]*?\n    \}\n\n    private fun handleAiWakeWordActivation''',
    '''    private fun triggerAssistantVoiceQuery() {\n        if (isGlassesCommandBlocked("voice-query command")) return\n        Log.i("AIHijack", "Triggering Cloud voice query")\n        triggerInternalVoiceQuery(AgentProviderType.CLOUD_AI)\n    }\n\n    private fun handleAiWakeWordActivation''',
)
sub(
    main,
    r'''            val providerType = when \(currentAssistantRoute\(\)\) \{\n                GlassesAssistantRoute\.LOCAL -> AgentProviderType\.LOCAL_AGENT\n                GlassesAssistantRoute\.CLOUD -> AgentProviderType\.CLOUD_AI\n            \}''',
    '            val providerType = AgentProviderType.CLOUD_AI',
)

# Remove Studio Bridge startup/recovery hooks that belonged to remote local-model servers.
sub(
    main,
    r'''        if \(\n            com\.ad_glasses\.localmodels\.remote\.RemoteOpenAiPrefs\.isBridgeConfigured\(this\) &&[\s\S]*?        \}\n        try \{''',
    '        try {',
)
exact(
    main,
    '        val voicePluginEnabled =\n            com.ad_glasses.localmodels.remote.RemoteOpenAiPrefs.isBridgeConfigured(this) ||\n                AutoAudioCapturePrefs.isEnabled(this) ||\n',
    '        val voicePluginEnabled =\n            AutoAudioCapturePrefs.isEnabled(this) ||\n',
)
exact(
    main,
    '        if (com.ad_glasses.localmodels.remote.RemoteOpenAiPrefs.isBridgeConfigured(this)) {\n            (application as? MyApplication)?.startStudioBridge()\n        }\n',
    '',
)

# ---------------------------------------------------------------------------
# Local Agent settings: keep automation page/safety state, drop local-LLM state.
# ---------------------------------------------------------------------------
agent_settings = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/plugins/localagent/LocalAgentSettingsActivity.kt'
for line in [
    'import androidx.compose.material3.RadioButton',
    'import com.ad_glasses.agent.LocalModelsConfigureActivity',
    'import com.ad_glasses.localmodels.session.LocalChatSessionManager',
    'import com.ad_glasses.shared.settings.AgentProviderType',
]:
    drop_line(agent_settings, line)
exact(agent_settings, '            providerType = AutomationPrefs.getProviderType(this),\n', '')
sub(
    agent_settings,
    r'''\n    private fun setPlanningProvider\(type: AgentProviderType\) \{[\s\S]*?\n    \}\n''',
    '\n',
)
exact(agent_settings, '    val providerType: AgentProviderType = AgentProviderType.LOCAL_AGENT,\n', '')
sub(
    agent_settings,
    r'''\n@Composable\nprivate fun ProviderOption\([\s\S]*?\n\}\n\n@Composable\nprivate fun StatusCard''',
    '\n@Composable\nprivate fun StatusCard',
)

# ---------------------------------------------------------------------------
# Daily summaries: Cloud profile only; remove local per-event model bulletizer.
# ---------------------------------------------------------------------------
daily = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/localagent/dailysummary/DailySummaryGenerator.kt'
for line in [
    'import com.ad_glasses.shared.settings.AgentProviderType',
    'import com.ad_glasses.localmodels.provider.LocalModelRequestPriority',
    'import com.ad_glasses.localmodels.provider.LocalModelsProvider',
]:
    drop_line(daily, line)
exact(daily, 'import com.ad_glasses.ai.router.ApiTokenClient\n', 'import com.ad_glasses.ai.router.ApiTokenClient\nimport com.ad_glasses.ai.router.AiProviderPrefs\n')
exact(daily, '    private val localModelsProvider = LocalModelsProvider()\n', '')
exact(daily, '    private const val MAX_LOCAL_EVENT_BULLETIZER_CALLS = 80\n', '')

sub(
    daily,
    r'''    fun providerHint\(context: Context\): String \{[\s\S]*?\n    \}\n''',
    '''    fun providerHint(context: Context): String {\n        val profile = AiProviderPrefs.getActiveProfile(context) ?: return "cloud_unconfigured"\n        return "cloud:${profile.provider.wire}:${profile.model}"\n    }\n''',
)
sub(
    daily,
    r'''    fun estimateBulletEventsForDate\([\s\S]*?\n    \}\n\n    private fun buildInputForDate''',
    '''    fun estimateBulletEventsForDate(\n        context: Context,\n        date: String = todayString(),\n    ): Int = 0\n\n    private fun buildInputForDate''',
)
sub(
    daily,
    r'''    private suspend fun prepareInputForGeneration\([\s\S]*?\n    private fun heuristicEventBullet''',
    '''    private suspend fun prepareInputForGeneration(\n        context: Context,\n        input: Input,\n        onBulletProgress: ((BulletProgress) -> Unit)? = null,\n    ): Input = input\n\n    private fun heuristicEventBullet''',
)
# Heuristic/merge helpers were only needed by the retired local event bulletizer.
sub(
    daily,
    r'''    private fun heuristicEventBullet\([\s\S]*?\n    private fun buildFullPrompt''',
    '    private fun buildFullPrompt',
)
# Remove local incremental-model special case.
sub(
    daily,
    r'''            val agentType = AutomationPrefs\.getProviderType\(context\)\n\n            if \(agentType == AgentProviderType\.LOCAL_AGENT[\s\S]*?\n            val \(usedInput, providerResult\) = try \{''',
    '            val (usedInput, providerResult) = try {',
)
sub(
    daily,
    r'''    private suspend fun generateSummary\(context: Context, prompt: String\): ProviderResponse \{[\s\S]*?\n    \}\n\n    private suspend fun runCloudApi''',
    '''    private suspend fun generateSummary(context: Context, prompt: String): ProviderResponse =\n        runCloudApi(context, prompt)\n\n    private suspend fun runCloudApi''',
)
sub(
    daily,
    r'''\n    private suspend fun runLocalModels\(context: Context, prompt: String\): ProviderResponse \{[\s\S]*?\n    \}\n\n    private fun isUsableSummaryReply''',
    '\n    private fun isUsableSummaryReply',
)
exact(daily, '        if (s.startsWith("No local model is installed.", ignoreCase = true)) return false\n', '')

# ---------------------------------------------------------------------------
# Shared Android service wrappers: Cloud profile only.
# ---------------------------------------------------------------------------
wrappers = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/AndroidSharedServiceWrappers.kt'
for line in [
    'import com.ad_glasses.agent.LocalAgentPrefs',
    'import com.ad_glasses.localmodels.provider.LocalModelsProvider',
    'import com.ad_glasses.shared.settings.AgentProviderType',
]:
    drop_line(wrappers, line)
exact(wrappers, '    private val localProvider = LocalModelsProvider()\n\n', '')
sub(
    wrappers,
    r'''        val reply = when \(LocalAgentPrefs\.getProviderType\(appContext\)\) \{[\s\S]*?        \}\n        return ChatResponse''',
    '''        val reply = ApiTokenClient.chat(\n            context = appContext,\n            messages = cleanMessages,\n        ).getOrThrow()\n        return ChatResponse''',
)
exact(
    wrappers,
    '            "Shared image analysis has no implicit upload route. Use the explicit Local or Cloud media pipeline.",\n',
    '            "Shared image analysis has no implicit upload route. Use the explicit Cloud media pipeline.",\n',
)
sub(
    wrappers,
    r'''class AndroidAiModelRegistry : AiModelRegistry \{[\s\S]*?\n\}''',
    '''class AndroidAiModelRegistry : AiModelRegistry {\n    override suspend fun listModels(): List<AiModel> = listOf(\n        AiModel("cloud-active", "Configured Cloud AI", "cloud", isLocal = false),\n    )\n\n    override fun getDefaultModelId(): String = "cloud-active"\n}''',
)

# ---------------------------------------------------------------------------
# MyApplication: no local-model preload and no remote-local Studio Bridge.
# ---------------------------------------------------------------------------
app = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/MyApplication.kt'
for line in [
    'import com.ad_glasses.ai.router.AiProviderPrefs',
    'import com.ad_glasses.ai.router.AiProviderType',
    'import com.ad_glasses.localmodels.remote.RemoteOpenAiPrefs',
    'import com.ad_glasses.localmodels.storage.LocalModelStorageRepository',
    'import com.ad_glasses.studiobridge.StudioApprovalHandler',
    'import com.ad_glasses.studiobridge.StudioBridgeClient',
    'import com.ad_glasses.studiobridge.StudioBridgeForegroundService',
]:
    drop_line(app, line)
exact(app, '    private var studioApprovalHandler: StudioApprovalHandler? = null\n\n', '')
exact(app, '        maybePreloadLocalModel()\n\n', '')
sub(
    app,
    r'''\n    /\*\* Start the Studio Bridge WebSocket connection for approval notifications\. \*/[\s\S]*?\n    /\*\* Initialize KMP shared services with Android implementations\. \*/''',
    '\n    /** Initialize KMP shared services with Android implementations. */',
)

# ---------------------------------------------------------------------------
# Gradle + manifest: remove local LLM runtimes/components.
# ---------------------------------------------------------------------------
gradle = 'android/AD-Glasses/app/build.gradle'
sub(
    gradle,
    r'''    def localLlamaRuntimeAarPath = \([\s\S]*?    \)\.toString\(\)\.trim\(\)\n''',
    '',
)
sub(
    gradle,
    r'''    if \(localLlamaRuntimeAarPath\.isEmpty\(\)\) \{[\s\S]*?    implementation\("com\.google\.ai\.edge\.litertlm:litertlm-android:0\.14\.0"\)\n''',
    '',
)

manifest = 'android/AD-Glasses/app/src/main/AndroidManifest.xml'
for line in [
    '        <activity android:name=".agent.LocalModelsConfigureActivity" android:exported="false" android:label="Local models" android:configChanges="orientation|screenSize|screenLayout|keyboardHidden" />',
    '        <service android:name=".localmodels.download.ModelDownloadForegroundService" android:exported="false" android:foregroundServiceType="dataSync" />',
    '        <service android:name=".studiobridge.StudioBridgeForegroundService" android:exported="false" android:foregroundServiceType="microphone" />',
]:
    drop_line(manifest, line)

# ---------------------------------------------------------------------------
# Small wording cleanup.
# ---------------------------------------------------------------------------
moonshot = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/transcription/MoonshotTranscriptionProvider.kt'
if (ROOT.joinpath(moonshot).exists():
    pass

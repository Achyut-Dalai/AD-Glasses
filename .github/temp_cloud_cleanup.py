from pathlib import Path
import re
import shutil

ROOT = Path('.')


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, value: str) -> None:
    (ROOT / path).write_text(value)


def replace(path: str, old: str, new: str, required: bool = True) -> None:
    value = read(path)
    if old not in value:
        if required:
            raise RuntimeError(f"{path}: missing expected text: {old[:120]!r}")
        return
    write(path, value.replace(old, new))


def sub(path: str, pattern: str, repl: str, required: bool = True, flags: int = re.S) -> None:
    value = read(path)
    updated, count = re.subn(pattern, repl, value, flags=flags)
    if count == 0 and required:
        raise RuntimeError(f"{path}: missing regex: {pattern[:140]!r}")
    if count:
        write(path, updated)


def drop_imports(path: str, imports: list[str]) -> None:
    value = read(path)
    for imp in imports:
        value = value.replace(f"import {imp}\n", "")
    write(path, value)


def remove_path(path: str) -> None:
    target = ROOT / path
    if target.is_dir():
        shutil.rmtree(target)
    elif target.exists():
        target.unlink()


# ---------------------------------------------------------------------------
# MainActivity: one inference route, Cloud AI. Keep Android TTS and Moonshine STT.
# ---------------------------------------------------------------------------
main = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/MainActivity.kt'
drop_imports(main, [
    'com.ad_glasses.localmodels.provider.LocalModelsProvider',
    'com.ad_glasses.localmodels.provider.localModelRequestCompatibilityIssue',
    'com.ad_glasses.localmodels.tts.StreamingSpeechSessionManager',
    'com.ad_glasses.localmodels.settings.LocalModelRuntime',
    'com.ad_glasses.localmodels.settings.LocalModelSettingsRepository',
    'com.ad_glasses.localmodels.storage.LocalModelStorageRepository',
])
sub(main, r'''\n    private val localSpeechSessionManager by lazy \{[\s\S]*?\n    \}\n''', '\n', required=False)
sub(
    main,
    r'''    private fun imageQueryUnsupportedReasonForCurrentSelection\(\): String\? \{[\s\S]*?\n    \}\n\n+    private fun refreshAiQueryButtonsState''',
    '''    private fun imageQueryUnsupportedReasonForCurrentSelection(): String? {\n        if (isMeizuMyvuSelected()) {\n            return "Image questions are unavailable for MYVU because its current transport does not expose camera capture."\n        }\n        return null\n    }\n\n    private fun refreshAiQueryButtonsState''',
)
sub(
    main,
    r'''    private suspend fun runMemoryAwareChosenProviderQuery\([\s\S]*?\n    private fun validateSelectedLocalModelForChosenProvider\(imageRequested: Boolean\): String\? \{[\s\S]*?\n    \}\n\n    private fun resolveImageQuestionPrompt''',
    '''    private suspend fun runMemoryAwareChosenProviderQuery(\n        userPrompt: String,\n        providerType: AgentProviderType,\n        imagePaths: List<String> = emptyList(),\n        audioPath: String? = null,\n        onToken: ((String) -> Unit)? = null,\n    ): String {\n        val date = todayDateString()\n        val languageTag = recognitionLanguageTag()\n        val systemPrompt = buildString {\n            append(buildCompactMemoryAwareSystemPrompt(queryText = userPrompt, date = date))\n            append("\\n\\n")\n            append(ImageQuestionDefaults.responseLanguageInstruction(languageTag))\n        }\n\n        if (imagePaths.isEmpty() && audioPath.isNullOrBlank()) {\n            val result = AssistantOrchestrator(\n                context = this,\n                executor = AndroidAssistantCapabilityExecutor(this, onToken = onToken),\n            ).handle(\n                turn = AssistantTurn(\n                    text = userPrompt,\n                    surface = AssistantInputSurface.GLASSES_VOICE,\n                    contextText = systemPrompt,\n                    webRequested = null,\n                ),\n                providerType = AgentProviderType.CLOUD_AI,\n            )\n            return result.spokenText.trim().ifBlank { result.richText.trim() }\n        }\n\n        val messages = listOf(\n            mapOf("role" to "system", "content" to systemPrompt),\n            mapOf("role" to "user", "content" to userPrompt),\n        )\n        return ApiTokenClient.chat(\n            context = this,\n            messages = messages,\n            imagePaths = imagePaths,\n            audioPath = audioPath,\n        ).getOrElse {\n            "Cloud AI unavailable (${it.message ?: it::class.java.simpleName})."\n        }.trim()\n    }\n\n    private fun resolveImageQuestionPrompt''',
)
sub(
    main,
    r'''    private fun triggerMemoryAwareImageQuery\([\s\S]*?\n    private fun beginAiQuestionForegroundWork\(''',
    '''    private fun triggerMemoryAwareImageQuery(\n        imagePath: String,\n        providerType: AgentProviderType,\n        resolvedPrompt: ResolvedImageQuestionPrompt,\n        onReplySpoken: (() -> Unit)? = null,\n    ) {\n        Log.i("AIHijack", "Running Cloud image query: $imagePath")\n        val onSpeechCompleted: () -> Unit = {\n            finishAiQuestionForegroundWork()\n            onReplySpoken?.invoke()\n        }\n        lifecycleScope.launch(Dispatchers.IO) {\n            try {\n                val routePrompt = resolvedPrompt.forRoute(ImageQuestionRoute.CLOUD_API)\n                val systemContext = buildCompactMemoryAwareSystemPrompt(\n                    queryText = routePrompt,\n                    date = todayDateString(),\n                )\n                val finalReply = try {\n                    AssistantOrchestrator(\n                        context = this@MainActivity,\n                        executor = AndroidAssistantCapabilityExecutor(this@MainActivity),\n                    ).handle(\n                        turn = AssistantTurn(\n                            text = routePrompt,\n                            surface = AssistantInputSurface.GLASSES_VISION,\n                            imagePath = imagePath,\n                            contextText = systemContext,\n                            webRequested = false,\n                        ),\n                        providerType = AgentProviderType.CLOUD_AI,\n                    ).let { result -> result.spokenText.trim().ifBlank { result.richText.trim() } }\n                } catch (error: CancellationException) {\n                    throw error\n                } catch (error: Exception) {\n                    Log.e("AIHijack", "Cloud image query failed", error)\n                    "I couldn't analyze that image with the active Cloud AI profile."\n                }\n                val replyToSpeak = finalReply.ifBlank {\n                    "I couldn't generate an answer for that image. Please try again."\n                }\n                runOnUiThread { speakVision(replyToSpeak, onDone = onSpeechCompleted) }\n            } catch (error: CancellationException) {\n                finishAiQuestionForegroundWork()\n                throw error\n            } catch (error: Exception) {\n                Log.e("AIHijack", "Image query pipeline failed", error)\n                finishAiQuestionForegroundWork()\n                runOnUiThread {\n                    Toast.makeText(\n                        this@MainActivity,\n                        "Image question failed: ${error.message ?: "unknown error"}",\n                        Toast.LENGTH_LONG,\n                    ).show()\n                }\n            } finally {\n                imageQueryInProgress.set(false)\n            }\n        }\n    }\n\n    private fun beginAiQuestionForegroundWork(''',
)
replace(main, '        cancelLocalStreamingSpeech("new voice query")\n', '', required=False)
sub(
    main,
    r'''    private fun triggerAssistantVoiceQuery\(\) \{[\s\S]*?\n    \}\n\n    private fun handleAiWakeWordActivation''',
    '''    private fun triggerAssistantVoiceQuery() {\n        if (isGlassesCommandBlocked("voice-query command")) return\n        Log.i("AIHijack", "Triggering Cloud voice query")\n        triggerInternalVoiceQuery(AgentProviderType.CLOUD_AI)\n    }\n\n    private fun handleAiWakeWordActivation''',
)
sub(
    main,
    r'''            val providerType = when \(currentAssistantRoute\(\)\) \{[\s\S]*?            \}\n            Log\.i\("AIHijack", "Starting image query source=\$\{source\.wireName\} provider=\$providerType"\)''',
    '''            val providerType = AgentProviderType.CLOUD_AI\n            Log.i("AIHijack", "Starting image query source=${source.wireName} provider=$providerType")''',
)
# Studio Bridge belonged to remote/local-model infrastructure.
sub(
    main,
    r'''        if \(\n            com\.ad_glasses\.localmodels\.remote\.RemoteOpenAiPrefs\.isBridgeConfigured\(this\) &&[\s\S]*?        \}\n        try \{''',
    '        try {',
    required=False,
)
replace(
    main,
    '        val voicePluginEnabled =\n            com.ad_glasses.localmodels.remote.RemoteOpenAiPrefs.isBridgeConfigured(this) ||\n                AutoAudioCapturePrefs.isEnabled(this) ||\n',
    '        val voicePluginEnabled =\n            AutoAudioCapturePrefs.isEnabled(this) ||\n',
    required=False,
)
replace(
    main,
    '        if (com.ad_glasses.localmodels.remote.RemoteOpenAiPrefs.isBridgeConfigured(this)) {\n            (application as? MyApplication)?.startStudioBridge()\n        }\n',
    '',
    required=False,
)

# ---------------------------------------------------------------------------
# Local Agent remains phone automation. Its planning route is always Cloud AI.
# ---------------------------------------------------------------------------
agent_settings = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/plugins/localagent/LocalAgentSettingsActivity.kt'
drop_imports(agent_settings, [
    'androidx.compose.material3.RadioButton',
    'com.ad_glasses.agent.LocalModelsConfigureActivity',
    'com.ad_glasses.localmodels.session.LocalChatSessionManager',
    'com.ad_glasses.shared.settings.AgentProviderType',
])
replace(agent_settings, '            providerType = AutomationPrefs.getProviderType(this),\n', '', required=False)
sub(agent_settings, r'''\n    private fun setPlanningProvider\(type: AgentProviderType\) \{[\s\S]*?\n    \}\n''', '\n', required=False)
replace(agent_settings, '    val providerType: AgentProviderType = AgentProviderType.LOCAL_AGENT,\n', '', required=False)
sub(agent_settings, r'''\n@Composable\nprivate fun ProviderOption\([\s\S]*?\n\}\n\n@Composable\nprivate fun StatusCard''', '\n@Composable\nprivate fun StatusCard', required=False)

# ---------------------------------------------------------------------------
# Daily summaries and shared AI adapters use only the active Cloud profile.
# ---------------------------------------------------------------------------
daily = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/localagent/dailysummary/DailySummaryGenerator.kt'
if (ROOT.joinpath(daily).exists():
    drop_imports(daily, [
        'com.ad_glasses.shared.settings.AgentProviderType',
        'com.ad_glasses.localmodels.provider.LocalModelRequestPriority',
        'com.ad_glasses.localmodels.provider.LocalModelsProvider',
    ])
    replace(daily, 'import com.ad_glasses.ai.router.ApiTokenClient\n', 'import com.ad_glasses.ai.router.ApiTokenClient\nimport com.ad_glasses.ai.router.AiProviderPrefs\n', required=False)
    replace(daily, '    private val localModelsProvider = LocalModelsProvider()\n', '', required=False)
    replace(daily, '    private const val MAX_LOCAL_EVENT_BULLETIZER_CALLS = 80\n', '', required=False)
    sub(daily, r'''    fun providerHint\(context: Context\): String \{[\s\S]*?\n    \}\n''', '''    fun providerHint(context: Context): String {\n        val profile = AiProviderPrefs.getActiveProfile(context) ?: return "cloud_unconfigured"\n        return "cloud:${profile.provider.wire}:${profile.model}"\n    }\n''', required=False)
    sub(daily, r'''    fun estimateBulletEventsForDate\([\s\S]*?\n    \}\n\n    private fun buildInputForDate''', '''    fun estimateBulletEventsForDate(\n        context: Context,\n        date: String = todayString(),\n    ): Int = 0\n\n    private fun buildInputForDate''', required=False)
    sub(daily, r'''    private suspend fun prepareInputForGeneration\([\s\S]*?\n    private fun heuristicEventBullet''', '''    private suspend fun prepareInputForGeneration(\n        context: Context,\n        input: Input,\n        onBulletProgress: ((BulletProgress) -> Unit)? = null,\n    ): Input = input\n\n    private fun heuristicEventBullet''', required=False)
    sub(daily, r'''    private fun heuristicEventBullet\([\s\S]*?\n    private fun buildFullPrompt''', '    private fun buildFullPrompt', required=False)
    sub(daily, r'''            val agentType = AutomationPrefs\.getProviderType\(context\)\n\n            if \(agentType == AgentProviderType\.LOCAL_AGENT[\s\S]*?\n            val \(usedInput, providerResult\) = try \{''', '            val (usedInput, providerResult) = try {', required=False)
    sub(daily, r'''    private suspend fun generateSummary\(context: Context, prompt: String\): ProviderResponse \{[\s\S]*?\n    \}\n\n    private suspend fun runCloudApi''', '''    private suspend fun generateSummary(context: Context, prompt: String): ProviderResponse =\n        runCloudApi(context, prompt)\n\n    private suspend fun runCloudApi''', required=False)
    sub(daily, r'''\n    private suspend fun runLocalModels\(context: Context, prompt: String\): ProviderResponse \{[\s\S]*?\n    \}\n\n    private fun isUsableSummaryReply''', '\n    private fun isUsableSummaryReply', required=False)
    replace(daily, '        if (s.startsWith("No local model is installed.", ignoreCase = true)) return false\n', '', required=False)

wrappers = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/AndroidSharedServiceWrappers.kt'
if (ROOT.joinpath(wrappers).exists():
    drop_imports(wrappers, [
        'com.ad_glasses.agent.LocalAgentPrefs',
        'com.ad_glasses.localmodels.provider.LocalModelsProvider',
        'com.ad_glasses.shared.settings.AgentProviderType',
    ])
    replace(wrappers, '    private val localProvider = LocalModelsProvider()\n\n', '', required=False)
    sub(wrappers, r'''        val reply = when \(LocalAgentPrefs\.getProviderType\(appContext\)\) \{[\s\S]*?        \}\n        return ChatResponse''', '''        val reply = ApiTokenClient.chat(\n            context = appContext,\n            messages = cleanMessages,\n        ).getOrThrow()\n        return ChatResponse''', required=False)
    replace(wrappers, '            "Shared image analysis has no implicit upload route. Use the explicit Local or Cloud media pipeline.",\n', '            "Shared image analysis has no implicit upload route. Use the explicit Cloud media pipeline.",\n', required=False)
    sub(wrappers, r'''class AndroidAiModelRegistry : AiModelRegistry \{[\s\S]*?\n\}''', '''class AndroidAiModelRegistry : AiModelRegistry {\n    override suspend fun listModels(): List<AiModel> = listOf(\n        AiModel("cloud-active", "Configured Cloud AI", "cloud", isLocal = false),\n    )\n\n    override fun getDefaultModelId(): String = "cloud-active"\n}''', required=False)

# Application process no longer owns local-model preload or Studio Bridge.
app = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/MyApplication.kt'
drop_imports(app, [
    'androidx.core.content.ContextCompat',
    'com.ad_glasses.ai.router.AiProviderPrefs',
    'com.ad_glasses.ai.router.AiProviderType',
    'com.ad_glasses.localmodels.remote.RemoteOpenAiPrefs',
    'com.ad_glasses.localmodels.storage.LocalModelStorageRepository',
    'com.ad_glasses.plugins.PluginVoicePermissions',
    'com.ad_glasses.studiobridge.StudioApprovalHandler',
    'com.ad_glasses.studiobridge.StudioBridgeClient',
    'com.ad_glasses.studiobridge.StudioBridgeForegroundService',
])
replace(app, '    private var studioApprovalHandler: StudioApprovalHandler? = null\n', '', required=False)
replace(app, '        maybePreloadLocalModel()\n\n', '', required=False)
sub(app, r'''\n    /\*\* Start the Studio Bridge WebSocket connection for approval notifications\. \*/[\s\S]*?\n    /\*\* Initialize KMP shared services with Android implementations\. \*/''', '\n    /** Initialize KMP shared services with Android implementations. */', required=False)
sub(app, r'''\n    private fun maybePreloadLocalModel\(\) \{[\s\S]*?\n    \}\n''', '\n', required=False)

# ---------------------------------------------------------------------------
# Build + manifest: no response-generating local model runtimes/components.
# ---------------------------------------------------------------------------
gradle = 'android/AD-Glasses/app/build.gradle'
sub(gradle, r'''    def localLlamaRuntimeAarPath = \([\s\S]*?    \)\.toString\(\)\.trim\(\)\n''', '', required=False)
sub(gradle, r'''    if \(localLlamaRuntimeAarPath\.isEmpty\(\)\) \{[\s\S]*?    implementation\("com\.google\.ai\.edge\.litertlm:litertlm-android:0\.14\.0"\)\n''', '', required=False)
manifest = 'android/AD-Glasses/app/src/main/AndroidManifest.xml'
for line in [
    '        <activity android:name=".agent.LocalModelsConfigureActivity" android:exported="false" android:label="Local models" android:configChanges="orientation|screenSize|screenLayout|keyboardHidden" />\n',
    '        <service android:name=".localmodels.download.ModelDownloadForegroundService" android:exported="false" android:foregroundServiceType="dataSync" />\n',
    '        <service android:name=".studiobridge.StudioBridgeForegroundService" android:exported="false" android:foregroundServiceType="microphone" />\n',
]:
    replace(manifest, line, '', required=False)

# ---------------------------------------------------------------------------
# Shared UI agrees with Cloud-only reasoning and Moonshine-only local STT.
# ---------------------------------------------------------------------------
settings = 'android/AD-Glasses/shared/src/commonMain/kotlin/com/ad_glasses/shared/ui/settings/SettingsScreen.kt'
replace(settings, '    fun openLocalModels()\n', '', required=False)
sub(settings, r'''    OutlinedButton\(\n        onClick = actions::openLocalModels,[\s\S]*?    \) \{\n        Text\(stringResource\(Res\.string\.settings_configure_local_models\)\)\n    \}\n''', '', required=False)
sub(settings, r'''\n        stringResource\(Res\.string\.settings_faq_local_models_question\) to stringResource\(Res\.string\.settings_faq_local_models_answer\),''', '', required=False)
shared_dest = 'android/AD-Glasses/shared/src/commonMain/kotlin/com/ad_glasses/shared/ui/SharedDestinationScreen.kt'
replace(shared_dest, '    override fun openLocalModels() = Unit\n', '', required=False)

recordings = 'android/AD-Glasses/shared/src/commonMain/kotlin/com/ad_glasses/shared/ui/recordings/RecordingsScreen.kt'
sub(recordings, r'''@Composable\nprivate fun TranscriptionEngineDialog\([\s\S]*?\n\}\n\n@Composable\nprivate fun TranscriptionProgressDialog''', '''@Composable\nprivate fun TranscriptionEngineDialog(\n    selectedEngine: TranscriptionEngine,\n    onEngineSelected: (TranscriptionEngine) -> Unit,\n    onConfirm: () -> Unit,\n    onDismiss: () -> Unit,\n) {\n    AlertDialog(\n        onDismissRequest = onDismiss,\n        title = { Text(stringResource(Res.string.recordings_transcription_engine)) },\n        text = {\n            Column {\n                Text(stringResource(Res.string.recordings_moonshine_local), style = MaterialTheme.typography.bodyLarge)\n                Text(\n                    stringResource(Res.string.recordings_local_moonshine),\n                    style = MaterialTheme.typography.bodySmall,\n                    color = MaterialTheme.colorScheme.onSurfaceVariant,\n                )\n            }\n        },\n        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.action_start)) } },\n        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },\n    )\n}\n\n@Composable\nprivate fun TranscriptionProgressDialog''')

# ---------------------------------------------------------------------------
# Cloud profile UX: one per-turn web control. Profile only advertises capability.
# ---------------------------------------------------------------------------
prefs = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/router/AiProviderPrefs.kt'
replace(prefs, '        require(saved.baseUrl.isNotBlank()) { "API base URL is required." }\n', '        require(saved.baseUrl.startsWith("https://")) { "API base URL must use HTTPS." }\n')
replace(prefs, '        webMode = CloudWebMode.OFF,\n', '        webMode = if (provider.nativeWebCapable) CloudWebMode.AUTO else CloudWebMode.OFF,\n', required=False)
replace(prefs, '        webMode = if (profile.provider.nativeWebCapable) profile.webMode else CloudWebMode.OFF,\n', '        webMode = if (profile.provider.nativeWebCapable) CloudWebMode.AUTO else CloudWebMode.OFF,\n')

cloud_ui = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADNativeAiDetailScreens.kt'
replace(cloud_ui, '    var webMode by remember(initial.id) { mutableStateOf(initial.webMode) }\n', '', required=False)
replace(cloud_ui, '        webMode = webMode,\n', '        webMode = if (provider.nativeWebCapable) CloudWebMode.AUTO else CloudWebMode.OFF,\n', required=False)
replace(cloud_ui, '                                webMode = CloudWebMode.OFF\n', '', required=False)
sub(cloud_ui, r'''                if \(provider\.nativeWebCapable\) \{[\s\S]*?                \} else \{[\s\S]*?                \}\n''', '''                Text(\n                    if (provider.nativeWebCapable) {\n                        "Web search is available for this provider. Use the globe in Ask to enable it for a specific turn."\n                    } else {\n                        "This provider has no AD-integrated native web-search tool; Ask still works normally."\n                    },\n                    style = MaterialTheme.typography.labelSmall,\n                    color = ADColors.Muted,\n                )\n''', required=False)
sub(cloud_ui, r'''\n@Composable\nprivate fun ADWebModePill\([\s\S]*?\n\}\n\n@Composable\nprivate fun MoonshineVoiceInputCard''', '\n@Composable\nprivate fun MoonshineVoiceInputCard', required=False)

# Ask: globe is the only web toggle, and it is disabled for profiles without native web integration.
conversation = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADNativeConversationScreen.kt'
if 'import com.ad_glasses.ai.router.AiProviderPrefs\n' not in read(conversation):
    replace(conversation, 'import com.ad_glasses.ai.orchestrator.AssistantTurn\n', 'import com.ad_glasses.ai.orchestrator.AssistantTurn\nimport com.ad_glasses.ai.router.AiProviderPrefs\n')
replace(conversation, '    var webSearch by remember { mutableStateOf(false) }\n', '    var webSearch by remember { mutableStateOf(false) }\n    val webAvailable = AiProviderPrefs.getActiveProfile(context)?.webAvailable == true\n')
replace(conversation, '        val useWeb = webSearch\n', '        val useWeb = webSearch && webAvailable\n')
replace(conversation, '        webSearch = request.webSearchRequested\n', '        webSearch = request.webSearchRequested && webAvailable\n')
replace(conversation, '                webSearch = webSearch,\n                onWebSearchChange = { webSearch = it },\n', '                webSearch = webSearch,\n                webAvailable = webAvailable,\n                onWebSearchChange = { webSearch = it && webAvailable },\n')
replace(conversation, '    webSearch: Boolean,\n    onWebSearchChange: (Boolean) -> Unit,\n', '    webSearch: Boolean,\n    webAvailable: Boolean,\n    onWebSearchChange: (Boolean) -> Unit,\n')
replace(conversation, '                    enabled = !sending,\n', '                    enabled = !sending && webAvailable,\n', required=False)
replace(conversation, '                        contentDescription = if (webSearch) "Disable web search" else "Enable web search",\n                        tint = if (webSearch) ADColors.Blue else ADColors.Muted,\n', '                        contentDescription = when {\n                            !webAvailable -> "Web search unavailable for active profile"\n                            webSearch -> "Disable web search"\n                            else -> "Enable web search"\n                        },\n                        tint = if (webSearch && webAvailable) ADColors.Blue else ADColors.Muted,\n', required=False)

web_policy = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/orchestrator/AssistantWebPolicy.kt'
write(web_policy, '''package com.ad_glasses.ai.orchestrator\n\nimport com.ad_glasses.shared.chat.ChatMessage\n\n/** Web use is opt-in: a visible toggle or an explicit search/browse phrase. */\nobject AssistantWebPolicy {\n    fun shouldUseWeb(\n        text: String,\n        requested: Boolean? = null,\n        history: List<ChatMessage> = emptyList(),\n    ): Boolean = requested == true || EXPLICIT_WEB.containsMatchIn(text.trim())\n\n    private val EXPLICIT_WEB = Regex(\n        pattern = "\\\\b(search (?:the )?web|browse (?:the )?web|search online|browse online|use web search|" +\n            "look up .{0,80} (?:online|on the web|on the internet)|search the internet)\\\\b",\n        option = RegexOption.IGNORE_CASE,\n    )\n}\n''')

# ---------------------------------------------------------------------------
# Gemini key safety: never put API keys into URLs.
# ---------------------------------------------------------------------------
api = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/router/ApiAiRouter.kt'
replace(api, 'import java.net.URLEncoder\n', '', required=False)
replace(api, '        require(cleanBase.startsWith("https://") || cleanBase.startsWith("http://")) {\n            "API base URL must start with http:// or https://"\n        }\n', '        require(cleanBase.startsWith("https://")) { "API base URL must use HTTPS." }\n', required=False)
sub(api, r'''        val response = if \(provider == ApiProvider\.GOOGLE\) \{[\s\S]*?        \} else \{\n            getJson\("\$cleanBase/models", apiKey = key\)\n        \}\n''', '        val response = getJson("$cleanBase/models", apiKey = key)\n', required=False)
sub(api, r'''        val endpoint = "\$nativeBase/models/\$\{URLEncoder\.encode\(profile\.model, "UTF-8"\)\}:generateContent\?key=\$\{URLEncoder\.encode\(apiKey, "UTF-8"\)\}"\n        return postJson\(endpoint, apiKey = null, payload = payload\)''', '''        val endpoint = "$nativeBase/models/${profile.model}:generateContent"\n        return postJson(\n            endpoint,\n            apiKey = null,\n            payload = payload,\n            extraHeaders = mapOf("x-goog-api-key" to apiKey),\n        )''', required=False)
replace(api, '    private fun postJson(\n        url: String,\n        apiKey: String?,\n        payload: JSONObject,\n    ): JSONObject = requestJson("POST", url, apiKey, payload)\n\n    private fun getJson(url: String, apiKey: String?): JSONObject = requestJson("GET", url, apiKey, null)\n', '''    private fun postJson(\n        url: String,\n        apiKey: String?,\n        payload: JSONObject,\n        extraHeaders: Map<String, String> = emptyMap(),\n    ): JSONObject = requestJson("POST", url, apiKey, payload, extraHeaders)\n\n    private fun getJson(\n        url: String,\n        apiKey: String?,\n        extraHeaders: Map<String, String> = emptyMap(),\n    ): JSONObject = requestJson("GET", url, apiKey, null, extraHeaders)\n''', required=False)
replace(api, '        payload: JSONObject?,\n    ): JSONObject {\n', '        payload: JSONObject?,\n        extraHeaders: Map<String, String> = emptyMap(),\n    ): JSONObject {\n', required=False)
replace(api, '            if (!apiKey.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")\n', '            if (!apiKey.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")\n            extraHeaders.forEach { (name, value) -> conn.setRequestProperty(name, value) }\n', required=False)

# ---------------------------------------------------------------------------
# Small current-product wording cleanup.
# ---------------------------------------------------------------------------
for path in [
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADProductSettingsScreens.kt',
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/summarization/AiSummarizationService.kt',
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/localagent/LocalAgentBrain.kt',
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/transcription/MoonshotTranscriptionProvider.kt',
]:
    if ROOT.joinpath(path).exists():
        value = read(path)
        value = value.replace('Local AI and configured API', 'Cloud AI')
        value = value.replace('Local AI and configured API providers', 'Cloud AI providers')
        value = value.replace('selected AD Glasses Cloud API or Local AI route', 'active AD Glasses Cloud AI profile')
        value = value.replace('local LLM, remote endpoint', 'Cloud AI endpoint')
        value = value.replace('Use Gemma LiteRT local transcription or configure cloud credentials.', 'Install Moonshine for offline English transcription or configure cloud transcription credentials.')
        write(path, value)

# ---------------------------------------------------------------------------
# Remove response-generating local-model implementation, setup UI, Studio Bridge and their tests.
# Moonshine is intentionally outside these paths and remains.
# ---------------------------------------------------------------------------
for path in [
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/localmodels',
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/agent/LocalModelsConfigureActivity.kt',
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/transcription/GemmaLiteRtTranscriptionProvider.kt',
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/assistant',
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/studiobridge',
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/media/autocapture/AutoLoopVisualNoteGenerator.kt',
    'android/AD-Glasses/shared/src/commonMain/kotlin/com/ad_glasses/shared/localmodels',
    'android/AD-Glasses/shared/src/commonMain/kotlin/com/ad_glasses/shared/ui/localmodels',
    'android/AD-Glasses/app/src/androidTest/java/com/ad_glasses/localmodels',
    'android/AD-Glasses/app/src/androidTest/java/com/ad_glasses/ui/localmodels',
    'android/AD-Glasses/app/src/test/java/com/ad_glasses/localmodels',
    'android/AD-Glasses/shared/src/commonTest/kotlin/com/ad_glasses/localmodels',
    'android/AD-Glasses/shared/src/commonTest/kotlin/com/ad_glasses/shared/localmodels',
]:
    remove_path(path)

# Tests that referenced the removed Gemma transcription enum are stale and will be rebuilt around Moonshine-only behavior later if CI requires it.
recording_test = ROOT / 'android/AD-Glasses/app/src/androidTest/java/com/ad_glasses/ui/recordings/RecordingsScreenTest.kt'
if recording_test.exists():
    value = recording_test.read_text().replace('TranscriptionEngine.GEMMA', 'TranscriptionEngine.MOONSHINE')
    recording_test.write_text(value)

settings_test = ROOT / 'android/AD-Glasses/app/src/androidTest/java/com/ad_glasses/ui/settings/SettingsScreenTest.kt'
if settings_test.exists():
    value = settings_test.read_text()
    value = value.replace('Configure Local Agent planning, phone-control safety, and local models from its Native Plugins card.', 'Configure Cloud AI separately from Local Agent phone-control safety.')
    settings_test.write_text(value)

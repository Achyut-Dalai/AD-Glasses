from pathlib import Path

ROOT = Path('android/AD-Glasses/app/src/main/java/com/ad_glasses')


def edit(path: Path, transforms):
    text = path.read_text(encoding='utf-8')
    original = text
    for label, old, new in transforms:
        if new in text and old not in text:
            print(f'{path}: already applied: {label}')
            continue
        count = text.count(old)
        if count != 1:
            raise SystemExit(f'{path}: {label}: expected one match, found {count}')
        text = text.replace(old, new, 1)
        print(f'{path}: applied: {label}')
    if text != original:
        path.write_text(text, encoding='utf-8')


# MainActivity still legitimately uses MediaScannerConnection for legacy gallery publication.
edit(ROOT / 'MainActivity.kt', [
    (
        'restore MediaScannerConnection import',
        'import android.net.Network\n',
        'import android.media.MediaScannerConnection\nimport android.net.Network\n',
    ),
])

# Direct API-token client remains the Cloud REST boundary. Optional model override preserves
# plugin/model-specific choices without reviving the removed relay client.
edit(ROOT / 'ai/router/ApiAiRouter.kt', [
    (
        'add direct-API model override',
        '''        audioPath: String? = null,\n        maxTokens: Int = 2048,\n    ): Result<String> = runCatching {\n''',
        '''        audioPath: String? = null,\n        maxTokens: Int = 2048,\n        modelOverride: String? = null,\n    ): Result<String> = runCatching {\n''',
    ),
    (
        'resolve model override against selected provider',
        '''        val model = AiProviderPrefs.getModel(context, provider)\n        require(model.isNotBlank()) { "${provider.label} model is not configured" }\n''',
        '''        val model = modelOverride?.trim()?.takeIf { it.isNotBlank() }\n            ?: AiProviderPrefs.getModel(context, provider)\n        require(model.isNotBlank()) { "${provider.label} model is not configured" }\n''',
    ),
])

# There are two LocalAgentPrefs objects. Planner provider ownership lives in agent.LocalAgentPrefs;
# runtime safety/status preferences live in localagent.LocalAgentPrefs.
edit(ROOT / 'localagent/LocalAgentBrain.kt', [
    (
        'use inference provider preference owner',
        'AgentInferenceRouter.isRemotePlanner(LocalAgentPrefs.getProviderType(context))',
        'AgentInferenceRouter.isRemotePlanner(com.ad_glasses.agent.LocalAgentPrefs.getProviderType(context))',
    ),
])

edit(ROOT / 'plugins/livecaptionrelay/LiveCaptionRelayService.kt', [
    (
        'migrate caption translation import',
        'import com.ad_glasses.ai.router.CliRelayClient\n',
        'import com.ad_glasses.ai.router.ApiTokenClient\n',
    ),
    (
        'migrate caption translation to direct Cloud API',
        '''        return CliRelayClient.chat(\n            context = this,\n            chatId = "live_caption_${System.currentTimeMillis()}",\n            prompt = prompt,\n            messages = listOf(mapOf("role" to "user", "content" to prompt)),\n            modelOverride = LiveCaptionRelayPreferences.getCloudModelId(this),\n        ).fold(\n''',
        '''        return ApiTokenClient.chat(\n            context = this,\n            messages = listOf(mapOf("role" to "user", "content" to prompt)),\n            maxTokens = 512,\n            modelOverride = LiveCaptionRelayPreferences.getCloudModelId(this),\n        ).fold(\n''',
    ),
])

edit(ROOT / 'plugins/meetingsparknotes/MeetingSparkNotesService.kt', [
    (
        'migrate meeting summary import',
        'import com.ad_glasses.ai.router.CliRelayClient\n',
        'import com.ad_glasses.ai.router.ApiTokenClient\n',
    ),
    (
        'migrate meeting summary to direct Cloud API',
        '''        val result = CliRelayClient.chat(\n            context = this,\n            chatId = "meeting_spark_notes_${System.currentTimeMillis()}",\n            prompt = prompt,\n            messages = listOf(mapOf("role" to "user", "content" to prompt)),\n            modelOverride = MeetingSparkNotesPreferences.getCloudModelId(this),\n        )\n''',
        '''        val result = ApiTokenClient.chat(\n            context = this,\n            messages = listOf(mapOf("role" to "user", "content" to prompt)),\n            maxTokens = 2048,\n            modelOverride = MeetingSparkNotesPreferences.getCloudModelId(this),\n        )\n''',
    ),
])

edit(ROOT / 'ui/AndroidSharedServiceWrappers.kt', [
    (
        'migrate shared chat import',
        'import com.ad_glasses.ai.router.CliRelayClient\n',
        'import com.ad_glasses.ai.router.ApiTokenClient\n',
    ),
    (
        'remove obsolete shared chat prompt extraction',
        '''        val userPrompt = cleanMessages.lastOrNull { it["role"] == "user" }?.get("content")\n            ?: error("A user message is required")\n''',
        '''        require(cleanMessages.any { it["role"] == "user" }) { "A user message is required" }\n''',
    ),
    (
        'migrate shared cloud chat to direct Cloud API',
        '''            AgentProviderType.CLOUD_AI -> CliRelayClient.chat(\n                context = appContext,\n                chatId = "shared_${UUID.randomUUID()}",\n                prompt = userPrompt,\n                messages = cleanMessages,\n                modelOverride = model,\n            ).getOrThrow()\n''',
        '''            AgentProviderType.CLOUD_AI -> ApiTokenClient.chat(\n                context = appContext,\n                messages = cleanMessages,\n                modelOverride = model,\n            ).getOrThrow()\n''',
    ),
    (
        'remove obsolete UUID import',
        'import java.util.UUID\n',
        '',
    ),
])

edit(ROOT / 'plugins/localagent/LocalAgentPlugin.kt', [
    (
        'map cloud planner to current Cloud API provider',
        'AgentProviderType.CLOUD_AI -> AiProviderType.CLI_RELAY',
        'AgentProviderType.CLOUD_AI -> AiProviderType.CLOUD_API',
    ),
])

edit(ROOT / 'ui/adglasses/ADNativeAiDetailScreens.kt', [
    (
        'remove retired assistant-mode import',
        'import com.ad_glasses.shared.glasses.GlassesAssistantMode\n',
        '',
    ),
    (
        'remove retired assistant-mode write',
        '                LocalAgentPrefs.setGlassesAssistantMode(context, GlassesAssistantMode.CUSTOM_AI_PROVIDER)\n',
        '',
    ),
])

print('forward-only compile cleanup round 2 completed')

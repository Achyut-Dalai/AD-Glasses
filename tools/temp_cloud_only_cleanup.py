from pathlib import Path
import re

ROOT = Path('.')


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding='utf-8')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected exactly one match, found {count}')
    return text.replace(old, new, 1)


# Shared chat no longer has a local-model configuration state.
path = 'android/AD-Glasses/shared/src/commonMain/kotlin/com/ad_glasses/shared/ui/chat/ChatThreadScreen.kt'
text = read(path)
text = replace_once(
    text,
    '''    val inputLabel = if (composer.primaryAction == ChatComposerPrimaryAction.CONFIGURE_LOCAL_MODEL) {\n         stringResource(Res.string.chat_local_model_required)\n    } else {\n         stringResource(Res.string.chat_message)\n    }\n''',
    '''    val inputLabel = stringResource(Res.string.chat_message)\n''',
    'chat composer local-model label',
)
text = text.replace('''                            ChatComposerPrimaryAction.CONFIGURE_LOCAL_MODEL -> AppIcon.Model.imageVector()\n''', '')
text = text.replace('''                             ChatComposerPrimaryAction.CONFIGURE_LOCAL_MODEL -> stringResource(Res.string.chat_configure_local_model)\n''', '')
write(path, text)

# The settings section disclosure strings were generic but had stale local-model resource names.
path = 'android/AD-Glasses/shared/src/commonMain/kotlin/com/ad_glasses/shared/ui/settings/SettingsScreen.kt'
text = read(path)
text = text.replace('Res.string.local_models_collapse', 'Res.string.settings_section_collapse')
text = text.replace('Res.string.local_models_expand', 'Res.string.settings_section_expand')
write(path, text)

# Cloud profiles have one web capability flag derived from provider support; per-turn Ask controls web use.
path = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/router/AiProviderPrefs.kt'
text = read(path)
text, count = re.subn(
    r'''enum class CloudWebMode\(val wire: String, val label: String\) \{.*?\n\}\n\n(?=enum class ApiProvider)''',
    '',
    text,
    count=1,
    flags=re.S,
)
if count != 1:
    raise RuntimeError(f'CloudWebMode enum: expected one block, found {count}')
text = replace_once(
    text,
    '''data class CloudAiProfile(\n    val id: String,\n    val name: String,\n    val provider: ApiProvider,\n    val baseUrl: String,\n    val model: String,\n    val webMode: CloudWebMode = CloudWebMode.OFF,\n) {\n    val webAvailable: Boolean\n        get() = provider.nativeWebCapable && webMode == CloudWebMode.AUTO\n}\n''',
    '''data class CloudAiProfile(\n    val id: String,\n    val name: String,\n    val provider: ApiProvider,\n    val baseUrl: String,\n    val model: String,\n) {\n    val webAvailable: Boolean\n        get() = provider.nativeWebCapable\n}\n''',
    'CloudAiProfile web mode',
)
text = text.replace('''        model = provider.defaultModel,\n        webMode = if (provider.nativeWebCapable) CloudWebMode.AUTO else CloudWebMode.OFF,\n''', '''        model = provider.defaultModel,\n''')
start = text.find('    // Forward-compatible wrappers for callers being migrated away from the former single-provider model.\n')
end = text.find('    @Synchronized\n    private fun ensureLegacyMigrated', start)
if start < 0 or end < 0:
    raise RuntimeError('AiProviderPrefs compatibility wrapper block not found')
text = text[:start] + text[end:]
text = text.replace('''        model = profile.model.trim(),\n        webMode = if (profile.provider.nativeWebCapable) CloudWebMode.AUTO else CloudWebMode.OFF,\n''', '''        model = profile.model.trim(),\n''')
text = text.replace('''                    model = json.optString("model"),\n                    webMode = CloudWebMode.fromWire(json.optString("web_mode")),\n''', '''                    model = json.optString("model"),\n''')
text = text.replace('''        .put("model", profile.model)\n        .put("web_mode", profile.webMode.wire)\n''', '''        .put("model", profile.model)\n''')
write(path, text)

# Provider-aware model discovery: compatible /models first, with native Gemini fallback.
path = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/router/ApiAiRouter.kt'
text = read(path)
start = text.find('    /** Fetch models without ever returning the API key to UI state. */\n')
end = text.find('    private fun buildOpenAiMessages(', start)
if start < 0 or end < 0:
    raise RuntimeError('discoverModels block not found')
new_block = '''    /** Fetch models without ever returning the API key to UI state. */\n    suspend fun discoverModels(\n        context: Context,\n        provider: ApiProvider,\n        baseUrl: String,\n        profileId: String? = null,\n        apiKeyReplacement: String? = null,\n    ): Result<List<String>> = runCatching {\n        val key = apiKeyReplacement?.trim().orEmpty().ifBlank {\n            profileId?.let { AiProviderPrefs.apiKeyForRequest(context, it) }.orEmpty()\n        }\n        require(key.isNotBlank()) { "Enter an API key or use a profile that already has one saved." }\n        val cleanBase = baseUrl.trim().trimEnd('/')\n        require(cleanBase.startsWith("https://")) { "API base URL must use HTTPS." }\n\n        val response = if (provider == ApiProvider.GOOGLE) {\n            runCatching { getJson("$cleanBase/models", apiKey = key) }\n                .getOrElse { compatibleError ->\n                    val nativeBase = cleanBase.substringBeforeLast("/openai", cleanBase).trimEnd('/')\n                    runCatching {\n                        getJson(\n                            "$nativeBase/models",\n                            apiKey = null,\n                            extraHeaders = mapOf("x-goog-api-key" to key),\n                        )\n                    }.getOrElse { throw compatibleError }\n                }\n        } else {\n            getJson("$cleanBase/models", apiKey = key)\n        }\n\n        val models = selectableModelIds(response)\n        if (models.isEmpty()) throw IllegalStateException("The provider returned no selectable generation models.")\n        models.sortedWith(\n            compareBy<String> { it != provider.defaultModel }\n                .thenBy { !it.contains("flash", ignoreCase = true) }\n                .thenBy { it.lowercase() },\n        )\n    }\n\n    private fun selectableModelIds(response: JSONObject): Set<String> {\n        val models = linkedSetOf<String>()\n        response.optJSONArray("data")?.let { data ->\n            for (index in 0 until data.length()) {\n                data.optJSONObject(index)?.optString("id")?.trim()?.takeIf { it.isNotBlank() }?.let(models::add)\n            }\n        }\n        response.optJSONArray("models")?.let { data ->\n            for (index in 0 until data.length()) {\n                val item = data.optJSONObject(index) ?: continue\n                val methods = item.optJSONArray("supportedGenerationMethods")\n                if (methods != null) {\n                    var canGenerate = false\n                    for (methodIndex in 0 until methods.length()) {\n                        if (methods.optString(methodIndex) == "generateContent") {\n                            canGenerate = true\n                            break\n                        }\n                    }\n                    if (!canGenerate) continue\n                }\n                val id = item.optString("id").trim().ifBlank {\n                    item.optString("name").trim().removePrefix("models/")\n                }\n                if (id.isNotBlank()) models += id\n            }\n        }\n        return models\n    }\n\n'''
text = text[:start] + new_block + text[end:]
write(path, text)

# Android Cloud settings no longer imports the removed persisted web mode.
path = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADNativeAiDetailScreens.kt'
text = read(path).replace('import com.ad_glasses.ai.router.CloudWebMode\n', '')
write(path, text)

# Local Agent copy points users to Cloud AI profiles, not a removed local model screen.
path = 'android/AD-Glasses/app/src/main/res/values/strings_compose.xml'
text = read(path)
text = text.replace(
    '<string name="compose_local_agent_provider_description">AI provider and local model configuration are managed in Settings.</string>',
    '<string name="compose_local_agent_provider_description">Cloud AI profiles and provider credentials are managed in Cloud AI settings.</string>',
)
write(path, text)

# Shared resources: preserve translated generic expand/collapse strings, remove the retired Local Models section and stale chat/setup copy.
resources_root = ROOT / 'android/AD-Glasses/shared/src/commonMain/composeResources'
for file in sorted(resources_root.glob('values*/strings_extra.xml')):
    text = file.read_text(encoding='utf-8')
    is_base = file.parent.name == 'values'

    collapse_match = re.search(r'^\s*<string name="local_models_collapse">.*?</string>\s*$', text, flags=re.M)
    expand_match = re.search(r'^\s*<string name="local_models_expand">.*?</string>\s*$', text, flags=re.M)
    generic_lines = []
    if collapse_match:
        generic_lines.append(collapse_match.group(0).replace('local_models_collapse', 'settings_section_collapse').strip())
    if expand_match:
        generic_lines.append(expand_match.group(0).replace('local_models_expand', 'settings_section_expand').strip())

    text, block_count = re.subn(
        r'\n\s*<!-- Local models -->.*?(?=\n\s*<!-- Local agent and utility screens -->)',
        '\n',
        text,
        count=1,
        flags=re.S,
    )
    if block_count == 0:
        # Some translated resources do not keep the section comment; remove all local_models_* lines instead.
        text = re.sub(r'^\s*<string name="local_models_[^"]+">.*?</string>\s*\n?', '', text, flags=re.M)

    for name_pattern in (
        r'settings_configure_local_models',
        r'settings_faq_local_models_question',
        r'settings_faq_local_models_answer',
        r'chat_local_model_required',
        r'chat_configure_local_model',
    ):
        text = re.sub(rf'^\s*<string name="{name_pattern}">.*?</string>\s*\n?', '', text, flags=re.M)

    if generic_lines:
        marker = '<!-- Local agent and utility screens -->'
        insertion = ''.join(f'    {line}\n' for line in generic_lines) + '\n    '
        if marker in text:
            text = text.replace(marker, insertion + marker, 1)
        else:
            raise RuntimeError(f'{file}: local agent section marker missing')

    # Remove stale localized text for Cloud-owned chat/provider behavior so non-English builds fall back to the corrected base text.
    if not is_base:
        for name in ('settings_provider_description', 'chat_empty_body', 'dashboard_ai_wake_word_image_warning_body'):
            text = re.sub(rf'^\s*<string name="{name}">.*?</string>\s*\n?', '', text, flags=re.M)
    else:
        text = re.sub(
            r'<string name="settings_provider_description">.*?</string>',
            '<string name="settings_provider_description">Cloud AI uses the active encrypted provider profile configured on this device.</string>',
            text,
        )
        text = re.sub(
            r'<string name="chat_empty_body">.*?</string>',
            '<string name="chat_empty_body">Ask a question or attach media using your active Cloud AI profile.</string>',
            text,
        )
        text = re.sub(
            r'<string name="dashboard_ai_wake_word_image_warning_body">.*?</string>',
            '<string name="dashboard_ai_wake_word_image_warning_body">The HeyCyan SDK does not expose separate events for the AI voice wake word and the hardware AI audio-question button. If you choose AI image questions, pressing the hardware button for an AI voice question will also start an image question. Simple questions can ignore the captured image when they do not ask about it, but image requests can take longer because the captured frame must be uploaded and analyzed by the active Cloud AI profile.</string>',
            text,
        )

    file.write_text(text, encoding='utf-8')

# Dedicated Local LLM documentation/runtime helper is obsolete.
for relative in (
    'android/AD-Glasses/docs/local-models-plan.md',
    'android/AD-Glasses/docs/local-models.md',
    'android/AD-Glasses/docs/localmodels-vulkan-runtime-integration.md',
    'android/AD-Glasses/scripts/localmodels/use_local_llama_runtime.sh',
):
    target = ROOT / relative
    if target.exists():
        target.unlink()

# Remove local-text-model artifact rules while keeping all secret/build ignores.
path = 'android/AD-Glasses/.gitignore'
text = read(path)
text = re.sub(
    r'\n# Local model artifacts \(never commit weights\)\n\*\.gguf\n\*\.gguf\.part\n\*\.bin\n\*\.safetensors\napp/src/main/assets/models/\napp/src/main/ml/\n',
    '\n',
    text,
    count=1,
)
write(path, text)

# Guard against the main live text-LLM symbols returning in product source.
for root in (
    ROOT / 'android/AD-Glasses/app/src/main',
    ROOT / 'android/AD-Glasses/shared/src/commonMain',
):
    for file in root.rglob('*'):
        if not file.is_file() or file.suffix.lower() not in {'.kt', '.xml', '.java'}:
            continue
        payload = file.read_text(encoding='utf-8', errors='ignore')
        forbidden = [
            'LOCAL_MODEL',
            'LOCAL_MODELS',
            'LocalModelsProvider',
            'CONFIGURE_LOCAL_MODEL',
            'chat_local_model_required',
            'chat_configure_local_model',
            'local_models_title',
        ]
        hits = [token for token in forbidden if token in payload]
        if hits:
            raise RuntimeError(f'{file}: remaining local text-LLM tokens: {hits}')

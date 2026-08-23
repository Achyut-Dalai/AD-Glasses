from pathlib import Path
import re

ROOT = Path('.')


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding='utf-8')


path = 'android/AD-Glasses/shared/src/commonMain/kotlin/com/ad_glasses/shared/ui/chat/ChatThreadScreen.kt'
text = read(path)
text = text.replace('import com.ad_glasses.shared.generated.resources.chat_configure_local_model\n', '')
text = text.replace('import com.ad_glasses.shared.generated.resources.chat_local_model_required\n', '')
text, _ = re.subn(
    r'''\s*val inputLabel = if \(composer\.primaryAction == ChatComposerPrimaryAction\.CONFIGURE_LOCAL_MODEL\) \{\s*stringResource\(Res\.string\.chat_local_model_required\)\s*\} else \{\s*stringResource\(Res\.string\.chat_message\)\s*\}\s*''',
    '\n    val inputLabel = stringResource(Res.string.chat_message)\n',
    text,
    count=1,
)
text = re.sub(
    r'^\s*ChatComposerPrimaryAction\.CONFIGURE_LOCAL_MODEL -> AppIcon\.Model\.imageVector\(\)\s*\n',
    '',
    text,
    flags=re.M,
)
text = re.sub(
    r'^\s*ChatComposerPrimaryAction\.CONFIGURE_LOCAL_MODEL -> stringResource\(Res\.string\.chat_configure_local_model\)\s*\n',
    '',
    text,
    flags=re.M,
)
write(path, text)

path = 'android/AD-Glasses/shared/src/commonMain/kotlin/com/ad_glasses/shared/ui/settings/SettingsScreen.kt'
text = read(path)
text = text.replace('Res.string.local_models_collapse', 'Res.string.settings_section_collapse')
text = text.replace('Res.string.local_models_expand', 'Res.string.settings_section_expand')
write(path, text)

path = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/router/ApiAiRouter.kt'
text = read(path)
start = text.find('    /** Fetch models without ever returning the API key to UI state. */\n')
end = text.find('    private fun buildOpenAiMessages(', start)
if start < 0 or end < 0:
    raise RuntimeError('discoverModels block not found')
new_block = '''    /** Fetch models without ever returning the API key to UI state. */
    suspend fun discoverModels(
        context: Context,
        provider: ApiProvider,
        baseUrl: String,
        profileId: String? = null,
        apiKeyReplacement: String? = null,
    ): Result<List<String>> = runCatching {
        val key = apiKeyReplacement?.trim().orEmpty().ifBlank {
            profileId?.let { AiProviderPrefs.apiKeyForRequest(context, it) }.orEmpty()
        }
        require(key.isNotBlank()) { "Enter an API key or use a profile that already has one saved." }
        val cleanBase = baseUrl.trim().trimEnd('/')
        require(cleanBase.startsWith("https://")) { "API base URL must use HTTPS." }

        val response = if (provider == ApiProvider.GOOGLE) {
            runCatching { getJson("$cleanBase/models", apiKey = key) }
                .getOrElse { compatibleError ->
                    val nativeBase = cleanBase.substringBeforeLast("/openai", cleanBase).trimEnd('/')
                    runCatching {
                        getJson(
                            "$nativeBase/models",
                            apiKey = null,
                            extraHeaders = mapOf("x-goog-api-key" to key),
                        )
                    }.getOrElse { throw compatibleError }
                }
        } else {
            getJson("$cleanBase/models", apiKey = key)
        }

        val models = selectableModelIds(response)
        if (models.isEmpty()) throw IllegalStateException("The provider returned no selectable generation models.")
        models.sortedWith(
            compareBy<String> { it != provider.defaultModel }
                .thenBy { !it.contains("flash", ignoreCase = true) }
                .thenBy { it.lowercase() },
        )
    }

    private fun selectableModelIds(response: JSONObject): Set<String> {
        val models = linkedSetOf<String>()
        response.optJSONArray("data")?.let { data ->
            for (index in 0 until data.length()) {
                data.optJSONObject(index)?.optString("id")?.trim()?.takeIf { it.isNotBlank() }?.let(models::add)
            }
        }
        response.optJSONArray("models")?.let { data ->
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val methods = item.optJSONArray("supportedGenerationMethods")
                if (methods != null) {
                    var canGenerate = false
                    for (methodIndex in 0 until methods.length()) {
                        if (methods.optString(methodIndex) == "generateContent") {
                            canGenerate = true
                            break
                        }
                    }
                    if (!canGenerate) continue
                }
                val id = item.optString("id").trim().ifBlank {
                    item.optString("name").trim().removePrefix("models/")
                }
                if (id.isNotBlank()) models += id
            }
        }
        return models
    }

'''
text = text[:start] + new_block + text[end:]
write(path, text)

path = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADNativeAiDetailScreens.kt'
text = read(path).replace('import com.ad_glasses.ai.router.CloudWebMode\n', '')
text = text.replace(
    '''        model = model,\n        webMode = if (provider.nativeWebCapable) CloudWebMode.AUTO else CloudWebMode.OFF,\n''',
    '''        model = model,\n''',
)
write(path, text)

path = 'android/AD-Glasses/app/src/main/res/values/strings_compose.xml'
text = read(path)
text = text.replace(
    '<string name="compose_local_agent_provider_description">AI provider and local model configuration are managed in Settings.</string>',
    '<string name="compose_local_agent_provider_description">Cloud AI profiles and provider credentials are managed in Cloud AI settings.</string>',
)
write(path, text)

resources_root = ROOT / 'android/AD-Glasses/shared/src/commonMain/composeResources'
for file in sorted(resources_root.glob('values*/strings_extra.xml')):
    text = file.read_text(encoding='utf-8')
    is_base = file.parent.name == 'values'

    collapse = re.search(r'^\s*<string name="local_models_collapse">.*?</string>\s*$', text, flags=re.M)
    expand = re.search(r'^\s*<string name="local_models_expand">.*?</string>\s*$', text, flags=re.M)
    generic_lines = []
    if collapse:
        generic_lines.append(collapse.group(0).replace('local_models_collapse', 'settings_section_collapse').strip())
    if expand:
        generic_lines.append(expand.group(0).replace('local_models_expand', 'settings_section_expand').strip())

    text, block_count = re.subn(
        r'\n\s*<!-- Local models -->.*?(?=\n\s*<!-- Local agent and utility screens -->)',
        '\n',
        text,
        count=1,
        flags=re.S,
    )
    if block_count == 0:
        text = re.sub(r'^\s*<string name="local_models_[^"]+">.*?</string>\s*\n?', '', text, flags=re.M)

    for name in (
        'settings_configure_local_models',
        'settings_faq_local_models_question',
        'settings_faq_local_models_answer',
        'chat_local_model_required',
        'chat_configure_local_model',
    ):
        text = re.sub(rf'^\s*<string name="{name}">.*?</string>\s*\n?', '', text, flags=re.M)

    if generic_lines and 'settings_section_collapse' not in text:
        insertion = ''.join(f'    {line}\n' for line in generic_lines)
        marker = '<!-- Local agent and utility screens -->'
        if marker in text:
            text = text.replace(marker, insertion + '\n    ' + marker, 1)
        elif '</resources>' in text:
            text = text.replace('</resources>', '\n' + insertion + '</resources>', 1)
        else:
            raise RuntimeError(f'{file}: resources closing tag missing')

    if is_base:
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
    else:
        for name in ('settings_provider_description', 'chat_empty_body', 'dashboard_ai_wake_word_image_warning_body'):
            text = re.sub(rf'^\s*<string name="{name}">.*?</string>\s*\n?', '', text, flags=re.M)

    file.write_text(text, encoding='utf-8')

for root in (
    ROOT / 'android/AD-Glasses/app/src/main',
    ROOT / 'android/AD-Glasses/shared/src/commonMain',
):
    for file in root.rglob('*'):
        if not file.is_file() or file.suffix.lower() not in {'.kt', '.xml', '.java'}:
            continue
        payload = file.read_text(encoding='utf-8', errors='ignore')
        forbidden = (
            'LOCAL_MODEL',
            'LOCAL_MODELS',
            'LocalModelsProvider',
            'CONFIGURE_LOCAL_MODEL',
            'chat_local_model_required',
            'chat_configure_local_model',
            'local_models_title',
            'CloudWebMode',
        )
        hits = [token for token in forbidden if token in payload]
        if hits:
            raise RuntimeError(f'{file}: remaining retired AI tokens: {hits}')

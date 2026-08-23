from pathlib import Path

ROOT = Path('.')


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding='utf-8')


# Final credential hardening: a saved secret belongs to the provider + endpoint it was
# originally created for. Never silently reuse it after either identity changes.
prefs_path = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/router/AiProviderPrefs.kt'
prefs = read(prefs_path)
old = '''        val existing = readProfile(prefs, saved.id)\n        val replacement = apiKeyReplacement?.trim().orEmpty()\n        if (existing == null && replacement.isBlank()) {\n            require(hasApiKeyInternal(prefs, saved.id)) { "API key is required for a new profile." }\n        }\n'''
new = '''        val existing = readProfile(prefs, saved.id)\n        val replacement = apiKeyReplacement?.trim().orEmpty()\n        if (existing == null && replacement.isBlank()) {\n            require(hasApiKeyInternal(prefs, saved.id)) { "API key is required for a new profile." }\n        }\n        if (existing != null && replacement.isBlank()) {\n            require(existing.provider == saved.provider && existing.baseUrl == saved.baseUrl) {\n                "Enter a new API key after changing the provider or API base URL."\n            }\n        }\n'''
if old in prefs:
    prefs = prefs.replace(old, new, 1)
elif 'Enter a new API key after changing the provider or API base URL.' not in prefs:
    raise RuntimeError('Cloud profile credential guard insertion point not found')
write(prefs_path, prefs)

ui_path = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADNativeAiDetailScreens.kt'
ui = read(ui_path)
anchor = '''    var discoveryRunning by remember(initial.id) { mutableStateOf(false) }\n    var discoveryError by remember(initial.id) { mutableStateOf<String?>(null) }\n\n    fun draft(): CloudAiProfile = initial.copy(\n'''
replacement = '''    var discoveryRunning by remember(initial.id) { mutableStateOf(false) }\n    var discoveryError by remember(initial.id) { mutableStateOf<String?>(null) }\n    val savedKeyUsable = hasSavedKey &&\n        provider == initial.provider &&\n        baseUrl.trim().trimEnd('/') == initial.baseUrl.trim().trimEnd('/')\n\n    fun draft(): CloudAiProfile = initial.copy(\n'''
if anchor in ui:
    ui = ui.replace(anchor, replacement, 1)
elif 'val savedKeyUsable = hasSavedKey' not in ui:
    raise RuntimeError('Cloud profile editor saved-key guard insertion point not found')

ui = ui.replace(
    'placeholder = if (hasSavedKey) "API key saved · enter only to replace" else "API key",',
    'placeholder = if (savedKeyUsable) "API key saved · enter only to replace" else "API key",',
)
ui = ui.replace(
    'if (hasSavedKey) "The saved key cannot be revealed in AD Glasses." else "The key is encrypted before it is stored.",',
    'when {\n                            savedKeyUsable -> "The saved key cannot be revealed in AD Glasses."\n                            hasSavedKey -> "Enter a new API key after changing the provider or API base URL; the saved key will not be reused."\n                            else -> "The key is encrypted before it is stored."\n                        },',
)
ui = ui.replace(
    '(hasSavedKey || replacementKey.isNotBlank())',
    '(savedKeyUsable || replacementKey.isNotBlank())',
)
write(ui_path, ui)

# Guard the retired assistant-model surface after the final hardening pass. Moonshine and
# LocalAgent are intentionally excluded: they are speech input and automation, not text LLMs.
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

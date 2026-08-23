from pathlib import Path
import re

ROOT = Path('.')

prefs_path = ROOT / 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/router/AiProviderPrefs.kt'
prefs = prefs_path.read_text(encoding='utf-8')

guard = '''        if (existing != null && replacement.isBlank()) {\n            require(existing.provider == saved.provider && existing.baseUrl == saved.baseUrl) {\n                "Enter a new API key after changing the provider or API base URL."\n            }\n        }\n'''
pattern = re.compile(
    r'(?:        if \(existing != null && replacement\.isBlank\(\)\) \{\n'
    r'            require\(existing\.provider == saved\.provider && existing\.baseUrl == saved\.baseUrl\) \{\n'
    r'                "Enter a new API key after changing the provider or API base URL\."\n'
    r'            \}\n'
    r'        \}\n)+'
)
if not pattern.search(prefs):
    insertion = '''        if (existing == null && replacement.isBlank()) {\n            require(hasApiKeyInternal(prefs, saved.id)) { "API key is required for a new profile." }\n        }\n'''
    if insertion not in prefs:
        raise RuntimeError('Cloud profile credential guard insertion point not found')
    prefs = prefs.replace(insertion, insertion + guard, 1)
else:
    prefs = pattern.sub(guard, prefs, count=1)
prefs_path.write_text(prefs, encoding='utf-8')

ui_path = ROOT / 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADNativeAiDetailScreens.kt'
ui = ui_path.read_text(encoding='utf-8')
if 'val savedKeyUsable = hasSavedKey' not in ui:
    raise RuntimeError('Cloud profile editor credential guard is missing')
if '(hasSavedKey || replacementKey.isNotBlank())' in ui:
    raise RuntimeError('Cloud profile editor can still reuse a saved key after endpoint changes')

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

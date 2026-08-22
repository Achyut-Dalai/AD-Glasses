from pathlib import Path

ROOT = Path('android/AD-Glasses/app/src/main/java/com/ad_glasses')


def edit(path: Path, transforms):
    text = path.read_text(encoding='utf-8')
    original = text
    for label, old, new in transforms:
        count = text.count(old)
        if count != 1:
            raise SystemExit(f'{path}: {label}: expected one match, found {count}')
        text = text.replace(old, new, 1)
        print(f'{path}: applied: {label}')
    if text != original:
        path.write_text(text, encoding='utf-8')


edit(ROOT / 'ai/router/ApiAiRouter.kt', [
    (
        'remove relay-era model override parameter',
        '''        audioPath: String? = null,\n        maxTokens: Int = 2048,\n        modelOverride: String? = null,\n    ): Result<String> = runCatching {\n''',
        '''        audioPath: String? = null,\n        maxTokens: Int = 2048,\n    ): Result<String> = runCatching {\n''',
    ),
    (
        'restore selected API provider model as source of truth',
        '''        val model = modelOverride?.trim()?.takeIf { it.isNotBlank() }\n            ?: AiProviderPrefs.getModel(context, provider)\n''',
        '''        val model = AiProviderPrefs.getModel(context, provider)\n''',
    ),
])

for relative, old in [
    (
        'plugins/livecaptionrelay/LiveCaptionRelayService.kt',
        '''            maxTokens = 512,\n            modelOverride = LiveCaptionRelayPreferences.getCloudModelId(this),\n''',
    ),
    (
        'plugins/meetingsparknotes/MeetingSparkNotesService.kt',
        '''            maxTokens = 2048,\n            modelOverride = MeetingSparkNotesPreferences.getCloudModelId(this),\n''',
    ),
    (
        'ui/AndroidSharedServiceWrappers.kt',
        '''                messages = cleanMessages,\n                modelOverride = model,\n''',
    ),
]:
    replacement = {
        'plugins/livecaptionrelay/LiveCaptionRelayService.kt': '            maxTokens = 512,\n',
        'plugins/meetingsparknotes/MeetingSparkNotesService.kt': '            maxTokens = 2048,\n',
        'ui/AndroidSharedServiceWrappers.kt': '                messages = cleanMessages,\n',
    }[relative]
    edit(ROOT / relative, [('use selected provider model', old, replacement)])

print('selected-provider model alignment completed')

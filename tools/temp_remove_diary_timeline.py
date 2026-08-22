from pathlib import Path
import re
import shutil

ROOT = Path('.')


def read(path):
    return Path(path).read_text(encoding='utf-8')


def write(path, text):
    p = Path(path)
    old = p.read_text(encoding='utf-8')
    if text != old:
        p.write_text(text, encoding='utf-8')


# MainActivity: remove every live DayNote/AutoDiary/VisualDiary integration while preserving
# unrelated generic timeline concepts and current Cloud/Local AI architecture.
path = Path('android/AD-Glasses/app/src/main/java/com/ad_glasses/MainActivity.kt')
text = path.read_text(encoding='utf-8')
text = '\n'.join(
    line for line in text.splitlines()
    if 'com.ad_glasses.plugins.autodiary.' not in line
    and 'com.ad_glasses.plugins.visualdiary.' not in line
) + '\n'
text = text.replace(
'''        val notificationFeatureEnabled =
            AutoDiaryService.isEnabled(this) ||
                VisualDiaryPreferences.isEnabled(this) ||
                LocalAgentPlugin.isEnabled(this) ||
                isMeizuMyvuSelected()
''',
'''        val notificationFeatureEnabled =
            LocalAgentPlugin.isEnabled(this) ||
                isMeizuMyvuSelected()
''')
text = text.replace(
'        val needsAccessibility = AutoDiaryService.isEnabled(this) || LocalAgentPlugin.isEnabled(this)\n',
'        val needsAccessibility = LocalAgentPlugin.isEnabled(this)\n')
text = text.replace('        if (AutoDiaryService.isEnabled(this)) AutoDiaryService.startIfEnabled(this)\n', '')
text = text.replace('        startEnabledCameraFeatures()\n', '')
text = re.sub(
    r'\n    private fun ensureEnabledMetaCameraFeature\(\) \{.*?\n    override fun onNewIntent',
    '\n    override fun onNewIntent',
    text,
    flags=re.S,
)
text = re.sub(
    r'        CommunityPluginPrefs\.setNativePluginEnabled\(\n            this,\n            NativePluginIds\.AUTO_DIARY,\n            AutoDiaryService\.isEnabled\(this\),\n        \)\n',
    '', text)
text = re.sub(
    r'        CommunityPluginPrefs\.setNativePluginEnabled\(\n            this,\n            NativePluginIds\.VISUAL_DIARY,\n            VisualDiaryPreferences\.isEnabled\(this\),\n        \)\n',
    '', text)
text = re.sub(
    r'            NativePluginIds\.AUTO_DIARY -> NativePluginShortcutUiState\(.*?\n            \)\n',
    '', text, flags=re.S)
text = re.sub(
    r'            NativePluginIds\.VISUAL_DIARY -> NativePluginShortcutUiState\(.*?\n            \)\n',
    '', text, flags=re.S)
text = text.replace(
'''            NativePluginShortcutAction.CAPTURE -> if (pluginId == NativePluginIds.VISUAL_DIARY) {
                VisualDiaryService.captureNow(this)
            }
''',
'''            NativePluginShortcutAction.CAPTURE -> Unit
''')
text = text.replace(
'''            NativePluginShortcutAction.SUMMARIZE -> when (pluginId) {
                NativePluginIds.MEETING_SPARK_NOTES -> MeetingSparkNotesService.summarize(this)
                NativePluginIds.AUTO_DIARY -> AutoDiaryService.summarize(this)
            }
''',
'''            NativePluginShortcutAction.SUMMARIZE -> if (pluginId == NativePluginIds.MEETING_SPARK_NOTES) {
                MeetingSparkNotesService.summarize(this)
            }
''')
start = text.index('    private fun startNativePlugin(pluginId: String) {')
end = text.index('    private fun navigateToDestination(destination: AppDestination) {', start)
replacement = '''    private fun startNativePlugin(pluginId: String) {
        if (isMeizuMyvuSelected() && pluginId == NativePluginIds.AUTO_AUDIO) {
            Toast.makeText(
                this,
                "Auto Audio requires HeyCyan onboard media and is unavailable for MYVU.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        if (isMetaRaybanSelected() && pluginId == NativePluginIds.AUTO_AUDIO) {
            Toast.makeText(
                this,
                "Auto Audio records HeyCyan onboard files and is unavailable for Meta Ray-Ban.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        val start = {
            CommunityPluginPrefs.setNativePluginEnabled(this, pluginId, true)
            when (pluginId) {
                NativePluginIds.LOCAL_AGENT -> LocalAgentPlugin.start(this)
                NativePluginIds.MEETING_SPARK_NOTES -> {
                    MeetingSparkNotesPreferences.setEnabled(this, true)
                    MeetingSparkNotesService.start(this)
                }
                NativePluginIds.LIVE_CAPTION_RELAY -> {
                    LiveCaptionRelayPreferences.setEnabled(this, true)
                    LiveCaptionRelayService.start(this)
                }
                NativePluginIds.HANDS_FREE_TRANSLATOR -> {
                    HandsFreeTranslatorPreferences.setEnabled(this, true)
                    HandsFreeTranslatorService.start(this)
                }
                NativePluginIds.ERRAND_BRAIN -> {
                    ErrandBrainPreferences.setEnabled(this, true)
                    ErrandBrainService.start(this)
                }
                NativePluginIds.AUTO_AUDIO -> AutoAudioCaptureService.start(this)
                else -> Unit
            }
            refreshNativePluginShortcutState()
        }

        if (pluginId == NativePluginIds.LOCAL_AGENT) {
            start()
        } else {
            PluginVoicePermissions.ensure(this, onGranted = start)
        }
    }

    private fun stopNativePlugin(pluginId: String) {
        CommunityPluginPrefs.setNativePluginEnabled(this, pluginId, false)
        when (pluginId) {
            NativePluginIds.LOCAL_AGENT -> LocalAgentPlugin.stop(this)
            NativePluginIds.MEETING_SPARK_NOTES -> {
                MeetingSparkNotesPreferences.setEnabled(this, false)
                MeetingSparkNotesService.stop(this)
            }
            NativePluginIds.LIVE_CAPTION_RELAY -> {
                LiveCaptionRelayPreferences.setEnabled(this, false)
                LiveCaptionRelayService.stop(this)
            }
            NativePluginIds.HANDS_FREE_TRANSLATOR -> {
                HandsFreeTranslatorPreferences.setEnabled(this, false)
                HandsFreeTranslatorService.stop(this)
            }
            NativePluginIds.ERRAND_BRAIN -> {
                ErrandBrainPreferences.setEnabled(this, false)
                ErrandBrainService.stop(this)
            }
            NativePluginIds.AUTO_AUDIO -> AutoAudioCaptureService.stop(this)
            else -> Unit
        }
        refreshNativePluginShortcutState()
    }

'''
text = text[:start] + replacement + text[end:]
text = text.replace('            ADAutomation.AUTO_DIARY -> AutoDiarySettingsActivity::class.java\n', '')
text = text.replace('            ADAutomation.VISUAL_DIARY -> VisualDiarySettingsActivity::class.java\n', '')
path.write_text(text, encoding='utf-8')

# Remove product enum entries.
p = Path('android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADGlassesModels.kt')
text = p.read_text(encoding='utf-8')
text = re.sub(r'    AUTO_DIARY\(.*?\n    \),\n', '', text, flags=re.S)
text = re.sub(r'    VISUAL_DIARY\(.*?\n    \),\n', '', text, flags=re.S)
p.write_text(text, encoding='utf-8')

# Local Agent settings: remove old AutoDiary settings deep-link; retain shared-memory section.
p = Path('android/AD-Glasses/app/src/main/java/com/ad_glasses/plugins/localagent/LocalAgentSettingsActivity.kt')
text = p.read_text(encoding='utf-8')
text = text.replace('import com.ad_glasses.plugins.autodiary.AutoDiarySettingsActivity\n', '')
text = re.sub(
    r'                OutlinedButton\(\n                    onClick = \{\n                        startActivity\(Intent\(this@LocalAgentSettingsActivity, AutoDiarySettingsActivity::class\.java\)\)\n                    \},\n                    modifier = Modifier\.fillMaxWidth\(\),\n                \) \{\n                    Text\(stringResource\(R\.string\.compose_open_autodiary_settings\)\)\n                \}\n',
    '', text)
p.write_text(text, encoding='utf-8')

# Debug logger no longer watches a removed service tag.
p = Path('android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/debug/DebugLogSupport.kt')
text = p.read_text(encoding='utf-8').replace('        "VisualDiaryService",\n', '')
p.write_text(text, encoding='utf-8')

# Shared native plugin identifiers/catalog: remove both retired features.
p = Path('android/AD-Glasses/shared/src/commonMain/kotlin/com/ad_glasses/shared/plugins/NativePluginModels.kt')
text = p.read_text(encoding='utf-8')
text = text.replace('    const val AUTO_DIARY = "auto_diary"\n', '')
text = text.replace('    const val VISUAL_DIARY = "visual_diary"\n', '')
p.write_text(text, encoding='utf-8')

p = Path('android/AD-Glasses/shared/src/commonMain/kotlin/com/ad_glasses/shared/ui/SharedDestinationScreen.kt')
text = p.read_text(encoding='utf-8')
text = re.sub(r'        NativePluginCardData\(\n            id = NativePluginIds\.AUTO_DIARY,.*?\n        \),\n', '', text, flags=re.S)
text = re.sub(r'        NativePluginCardData\(\n            id = NativePluginIds\.VISUAL_DIARY,.*?\n        \),\n', '', text, flags=re.S)
p.write_text(text, encoding='utf-8')

# Android resources. Localized stale feature strings are removed so Android falls back to the
# current base copy instead of keeping obsolete DayNote/Diary permission wording.
res_root = Path('android/AD-Glasses/app/src/main/res')
for p in res_root.glob('values*/strings.xml'):
    lines = p.read_text(encoding='utf-8').splitlines()
    kept = []
    for line in lines:
        if re.search(r'onboarding_daily_facts_(title|desc|details)', line):
            continue
        if '<!-- Onboarding: DayNote -->' in line:
            continue
        if p.parent.name != 'values' and 'name="onboarding_optional_features_desc"' in line:
            continue
        kept.append(line)
    p.write_text('\n'.join(kept) + '\n', encoding='utf-8')

base_strings = res_root / 'values/strings.xml'
text = base_strings.read_text(encoding='utf-8')
text = re.sub(
    r'<string name="onboarding_optional_features_desc">.*?</string>',
    '<string name="onboarding_optional_features_desc">Auto Audio, Automation, and Cloud AI are optional and configured from Settings.</string>',
    text,
)
base_strings.write_text(text, encoding='utf-8')

for p in res_root.glob('values*/strings_compose.xml'):
    lines = p.read_text(encoding='utf-8').splitlines()
    kept = []
    for line in lines:
        if 'name="compose_open_autodiary_settings"' in line:
            continue
        if p.parent.name != 'values' and 'name="compose_local_agent_shared_memory_description"' in line:
            continue
        kept.append(line)
    text = '\n'.join(kept) + '\n'
    if p.parent.name == 'values':
        text = re.sub(
            r'<string name="compose_local_agent_shared_memory_description">.*?</string>',
            '<string name="compose_local_agent_shared_memory_description">Automation memory and approved context stay in local storage and remain under your privacy controls.</string>',
            text,
        )
    p.write_text(text, encoding='utf-8')

for p in res_root.glob('values*/strings_plugins_compose.xml'):
    lines = p.read_text(encoding='utf-8').splitlines()
    kept = []
    for line in lines:
        name_match = re.search(r'name="([^"]+)"', line)
        name = name_match.group(1) if name_match else ''
        if name.startswith('compose_autodiary_'):
            continue
        if name in {'compose_plugin_name_autodiary', 'compose_plugin_name_visual_diary'}:
            continue
        if name.startswith('compose_visual_') or name == 'compose_stop_visual_diary':
            continue
        if p.parent.name != 'values' and name == 'compose_shared_memory_description':
            continue
        kept.append(line)
    text = '\n'.join(kept) + '\n'
    if p.parent.name == 'values':
        text = re.sub(
            r'<string name="compose_shared_memory_description">.*?</string>',
            '<string name="compose_shared_memory_description">Automation memory retention, deletion, and vault controls are managed in Settings &gt; Memory Privacy.</string>',
            text,
        )
    p.write_text(text, encoding='utf-8')

# Shared/iOS resource catalog: retire feature cards completely and remove obsolete MYVU wording.
shared_res = Path('android/AD-Glasses/shared/src/commonMain/composeResources')
for p in shared_res.glob('values*/strings_extra.xml'):
    lines = p.read_text(encoding='utf-8').splitlines()
    kept = []
    for line in lines:
        if any(key in line for key in (
            'name="native_auto_diary_title"',
            'name="native_auto_diary_description"',
            'name="native_visual_diary_title"',
            'name="native_visual_diary_description"',
        )):
            continue
        if p.parent.name != 'values' and 'name="dashboard_meizu_unsupported"' in line:
            continue
        kept.append(line)
    text = '\n'.join(kept) + '\n'
    if p.parent.name == 'values':
        text = re.sub(
            r'<string name="dashboard_meizu_unsupported">.*?</string>',
            '<string name="dashboard_meizu_unsupported">Voice plugins use the MYVU HFP microphone route after connection. Camera capture and onboard-media sync are not supported because MYVU has no camera or media store.</string>',
            text,
        )
    p.write_text(text, encoding='utf-8')

# Remove generated design artifacts dedicated only to the retired features.
docs_root = Path('android/docs/stitch_ad_glasses')
if docs_root.exists():
    for child in list(docs_root.iterdir()):
        n = child.name.lower()
        if ('auto_diary' in n or 'autodiary' in n or 'visual_diary' in n):
            if child.is_dir():
                shutil.rmtree(child)
            else:
                child.unlink()

# Remove feature-specific lines from planning/docs/prototypes. Generic uses of 'timeline' as a UI
# chronology, firmware stepper, chat layout, or meeting-summary field are intentionally preserved.
feature_re = re.compile(r'daynote|day note|autodiary|auto diary|visual diary|visual_diary', re.I)
for root in [Path('android/AD-Glasses'), Path('android/docs'), Path('ios')]:
    if not root.exists():
        continue
    for p in root.rglob('*'):
        if not p.is_file() or p.suffix.lower() not in {'.md', '.html', '.js'}:
            continue
        if '/src/test/' in p.as_posix() or '/src/androidTest/' in p.as_posix() or '/src/commonTest/' in p.as_posix():
            continue
        try:
            lines = p.read_text(encoding='utf-8').splitlines()
        except UnicodeDecodeError:
            continue
        new_lines = [line for line in lines if not feature_re.search(line)]
        if new_lines != lines:
            p.write_text('\n'.join(new_lines) + '\n', encoding='utf-8')

# Guard: no live production source/resource references may remain. Generic 'timeline' is excluded.
violations = []
for root in [Path('android/AD-Glasses/app/src/main'), Path('android/AD-Glasses/shared/src/commonMain')]:
    for p in root.rglob('*'):
        if not p.is_file():
            continue
        try:
            s = p.read_text(encoding='utf-8')
        except (UnicodeDecodeError, OSError):
            continue
        if feature_re.search(s):
            violations.append(str(p))
if violations:
    raise SystemExit('Retired Diary/Visual Diary references remain in production files:\n' + '\n'.join(violations))

from pathlib import Path
import re

ROOT = Path('.')


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text)


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{path}: expected one occurrence, found {count}: {old[:80]!r}')
    write(path, text.replace(old, new, 1))


# Proven zero-reference visual resources. Current launcher/hero/lens resources are deliberately kept.
for rel in [
    'android/AD-Glasses/app/src/main/res/drawable/bg_bubble_assistant.xml',
    'android/AD-Glasses/app/src/main/res/drawable/bg_bubble_received.xml',
    'android/AD-Glasses/app/src/main/res/drawable/bg_bubble_sent.xml',
    'android/AD-Glasses/app/src/main/res/drawable/bg_bubble_user.xml',
    'android/AD-Glasses/app/src/main/res/drawable/bg_circle_send.xml',
    'android/AD-Glasses/app/src/main/res/drawable/bg_community_hero.xml',
    'android/AD-Glasses/app/src/main/res/drawable/bg_logo_slot_placeholder.xml',
    'android/AD-Glasses/app/src/main/res/drawable/edit_text_background.xml',
    'android/AD-Glasses/app/src/main/res/drawable/ic_chevron_right.xml',
    'android/AD-Glasses/app/src/main/res/drawable/ic_launcher_background.xml',
    'android/AD-Glasses/app/src/main/res/drawable/ic_launcher_foreground.xml',
    'android/AD-Glasses/app/src/main/res/drawable/ic_menu_hamburger.xml',
    'android/AD-Glasses/app/src/main/res/drawable/ic_navigate_before_black_44dp.xml',
    'android/AD-Glasses/app/src/main/res/drawable/logo_ad_glasses.png',
    'android/AD-Glasses/app/src/main/res/drawable-nodpi/ad_glasses_hero_v2.png',
    'android/AD-Glasses/app/src/main/res/drawable-nodpi/ad_glasses_hero_v3.png',
    'android/AD-Glasses/app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png',
    'android/AD-Glasses/app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png',
    'android/AD-Glasses/app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png',
    'android/AD-Glasses/app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png',
    'android/AD-Glasses/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png',
]:
    path = ROOT / rel
    if path.exists():
        path.unlink()

# Unmounted page and unused experimental routing/profile state.
for rel in [
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADNativeAiScreen.kt',
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/runtime/ADIntelligencePrefs.kt',
]:
    path = ROOT / rel
    if path.exists():
        path.unlink()

# Compatibility AI/plugin entry points belong in Device Center, where Cloud/Local config now lives.
replace_once(
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADGlassesApp.kt',
    '''            ADExternalDestination.AI -> {\n                routeStack = listOf(ADRoute.MAIN)\n                selectedTab = ADTab.AI\n            }''',
    '''            ADExternalDestination.AI -> {\n                routeStack = listOf(ADRoute.MAIN, ADRoute.DEVICE_CENTER)\n            }''',
)

# Remove the retired phone-UI intent from the request router. Explicit background Automation remains a capability.
replace_once(
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/router/AssistantRequestRouter.kt',
    '''    /** Deprecated compatibility token; route() never emits this. */\n    EXECUTE_UI_TASK,\n''',
    '',
)

replace_once(
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/orchestrator/AssistantOrchestrator.kt',
    '''                AssistantIntent.EXECUTE_UI_TASK -> AssistantResult(\n                    spokenText = "Phone UI automation is no longer available as an AI invocation method.",\n                )\n''',
    '',
)

# MainActivity still carried the unreachable retired intent handler. Remove only that when branch.
main_path = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/MainActivity.kt'
main = read(main_path)
pattern = re.compile(
    r'''\n\s{28}AssistantIntent\.EXECUTE_UI_TASK -> runOnUiThread \{.*?\n\s{28}\}\n\n(?=\s{28}AssistantIntent\.CLARIFY ->)''',
    re.S,
)
main, count = pattern.subn('\n', main, count=1)
if count != 1:
    raise SystemExit(f'{main_path}: expected one retired EXECUTE_UI_TASK branch, found {count}')
# Drop the now-unused alias import only if nothing else references it.
if main.count('AutomationPrefs') == 1:
    main = main.replace('import com.ad_glasses.agent.LocalAgentPrefs as AutomationPrefs\n', '')
write(main_path, main)

# Correct stale product copy: Live actually has Google Search; standard Cloud chat currently does not browse.
old_web = 'Fresh information can use Web Search through the relay you choose; local features stay local where possible.'
new_web = 'Gemini Live can ground current questions with Google Search. Standard Cloud chat does not browse unless its selected provider route explicitly supports web search.'
for rel in [
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADMinimalAboutScreen.kt',
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADProductSettingsScreens.kt',
]:
    replace_once(rel, old_web, new_web)

# Guardrails: do not accidentally remove current visual identity or page-state files.
for rel in [
    'android/AD-Glasses/app/src/main/res/drawable-nodpi/ad_glasses_hero_v4.png',
    'android/AD-Glasses/app/src/main/res/drawable-nodpi/ad_glasses_icon_source.png',
    'android/AD-Glasses/app/src/main/res/drawable-nodpi/ad_lens_shutter.png',
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADNativeConversationScreen.kt',
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADNativeLibraryScreens.kt',
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADProductSettingsScreens.kt',
]:
    if not (ROOT / rel).exists():
        raise SystemExit(f'guardrail: required current surface/state file missing: {rel}')

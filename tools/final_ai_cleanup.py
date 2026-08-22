from pathlib import Path
import re

ROOT = Path('android/CyanBridge')
MAIN = ROOT / 'app/src/main/java/com/fersaiyan/cyanbridge/MainActivity.kt'

# First-class naming: no subscription or API-token-as-mode vocabulary.
for path in ROOT.rglob('*'):
    if not path.is_file() or path.suffix not in {'.kt', '.kts', '.xml', '.md'}:
        continue
    text = path.read_text(errors='ignore')
    new = (text
        .replace('AgentProviderType.PRO_SUBSCRIPTION', 'AgentProviderType.CLOUD_AI')
        .replace('AiProviderType.API_TOKEN', 'AiProviderType.CLOUD_API')
        .replace('GlassesAssistantRoute.PRO', 'GlassesAssistantRoute.CLOUD')
        .replace('PRO_SUBSCRIPTION', 'CLOUD_AI'))
    if new != text:
        path.write_text(new)

text = MAIN.read_text()

# Remove inherited consumer-assistant imports.
for token in [
    'GlassesAssistantMode',
    'DefaultAssistantResolver',
    'ExternalAssistantAutomationInspector',
    'ExternalAssistantAutomationPolicy',
    'ExternalAssistantAccessibilityAutomation',
    'ExternalImageAutomationStage',
    'ExternalImageAutomationStore',
    'ImageAutomationTarget',
]:
    text = re.sub(rf'^import .*\b{re.escape(token)}\b.*\n', '', text, flags=re.M)

# Remove stored assistant-mode initialization/state. Provider selection itself remains Cloud/Local.
text = re.sub(r'^\s*private const val AI_MODE_PHONE_ASSISTANT.*\n', '', text, flags=re.M)
text = re.sub(r'^\s*private const val AI_MODE_CUSTOM_AI_PROVIDER.*\n', '', text, flags=re.M)
text = re.sub(r'^\s*private var aiAssistantMode\s*=.*\n', '', text, flags=re.M)
text = re.sub(
    r'\n\s*aiAssistantMode = when \(AutomationPrefs\.getGlassesAssistantMode\(this\)\) \{.*?\n\s*\}\n',
    '\n', text, flags=re.S,
)

# Utilities for brace-balanced Kotlin function / when-branch editing.
def find_matching_brace(src, open_index):
    depth = 0
    in_str = False
    esc = False
    for i in range(open_index, len(src)):
        c = src[i]
        if in_str:
            if esc: esc = False
            elif c == '\\': esc = True
            elif c == '"': in_str = False
            continue
        if c == '"': in_str = True; continue
        if c == '{': depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0: return i
    raise RuntimeError('unbalanced braces')

def replace_function(src, name, replacement):
    m = re.search(rf'(?m)^\s*private\s+(?:suspend\s+)?fun\s+{re.escape(name)}\s*\(', src)
    if not m: return src
    brace = src.find('{', m.start())
    if brace < 0: raise RuntimeError(f'no body for {name}')
    end = find_matching_brace(src, brace)
    return src[:m.start()] + '\n' + replacement.rstrip() + '\n' + src[end+1:]

def remove_function(src, name):
    return replace_function(src, name, '')

def remove_when_branch(src, marker):
    pos = src.find(marker)
    while pos >= 0:
        line_start = src.rfind('\n', 0, pos) + 1
        arrow = src.find('->', pos)
        if arrow < 0: break
        body = src.find('{', arrow)
        if body >= 0 and body < src.find('\n', arrow) + 500:
            end = find_matching_brace(src, body)
            src = src[:line_start] + src[end+1:]
        else:
            line_end = src.find('\n', arrow)
            src = src[:line_start] + src[line_end+1:]
        pos = src.find(marker)
    return src

# Current route is entirely owned by the selected AD Cloud/Local provider.
text = replace_function(text, 'currentAssistantRoute', '''    private fun currentAssistantRoute(): GlassesAssistantRoute =
        GlassesAssistantRoutingPolicy.resolve(AutomationPrefs.getProviderType(this))''')
text = replace_function(text, 'resolveEffectiveAiAssistantMode', '''    private fun resolveEffectiveAiAssistantMode(): String = AutomationPrefs.getProviderType(this).name''')
text = replace_function(text, 'refreshAiModeButtons', '''    private fun refreshAiModeButtons() {
        refreshAiQueryButtonsState()
    }''')

# Remove obsolete dashboard and legacy XML click branches.
for marker in [
    'is GlassesDashboardAction.SelectAssistantMode ->',
    'GlassesDashboardAction.OpenExternalImageAutomationDiagnostics ->',
    'is GlassesDashboardAction.SelectPhoneAssistant ->',
    'GlassesDashboardAction.OpenPhoneAssistantSystemSettings ->',
    'GlassesDashboardAction.LinkProSubscriptionProfile ->',
    'GlassesDashboardAction.RefreshProSubscriptionStatus ->',
    'GlassesDashboardAction.TestProSubscriptionEndpoint ->',
    'binding.btnModeGemini ->',
    'binding.btnModeChatgpt ->',
    'binding.btnModeInternal ->',
]:
    text = remove_when_branch(text, marker)

# Drop legacy mode/consumer-app functions. Any missed call fails compilation rather than restoring compatibility.
for name in [
    'selectPhoneAssistant', 'selectCustomAiProvider', 'launchPhoneAssistant',
    'runPhoneAssistantHealthCheck', 'openAssistantSettings',
    'usesExternalImageAutomation', 'usesExternalAssistantUi', 'externalImageQuestion',
    'selectedImageAutomationTarget', 'startExternalImageShare',
    'runExternalImageAutomationDiagnostics', 'handleExternalImageAutomation',
    'stageImageForExternalShare',
]:
    text = remove_function(text, name)

# Old XML buttons no longer participate in the host's generic click registration.
for ref in ['binding.btnModeGemini,\n', 'binding.btnModeChatgpt,\n', 'binding.btnModeInternal,\n']:
    text = text.replace(ref, '')

MAIN.write_text(text)

# Remove standalone compatibility / CLI consumer routing files. Cloud API and Gemini Live remain.
for rel in [
    'app/src/main/java/com/fersaiyan/cyanbridge/ai/image/RemovedConsumerAssistantCompatibility.kt',
    'app/src/main/java/com/fersaiyan/cyanbridge/ai/router/CliRelayRouter.kt',
    'app/src/main/java/com/fersaiyan/cyanbridge/ai/router/GeminiCliRelayClient.kt',
    'app/src/main/java/com/fersaiyan/cyanbridge/ai/router/ChatGptCliRelayClient.kt',
    'app/src/main/java/com/fersaiyan/cyanbridge/agent/CloudAiPrefs.kt',
]:
    p = ROOT / rel
    if p.exists(): p.unlink()

print('one-shot cleanup applied')

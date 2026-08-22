#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text()


def write(rel, text):
    (ROOT / rel).write_text(text)


def replace_exact(text, old, new, label, expected=1):
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"{label}: expected {expected} exact matches, found {count}")
    return text.replace(old, new)


def sub_exact(text, pattern, repl, label, expected=1, flags=0):
    out, count = re.subn(pattern, repl, text, count=expected, flags=flags)
    if count != expected:
        raise RuntimeError(f"{label}: expected {expected} regex matches, found {count}")
    return out


def remove_import_if_unused(text, fqcn):
    symbol = fqcn.rsplit('.', 1)[-1]
    import_line = f"import {fqcn}\n"
    if import_line in text and text.count(symbol) == 1:
        text = text.replace(import_line, "")
    return text


# 1) Shared dashboard: the presentation contract already removed consumer-assistant modes.
rel = "android/CyanBridge/shared/src/commonMain/kotlin/com/fersaiyan/cyanbridge/shared/ui/glasses/GlassesDashboardScreen.kt"
text = read(rel)
text = text.replace("import com.fersaiyan.cyanbridge.shared.glasses.GlassesAssistantMode\n", "")
text = sub_exact(
    text,
    r'(        SectionTitle\(stringResource\(Res\.string\.dashboard_ai_assistant\), accented = true\)\n)'
    r'        Row\(horizontalArrangement = Arrangement\.spacedBy\(6\.dp\)\) \{.*?\n        \}\n'
    r'(        ActionRow\()',
    r'\1\2',
    "remove dashboard assistant mode chips",
    flags=re.S,
)
text = sub_exact(
    text,
    r'\n        OutlinedButton\(\n'
    r'            onClick = \{ onAction\(GlassesDashboardAction\.OpenExternalImageAutomationDiagnostics\) \},\n'
    r'            modifier = Modifier\.fillMaxWidth\(\),\n'
    r'        \) \{\n'
    r'            Text\(stringResource\(Res\.string\.dashboard_gemini_chatgpt_setup\)\)\n'
    r'        \}\n',
    '\n',
    "remove external assistant diagnostics button",
    flags=re.S,
)
text = sub_exact(
    text,
    r'\n@Composable\nprivate fun AssistantModeChip\(.*?\n\}\n\n(?=@Composable\nprivate fun MetaRaybanControls)',
    '\n',
    "remove AssistantModeChip",
    flags=re.S,
)
write(rel, text)

# 2) Vision route names: only AD-owned Cloud or Local routes remain.
rel = "android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/ai/vision/ImageQuestionPrompt.kt"
text = read(rel)
text = replace_exact(
    text,
    "enum class ImageQuestionRoute {\n    PRO_RELAY,\n    LOCAL_GEMMA,\n    EXTERNAL_ASSISTANT,\n}\n",
    "enum class ImageQuestionRoute {\n    CLOUD_AI,\n    LOCAL_GEMMA,\n}\n",
    "replace image question routes",
)
text = replace_exact(
    text,
    "        ImageQuestionRoute.PRO_RELAY,\n        ImageQuestionRoute.LOCAL_GEMMA,\n        ImageQuestionRoute.EXTERNAL_ASSISTANT\n        -> text\n",
    "        ImageQuestionRoute.CLOUD_AI,\n        ImageQuestionRoute.LOCAL_GEMMA -> text\n",
    "replace prompt route branches",
)
write(rel, text)

# 3) MainActivity: remove half-deleted consumer-assistant/subscription architecture.
rel = "android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/MainActivity.kt"
text = read(rel)
for line in [
    "import com.fersaiyan.cyanbridge.agent.CloudAiPrefs\n",
    "import com.fersaiyan.cyanbridge.ai.router.CliRelayClient\n",
]:
    text = text.replace(line, "")
if "import com.fersaiyan.cyanbridge.ai.router.ApiTokenClient\n" not in text:
    anchor = "import com.fersaiyan.cyanbridge.ai.router.AssistantSpeechPolicy\n"
    if anchor not in text:
        raise RuntimeError("MainActivity import anchor missing")
    text = text.replace(anchor, anchor + "import com.fersaiyan.cyanbridge.ai.router.ApiTokenClient\n")

# Removed actions must not survive in the host's exclusive-session allow-list.
text = text.replace("                is GlassesDashboardAction.SelectAssistantMode,\n", "")
text = text.replace("                GlassesDashboardAction.OpenExternalImageAutomationDiagnostics,\n", "")

# Remove the malformed, removed-mode bridge and keep one direct provider routing function.
text = sub_exact(
    text,
    r'\n    private fun resolveEffectiveAiAssistantMode\(\): String = AutomationPrefs\.getProviderType\(this\)\.name\n.*?'
    r'\n    private fun imageQueryUnsupportedReasonForCurrentSelection\(\): String\? \{',
    '\n    private fun currentAssistantRoute(): GlassesAssistantRoute =\n'
    '        GlassesAssistantRoutingPolicy.resolve(AutomationPrefs.getProviderType(this))\n\n'
    '    private fun imageQueryUnsupportedReasonForCurrentSelection(): String? {',
    "replace malformed assistant routing bridge",
    flags=re.S,
)

# External consumer-assistant diagnostics/requirements are gone, not hidden.
text = sub_exact(
    text,
    r'\n    private fun externalImageAutomationUnsupportedReason\(\): String\? \{.*?'
    r'\n    private fun refreshAiQueryButtonsState\(\) \{',
    '\n    private fun refreshAiQueryButtonsState() {',
    "remove external image automation requirements",
    flags=re.S,
)
text = replace_exact(
    text,
    "        val unsupportedReason = imageQueryUnsupportedReasonForCurrentSelection()\n            ?: externalImageAutomationUnsupportedReason()\n",
    "        val unsupportedReason = imageQueryUnsupportedReasonForCurrentSelection()\n",
    "simplify image support state",
)
text = re.sub(
    r'\n\s*if \(maybeShowGeminiChatGptImageRequirementsWarning\(\)\) \{\n\s*return@setOnClickListener\n\s*\}',
    '',
    text,
)

# The selected provider is the route. There is no phone-assistant fallback branch.
text = sub_exact(
    text,
    r'\n    private fun triggerAssistantVoiceQuery\(\) \{.*?\n    \}\n\n    private fun handleAiWakeWordActivation',
    '\n    private fun triggerAssistantVoiceQuery() {\n'
    '        if (isGlassesCommandBlocked("voice-query command")) return\n'
    '        val providerType = AutomationPrefs.getProviderType(this)\n'
    '        Log.i("AIHijack", "Triggering voice query for $providerType")\n\n'
    '        when (currentAssistantRoute()) {\n'
    '            GlassesAssistantRoute.LOCAL -> triggerInternalVoiceQuery(AgentProviderType.LOCAL_AGENT)\n'
    '            GlassesAssistantRoute.CLOUD -> triggerInternalVoiceQuery(AgentProviderType.CLOUD_AI)\n'
    '        }\n'
    '    }\n\n'
    '    private fun handleAiWakeWordActivation',
    "replace voice query routing",
    flags=re.S,
)

# Remove external share/accessibility automation wholesale.
text = sub_exact(
    text,
    r'\n    private fun canStartExternalImageShare\(packageName: String\): Boolean \{.*?'
    r'\n    private fun triggerAssistantImageQuery\(',
    '\n    private fun triggerAssistantImageQuery(',
    "remove external image handoff",
    flags=re.S,
)

# Lens/image requests always go to the selected AD-owned provider.
text = sub_exact(
    text,
    r'    private fun triggerAssistantImageQuery\(\n'
    r'        imagePath: String,\n'
    r'        userQuestion: String\? = null,\n'
    r'        source: ImageQuestionSource = ImageQuestionSourcePolicy\.defaultSource\(\),\n'
    r'        onReplySpoken: \(\(\) -> Unit\)\? = null,\n'
    r'    \) \{.*?\n    \}\n\n    private fun analyzeSyncedCapture',
    '    private fun triggerAssistantImageQuery(\n'
    '        imagePath: String,\n'
    '        userQuestion: String? = null,\n'
    '        source: ImageQuestionSource = ImageQuestionSourcePolicy.defaultSource(),\n'
    '        onReplySpoken: (() -> Unit)? = null,\n'
    '    ) {\n'
    '        val now = System.currentTimeMillis()\n'
    '        if (now - lastImageQueryAtMs < 5000) {\n'
    '            Log.w("AIHijack", "Image query debounced (last was ${now - lastImageQueryAtMs}ms ago)")\n'
    '            return\n'
    '        }\n'
    '        if (!imageQueryInProgress.compareAndSet(false, true)) {\n'
    '            Log.w("AIHijack", "Image query already in progress; treating duplicate action as barge-in")\n'
    '            cancelLocalStreamingSpeech("duplicate image-query action")\n'
    '            imageQueryInProgress.set(false)\n'
    '            return\n'
    '        }\n'
    '        beginAiQuestionForegroundWork("Analyzing glasses image")\n'
    '        lastImageQueryAtMs = now\n'
    '        val resolvedPrompt = resolveImageQuestionPrompt(userQuestion)\n'
    '        val providerType = when (currentAssistantRoute()) {\n'
    '            GlassesAssistantRoute.LOCAL -> AgentProviderType.LOCAL_AGENT\n'
    '            GlassesAssistantRoute.CLOUD -> AgentProviderType.CLOUD_AI\n'
    '        }\n'
    '        Log.i("AIHijack", "Routing ${source.wireName} image query to $providerType")\n'
    '        triggerMemoryAwareImageQuery(\n'
    '            imagePath = imagePath,\n'
    '            providerType = providerType,\n'
    '            resolvedPrompt = resolvedPrompt,\n'
    '            onReplySpoken = onReplySpoken,\n'
    '        )\n'
    '    }\n\n'
    '    private fun analyzeSyncedCapture',
    "replace image query routing",
    flags=re.S,
)

# Follow-up conversation is always owned by AD now.
text = text.replace("            val externalAutomation = usesExternalImageAutomation()\n", "")
text = text.replace("                onReplySpoken = if (externalAutomation) null else ::offerFollowUp,\n", "                onReplySpoken = ::offerFollowUp,\n")
text = re.sub(
    r'\n\s*// The default assistant owns external response playback; CyanBridge follow-ups are\n'
    r'\s*// only offered for Local and Pro responses that CyanBridge itself speaks\.\n'
    r'\s*if \(externalAutomation\) return@launch\n',
    '\n',
    text,
)

# Cloud multimodal requests use the real API client, not the deleted relay/model bucket.
text = sub_exact(
    text,
    r'            AgentProviderType\.CLOUD_AI -> \{\n'
    r'                CliRelayClient\.chat\(\n'
    r'                    context = this,\n'
    r'                    chatId = "glasses_\$\{System\.currentTimeMillis\(\)\}",\n'
    r'                    prompt = userPrompt,\n'
    r'                    messages = messages,\n'
    r'                    modelOverride = CloudAiPrefs\.getRequestsModel\(this\),\n'
    r'                \)\.getOrElse \{\n'
    r'                    "Pro endpoint error: \$\{it\.message \?: "unknown error"\}"\n'
    r'                \}\n'
    r'            \}',
    '            AgentProviderType.CLOUD_AI -> {\n'
    '                ApiTokenClient.chat(\n'
    '                    context = this,\n'
    '                    messages = messages,\n'
    '                    imagePaths = imagePaths,\n'
    '                    audioPath = audioPath,\n'
    '                ).getOrElse {\n'
    '                    "Cloud AI error: ${it.message ?: "unknown error"}"\n'
    '                }\n'
    '            }',
    "replace deleted CLI relay call",
)

text = text.replace("ImageQuestionRoute.PRO_RELAY", "ImageQuestionRoute.CLOUD_AI")
text = text.replace("triggerCliRelayImageCaptureAndQuery", "triggerImageCaptureAndQuery")
text = text.replace("Hijacking to Phone Assistant", "routing to AD assistant")

# Drop imports that became dead after removing the external-share path.
for fqcn in [
    "android.content.ClipData",
    "androidx.core.content.FileProvider",
]:
    text = remove_import_if_unused(text, fqcn)

write(rel, text)

# 4) Remove dead relay shim if it still exists.
relay = ROOT / "android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/ai/router/CliRelayRouter.kt"
if relay.exists():
    relay.unlink()

# 5) Remove stale shared labels/comments rather than leaving unreachable UI vocabulary.
for rel in [
    "android/CyanBridge/shared/src/commonMain/composeResources/values/strings.xml",
    "android/CyanBridge/shared/src/commonMain/composeResources/values/strings_extra.xml",
]:
    text = read(rel)
    for name in [
        "dashboard_phone_assistant",
        "dashboard_custom_provider",
        "dashboard_gemini_chatgpt_setup",
    ]:
        text = re.sub(rf'\s*<string name="{name}">.*?</string>\s*', '\n', text)
    text = text.replace("    <!-- Pro subscription -->\n", "")
    write(rel, text)

print("Post-cleanup source repair applied.")

from pathlib import Path

ROOT = Path("android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# ADNativeAiScreen: Cloud/Local selection is the only AI route choice.
ai_path = ROOT / "ADNativeAiScreen.kt"
ai = ai_path.read_text()
ai = ai.replace("import com.ad_glasses.shared.glasses.GlassesAssistantMode\n", "")
ai = replace_once(
    ai,
    "        LocalAgentPrefs.setGlassesAssistantMode(context, GlassesAssistantMode.CUSTOM_AI_PROVIDER)\n",
    "",
    "remove retired glasses assistant mode write",
)
if "GlassesAssistantMode" in ai or "setGlassesAssistantMode" in ai:
    raise SystemExit("ADNativeAiScreen still references retired glasses assistant mode")
ai_path.write_text(ai)


# ADNativeConversationScreen: this surface is always the AD-owned durable conversation.
conv_path = ROOT / "ADNativeConversationScreen.kt"
conv = conv_path.read_text()
for stale_import in (
    "import android.content.Intent\n",
    "import com.ad_glasses.ai.image.DefaultAssistantResolver\n",
    "import com.ad_glasses.ai.image.ExternalAssistantAccessibilityAutomation\n",
    "import com.ad_glasses.ai.image.ExternalAssistantAutomationInspector\n",
    "import com.ad_glasses.ai.image.ImageAutomationTarget\n",
    "import com.ad_glasses.shared.glasses.GlassesAssistantMode\n",
):
    conv = conv.replace(stale_import, "")

conv = replace_once(
    conv,
    "    val phoneAssistantMode = LocalAgentPrefs.getGlassesAssistantMode(context) ==\n        GlassesAssistantMode.PHONE_ASSISTANT\n    val externalTarget = if (phoneAssistantMode) {\n        ImageAutomationTarget.forDefaultAssistant(DefaultAssistantResolver.packageName(context))\n    } else {\n        ImageAutomationTarget.NONE\n    }\n",
    "",
    "remove phone assistant mode resolution",
)

conv = replace_once(
    conv,
    '''        if (phoneAssistantMode) {\n            message = ""\n            webSearch = false\n            sending = true\n            errorText = null\n            lastFailedPrompt = null\n            scope.launch {\n                handOffTextToPhoneAssistant(context, prompt).onFailure { error ->\n                    errorText = error.message ?: "Couldn’t send that prompt to the assistant app."\n                    lastFailedPrompt = prompt\n                    message = prompt\n                }\n                sending = false\n            }\n            return\n        }\n''',
    "",
    "remove external assistant text handoff",
)

conv = replace_once(
    conv,
    '''                Text(\n                    if (phoneAssistantMode) {\n                        "Opens ${externalTarget.label}; that app owns the reply"\n                    } else {\n                        "AD-owned ${internalProvider.label} conversation"\n                    },\n                    style = MaterialTheme.typography.bodySmall,\n                    color = ADColors.Muted,\n                )\n''',
    '''                Text(\n                    "AD-owned ${internalProvider.label} conversation",\n                    style = MaterialTheme.typography.bodySmall,\n                    color = ADColors.Muted,\n                )\n''',
    "simplify conversation route subtitle",
)
conv = conv.replace(
    "            if (!phoneAssistantMode && (messages.isNotEmpty() || pendingPrompt != null)) {",
    "            if (messages.isNotEmpty() || pendingPrompt != null) {",
    1,
)
conv = replace_once(
    conv,
    '''        ADConversationRouteDisclosure(\n            phoneAssistantMode = phoneAssistantMode,\n            externalTarget = externalTarget,\n            internalProviderName = internalProvider.label,\n        )\n''',
    '''        ADConversationRouteDisclosure(\n            internalProviderName = internalProvider.label,\n        )\n''',
    "simplify route disclosure call",
)
conv = replace_once(
    conv,
    '''            if ((phoneAssistantMode || messages.isEmpty()) && pendingPrompt == null) {\n                item(key = "empty") {\n                    ADConversationEmptyState(\n                        externalAppName = externalTarget.label.takeIf { phoneAssistantMode },\n                        onSuggestion = ::useSuggestion,\n                    )\n                }\n            }\n\n            if (!phoneAssistantMode) {\n                items(messages, key = { it.id }) { chatMessage ->\n                    ADConversationTurn(chatMessage)\n                }\n\n                pendingPrompt?.takeUnless { pendingAlreadyPersisted }?.let { prompt ->\n                    item(key = "pending-user") {\n                        ADUserTurn(prompt)\n                    }\n                }\n\n                if (pendingPrompt != null) {\n                    item(key = "pending-reply") {\n                        ADAssistantThinking()\n                    }\n                }\n            }\n''',
    '''            if (messages.isEmpty() && pendingPrompt == null) {\n                item(key = "empty") {\n                    ADConversationEmptyState(onSuggestion = ::useSuggestion)\n                }\n            }\n\n            items(messages, key = { it.id }) { chatMessage ->\n                ADConversationTurn(chatMessage)\n            }\n\n            pendingPrompt?.takeUnless { pendingAlreadyPersisted }?.let { prompt ->\n                item(key = "pending-user") {\n                    ADUserTurn(prompt)\n                }\n            }\n\n            if (pendingPrompt != null) {\n                item(key = "pending-reply") {\n                    ADAssistantThinking()\n                }\n            }\n''',
    "make durable AD conversation unconditional",
)
conv = replace_once(
    conv,
    '''        ADConversationComposer(\n            message = message,\n            onMessageChange = { message = it },\n            webSearch = webSearch,\n            phoneAssistantMode = phoneAssistantMode,\n            externalAppName = externalTarget.label,\n            sending = sending,\n            focusRequester = composerFocusRequester,\n            onSend = ::send,\n        )\n''',
    '''        ADConversationComposer(\n            message = message,\n            onMessageChange = { message = it },\n            webSearch = webSearch,\n            sending = sending,\n            focusRequester = composerFocusRequester,\n            onSend = ::send,\n        )\n''',
    "remove external assistant composer arguments",
)

old_disclosure = '''@Composable\nprivate fun ADConversationRouteDisclosure(\n    phoneAssistantMode: Boolean,\n    externalTarget: ImageAutomationTarget,\n    internalProviderName: String,\n) {\n    val title = if (phoneAssistantMode) {\n        "${externalTarget.label} app handoff"\n    } else {\n        "$internalProviderName · AD-owned reply"\n    }\n    val detail = if (phoneAssistantMode) {\n        "The assistant app keeps context and speaks. AD does not receive or save its answer."\n    } else {\n        "AD receives the text, speaks it through Android TTS, and removes this conversation after 7 days."\n    }\n    Surface(\n        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),\n        shape = RoundedCornerShape(13.dp),\n        color = if (phoneAssistantMode) ADColors.SurfaceSubtle else ADColors.BlueSoft,\n    ) {\n        Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {\n            Text(title, style = MaterialTheme.typography.labelLarge, color = ADColors.Ink)\n            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)\n        }\n    }\n}\n'''
new_disclosure = '''@Composable\nprivate fun ADConversationRouteDisclosure(\n    internalProviderName: String,\n) {\n    Surface(\n        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),\n        shape = RoundedCornerShape(13.dp),\n        color = ADColors.BlueSoft,\n    ) {\n        Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {\n            Text(\n                "$internalProviderName · AD-owned reply",\n                style = MaterialTheme.typography.labelLarge,\n                color = ADColors.Ink,\n            )\n            Text(\n                "AD receives the text, speaks it through Android TTS, and removes this conversation after 7 days.",\n                style = MaterialTheme.typography.bodySmall,\n                color = ADColors.Muted,\n            )\n        }\n    }\n}\n'''
conv = replace_once(conv, old_disclosure, new_disclosure, "replace route disclosure")

conv = conv.replace(
    '''private fun ADConversationEmptyState(\n    externalAppName: String? = null,\n    onSuggestion: (String, Boolean) -> Unit,\n) {''',
    '''private fun ADConversationEmptyState(\n    onSuggestion: (String, Boolean) -> Unit,\n) {''',
    1,
)
conv = replace_once(
    conv,
    '''        Text(\n            if (externalAppName != null) "Continue in $externalAppName" else "What do you want to know?",\n            style = MaterialTheme.typography.titleLarge,\n        )\n        Spacer(Modifier.size(4.dp))\n        Text(\n            if (externalAppName != null) {\n                "Sending opens the assistant app. Its reply, voice and conversation stay there."\n            } else {\n                "Ask AD anything, explicitly request web only when using a configured cloud route, or continue a glasses request."\n            },\n            style = MaterialTheme.typography.bodySmall,\n            color = ADColors.Muted,\n            textAlign = TextAlign.Center,\n            modifier = Modifier.padding(horizontal = 18.dp),\n        )\n''',
    '''        Text(\n            "What do you want to know?",\n            style = MaterialTheme.typography.titleLarge,\n        )\n        Spacer(Modifier.size(4.dp))\n        Text(\n            "Ask AD anything, explicitly request web only when using a configured cloud route, or continue a glasses request.",\n            style = MaterialTheme.typography.bodySmall,\n            color = ADColors.Muted,\n            textAlign = TextAlign.Center,\n            modifier = Modifier.padding(horizontal = 18.dp),\n        )\n''',
    "remove external assistant empty-state copy",
)
conv = replace_once(
    conv,
    '''            ADPromptSuggestion(\n                if (externalAppName != null) "Ask about something current" else "Search the web for something current",\n                web = externalAppName == null,\n            ) {\n                onSuggestion(if (externalAppName != null) "What's current about " else "Search the web for ", externalAppName == null)\n            }\n''',
    '''            ADPromptSuggestion(\n                "Search the web for something current",\n                web = true,\n            ) {\n                onSuggestion("Search the web for ", true)\n            }\n''',
    "simplify web suggestion",
)

conv = replace_once(
    conv,
    '''private fun ADConversationComposer(\n    message: String,\n    onMessageChange: (String) -> Unit,\n    webSearch: Boolean,\n    phoneAssistantMode: Boolean,\n    externalAppName: String,\n    sending: Boolean,\n''',
    '''private fun ADConversationComposer(\n    message: String,\n    onMessageChange: (String) -> Unit,\n    webSearch: Boolean,\n    sending: Boolean,\n''',
    "remove external assistant composer parameters",
)
conv = replace_once(
    conv,
    '''                                    when {\n                                        phoneAssistantMode -> "Open $externalAppName with a prompt…"\n                                        webSearch -> "Search the web"\n                                        else -> "Ask AD…"\n                                    },\n''',
    '''                                    if (webSearch) "Search the web" else "Ask AD…",\n''',
    "simplify composer placeholder",
)

handoff_start = conv.find("private suspend fun handOffTextToPhoneAssistant")
if handoff_start == -1:
    raise SystemExit("external assistant handoff function not found")
constants_start = conv.find("private const val CONVERSATION_REFRESH_MS", handoff_start)
if constants_start == -1:
    raise SystemExit("conversation constants not found after handoff function")
conv = conv[:handoff_start] + conv[constants_start:]

for stale in (
    "phoneAssistantMode",
    "externalTarget",
    "externalAppName",
    "DefaultAssistantResolver",
    "ExternalAssistantAccessibilityAutomation",
    "ExternalAssistantAutomationInspector",
    "ImageAutomationTarget",
    "GlassesAssistantMode",
    "getGlassesAssistantMode",
    "handOffTextToPhoneAssistant",
    "Gemini or ChatGPT app",
):
    if stale in conv:
        raise SystemExit(f"ADNativeConversationScreen still contains stale token: {stale}")
conv_path.write_text(conv)

print("Updated ADNativeAiScreen.kt and ADNativeConversationScreen.kt")

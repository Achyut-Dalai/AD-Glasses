from pathlib import Path
import re

ROOT = Path('.')


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding='utf-8')


def replace(path: str, old: str, new: str, *, required: bool = True) -> None:
    text = read(path)
    if old not in text:
        if required:
            raise SystemExit(f'missing expected text in {path}: {old[:100]!r}')
        return
    write(path, text.replace(old, new))


# 1) Fix stale rebrand paths/names in iOS KMP CI.
p = '.github/workflows/ios-kmp-host.yml'
text = read(p)
replacements = {
    'android/CyanBridge': 'android/AD-Glasses',
    'ios/CyanBridgeKMPHostTests': 'ios/AD-GlassesKMPHostTests',
    'ios/CyanBridgeKMPHost': 'ios/AD-GlassesKMPHost',
    'CyanBridgeKMPHost': 'AD-GlassesKMPHost',
    'CyanBridgeDerivedData': 'AD-GlassesDerivedData',
}
for old, new in replacements.items():
    text = text.replace(old, new)
write(p, text)

# 2) Remove dead compatibility slots for the retired phone-assistant modes.
p = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/databinding/AcitivytMainBinding.kt'
text = read(p)
for line in [
    '    val cbImageAsAssistant = CheckSlot(text = "Direct Assistant", initialChecked = true)\n',
    '    val btnModeGemini = ControlSlot(text = "Phone Assistant")\n',
    '    val btnModeChatgpt = ControlSlot(text = "Phone Assistant", visibility = View.GONE)\n',
    '    val btnModeInternal = ControlSlot(text = "AD Local / Cloud")\n',
]:
    text = text.replace(line, '')
write(p, text)

# 3) Remove the stale shared test for a type deleted from production.
p = 'android/AD-Glasses/shared/src/commonTest/kotlin/com/ad_glasses/shared/ui/glasses/GlassesDashboardScreenTest.kt'
text = read(p)
text = text.replace('import com.ad_glasses.shared.glasses.GlassesAssistantMode\n', '')
text = text.replace('Connected: ADGlasses V2', 'Connected: AD Glasses V2')
text = re.sub(
    r'\n    @Test\n    fun assistantModesExposeOnlyPhoneAndCustomAi\(\) \{.*?\n    \}\n',
    '\n',
    text,
    flags=re.S,
)
write(p, text)

# 4) Remove dead compose_external_* resources from every Android locale.
for path in (ROOT / 'android/AD-Glasses/app/src/main/res').glob('values*/strings_compose.xml'):
    text = path.read_text(encoding='utf-8')
    text = re.sub(r'^\s*<string name="compose_external_[^"]+">.*?</string>\s*\n?', '', text, flags=re.M)
    path.write_text(text, encoding='utf-8')

# 5) Make current Cloud UI describe only AD-owned REST/Realtime architecture.
p = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADNativeAiDetailScreens.kt'
text = read(p)
text = text.replace(
    '"Gemini Live ready through AD\'s short-lived-token relay"',
    '"Gemini Live ready through AD\'s authenticated Realtime session service"',
)
text = text.replace(
    '"Configure the relay used to mint short-lived Gemini Live sessions"',
    '"Configure the service used to authorize short-lived Gemini Live sessions"',
)
text = text.replace(
    '"Realtime is AD\'s own WebSocket audio path. It does not launch or control the Gemini app. " +\n                    "OpenAI Realtime can live in this same Cloud layer when its client is added."',
    '"Realtime is AD\'s own WebSocket audio path and stays inside AD Glasses. " +\n                    "OpenAI Realtime can live in this same Cloud layer when its client is added."',
)
text = text.replace('Text("Configure Realtime relay")', 'Text("Configure Realtime service")')
text = text.replace(
    '"Provider API keys and relay tokens are stored with Android Keystore-backed encrypted preferences. " +\n                "Standard REST talks directly to the selected provider; the relay is optional and scoped to AD-owned cloud infrastructure such as Realtime token issuance."',
    '"Provider API keys and Realtime session credentials are stored with Android Keystore-backed encrypted preferences. " +\n                "Standard REST talks directly to the selected provider; the authenticated Realtime service is scoped to AD-owned session authorization."',
)
write(p, text)

# 6) Remove stale provider-app history wording from Privacy UI.
replace(
    'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADProductSettingsScreens.kt',
    'Text("This permanently deletes every AD-owned Local AI and configured API conversation. Gemini and ChatGPT app history is managed inside those apps.")',
    'Text("This permanently deletes every conversation stored by AD Glasses for Local AI and configured API providers on this phone. It does not delete provider-side account data.")',
)

# 7) Keep migration inputs, but remove companion-app language from production comments.
p = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ai/assistant/AiProviderType.kt'
text = read(p)
text = text.replace(
    ' * Consumer Gemini/ChatGPT app identities are retired. Old serialized values migrate to CLOUD so\n * they cannot recreate an external-app handoff after upgrade.\n',
    ' * Legacy external-assistant provider tokens are retired. Old serialized values migrate to CLOUD\n * so upgrades remain on the AD-owned Cloud/Local routing architecture.\n',
)
write(p, text)

replace(
    'android/AD-Glasses/assistant-role/src/main/java/com/ad_glasses/assistant/ADAssistantRole.kt',
    ' * gives AD a first-class, screen-off system integration point without handing requests to a\n * consumer assistant app.\n',
    ' * gives AD a first-class, screen-off system integration point while keeping the assistant\n * session owned by AD Glasses.\n',
)

# 8) Retire the obsolete Termux relay design instead of documenting deleted runtime classes.
write('android/AD-Glasses/docs/termux-server-mvp-plan.md', '''# Retired Termux Server MVP Design\n\nThis document is retained only to record a superseded experiment. The old general-purpose phone/CLI relay design is **not** part of the current AD Glasses runtime and must not be used as an implementation guide.\n\n## Current AI architecture\n\nAD Glasses supports three inference lanes:\n\n1. **Cloud REST** — authenticated requests go directly through the configured API provider and model.\n2. **Cloud Realtime / Gemini Live** — bounded AD-owned realtime sessions use the Gemini Live API and the dedicated session-authorization plumbing required for short-lived credentials.\n3. **Local fallback** — an installed on-device model may be used when the user selects Local AI or when the configured fallback policy allows it.\n\nStandard text responses remain AD-owned and are spoken with Android TTS. Android Assistant-role integration is also AD-owned; it is not a handoff to another assistant application.\n\n## What was retired\n\nThe earlier Termux prototype exposed a broad set of chat, voice, image, capability, entitlement, and transcription endpoints. Those endpoints and their former client/router classes are no longer the canonical application contract. Do not restore them to make old documentation or tests compile.\n\nIf a future self-hosted service is added, it should be introduced as a new explicit provider with a documented API and privacy boundary rather than reviving the retired relay architecture.\n''')

# 9) Rewrite the engineering handoff so it reflects the post-revamp architecture.
write('AD_GLASSES_ENGINEERING_HANDOFF.md', '''# AD Glasses engineering handoff\n\nUpdated: 2026-08-23\n\n## Product invariants\n\n- **AD Glasses is the assistant.** Cloud and local models are inference engines behind AD, not separate assistant applications.\n- Chats stores AD-owned Local AI and configured Cloud REST conversations. `New topic` starts a clean thread; `Forget this conversation` deletes the current thread.\n- Never silently switch providers, send a local request to a remote service, drop an image, or perform network traffic that the selected route does not require. Fallback behavior must follow explicit user configuration.\n- Standard Cloud REST requests are authenticated with the configured provider credentials. AD receives the response text, stores the conversation when appropriate, and speaks concise replies with Android TTS.\n- Cloud Realtime / Gemini Live is a separate AD-owned WebSocket/audio path for bounded conversational sessions. It is not a phone-app handoff.\n- Local AI is the optional private/offline reasoning lane when a compatible model is installed. Moonshine is an offline English speech-to-text lane; it is not response speech.\n- Android Assistant-role integration belongs to AD Glasses itself and must stay lightweight; inference/audio work remains session-scoped.\n\n## Current implementation\n\n- Android package: `com.ad_glasses`.\n- Android project: `android/AD-Glasses`.\n- Deep-link scheme: `ad-glasses://`.\n- Cloud REST provider/model selection is owned by the current AI provider preferences/router.\n- Gemini Live provides the current Cloud Realtime path.\n- Local model inference remains optional fallback/on-device execution.\n- Android `TextToSpeech` is the standard speech-output path for text responses.\n- The AD Assistant role is available as a first-class Android system integration without delegating the assistant session to another app.\n\n## MainActivity and device-runtime guardrails\n\n- Preserve device-specific routing for HeyCyan/Oudmon, Meta, Eyevue, and MYVU; never send one vendor's protocol command to another device family.\n- Keep Activity-owned recognition, image, foreground-service, and coroutine work lifecycle-aware.\n- MYVU currently has no camera capture through its transport; image questions must report that capability boundary rather than falling through to another protocol.\n- Gemini Live and Cloud REST are independent cloud lanes. Local inference remains an optional fallback, not a replacement for either cloud lane.\n\n## Chats, voice, and media\n\n- Typed Chats queries are not auto-spoken by default.\n- Voice replies use Android TTS for standard Cloud REST/Local text turns; Realtime sessions may return their own streamed audio.\n- Current same-thread turns are serialized by the conversation coordinator so responses cannot be written into the wrong thread.\n- Lens/media Ask AI entry points should persist AD-owned results into Chats when the product flow calls for durable conversation history.\n- Physical-glasses testing is still required for camera transport, Bluetooth audio routing, wake timing, and cross-device resource arbitration.\n\n## Provider/error behavior\n\n- Prefer typed outcomes such as setup required, offline, unsupported modality, provider failure, permission required, and cancelled.\n- Retry only safe transient failures with bounded backoff. Never retry authentication/configuration failures as if they were transient.\n- Provider switches must not silently leak context to another provider. If previous context is shared, that choice should be explicit.\n\n## Privacy and storage\n\n- App data remains in Android's per-app sandbox. Configured secrets use encrypted preferences.\n- Local model files and AD conversation data must remain excluded from inappropriate Android backup/device-transfer paths.\n- **Clear all AD Chats** deletes conversations stored by AD Glasses on the phone; it does not claim to delete provider-side account data.\n- Do not claim cryptographic end-to-end protection for glasses transport until the physical protocol has been verified.\n\n## Validation before release\n\n1. Run shared Android compilation and the full app unit-test task.\n2. Build `:app:assembleDebug`.\n3. Verify the branded output `android/AD-Glasses/app/build/outputs/apk/debug/AD-Glasses.apk`.\n4. Run 16 KB native-library compatibility checks and zip alignment on release artifacts.\n5. Audit logs/crash reports for prompts, image paths, tokens, and transcripts.\n6. Re-run repo-wide checks for retired package/brand strings and deleted assistant-route symbols.\n\n## Guardrails for future changes\n\n- Do not restore the retired consumer-assistant handoff architecture to satisfy old tests or documentation.\n- Do not restore the retired general-purpose relay/CLI routing architecture.\n- Preserve Cloud REST, Cloud Realtime/Gemini Live, Local fallback, Android TTS, and AD Assistant-role integration as distinct responsibilities.\n- Keep migration parsers for old serialized provider values only when they map upgrades safely into the current Cloud/Local model.\n''')

# 10) Modernize the runtime architecture document by replacing the obsolete execution/handoff claims.
p = 'android/AD-Glasses/AD_ASSISTANT_RUNTIME.md'
text = read(p)
text = text.replace('- external automation is a background Android execution backend, not an AI provider.\n', '- Local Agent and supported Android APIs provide AD-owned phone-side actions; Accessibility is used only where current policy permits it.\n')
text = text.replace('- Gemini/ChatGPT mobile-app UI handoff is optional compatibility behavior, never the core runtime.\n', '- Consumer assistant-app handoff is retired; Cloud REST and Cloud Realtime stay inside AD Glasses.\n')
text = text.replace('                    direct/native  external automation   visible fallback', '                    direct/native   Local Agent      visible fallback')
text = text.replace('The product chooses a route from intent and available capabilities; the user should not have to choose “Gemini vs external automation vs local AI” for every request.', 'The product chooses a route from intent and available capabilities; the user should not have to choose implementation plumbing for every request.')
text = text.replace('background/system/external automation executor', 'supported Android/Local Agent executor')
text = text.replace('direct Android/media API where available, otherwise external automation', 'direct Android/media API where available, otherwise the supported AD action executor')
text = text.replace('direct app contract/AppFunction when authorized → external automation → Accessibility fallback', 'direct app contract/AppFunction when authorized → AD action executor → Accessibility fallback')
text = text.replace('3. **external automation background broadcast** — broad screen-off Android automation through the stable `com.ad_glasses.AUTOMATION_EVENT` contract.\n4. **System assistant privilege/fallback**', '3. **AD-owned action executor / Local Agent** — use only capabilities implemented and permissioned by the current app.\n4. **System assistant privilege/fallback**')
text = text.replace('\nexternal automation and provider selection are independent. A Gemini request can execute through external automation; a local-model request can execute through external automation; changing AI must never break automation profiles.\n', '\nAction execution and inference-provider selection are independent. Changing Cloud/Local inference must not change the permissions or safety policy of AD-owned phone actions.\n')
text = text.replace('- Assistant-app handoff and Accessibility belong under advanced/fallback configuration, not the primary product story.\n', '- Accessibility belongs under advanced/fallback configuration, not the primary product story.\n')
text = text.replace('- direct external automation background broadcast contract;\n- external automation-vs-Accessibility executor preference independent of AI provider;\n', '- AD-owned action routing remains independent of AI provider selection;\n')
text = text.replace('- keep visible assistant-app / Accessibility paths only as explicit fallbacks.\n', '- keep Accessibility-only paths as explicit, permissioned fallbacks.\n')
text = text.replace('The React Native app has three jobs:', 'The Android app has three jobs:')
write(p, text)

# 11) Remove obsolete companion-app references from active product UI copy.
p = 'android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADNativeAiDetailScreens.kt'
text = read(p).replace('and stays inside AD Glasses.', 'and stays inside AD Glasses.')
write(p, text)

# 12) Current product/design docs: replace external-assistant setup guidance with AD-owned architecture.
for p in [
    'android/docs/ad-glasses 2/product/PRODUCT_BLUEPRINT.md',
    'android/docs/stitch_ad_glasses/ad_glasses_product_design_blueprint.md_1.md',
    'android/docs/stitch_ad_glasses/ad_glasses_product_design_blueprint.md_2.md',
]:
    text = read(p)
    text = text.replace(
        '| External assistant automation | Android default assistant, external automation profile import/verification, AutoInput/accessibility checks, voice/image tests | Present as an optional advanced provider with a step-by-step readiness checklist and explicit phone-unlocked limitation | AI Services owns configuration; everyday Assistant UI only shows it when ready |',
        '| AD-owned action fallback | Supported Android APIs, Local Agent permissions, and Accessibility checks where required | Present as an advanced capability with explicit permission and phone-unlocked limitations | AI Services owns configuration; everyday Assistant UI stays provider-agnostic |',
    )
    text = text.replace(
        '| External assistant/external automation setup | AI Services & Models → External assistant (advanced) |',
        '| AD-owned action fallback setup | AI Services & Models → Advanced action permissions |',
    )
    write(p, text)

p = 'android/docs/ad-glasses 2/design/CANONICAL_UI_SPEC.md'
text = read(p)
text = text.replace('| 12 | Advanced and Diagnostics | Logs, external automation, device labs and prototype runtimes |', '| 12 | Advanced and Diagnostics | Logs, device labs, action permissions and prototype runtimes |')
text = text.replace('- default-assistant/external automation/AutoInput/Accessibility readiness and voice/image tests;', '- AD Assistant-role, Local Agent, Accessibility readiness, and voice/image tests;')
write(p, text)

# 13) Historical implementation plans: remove claims that retired assistant-app/automation architecture is current.
p = 'android/AD-Glasses/COMPOSE_MIGRATION_PLAN.md'
text = read(p)
text = text.replace('including the ChatGPT/Gemini external automation assistant as a normal plugin card rather than a special banner.', 'including cloud/local AI capabilities as normal product surfaces rather than special external-assistant banners.')
text = text.replace('Accessibility, external automation, Play Billing', 'Accessibility, Play Billing')
text = text.replace('preserve server refresh, external automation setup, form validation, and submission behavior', 'preserve server refresh, form validation, and submission behavior')
write(p, text)

p = 'android/AD-Glasses/UI_REVAMP_AUDIT.md'
text = read(p).replace('move external automation/bridge verification/accessibility into a clearly secondary Advanced setup section;', 'move Local Agent/accessibility verification into a clearly secondary Advanced setup section;')
write(p, text)

p = 'android/AD-Glasses/docs/local-models-plan.md'
text = read(p).replace('Existing provider architecture: external automation / API models / Pro subscription routing via `AiProviderPrefs` and `AiAssistantRouter`.', 'Current provider architecture: configured Cloud API models plus Local fallback via the current provider preferences/router.')
write(p, text)

# 14) Root bridge prompt: update only statements that describe current AD runtime; preserve unrelated hardware research.
p = 'BRIDGE_AGENT_PROMPT.md'
text = read(p)
text = text.replace('currently supports BLE device management, media sync, assistant routing, external automation integration, and privacy-focused local handling.', 'currently supports BLE device management, media sync, AD-owned Cloud/Local assistant routing, and privacy-focused local handling.')
text = text.replace('and Android-only Gemini/ChatGPT assistant routing through external automation automation.', 'and Android-owned Cloud REST, Gemini Live, and Local fallback assistant routing.')
text = text.replace('android/AD-Glasses/external automation/external automation_AI.xml\n', '')
text = text.replace('\nexternal automation\nACTION_external automation_COMMAND\n', '\n')
text = text.replace('- Where are external automation intents implemented?', '- Where are AD-owned phone action/executor boundaries implemented?')
write(p, text)

# 15) Prototype docs that still present the retired setup as a product feature.
p = 'android/docs/ad-glasses 2/ai-studio/01_ANDROID_PROTOTYPE_BUILD_BRIEF.md'
text = read(p).replace('Accessibility automation, external automation/AutoInput, notification forwarding', 'Accessibility automation, notification forwarding')
write(p, text)

p = 'android/docs/ad-glasses 2/references/canonical-ui/prototype/app.js'
text = read(p).replace('external automation, AutoInput and Accessibility readiness', 'Local Agent and Accessibility readiness')
write(p, text)

# 16) Strip clearly stale external-assistant setup copy from generated stitch reference pages.
p = 'android/docs/stitch_ad_glasses/advanced_diagnostics_hub/code.html'
text = read(p).replace('external automation Integration', 'AD Action Integration')
write(p, text)

p = 'android/docs/stitch_ad_glasses/advanced_external_ai_setup/code.html'
text = read(p)
text = text.replace('external AI automation tasks (like Assistant or external automation)', 'AD action fallback tasks')
text = text.replace('Import external automation Profile', 'Review AD Action Permissions')
text = text.replace('Load the XML configuration into external automation.', 'Review the permissions required by the selected AD action capability.')
write(p, text)

print('legacy reference cleanup applied')

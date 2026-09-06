# AD Glasses engineering handoff

Updated: 2026-08-28

## Product invariants

- **AD Glasses is the assistant.** Cloud and local models are inference engines behind AD, not separate assistant applications.
- Never silently switch providers, send local requests to remote services, drop an image, or perform network traffic that the selected route does not require.
- Provider and hardware capability failures should be explicit: setup required, offline, unsupported modality, permission required, provider failure, or cancelled.
- New hardware integrations belong behind provider/adapter boundaries rather than inside feature UI.

## Active hardware scope

- **HeyCyan:** primary glasses path and the reference architecture for connection/media behavior.
- **Meta:** retained as a vendor/provider boundary. A platform may report it as not configured when no verified SDK/protocol implementation is present.

Do not reintroduce MyVu, EyeVue, MemoMind/XGIMI, Even, Mentra, or another glasses family as sample dumps, SDK submodules, or cross-cutting feature code without a new product decision. If another family is added later, isolate its transport in a provider and expose only shared capabilities upward.

## Android

- Package: `com.ad_glasses`.
- Project: `android/AD-Glasses/`.
- UI: Kotlin + Jetpack Compose.
- Deep-link scheme: `ad-glasses://`.
- Cloud REST, Cloud Realtime/Gemini Live, Local fallback, Android TTS, and Android assistant-role integration remain separate responsibilities.
- Moonshine remains an Android speech dependency; the iOS app does not share that runtime.
- Preserve the confirmed HeyCyan BLE plus Wi-Fi media-transfer path and review `android/AGENTS.md` before changing it.
- Keep Activity-owned recognition, image, foreground-service, and coroutine work lifecycle-aware.

## iOS

- Project: `ios/ADGlasses.xcodeproj`.
- UI: native Swift + SwiftUI, deployment target iOS 17+.
- Speech: Apple Speech behind `SpeechTranscribing`; no Moonshine model is bundled.
- Glasses: `GlassesProvider` boundary with HeyCyan and Meta vendor identities.
- HeyCyan transport starts with CoreBluetooth and must not invent undocumented GATT identifiers.
- Meta has no bundled SDK in the current tree and should report that configuration state plainly.
- UI must respect iPhone safe areas and the home indicator. Do not mirror Android system-navigation/button assumptions.
- Keep signing/packaging ordinary; do not introduce App Store-only architecture requirements into core features.

## AI/provider behavior

- Cloud REST provider/model selection is owned by provider preferences/router.
- Gemini Live is the current Cloud Realtime path.
- Local model inference is optional private/offline execution, not an implicit replacement for cloud lanes.
- Android TTS is the standard speech-output path for text responses on Android; iOS speech output should use a native iOS abstraction when added.
- Typed outcomes and bounded retries are preferred. Authentication/configuration failures are not transient retries.
- Provider switches must not silently leak conversation context to another provider.

## Privacy and storage

- Keep secrets in platform-appropriate secure storage.
- Do not log prompts, transcripts, tokens, device credentials, or private media paths.
- Local model/conversation data must remain excluded from inappropriate backup or transfer paths.
- Do not claim cryptographic transport guarantees until the physical glasses protocol has been verified.

## Validation before release

When a feature set is ready for validation rather than while the app shell is still moving:

1. Run Android unit tests and assemble the debug app.
2. Build the native iOS scheme for a simulator without code signing.
3. Test Bluetooth, microphone, audio routing, and media behavior on physical phones/glasses.
4. Test iPhone layouts on small and large screens, including keyboard and home-indicator safe areas.
5. Audit logs and release artifacts for secrets/private data.

## Guardrails for future changes

- Do not restore retired cross-vendor demo/research trees merely as reference baggage.
- Keep Android and iOS native to their platforms.
- Share contracts and behavior where useful, but do not force a cross-platform UI framework into either app.
- Prefer a stable capability interface so future glasses support is an adapter-sized change.

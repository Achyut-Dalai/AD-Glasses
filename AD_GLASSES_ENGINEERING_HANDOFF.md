# AD Glasses engineering handoff

Updated: 2026-08-23

## Product invariants

- **AD Glasses is the assistant.** Cloud and local models are inference engines behind AD, not separate assistant applications.
- Chats stores AD-owned Local AI and configured Cloud REST conversations. `New topic` starts a clean thread; `Forget this conversation` deletes the current thread.
- Never silently switch providers, send a local request to a remote service, drop an image, or perform network traffic that the selected route does not require. Fallback behavior must follow explicit user configuration.
- Standard Cloud REST requests are authenticated with the configured provider credentials. AD receives the response text, stores the conversation when appropriate, and speaks concise replies with Android TTS.
- Cloud Realtime / Gemini Live is a separate AD-owned WebSocket/audio path for bounded conversational sessions. It is not a phone-app handoff.
- Local AI is the optional private/offline reasoning lane when a compatible model is installed. Moonshine is an offline English speech-to-text lane; it is not response speech.
- Android Assistant-role integration belongs to AD Glasses itself and must stay lightweight; inference/audio work remains session-scoped.

## Current implementation

- Android package: `com.ad_glasses`.
- Android project: `android/AD-Glasses`.
- Deep-link scheme: `ad-glasses://`.
- Cloud REST provider/model selection is owned by the current AI provider preferences/router.
- Gemini Live provides the current Cloud Realtime path.
- Local model inference remains optional fallback/on-device execution.
- Android `TextToSpeech` is the standard speech-output path for text responses.
- The AD Assistant role is available as a first-class Android system integration without delegating the assistant session to another app.

## MainActivity and device-runtime guardrails

- Preserve device-specific routing for HeyCyan/Oudmon, Meta, Eyevue, and MYVU; never send one vendor's protocol command to another device family.
- Keep Activity-owned recognition, image, foreground-service, and coroutine work lifecycle-aware.
- MYVU currently has no camera capture through its transport; image questions must report that capability boundary rather than falling through to another protocol.
- Gemini Live and Cloud REST are independent cloud lanes. Local inference remains an optional fallback, not a replacement for either cloud lane.

## Chats, voice, and media

- Typed Chats queries are not auto-spoken by default.
- Voice replies use Android TTS for standard Cloud REST/Local text turns; Realtime sessions may return their own streamed audio.
- Current same-thread turns are serialized by the conversation coordinator so responses cannot be written into the wrong thread.
- Lens/media Ask AI entry points should persist AD-owned results into Chats when the product flow calls for durable conversation history.
- Physical-glasses testing is still required for camera transport, Bluetooth audio routing, wake timing, and cross-device resource arbitration.

## Provider/error behavior

- Prefer typed outcomes such as setup required, offline, unsupported modality, provider failure, permission required, and cancelled.
- Retry only safe transient failures with bounded backoff. Never retry authentication/configuration failures as if they were transient.
- Provider switches must not silently leak context to another provider. If previous context is shared, that choice should be explicit.

## Privacy and storage

- App data remains in Android's per-app sandbox. Configured secrets use encrypted preferences.
- Local model files and AD conversation data must remain excluded from inappropriate Android backup/device-transfer paths.
- **Clear all AD Chats** deletes conversations stored by AD Glasses on the phone; it does not claim to delete provider-side account data.
- Do not claim cryptographic end-to-end protection for glasses transport until the physical protocol has been verified.

## Validation before release

1. Run shared Android compilation and the full app unit-test task.
2. Build `:app:assembleDebug`.
3. Verify the branded output `android/AD-Glasses/app/build/outputs/apk/debug/AD-Glasses.apk`.
4. Run 16 KB native-library compatibility checks and zip alignment on release artifacts.
5. Audit logs/crash reports for prompts, image paths, tokens, and transcripts.
6. Re-run repo-wide checks for retired package/brand strings and deleted assistant-route symbols.

## Guardrails for future changes

- Do not restore the retired consumer-assistant handoff architecture to satisfy old tests or documentation.
- Do not restore the retired general-purpose relay/CLI routing architecture.
- Preserve Cloud REST, Cloud Realtime/Gemini Live, Local fallback, Android TTS, and AD Assistant-role integration as distinct responsibilities.
- Keep migration parsers for old serialized provider values only when they map upgrades safely into the current Cloud/Local model.

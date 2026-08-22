# AD Glasses engineering handoff

Updated: 2026-08-21

## Product decisions

- Chats stays. It is the durable companion surface for exact details, links, silent use, copy/share, and continuing interactions that began by voice or media.
- AD-managed Chats use a **7-day inactivity retention window**. `New topic` starts a clean thread. `Forget this conversation` deletes the current thread immediately.
- Never silently switch providers, send a local request to a remote server, drop an image, invoke Tasker, or perform relay/web traffic. A fallback must be explicitly configured or approved by the user at the time of failure.
- “Gemini app” and “ChatGPT app” are external app handoffs. The external app owns the answer, voice, and context; AD cannot capture that answer in Chats through Android's assistant intent.
- Local AI and a future explicit cloud text API are AD-owned routes: AD receives full text, saves it in Chats, and speaks a concise version using Android TTS. Moonshine is English speech-to-text (input), not the response voice.
- Offline fallback should be Local AI. ChatGPT and Gemini app handoffs are not offline fallbacks.

## Verified on the connected Android phone

- Device: `R9ZY302QCQF`.
- The app downloaded, registered, selected, loaded, and generated with **Qwen2.5 0.5B Instruct Q4_K_M**.
- File size: 491,400,032 bytes (about 468.6 MiB).
- SHA-256: `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`.
- Profile: FAST, CPU, context 2048, maximum output 512.
- Repeated runtime checks completed at roughly 12–14 generated tokens/second. An actual Chats request returned `LOCAL READY`.
- A PNG renamed/imported as a model was removed. Imports now use a single validated path, detect GGUF/LiteRT, reject unsupported content, clean partial files, and support safe removal.
- `zipalign -c -P 16 -v 4` passed on the built APK in the earlier verification pass.
- No relay endpoint was probed during this work.

## Implemented in the current worktree

- Tasker assets, routing defaults, and Tasker execution paths removed.
- Explicit Local / Gemini app / ChatGPT app / cloud route presentation; unsafe fresh-install relay defaults removed.
- Local never silently becomes an OpenAI-compatible remote route.
- Local runtime operations serialized; unload/remove cannot close the engine during generation.
- Context budgeting, Qwen-compatible text routing, device FAST default, safe model import/remove, resilient download state, checksum support, and focused tests.
- Same-topic turns are serialized by a turn coordinator. Responses are written to the thread captured when the turn began, eliminating the wrong-thread race.
- AD-only retention worker and immediate cleanup, now seven days.
- Exact context commands are handled before inference: `new topic`, `new conversation`, and `forget this/current conversation/chat`.
- Lens/media Ask AI entry points persist AD-owned results into Chats.
- Live translation uses on-device ML Kit phrase translation with queued recognition. It does not silently invoke relay or an LLM.
- Main app-owned replies use concise speech while keeping the complete answer in Chats.
- Local AI screen uses the AD design, validated catalog/import/controller, safe top/bottom insets, and no redundant top title.
- Chat/model databases and local model files are excluded from Android cloud backup/device transfer. Encrypted secret preference files are excluded as well.
- Settings → Privacy now has a confirmed **Clear all AD Chats** action. It deletes only AD-owned Local/API conversations; Gemini and ChatGPT app history remains owned by those apps.

## Important behavior truths

### Voice

- A typed Chats query is not auto-spoken. That is why no voice was heard during the keyboard test.
- Moonshine cannot produce a spoken response. The repo uses it for offline English transcription. Current response speech is Android `TextToSpeech`.
- The principal glasses voice path still uses Android `SpeechRecognizer`; Moonshine is not yet the primary continuous glasses recognizer.
- Gemini app handoff will normally speak with Gemini's own voice. It does not create an AD-owned answer or Chats record.
- Gemini Live audio is a separate relay-token-based experimental path and is not the visible Gemini app selector.

### Topics and context

- Current rule: continue the active thread unless the user explicitly starts a new topic or forgets it. There is intentionally no silent semantic topic splitting.
- An unrelated question therefore remains in the active context. If the user later returns to the earlier subject, that unrelated turn is still present.
- Recommended next UX: named recent threads plus `New`, `One-off/private`, and `Switch topic` controls. If a local heuristic detects a sharp topic change, it should **ask** whether to split; it should never decide silently.
- Returning to an older subject requires a thread picker or a voice command such as “switch to trip planning.” That is not implemented yet.

### Translation

- Current implementation is realistic phrase-by-phrase translation, not true simultaneous full duplex. It queues recognized phrases and pauses/restarts listening around speech output, so words spoken while TTS is playing may still be missed.
- Best near-term mode: on-device ML Kit for private/offline phrase translation. Optional Gemini Live can later provide a more natural online mode, clearly labeled as online and Gemini-voiced.
- Whisper/Moonshine are recognizers only; they need microphone permission but cannot reason, translate conversationally, or answer like Gemini by themselves.

### Connection security

- App data is in Android's per-app sandbox; configured secrets use encrypted preferences; Chats/models are excluded from backup after this pass.
- BLE pairing/bond validation and Wi-Fi transfer endpoint validation were hardened in code.
- Do not claim end-to-end encryption between glasses and phone until the physical glasses protocol is tested. The firmware's Wi-Fi media transfer may still use local HTTP; application validation reduces exposure but is not cryptographic E2E protection.

## Remaining work, in priority order

### P0 — verify this exact worktree

1. Run the focused unit tests and `assembleDebug` after the final seven-day/inset/speech-policy edits.
2. Install the new APK and visually recheck Local AI top/bottom edges on `R9ZY302QCQF`.
3. Re-run a local Chats query. The request router now skips an unnecessary classifier generation for ordinary Local requests; compare first-token and total latency.
4. Verify bad imports from the Android picker: PNG, renamed PNG, empty file, truncated GGUF, and a valid GGUF.

### P1 — Chats as the cross-surface hub

1. Add recent/named thread UI, `New`, `One-off/private`, `Forget`, and explicit switch-topic actions.
2. Show source badges per turn: Voice, Lens, Media, Typed; record the immutable provider/route used by that turn.
3. Add Copy, Share, Read aloud, Retry, and Ask about this result. Do not auto-speak typed queries by default.
4. The backend serializes same-thread turns, but the typed composer currently disables sending while one request is active. Add a visible queued-send model if multiple typed questions should be accepted.
5. On provider switch, ask whether to start fresh or explicitly share earlier context.

### P1 — provider/error behavior

1. Replace prose transport failures with typed outcomes: setup required, offline, unsupported modality, provider failure, permission required, and cancelled.
2. Retry only safe transient failures with bounded exponential backoff. Never retry authentication/configuration failures.
3. Default fallback policy is `NEVER`. Add optional `Ask each time`; only later add an explicit ordered list. Local can be offered when network is unavailable.
4. Gemini/ChatGPT app selection must be tested end-to-end with accessibility submission. Installing an app does not guarantee it is Android's active assistant.
5. If AD-owned Gemini continuity is required, add an explicit Gemini text API transport. Do not confuse it with Gemini app handoff or Gemini Live.

### P1 — voice and translation

1. Integrate Moonshine as an explicit offline English input option for the main glasses voice path; retain Android recognition as a user-selected option.
2. Build one Bluetooth audio route controller shared by recognition, TTS, image answers, and translation, and verify the actual glasses output device before speaking.
3. Translation needs audio-focus arbitration and a small listening/speaking state machine. Test interruption, rapid alternating speakers, background noise, Bluetooth disconnect, and language-pack download failure.
4. Treat Gemini Live as an optional online mode only after token issuance, privacy disclosure, cancellation, reconnection, and cost behavior are production-ready.

### P1 — Lens/media and concurrency

1. Test Lens capture with physical glasses. Phone-only UI/backend testing cannot validate camera transport, timing, or firmware behavior.
2. Test whether translation, Lens capture, local generation, recording/Soundbites, and TTS compete for microphone, audio focus, BLE/Wi-Fi transfer, or model memory. Define one user-visible resource arbitration policy.
3. Keep only the latest successful media analysis as a seven-day-expiring artifact if that remains the product choice; do not overwrite a success with a failed attempt.

### P2 — packaging and release

1. Run Play/Android 16 KB native-library compatibility checks on every release artifact, not just zip alignment.
2. Add instrumentation tests for Local AI lifecycle/insets, download process death/resume, media-to-Chat persistence, and Bluetooth route loss.
3. Audit logs/crash reports for prompts, image paths, tokens, and transcripts before release.

## Commands

```bash
cd android/CyanBridge
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest :app:assembleDebug
adb -s R9ZY302QCQF install -r app/build/outputs/apk/debug/AD-Glasses.apk
zipalign -c -P 16 -v 4 app/build/outputs/apk/debug/AD-Glasses.apk
```

## Guardrails for the next engineer

- Preserve the user's unrelated dirty-worktree changes.
- Do not contact or probe a relay server unless the user explicitly asks.
- Do not reintroduce Tasker, silent provider fallback, or “success” results without confirmed execution.
- External assistant handoff cannot provide AD-owned voice or Chats continuity. Keep that limitation explicit in UI and tests.

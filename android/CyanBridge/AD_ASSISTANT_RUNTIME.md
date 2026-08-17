# AD Assistant Runtime

AD Glasses is a displayless glasses assistant. The phone is its compute, memory and tool plane; it is not the primary interaction surface.

## Product invariant

**Normal glasses use must not require the phone screen to turn on.**

A visible phone UI is an explicit fallback for an action Android cannot complete safely or legally in the background. The user should hear that a visible step is required before AD attempts it.

The React Native app has three jobs:

1. operate and configure the glasses;
2. configure the intelligence and permissions behind AD;
3. inspect captured memory and media when the user chooses to use the phone.

The assistant itself does not live in a chat screen.

## Identity

**AD is the assistant. Providers are engines. Executors are tools.**

- Gemini is the recommended cloud reasoning engine.
- Gemini Live is the preferred low-latency conversational path when available.
- Gemini Google Search grounding supplies fresh/current information inside the AI turn; there is no separate “web search UI” in the product.
- Moonshine/local speech is the private/offline transcription lane and is also useful for recordings and recovery.
- Local AI is the private/offline reasoning lane when a model is configured.
- OpenAI/Codex remains an advanced alternate provider, not a dependency of the core glasses experience.
- Tasker is a background Android execution backend, not an AI provider.
- Accessibility automation is an explicit last-resort visible fallback.
- Gemini/ChatGPT mobile-app UI handoff is optional compatibility behavior, never the core runtime.

## Normal wake-word path

```text
Glasses wake word / AI button
        │
        ▼
AD glasses connection runtime
        │
        ├── voice audio ───────────────┐
        ├── optional camera frame ─────┤
        ├── recent artifact context ───┤
        └── user/tool policy ──────────┤
                                       ▼
                             AD session router
                              │           │
                  online/live │           │ private/offline
                              ▼           ▼
                         Gemini Live   Moonshine + Local AI
                              │           │
                              └─────┬─────┘
                                    ▼
                              AD tool router
                         │          │          │
                    direct/native  Tasker   visible fallback
                         │          │          │
                         └──────┬───┴──────────┘
                                ▼
                         speech to glasses
```

The phone display stays off throughout the normal path.

## Use-case routing

The product chooses a route from intent and available capabilities; the user should not have to choose “Gemini vs Tasker vs local AI” for every request.

| User says / does | Primary route | Phone display |
|---|---|---|
| “What time is my next meeting?” | AD tool/native calendar integration; Gemini only if language interpretation is needed | Off |
| “What’s happening with X today?” | Gemini + Google Search grounding | Off |
| “Explain this to me” / general knowledge | Gemini Live during an active voice session; standard Gemini request for a one-shot turn | Off |
| “What am I looking at?” | glasses camera frame → Gemini vision/Live | Off |
| “Read/translate that sign” | glasses camera/audio → Translate capability / Gemini | Off |
| “Take a photo” | direct glasses command | Off |
| “Start/stop video” | direct glasses command | Off |
| “Record this” | direct glasses audio capture | Off |
| “Summarize that recording” | stored artifact → Moonshine/local transcript → configured reasoning engine | Off while processing; phone UI only if user opens the result |
| “What did I see yesterday?” | AD memory/index → relevant captures → configured reasoning engine | Off |
| “Call Alex” | contact resolution → background/system/Tasker executor → spoken confirmation before external communication | Off when Android permits; system confirmation only if required |
| “Text Alex I’m late” | resolve contact + compose action → spoken confirmation → background executor | Off when destination contract permits |
| “Turn volume down / pause music” | direct Android/media API where available, otherwise Tasker | Off |
| “Open X and do Y” | direct app contract/AppFunction when authorized → Tasker → Accessibility fallback | Off unless only visible automation can finish it |
| “Remind me every weekday…” | AD Cron/scheduler | Off |
| No internet / private request | Moonshine + local model when configured | Off |
| User opens a saved photo/recording/note | artifact detail surface with optional voice/text follow-up scoped to that artifact | On by user choice, not required for assistant operation |

This matrix is the product contract. Provider selection changes implementation, not the interaction model.

## Why this is not the “earbuds launch Gemini” product

Generic Bluetooth-assistant invocation only supplies a microphone/button into the phone's selected assistant. AD additionally owns:

- the glasses wake event;
- the glasses camera and capture timing;
- device state, battery, transfer and recording state;
- visual memory, recordings, notes and generated artifacts;
- private/local models and transcription;
- per-capability permissions and privacy policy;
- automation/tool execution policy;
- action confirmations;
- context selection across recent captures and sessions;
- response routing back to the glasses.

Gemini is intelligence behind AD, not AD's visible identity.

## Speech paths

### Live conversation

Use direct Gemini Live audio when the selected provider is Gemini and a Live session is available. Stream audio directly; do not transcribe with Whisper/Moonshine first unless policy or recovery requires text. This avoids an unnecessary ASR → text → model → TTS chain for every turn.

A Live session is **bounded**. It begins after a wake event and ends after conversation idle/explicit stop/network failure. AD must not keep a cloud audio session open all day.

### Private/offline question

Use local speech recognition (Moonshine; Vosk remains legacy fallback while migration completes), then local reasoning when a compatible local model exists. If local reasoning is unavailable, AD can offer the configured cloud route without forcing a phone UI.

### Recordings

Recordings are artifacts, not live-assistant sessions. Store the recording first, then run the transcription pipeline. Moonshine/local transcription is preferred for privacy/offline operation; configured cloud transcription can remain optional.

## Vision

A user asking “what am I looking at?” should not open a phone camera or assistant app.

AD requests a glasses frame/thumbnail through the existing device-specific camera pipeline and adds it to the active AI turn. The resulting image remains governed by the capture/privacy policy and may become a Library artifact only when the product flow calls for persistence.

For a saved photo/video in Library, AI follow-up belongs to that artifact. The phone may show a contextual detail surface because the user has explicitly opened the artifact; this is not a global chat destination.

## Current information

Fresh/current questions are a reasoning capability, not a navigation destination. For Gemini, use Google Search grounding/tooling inside the request. The answer is spoken through the glasses; source detail can be retained for an optional phone-side detail view.

## Android action hierarchy

AD chooses the least-visible capable route.

1. **Direct app/native API** — use APIs owned by AD or a target app when a supported contract exists.
2. **Structured Android agent/app functions** — feature-gated on Android versions/devices where the API and permission model are actually available. Do not make the product depend on this preview-era path.
3. **Tasker background broadcast** — broad screen-off Android automation through the stable `com.fersaiyan.cyanbridge.AUTOMATION_EVENT` contract.
4. **System assistant privilege/fallback** — use system assistant capabilities only when they provide a supported locked-screen operation that AD cannot execute itself.
5. **Accessibility / visible app automation** — last resort. The user must be told that the phone may need to wake/unlock.

Tasker and provider selection are independent. A Gemini request can execute through Tasker; a local-model request can execute through Tasker; changing AI must never break automation profiles.

## Confirmation policy

| Class | Examples | Default |
|---|---|---|
| Read-only | time, battery, weather/current info, summarize a capture | execute silently, speak result |
| Reversible device action | media controls, volume, start/stop recording | execute in background, brief spoken acknowledgement |
| External communication | call, send message/email, post/share | ask for spoken confirmation unless the user has explicitly pre-authorized a narrow automation |
| Destructive / financial / security-sensitive | delete data, purchase, account/security changes | explicit confirmation; prefer system-owned confirmation UI when required |
| UI-only fallback | unsupported app workflow | explain that phone interaction is required before waking/unlocking it |

## Android Assistant role

AD can optionally be selected as Android's Assistant role. This strengthens system-level integration and gives the product a first-class assistant identity on Android. It is not required for the glasses wake word; the glasses connection runtime already receives that event directly.

The role service must remain lightweight. Heavy audio/AI work is session-scoped. Selecting the role is a user-controlled Android system decision; AD only opens the official role chooser.

## Screen-on exceptions

Screen-on is not a failure when Android or the destination app genuinely requires user-visible consent/confirmation. Examples can include:

- an app exposes no background/direct contract for the requested action;
- Android requires a chooser, permission, biometric or other system confirmation;
- an external app requires the user to review content before sending;
- a sensitive automation reaches the policy boundary above.

In those cases AD asks first. It never silently wakes the screen merely because an inherited UI automation path exists.

## UI consequences

- Root navigation: Home / AI / Library.
- No global Prompt/Chat tab.
- Home starts glasses actions directly.
- AI configures AD's engines, runtime and execution policy.
- Library is the durable artifact/memory plane.
- Contextual conversation may exist inside a photo, recording, note or other artifact detail when it materially helps the user inspect that artifact.
- Assistant-app handoff and Accessibility belong under advanced/fallback configuration, not the primary product story.

## Implementation status

Already present in this branch:

- glasses-first React Native navigation;
- direct native bridge into the existing glasses runtime;
- direct Tasker background broadcast contract;
- Tasker-vs-Accessibility executor preference independent of AI provider;
- Gemini as the recommended/default cloud provider in product settings;
- direct Gemini Live WebSocket client with native audio output, image injection and session resumption;
- Gemini Live glasses-PCM input mode that avoids opening a second phone microphone path;
- Gemini Live Google Search plus guarded AD function tools;
- spoken-confirmation boundary for external/sensitive background phone actions;
- bounded headless `ADGeminiLiveSession` coordinator backed by the foreground-execution service;
- Moonshine module and existing transcription pipeline;
- optional Android Assistant role module and React Native role chooser/status.

Remaining runtime work:

- connect each supported glasses device's raw wake-session audio callback to `ADGeminiLiveSession.offerPcm` without requiring visible Activity state;
- extract the remaining direct hardware action dispatcher from `MainActivity` into a process/service-scoped glasses runtime so Live tools survive Activity destruction;
- feed camera frames into the same active Live session for natural “what am I looking at?” follow-ups;
- add the local/Moonshine fallback policy at the session router rather than only at separate one-shot/transcription paths;
- replace polling UI state with native events;
- keep visible assistant-app / Accessibility paths only as explicit fallbacks.

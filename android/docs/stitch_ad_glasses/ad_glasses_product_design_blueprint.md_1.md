# AD Glasses product design blueprint

Status: design contract before implementation

This document defines the intended product experience. Stitch may explore visual execution, but it must not change the information architecture, remove supported functionality, invent device behavior, or introduce monetization.

## 1. Product thesis

AD Glasses turns a phone into the compute, storage, privacy, and control layer for compatible smart glasses. The glasses supply sensors and lightweight hardware capabilities; the phone supplies AI, organization, automation, and understandable controls.

The experience should feel like a calm hardware companion: immediate when the owner wants to capture or ask, transparent when data moves between glasses and phone, and quiet when nothing needs attention.

Product promise: **Your glasses. Your AI. Your data.**

## 2. Decisions to lock before visual design

### Navigation

Use four bottom destinations:

1. **Home** — device state, the next useful action, active work, and recent results.
2. **Assistant** — voice, camera, live, and text AI interactions plus conversation history.
3. **Library** — synced photos/videos/audio, recordings, transcripts, notes, summaries, and memories.
4. **Automations** — built-in and community plugins, schedules, permissions, and active automation state.

Open **Settings** from the top-right icon/avatar. Settings is important but not frequent enough to occupy permanent bottom navigation. Do not preserve the current five-tab structure merely because it exists.

### Page count

Design **17 reusable page templates**, not one screen for every backend class:

| # | Template | Navigation role |
|---|---|---|
| 1 | Welcome | First run |
| 2 | Setup & permissions | First run / recovery |
| 3 | Pair glasses | First run / device management |
| 4 | Home | Primary destination |
| 5 | Assistant | Primary destination |
| 6 | Conversation & live session | Assistant detail |
| 7 | Library | Primary destination |
| 8 | Content detail | Library detail |
| 9 | Automations | Primary destination |
| 10 | Automation detail | Automations detail |
| 11 | Device Center | Home utility |
| 12 | HeyCyan Firmware Update | Device Center utility |
| 13 | Sync Center | Home and Library utility |
| 14 | Settings | Global utility |
| 15 | AI Services & Models | Settings and Assistant utility |
| 16 | Privacy & Data | Settings utility |
| 17 | Advanced & Diagnostics | Settings / Device Center utility |

State variants are frames, not new pages. Stitch should create roughly 35–45 frames across these templates, prioritizing the states listed below instead of duplicating static screens.

### Device selection and support maturity

AD Glasses is device-extensible but **HeyCyan-first**. The owner currently has HeyCyan hardware, so HeyCyan is the default, the first complete release path, and the path against which the redesign is physically tested. Other integrations remain visible only at their honest maturity level.

| Target | Product classification | What the UI may promise now |
|---|---|---|
| HeyCyan-compatible | **Primary** | Pairing, BLE control, device state, supported capture/recording commands, and local Wi-Fi media sync; individual vendor commands still require physical validation |
| Eyevue | **Experimental device integration** | Device-specific BLE/Wi-Fi code exists; show experimental status and never imply reliability until tested on physical Eyevue hardware |
| Meizu MYVU / Star Air | **Experimental device integration** | Device-specific BLE/RFCOMM and display/audio-oriented controls exist; no camera capture, onboard media sync, or Visual Diary capability |
| Meta Ray-Ban | **Partial setup** | Selection and optional Meta DAT registration plumbing when its SDK is available; session, camera stream, photo capture, and display rendering must say Not implemented rather than appearing operational |
| Generic audio glasses/headsets | **Limited** | Android audio/microphone routing only; no camera, onboard storage, media sync, or glasses display promises |
| Unknown device | **Safe fallback** | Identification and generic Bluetooth information only; do not send HeyCyan or other vendor commands until a compatible adapter is deliberately selected |
| MemoMind / XGIMI | **Research** | Protocol research and transport components are retained, but this is not a normal pairable product path until a complete adapter is registered and hardware-tested |
| EvenHub / Even Realities | **Prototype runtime** | EvenHub-compatible content/runtime experimentation only; do not imply direct pairing or control of Even Realities hardware without a real device adapter |
| MentraOS | **Prototype runtime** | Local relay/message compatibility experimentation only; it is not a glasses brand or pairing target |

Device selection uses two related surfaces:

1. **Pair glasses** scans first, auto-detects known advertisements/service IDs, shows the detected family and maturity label, and allows a manual correction before connecting.
2. **Devices** is reached from the Home Device Stage or Settings. It shows the active device, **Add glasses**, **Choose by brand**, and research/runtime entries in a separate Labs area.

Only one hardware device is active at a time. Remembered-device switching is a later enhancement until persistence and session switching are fully implemented. Meta may use its provider registration flow rather than ordinary BLE pairing. MemoMind, EvenHub, and Mentra must not appear as normal connectable results merely because research code exists.

### Connection model and presentation

Connection state must be designed as a product flow rather than one changing label. **Connected** means the active device's normal control transport is ready—BLE for HeyCyan—not that temporary local Wi-Fi is active. Wi-Fi appears as a separate secondary transport only during supported sync, firmware, preview, or debug sessions.

| State | Device Stage treatment | Available actions |
|---|---|---|
| Not set up | Quiet generic glasses render, “No glasses connected” | Connect glasses; Explore app |
| Permission needed | Render remains stable; inline explanation identifies the missing permission | Continue; Not now where safe |
| Bluetooth off | Bluetooth-off icon and “Turn on Bluetooth” | Turn on; Troubleshoot |
| Scanning | Subtle search motion and “Looking for nearby glasses” | Stop scan |
| Device found | Result row, detected family, maturity, capability preview | Review device |
| Pairing | “Pairing…” with system-dialog expectation if applicable | Cancel |
| Connecting | “Connecting…” with indeterminate progress and plain-language stage | Cancel; Taking too long? after a threshold |
| Connected | Teal Connected pill, active device name, only available battery/storage metrics | Ask, Capture, Sync, Record as supported; Device Center |
| Connected with limited capabilities | Connected plus Limited/Partial/Experimental label and capability summary | Only supported actions |
| Reconnecting | Keep prior device identity, subdued metrics marked stale, “Reconnecting…” | Stop retry; Choose another device |
| Disconnected | Last device and last-seen time remain visible | Reconnect; Choose device; Troubleshoot |
| Connection failed | Short categorized reason without raw SDK jargon | Try again; View details; Choose device |
| Busy with exclusive session | Connected identity plus Activity Banner for Sync, Firmware update, Preview, or Recording | Open activity; Stop/cancel only when safe |

Rules:

- Keep the Device Stage dimensions and information positions stable across states to avoid visual jumping.
- Connection success uses a short confirmation transition and haptic, then exposes supported quick actions.
- Never use a decorative green/red glow as the only status cue.
- After roughly 8–12 seconds of connecting, add “This is taking longer than expected” and recovery choices without automatically declaring failure.
- A connection-detail sheet may show adapter, BLE state, temporary Wi-Fi state, signal quality, last successful contact, retry count, and a copyable sanitized error ID.
- Raw MAC addresses, UUIDs, IP addresses, and protocol logs remain in Advanced unless needed for a specific recovery step.
- If the official/vendor app is competing for the same device, say so plainly and offer steps to disconnect it.

### Appearance

AD Glasses is **light mode only**. Do not create a dark theme, System theme option, theme switcher, or alternate theme resources for the redesign. Use a white, graphite, and cool-gray foundation with one restrained electric blue for selection, focus, links, and progress. Primary actions are high-contrast graphite-black. The desired character is Apple-like product calm with Vercel-like precision, adapted for an Android hardware companion rather than copied from either brand. The glasses render floats directly in this light system with no contrasting image rectangle.

Light composition rules:

- Use `#F6F7F9` as the continuous page canvas and pure white only for meaningful controls or grouped content.
- Home's product hero is open space, not a card. Its transparent render, soft shadow, and faint ambient halo visually merge with the canvas.
- Use graphite-black for the one strongest action in a region; use electric blue for active/selected/progress meaning rather than filling every button.
- Prefer crisp hairlines, tonal elevation, aligned edges, and deliberate whitespace over nested cards or heavy shadows.
- Keep root headings compact. Home uses the logo mark rather than a text title.
- Bottom navigation is a clean white surface with a fine top separator and a restrained selected indicator; it is not a floating glass capsule.
- Use one coherent icon family and one motion language throughout every page.

### Naming

Use plain outcomes rather than implementation terms:

- Chats becomes **Assistant**.
- Media becomes **Library**.
- Plugins becomes **Automations**.
- Glasses becomes **Home**, with **Device Center** for hardware controls.
- “Custom AI provider” becomes **Your cloud** where appropriate.
- Do not show raw protocol names unless the owner opens Advanced or Diagnostics.

## 3. Experience principles

1. **Glance first.** Home answers three questions immediately: Are my glasses connected? What is active? What can I do next?
2. **Reveal complexity progressively.** Everyday actions stay visible; firmware, raw networking, compatibility detail, and debugging move deeper.
3. **State is content.** Connected, recording, transferring, listening, processing, offline, and failed states must be obvious through icon, text, and layout—not color alone.
4. **Privacy is contextual.** Show “On device” or “Sent to your cloud” beside the action where it matters, rather than hiding it in a policy page.
5. **One owner, no storefront pressure.** There are no subscriptions, payment prompts, quotas, donation requests, premium gates, or upgrade copy.
6. **Hardware truth wins.** Only show an action when the selected device family supports it. Explain unavailable capabilities without blaming the user.
7. **Designed for interruption.** Capture, sync, recording, and live AI continue through compact activity banners and resumable detail views.

## 4. Product capability plan

The redesign keeps every implemented owner-facing capability except Walking Aid and preserves experimental/research work without falsely promoting it to supported functionality. “Keep” does not mean preserving its current screen or implementation shape. It means the outcome remains available at its verified maturity while navigation, copy, state handling, safety, and backend boundaries may improve.

| Product area | Preserve | Improve in the redesign | Design consequence |
|---|---|---|---|
| Device support | HeyCyan primary; Eyevue and Meizu experimental; Meta partial; generic audio limited; MemoMind research; EvenHub/Mentra runtimes | Central capability registry; explicit maturity metadata; one active device with future remembered devices; safe unknown fallback | Pairing and Device Center show only truthful actions, support level, and compatible modules |
| Connection | BLE scan, bind, reconnect, disconnect, battery and version requests | Connection state machine, bounded retry/backoff, last-seen information, actionable error categories, safe auto-reconnect | Device Stage and connection detail use stable named states rather than raw SDK messages |
| Capture | Glasses photo, video, audio, image question, family-specific capture paths | One shared capture entry, device-aware choices, visible privacy/source, reliable completion state | Capture is a quick action and an Assistant attachment source, not duplicated settings |
| Recording | Meeting capture, timer/duration controls, phone or Bluetooth microphone sources | Persistent elapsed-time banner, interruption recovery, input/source verification, clear stop reason | Recording remains visible across the app and opens a focused detail view |
| Media transfer | BLE trigger, Wi-Fi Direct/local HTTP transfer, photo/video/audio import | One recommended flow, resumable checkpointing where protocol permits, duplicate detection, per-file retry, background/foreground-service reliability, partial-success summary | Sync Center explains stages and recovery without exposing protocol jargon by default |
| Assistant | Text chat, glasses voice questions, image questions, local and cloud routing, Gemini Live | Action-first hub, explicit context sources, provider health, safe local/cloud fallback, response saving and source links | Assistant replaces Chats and starts with a composer rather than history alone |
| Library | Recordings, synced gallery, transcripts, notes, summaries, screen captures, daily facts | Unified searchable timeline, collections, provenance, linked media/transcript/summary objects, retention controls | One Library destination with adaptable detail pages |
| Automations | Local Agent, Meeting Spark Notes, Live Caption Relay, Hands-Free Translator, Errand Brain, Auto Diary, Auto Audio, Visual Diary | Compatibility declarations, permission/data manifests, test-before-enable, run history, global pause, conflict handling for camera/mic sessions | Plugins become outcome-oriented Automations with a consistent detail template |
| Community plugins | Catalog, plugin opening, publish flow | Trust metadata, declared permissions/data access, compatibility, version/source information, disable/report controls | Kept as a secondary area inside Automations, not the main product hierarchy |
| Local AI | Local models, local agent, Moonshine/Gemma-related processing | Capability and storage checks, model readiness, download/configuration clarity, health test, graceful fallback | On-device provider card in AI Services with honest ready/not-ready states |
| Owner cloud | Relay URL, secure optional token, optional email, separate model choices, connection test | Provider health, discovered-model picker, timeout/auth diagnostics, per-task routing, no secrets in logs | “Your cloud” is a first-class provider with a visible privacy boundary |
| External assistant automation | Android default assistant, external automation profile import/verification, AutoInput/accessibility checks, voice/image tests | Present as an optional advanced provider with a step-by-step readiness checklist and explicit phone-unlocked limitation | AI Services owns configuration; everyday Assistant UI only shows it when ready |
| Memory & privacy | Local memory, planned backend modes, source eligibility, vault, OCR/transcript controls, export/import/clear | Data inventory, per-source retention, provenance, pause passive collection, clearer unfinished-backend state | Dedicated Privacy & Data page rather than scattered settings cards |
| Device-specific tools | HeyCyan controls; experimental Eyevue and Meizu controls; partial Meta registration; research display adapters | Capability-led modules, maturity-aware availability, consistent state language, safe error recovery | Family-specific Device Center examples never present unimplemented controls as working |
| HeyCyan firmware | Existing combined V821 Wi-Fi `.swu` then JieLi BLE `.bin` OTA research, exact-base catalog checks, local pair picker, exclusive session, and post-stage readiness checks | Dedicated HeyCyan-only lab flow, owner-controlled sources, enforceable preflight, state-aware cancellation, partial-update recovery, persisted status, and verified before/after versions | Firmware Update is a separate Device Center page; debug probes stay in Advanced |
| Display bridge and runtimes | GlassesBridge display commands, notification forwarding, Terminal HUD, EvenHub compatibility runtime, Mentra-compatible local relay | Explicit trust boundary for WebView/local relay, runtime status, active display target, stop/clear, limited logs, and clear experimental labels | An External runtimes module inside Advanced, with only active output surfaced in the global activity model |
| Expert tools | OTA/firmware, patch request, live preview, Wi-Fi ADB, raw diagnostics, log collection, transcription debug | Integrity/preflight checks, secret redaction, explicit risk levels, exportable diagnostic bundle | Advanced & Diagnostics is separate and visually secondary |
| Appearance/localization | Existing theme behavior and supported languages | Use one deliberate light appearance; test long translations and font scaling | No theme selector, dark mode, accent profiles, wallpapers, chat skins, or decorative theme system |

### New product behaviors worth building

These improve the app without changing the glasses protocol:

1. **Device capability registry.** Every feature asks a single source of truth whether the selected device supports camera, display, onboard storage, Wi-Fi sync, audio files, or a particular control. This prevents contradictory controls across screens.
2. **One global activity model.** Capture, recording, transfer, live translation/captioning, OTA, and live AI share explicit states and conflict rules. The owner can always see what owns the camera/microphone/network session and stop it.
3. **Global pause for passive automations.** One privacy action pauses Auto Diary, Auto Audio, Visual Diary, screen context, and other passive collection without losing configuration.
4. **Provenance everywhere.** Library items and AI responses record source device, capture time, automation, model/provider, and whether processing occurred locally or through Your cloud.
5. **Safe sync ledger.** Track discovered, transferred, verified, skipped duplicate, and failed items. Never imply success for a partially imported batch, and never delete glasses media automatically unless a future verified-delete flow is deliberately designed.
6. **Provider health.** On-device and cloud providers have clear Ready, Needs setup, Testing, Offline, Authentication failed, and Model unavailable states. The Assistant can offer an allowed fallback rather than simply failing.
7. **Permission and data manifest for automations.** Before first enable, show exactly which sensors, screen context, memory, notifications, and cloud routes an automation uses.
8. **Linked knowledge objects.** A recording, transcript, meeting summary, extracted facts, and saved assistant answer stay linked so the owner can trace or delete derived data.
9. **Notification discipline.** Notifications are limited to active recording/capture, completed or failed sync, automation requiring attention, and important device/AI failures. Marketing and engagement notifications do not exist.
10. **Redacted diagnostic export.** A support bundle removes tokens, relay credentials, personal transcript content by default, and unnecessary device identifiers before sharing.

### Later enhancements, not first-design blockers

- Multiple remembered devices with fast switching while allowing only one active hardware session.
- Optional Android Quick Settings tile for Connect, Ask, Record, or Sync.
- Local semantic Library search when an on-device embedding model is ready.
- User-authored private automations built from safe triggers/actions.
- Encrypted multi-device sync after an owner-controlled backend exists.
- Tablet and foldable two-pane layouts after the phone experience is stable.

These must not be shown as finished in the first prototype unless their backend state is explicitly “planned” or “not configured.”

## 5. Brand and identity

### Brand character

AD Glasses is **quietly capable, private, precise, and owner-controlled**. It should not sound like a startup selling AI access or a hacker tool exposing protocols. Copy is direct and reassuring:

- Prefer “Connect glasses” over “Initialize device handshake.”
- Prefer “Your cloud needs attention” over “HTTP 401.”
- Prefer “3 files could not be transferred” over “Sync failed.”
- Put technical detail behind **View details** for diagnosis.

Avoid exclamation marks, anthropomorphic assistant copy, exaggerated intelligence claims, fear-based privacy language, and slogans beyond the first-run promise.

### Logo direction

The identity is **mark-first**. The Home app bar uses a compact symbol, not an “AD Glasses” text heading. The wordmark is a separate asset used on Welcome, About, and brand/export surfaces only.

Ask Stitch for four professional black-and-white symbol candidates across two families:

1. **Abstract optics:** precise negative-space geometry suggesting two viewpoints, focus, or connection without drawing literal eyeglass frames.
2. **Reduced bridge:** an exceptionally simple paired-form or bridge silhouette that still feels original at 16–24px.

Do not preselect a winner. Judge the actual small monochrome marks first, then develop the strongest one into the adaptive icon and wordmark. An AD monogram is optional and must not be forced.

Logo constraints:

- One-color silhouette must work at 16–24px.
- No gradients, 3D, glow, sparkle/star “AI” clichés, eye illustration, face, robot, brain, generic infinity mark, clip-art glasses, wordmark inside the icon, or resemblance to an existing technology/eyewear mark.
- Prepare Android adaptive foreground/background layers inside the safe zone.
- Show every candidate black-on-white, white-on-black, and at 16dp, 24dp, and 48dp before presenting large mockups.
- After selection, deliver the light-app adaptive icon, Android monochrome themed icon, 24dp in-app mark, notification glyph, and a carefully kerned wordmark lockup.
- Apply electric blue only after the one-color identity is approved.
- Test circle, squircle, rounded-square, and masked adaptive-icon previews.

### Product imagery

Create one original isolated product render: brand-neutral satin graphite glasses, realistic clear lenses, subtle cool highlights, and a clean transparent alpha background. Deliver a high-resolution transparent PNG or WebP. There must be no studio field, rectangular background, card, vignette, baked gradient, ground plane, visible image edge, checkerboard, or white matte. The glasses must look naturally suspended within the page rather than pasted onto it.

Welcome and Home may use different container crops of this same source asset. Build the soft contact shadow and extremely restrained cool-gray/blue ambient halo as separate editable UI layers behind the transparent render so they blend perfectly with the page. Product imagery never contains UI text, connection state, battery, people, branded hardware details, or sci-fi overlays. All state remains editable interface content.

### Motion and hero behavior

Motion should make the light interface feel polished and responsive, not busy:

- First appearance: glasses fade from 0 to 100 percent, move upward 8dp, and scale from 0.97 to 1 over roughly 380–420ms with a smooth emphasized-decelerate curve.
- Scroll response: the isolated glasses and its editable shadow may separate by 4–6dp of restrained parallax; return cleanly without springy overshoot.
- Connecting: animate a thin signal trace or soft focus sweep behind the glasses while keeping the image itself stable.
- Connected: play one short halo expansion and light haptic, then settle completely. Do not run a perpetual success animation.
- State changes: crossfade status, metrics, and available actions over 200–280ms without moving their anchors.
- Press feedback: 100–140ms tonal response and at most 0.98 scale on prominent cards/actions.
- Sheets and focused details: 240–280ms movement with predictive-back-compatible behavior.
- Recording and transfer may use meaningful live motion—a restrained recording pulse and actual progress—not decorative looping.
- Never use endless floating/bobbing, spinning glasses, shimmer on loaded content, particle fields, animated gradients, or motion baked into a video asset.
- Respect Android reduced-motion settings; replace spatial movement with short fades while retaining state clarity.

### Iconography, sound, and haptics

- Use one rounded, outlined Android icon family; filled variants indicate selection only.
- Use familiar hardware/action metaphors and label uncommon controls.
- Haptics: light confirmation for connection and capture, stronger warning for destructive confirmation, none for passive decorative motion.
- Sound is opt-in and limited to capture/recording events when hardware/system behavior permits.
- Respect system silent mode, reduced motion, and accessibility settings.

## 6. Global shell

### Top app bar

- Home: approved 24dp AD Glasses mark at left, Settings icon and optional notification/status dot at right. No “AD Glasses” text, greeting, or oversized heading.
- Other primary tabs: compact page title, optional search, and Settings icon only when useful. Avoid marketing-scale typography.
- Detail pages: back action, title, and a restrained overflow menu.

### Bottom navigation

- Four equally weighted items with icon and label: Home, Assistant, Library, Automations.
- Selected item uses electric blue plus a subtle tonal indicator.
- Keep labels visible. Do not use a floating center button or five tiny icons.
- Hide the bar in focused conversation/live, media viewer, setup, and confirmation flows.

### Persistent activity banner

Place a compact banner just above bottom navigation when a long-running activity exists. It can represent one primary active session at a time: media sync, meeting recording, live translation, live captions, visual diary capture, or AI processing. Tapping it opens the corresponding detail page. Never use an activity banner as an advertisement.

## 7. Page specifications

### 1 — Welcome

Goal: explain value without making the owner swipe through marketing slides.

- Brand-neutral glasses illustration or soft 3D crop.
- Headline: “Your glasses. Your AI. Your data.”
- Two concise supporting lines: connect compatible glasses; use on-device or owner-configured cloud AI.
- Primary: **Set up my glasses**.
- Secondary: **Explore without pairing**.
- Links: Privacy and supported devices.

One screen is sufficient. Do not build a carousel.

### 2 — Setup & permissions

Goal: build trust and request permissions just in time.

- Step indicator: Permission → Find → Confirm. If the owner chooses a brand first, retain that as a scan filter rather than a binding decision.
- Default to **Scan for glasses**, with HeyCyan-compatible as the primary supported path.
- Provide **Choose by brand** for HeyCyan-compatible, Eyevue, Meta Ray-Ban, Meizu MYVU / Star Air, Generic audio glasses, or I’m not sure.
- Show Primary, Experimental, Partial, or Limited labels with a one-line explanation. Keep Research devices and compatibility runtimes in Labs rather than this first-run list.
- Permission rows explain Nearby devices, Notifications, Microphone, and Photos/Media.
- Accessibility and battery-optimization exceptions are optional and requested only for a feature that needs them.
- Each permission shows Not requested, Allowed, or Needs attention.
- Request Nearby devices for discovery first. Ask for microphone, media, notifications, accessibility, or battery exceptions later when the detected device and chosen feature require them.

Frames: device selection, permission rationale, denied/recovery.

### 3 — Pair glasses

Goal: make BLE discovery understandable and recoverable.

- Calm scanning animation with a text label; no fake radar.
- Device rows show name, inferred family, signal strength in words, and previous pairing if known.
- Selecting a device opens a confirmation sheet with detected family, support maturity, available capabilities, and manual **Change type** before binding.
- Unknown devices stay in a safe generic mode until a compatible adapter is deliberately selected.
- Meta Ray-Ban branches into its provider/registration setup when Meta DAT is available; it must not be sent through the HeyCyan connector.
- Explain that BLE handles control and that Wi-Fi may be used later for media sync.
- Empty state gives retry, Bluetooth settings, and troubleshooting.

Frames: scanning, results, connecting, paired, and no devices/error.

### 4 — Home

Goal: make the connected glasses feel alive without becoming a control panel.

Recommended hierarchy:

```text
┌──────────────────────────────────┐
│ [mark]                  Settings │
│                                  │
│       Connected · My glasses     │
│                                  │
│      transparent glasses         │
│       floating hero render       │
│                                  │
│       Battery 82% · Storage 61%  │
│ [active transfer/recording banner]│
│                                  │
│ Quick actions                    │
│ [ Ask ] [ Capture ] [ Sync ] [●] │
│                                  │
│ Today                            │
│ recent photo / note / summary    │
│                                  │
│ Ready automations                │
│ one contextual shortcut          │
└─ Home ─ Assistant ─ Library ─ Auto┘
```

Device Stage rules:

- Use the approved transparent glasses render inside a 200–240dp open hero field whose background is exactly the page background. No card edge or image rectangle may be visible.
- Build the shadow, optional ambient halo, state animation, status, and metrics as editable UI layers; do not bake them into the image.
- Connected state: one-time editable ambient-halo confirmation and a clear Connected pill; the source image itself does not change.
- Disconnected state: remove the active halo, keep the source image unchanged, and show the primary **Connect glasses** action.
- Connecting state: retain the same composition, use an indeterminate progress line and “Connecting…”, suppress unsupported quick actions, and reveal cancellation/slow-connection recovery at the appropriate time.
- Reconnecting state: preserve the known device name and last-seen information while marking battery/storage values stale until refreshed.
- Syncing state: progress belongs in the activity banner; a restrained arc may appear behind the product but never replaces numeric progress.
- Recording state: add an amber recording pill and timer; do not turn the product red.
- Errors appear as a message and recovery action below the stage.
- Device name, state, battery, and storage remain real text, not baked into artwork.

Quick actions are Ask, Capture, Sync, and Record. Unsupported actions are omitted or replaced with a device-appropriate action; do not show a grid of disabled buttons.

“Today” shows at most three meaningful recent outputs. “Ready automations” shows one contextual automation and a link to all.

Frames: not set up, scanning/connecting, connected/idle, reconnecting, disconnected, active recording, syncing, exclusive firmware session, and recoverable connection error.

### 5 — Assistant

Goal: start an AI interaction faster than opening a chat list.

- A prominent multimodal composer: “Ask about anything…” with voice, camera, attachment, and send.
- Four modes: **Voice**, **What I see**, **Live**, and **Text**.
- A small processing boundary above the composer: On device or Your cloud · model name.
- Recent conversations appear below, searchable and grouped by date.
- Suggestions are contextual and short; do not fill the page with prompt templates.
- Unconfigured cloud state explains the choice and links to AI Services without blocking on-device options.

Frames: ready/local, ready/your-cloud, listening or live, and cloud unavailable.

### 6 — Conversation & live session

Goal: provide a focused, multimodal AI exchange.

- Standard user and assistant messages with readable widths, selectable text, timestamps only when useful, and image/audio attachments.
- Composer supports capture from glasses when available, phone camera, audio, and files.
- Model/privacy chip is visible but compact.
- Streaming state includes Stop; failed responses include Retry and diagnostic detail in a sheet.
- Live mode becomes a focused full-screen state with listening/speaking waveform, transcript preview, mute, camera-context toggle, and End.
- Save-to-Library is explicit for important responses.

Frames: populated conversation, generating, recoverable error, and live session.

### 7 — Library

Goal: make everything captured or created easy to find in one place.

- Search with filter chips: All, Photos, Videos, Audio, Notes, and Memories.
- Toggle between Timeline and Collections; do not build separate top-level pages for every media type.
- Timeline groups content by day with clear source badges: Glasses, Phone, Meeting, Automation, or Imported.
- Collections include Recordings, Meeting notes, Daily summaries, Visual diary, and Saved AI answers.
- Show the transfer activity banner during sync.
- Multi-select enables share, export, and delete; destructive actions require confirmation.

Frames: populated timeline, empty library, filtered collection, and syncing.

### 8 — Content detail

Goal: use one adaptable detail pattern for media and knowledge artifacts.

- Photo/video: edge-to-edge preview, metadata, Ask about this, save/share/delete.
- Audio: waveform, play controls, duration, source, transcription state, Transcribe action.
- Note/transcript/summary: clean reading view, source, created date, linked recording/media, rename, export, and delete.
- Transcript may show Moonshine or Gemma as the engine in metadata, not as a large brand panel.
- Memory item: why it was stored, eligible sources, edit/delete, and privacy mode.

Frames: visual media, audio/transcription, and note/transcript.

### 9 — Automations

Goal: turn the existing plugin list into a coherent capability catalog.

- First section: **Active now**, only when something is running.
- Group built-ins by outcome:
  - Personal AI: Local Agent.
  - Meetings & communication: Meeting Spark Notes, Live Caption Relay, Hands-Free Translator.
  - Capture & memory: Auto Diary, Auto Audio, Visual Diary.
  - Productivity: Errand Brain.
- Each card shows name, one-sentence outcome, availability, active state, and settings action.
- A first enable opens Automation detail for permissions and data review; later toggles may be direct.
- Device-incompatible automations remain discoverable with a clear explanation.
- Community catalog and Publish Plugin remain secondary sections, without prizes or monetization.
- Walking Aid does not appear anywhere.

Frames: catalog, active automation, and incompatible-device state.

### 10 — Automation detail

Goal: use a consistent setup pattern for all eight built-ins and community plugins.

- Header: icon, name, one-sentence result, On/Off state.
- Compatibility panel for the selected device.
- “How it works” in three concise steps.
- Inputs and outputs: microphone, camera, screen context, notifications, memory, cloud, etc.
- Privacy boundary: On device, Your cloud, or mixed.
- Permission list and configuration controls.
- Test action where practical.
- Review & enable action.
- Activity/history tab for automation-specific output.

Local Agent may link within this template to pending actions, app blacklist, daily facts, screen captures, and daily summary. These share list/detail components rather than introducing new visual languages.

### 11 — Device Center

Goal: centralize hardware controls without crowding Home.

- Device identity, family, connection transport, battery, storage, firmware version, and last seen.
- A device switcher opens **Devices**, where the owner can view the active device, add glasses, choose by brand, and see honest support maturity. Only one device is active.
- Everyday controls: disconnect/reconnect, request battery, sync time, volume, wearing detection, video duration, and audio duration where supported.
- Family-specific modules:
  - HeyCyan-compatible capture and storage controls.
  - Eyevue experimental controls, clearly labeled until physical validation.
  - Meta Ray-Ban selection and optional DAT registration. Session, stream, capture, and display remain visible only as **Not implemented** capability information, never enabled actions.
  - Meizu MYVU experimental connect, notification, teleprompter, clock, and comfort brightness; no camera or media-sync actions.
  - Generic audio controls.
- Unsupported modules are absent; do not expose every family at once.
- MemoMind/XGIMI research and EvenHub/Mentra runtimes live under Labs/Advanced, not the consumer device switcher.
- Advanced link at the bottom.

Frames: HeyCyan-compatible primary, Eyevue experimental, Meizu experimental, Meta partial-setup, and generic/unknown examples.

### 12 — HeyCyan Firmware Update

Goal: provide a deliberate, recoverable-looking interface for the existing two-component HeyCyan OTA research without implying that flashing personal hardware is proven safe.

Entry point: **Device Center → HeyCyan-compatible device → Firmware**. Show the row only for the active HeyCyan profile. It displays installed Wi-Fi-chip and Bluetooth-chip firmware versions, last check, and **Firmware lab** status. Firmware update never appears as a Home quick action, notification promotion, or control for other brands.

Product status: **Experimental / lab-only until physically validated**. The upstream code and official-app research define a combined Wi-Fi-then-BLE flow, but this is not proof that an arbitrary package is compatible or recoverable on the owner's glasses.

Normal update unit:

- Treat the Wi-Fi/V821 `.swu` and Bluetooth/JieLi `.bin` as one compatible pair.
- Do not offer a normal chip selector or BLE-first path.
- Stage and validate both components before sending any OTA command.
- Wi-Fi update runs first; BLE update begins only after Wi-Fi completion, transport cleanup, reconnection, and a fresh readiness check.
- After BLE DFU, reconnect and read fresh versions before reporting success.

Source choices:

1. **Approved update from Your firmware service** — preferred future path using the owner's configured HTTPS relay/catalog. It must return both exact-base artifacts with expected target, current-version baseline, byte size, and SHA-256. Do not call it the author's server, Stealth, Pro, or an entitlement.
2. **Local recovery package** — advanced lab path that selects the Wi-Fi `.swu` followed by the companion BLE `.bin`. File extension alone is not compatibility proof. Require explicit acknowledgement and keep this unavailable outside the lab/debug policy until safeguards are implemented.
3. **Debug channel** — developer-build only, available only if the owner's relay explicitly supports it. Do not show it in ordinary release UI.

Page structure:

- Header: Firmware update; HeyCyan-only and Experimental labels.
- Connected-device identity with model/profile and sanitized identifier.
- Installed versions card with separate Wi-Fi chip and Bluetooth chip rows.
- Update source card and **Check compatibility** action.
- Preflight checklist: correct active HeyCyan device, stable BLE, complete current version identifiers, both compatible artifacts present, integrity metadata verified where available, sufficient device/phone power according to a hardware-tested policy, required Bluetooth/Wi-Fi permissions, and no recording/sync/preview/ADB/automation session active.
- Compatibility result with before/target versions and both artifact identities. The primary update action remains disabled until every enforceable check passes.
- Risk acknowledgement that interruption or incompatible firmware may make the glasses unusable.
- Final hold-to-confirm or two-step confirmation showing the exact device and both target components.

Progress uses one vertical six-stage timeline:

1. Preparing and reading current versions.
2. Validating and staging both artifacts.
3. Updating Wi-Fi chip over the local glasses connection.
4. Restoring Bluetooth and rechecking the device.
5. Updating Bluetooth chip.
6. Reconnecting and verifying installed versions.

Show one overall state plus component-specific progress and plain-language detail. Map internal states such as entering OTA mode, starting local Wi-Fi, waiting for the glasses address, starting the phone HTTP server, transferring, tearing down Wi-Fi, waiting for fresh BLE, BLE DFU, and verification into these six understandable stages. Technical detail remains expandable.

Cancellation and interruption rules:

- Before flashing starts: **Cancel update** is available and removes staged temporary files.
- During a stage where cancellation is implemented and hardware-safe: show **Cancel safely** with confirmation.
- During a non-interruptible critical stage: replace Cancel with “Do not turn off or move away from the glasses”; never offer a button that cannot be honored safely.
- OTA owns the exclusive glasses session. Home shows a Firmware update Activity Banner and blocks capture, sync, preview, ADB, and conflicting background automation controls.
- Design a foreground notification so Android process/background transitions do not hide the active risk.

Required result and recovery frames:

- No approved compatible pair: no component flashed; use local export or **Request compatible package** through the owner's service after consent.
- Preparation/download/integrity failure: no component flashed; retry or change source.
- Wi-Fi stage failure: BLE stage not started; show recovery and diagnostic export.
- Wi-Fi completed but BLE stage failed: explicit **Partial update** state, current known versions, reconnect/recovery actions, and no generic success message.
- Verification unavailable: “Update process finished, but versions could not be verified”; reconnect and verify again.
- Complete: before/after version rows, both components verified, timestamp, Done.
- Device disconnected or Android interrupted: resume/recover based on persisted OTA state; never reset visually to Idle without checking the device.

Patch/request behavior belongs to the owner's infrastructure. If the owner relay is not configured, offer **Export compatibility request** locally instead of sending diagnostics to the original author or any third party. Any submitted bundle requires a preview and explicit consent and is redacted by default.

Frames: overview/no check, checking, compatible package ready, incompatible/unavailable, preflight blocked, confirmation, each major progress stage, safe cancellation, Wi-Fi failure, partial update, verification pending, and verified completion.

### 13 — Sync Center

Goal: explain the BLE-to-Wi-Fi handoff and recover cleanly.

- Stepper: Prepare glasses → Establish Wi-Fi → Read media list → Transfer → Save to Library.
- Counts for photos, videos, and audio; current filename only in detail.
- Determinate progress when known and indeterminate progress otherwise.
- Cancel is always reachable and explains what is already saved.
- Recovery messages distinguish Bluetooth disconnected, Wi-Fi unavailable, glasses IP unavailable, list unavailable, file failed, and storage permission/space problems.
- Default to one recommended flow. Put alternate strict/custom protocol selection in Advanced because it is implementation detail.
- Do not imply internet access is required; the transfer is local between glasses and phone.

Frames: ready, transferring, complete, and recoverable failure.

### 14 — Settings

Goal: expose owner-level configuration without becoming a wall of expandable cards.

- Top summary rows: selected device and AI Services state.
- Sections:
  - General: Language and Notifications.
  - Intelligence: AI Services & Models, image-question defaults, automation provider.
  - Privacy: Memory & Privacy, Transcripts, permissions.
  - Data: export, import, storage usage, clear local data.
  - Support: diagnostics, FAQ, about/version.
- There is no Appearance or theme-choice row; the app has one deliberate light appearance.
- No account, billing, subscription, donation, or author-server section.

### 15 — AI Services & Models

Goal: configure on-device AI and the owner’s cloud without assuming a vendor backend.

- Two provider cards: **On device** and **Your cloud**.
- On-device detail handles model availability, download/configuration state, storage, and test.
- Your cloud fields: relay base URL, optional API token with reveal/hide, optional email, and separate Chat/Requests, Image/Questions, and Automation/Tasks models defaulting to auto.
- Explain that the token is encrypted at rest on the phone and never put an example secret in designs.
- Save and Save & test connection.
- Test states: not configured, invalid URL, testing, connected with available models, authentication failed, server unreachable, and valid relay with no models.
- State: “No subscription or author account is required.”

### 16 — Privacy & Data

Goal: make local/cloud boundaries and stored data inspectable.

- Current memory mode and plain-language data path.
- Memory modes: Private Local, Encrypted Sync, Fast Cloud Memory, and Confidential Cloud Beta. Backend-dependent modes say Backend not configured when unavailable; they are not premium locks.
- Memory source eligibility for explicit facts, daily facts, screen OCR, derived summaries, imported text, and system notes.
- OCR retention, name redaction, transcript storage, vault lock/passphrase, export/import, and clear local data.
- A data inventory summarizes media, transcripts, notes, memory, model files, and logs.

### 17 — Advanced & Diagnostics

Goal: retain expert functionality without letting it define the app.

- Clearly labeled owner/developer area with warning copy.
- Logs and transcription diagnostics.
- Live preview.
- Firmware lab diagnostics, OTA logs, pull-mode tests, raw version dumps, and debug-only controls linked from the dedicated HeyCyan Firmware Update page; normal update progress and recovery stay on that page.
- Wi-Fi ADB debug with explicit security warning.
- External image automation diagnostics, device listeners, classic Bluetooth scan, firmware/version dumps, and raw connection information.
- External runtimes: EvenHub-compatible WebView URL/load/stop/log controls; Mentra-compatible local relay state and sessions; Terminal HUD; active GlassesBridge adapter; notification forwarding permissions and filters.
- Risky actions require confirmation and never share the visual prominence of daily actions.

## 8. Reusable components

Stitch and implementation should name and reuse these components:

- `AdTopBar`
- `AdBottomNavigation`
- `DeviceStage`
- `DeviceResultRow`
- `SupportMaturityLabel`
- `DeviceCapabilitySummary`
- `ConnectionStatusPill`
- `DeviceMetricTile`
- `QuickActionButton`
- `ActivityBanner`
- `PrivacyBoundary`
- `AutomationCard`
- `CompatibilityPanel`
- `PermissionRow`
- `LibraryItem`
- `TranscriptBlock`
- `AiComposer`
- `TransferStepper`
- `FirmwareVersionRow`
- `FirmwarePreflightCheck`
- `FirmwareStageTimeline`
- `InlineStateMessage`
- `EmptyState`
- `ConfirmationSheet`

Use standard Material 3 behavior beneath the custom styling so the design remains practical in Jetpack Compose and Compose Multiplatform.

## 9. Critical journeys to prototype

### Pair and reach Home

Welcome → nearby-device rationale → scan or choose brand → confirm detected type/capabilities → connecting → connected Home.

Validate denial recovery, no results, wrong family, and explore-without-pairing.

### Ask what the glasses see

Home: Ask or Capture → Assistant in What I see mode → capture/thumbnail → On device or Your cloud boundary → answer → optionally save to Library.

Validate no camera support, not connected, cloud not configured, capture failed, and model error.

### Sync media

Home or Library → Sync Center → BLE preparation → local Wi-Fi handoff → list → transfer → saved to Library.

Validate cancellation and each recoverable failure without inventing protocol behavior.

### Update HeyCyan firmware in lab mode

Device Center → Firmware → read current versions → choose owner-service or local paired artifacts → compatibility check → preflight → confirm exact device/pair → Wi-Fi stage → fresh BLE readiness → BLE stage → reconnect → verify both versions.

Prototype blocked preflight, no compatible pair, cancellation before flashing, Wi-Fi failure, partial update after Wi-Fi success/BLE failure, verification pending, and verified completion. This is a safety prototype, not authorization to flash a personal device.

### Start an automation

Automations → automation detail → compatibility → permissions/data review → configure → test → enable → active banner → output in Library.

### Configure owner cloud

Assistant or Settings → AI Services → Your cloud → enter URL/token/models → test → ready → return to original task.

## 10. Accessibility and resilience

- Minimum 48dp targets and 8dp between independent controls.
- Meet WCAG AA contrast for text and meaningful icons throughout the light interface.
- Support 200 percent font scaling and long translated strings.
- Provide TalkBack labels, sensible focus order, headings, and state announcements.
- Pair state colors with icon and text.
- Preserve drafts when leaving forms or when Android recreates the activity.
- Loading should not cause major layout jumps.
- Empty, loading, offline, permission-denied, unavailable, partial-success, and recoverable-error states are required components, not afterthoughts.

## 11. Deliberate exclusions

- Walking Aid and all related hazard or mobility features.
- Subscriptions, billing, trials, donations, quotas, premium tiers, and upgrade prompts.
- Accent profiles, custom palettes, wallpapers, animated themes, and decorative chat skins.
- Original AD Glasses name, logo, author identity, or author-hosted AI assumptions.
- A social feed, gamification, streaks, badges, or engagement notifications.
- Fake health metrics, fake AR overlays, and device capabilities not supported by the selected hardware.

## 12. Product and design release plan

### Foundation — approve before UI implementation

- Product architecture and 17 page templates.
- Logo direction and adaptive-icon system.
- Light-only tokens and component inventory.
- Selected Product Stage Home direction and its critical states.
- Six clickable critical journeys, including the HeyCyan firmware safety flow.

### Release 1 — complete owner app

- Four-destination shell and redesigned onboarding.
- HeyCyan is the complete primary hardware path and first physical-device acceptance target.
- All implemented owner functionality except Walking Aid is mapped into the new experience; experimental, partial, and research integrations retain honest labels and disabled/unavailable actions where necessary.
- Capability-aware Device Center.
- Unified Assistant, Library, and Automations.
- Owner cloud and on-device model configuration.
- Contextual privacy boundaries, global activity banner, and passive-automation pause.
- Safe Sync Center with accurate partial-success handling.
- Dedicated HeyCyan Firmware Update lab flow using only local packages or the owner's future firmware service, with no claim of safety until physical recovery testing passes.
- Advanced/diagnostics retained behind deliberate separation.
- One light appearance only.

### Release 1 quality gate

- Existing BLE and Wi-Fi regression suite plus emulator UI tests.
- Physical-device validation for each device family available to the owner.
- Firmware update remains disabled outside the lab policy until compatible-pair validation, interruption recovery, persisted OTA state, and post-update verification are proven on recoverable HeyCyan hardware.
- Connection interruption, permission denial, Android process recreation, low storage, no network, and provider outage testing.
- Accessibility validation at large fonts and with TalkBack.
- Secret/log redaction review.
- No monetization or Walking Aid references in source, resources, navigation, or generated assets.

### Release 1.1 and later

- Multiple remembered-device switching.
- Promote Eyevue, Meizu, Meta, MemoMind/XGIMI, or a runtime-backed device from experimental/partial/research only after its adapter, capability matrix, error recovery, and physical-device tests pass.
- Quick Settings tile and optional launcher shortcuts.
- Local semantic search.
- Private automation builder.
- Encrypted owner-backend sync when implemented.
- Tablet/foldable layouts.

## 13. Implementation architecture implied by the product

The visual rewrite should not attach new screens directly to the current large activity. Migrate incrementally toward:

```text
Compose screens and reusable components
        ↓
ViewModels / immutable UI state / user intents
        ↓
Use cases: connect, capture, sync, ask, transcribe, automate
        ↓
Repositories and capability registry
        ↓
Existing vendor SDK, BLE, Wi-Fi, media, AI, and secure-storage adapters
```

Rules:

- Preserve proven protocol code behind interfaces; do not rewrite it from Stitch or AI Studio output.
- One state machine owns each long-running hardware session.
- UI state uses product language; raw SDK/network errors are mapped to stable error categories with optional detail.
- Device support is driven by capabilities rather than scattered device-name checks.
- Long-running recording/sync/automation work uses appropriate Android services and recoverable state.
- Credentials remain encrypted and redacted from logs, exports, screenshots, and crash reports.
- Generated mock networking and web storage are discarded.
- Each migrated flow must be testable before the old UI entry point is removed.

## 14. Design acceptance gate

Do not implement the complete visual rewrite until the following are accepted from Stitch:

1. One shared light-only design system.
2. Two refinements of the Product Stage direction plus one compact Balanced Utility fallback. Do not recreate the rejected typography-led Direction C.
3. A selected Home direction with not-set-up, scanning, connecting, connected, reconnecting, disconnected, recording, syncing, firmware-active, and recoverable-error states.
4. The four-tab navigation shell and one representative detail screen.
5. The pairing, ask-what-I-see, sync, HeyCyan firmware safety, automation enable, and cloud setup prototypes.
6. Reusable components and assets with editable text and layers.
7. A consistency and accessibility audit.

The staged prompts are in `AD_GLASSES_STITCH_HANDOFF.md`. The machine-readable visual identity is in `design/AD_GLASSES_DESIGN.md`.

## 15. Existing-functionality coverage map

This map prevents the visual redesign from accidentally deleting a capability merely because its old Activity or screen disappears.

| Existing surface/capability | Redesigned home |
|---|---|
| Welcome, feature onboarding, battery optimization | Welcome; Setup & permissions |
| Device bind and scan | Pair glasses |
| Current glasses dashboard | Home; Device Center; HeyCyan Firmware Update; Sync Center; Advanced |
| Chat list and thread | Assistant; Conversation & live session |
| Gemini Live | Conversation & live session |
| Recordings and synced media gallery | Library; Content detail |
| Notes list/detail, daily facts, daily summaries | Library; Content detail |
| Screen captures | Library collection; Local Agent automation detail |
| Community plugins | Automations secondary Community section |
| Publish plugin | Automations secondary Publish flow using the same form components |
| Local Agent settings and pending actions/app blacklist | Automation detail; linked management lists |
| Meeting Spark Notes settings | Automation detail; recording Activity Banner; Library output |
| Live Caption Relay settings | Automation detail; live Activity Banner; Library transcript output |
| Hands-Free Translator settings | Automation detail; live Activity Banner; optional Library output |
| Errand Brain settings | Automation detail; Library/notification outputs |
| Auto Diary settings | Automation detail; Library Daily summaries collection |
| Auto Audio settings | Automation detail; Library Recordings collection |
| Visual Diary settings | Automation detail; Library Visual diary collection |
| Settings expandable cards | Settings; AI Services; Privacy & Data; Advanced |
| Existing appearance settings | Removed from redesigned navigation; AD Glasses uses one light appearance |
| Cloud settings | AI Services & Models → Your cloud |
| Local model configuration | AI Services & Models → On device |
| External assistant/external automation setup | AI Services & Models → External assistant (advanced) |
| Transcription debug | Advanced & Diagnostics |
| HeyCyan combined OTA and firmware compatibility/patch request | Dedicated HeyCyan Firmware Update page; raw probes/logs remain in Advanced |
| Live preview and Wi-Fi ADB | Advanced & Diagnostics, linked from Device Center |
| Meta Ray-Ban selection/registration plus session/stream/capture/display placeholders | Device Center partial-setup module; unimplemented capabilities remain non-interactive and explicitly labeled |
| Meizu MYVU notification/teleprompter/clock/brightness | Device Center family module |
| MemoMind/XGIMI transport and protocol research | Labs/Advanced research entry; no consumer pairing promise until a complete adapter is registered and physically validated |
| EvenHub-compatible runtime | Advanced → External runtimes |
| Mentra-compatible local relay | Advanced → External runtimes |
| Terminal HUD and display bridge | Advanced → External runtimes; active display status in Activity Banner |
| Notification forwarding | Device Center or Automation detail with app filters and explicit permission |
| Log collection, export/import, clear data, vault | Privacy & Data; Advanced & Diagnostics |

Walking Aid is intentionally the sole feature family omitted. Monetization surfaces are also intentionally omitted because they are not product functionality for this owner-controlled app.

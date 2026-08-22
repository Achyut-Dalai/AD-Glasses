# AD Glasses canonical UI specification

## 1. The simple mental model

AD Glasses has four everyday destinations and one shared activity model.

| Destination | The question it answers |
|---|---|
| Home | Are my glasses usable, what is active, and what can I do now? |
| Assistant | What can I ask, understand, search, recall, or safely act on? |
| Library | Where are my captures, recordings, transcripts, notes, and memories? |
| Automations | Which useful background or hands-free workflows can I configure? |

Setup, pairing, details, Sync, Settings, firmware, and diagnostics are opened only when needed. They are not additional bottom tabs.

A connecting Home, connected Home, recording Home, and syncing Home are one Home screen driven by state. A photo detail and meeting-summary detail are one reusable Content Detail family. Eight automation setup screens use one reusable Automation Detail family.

## 2. Product contract

AD Glasses is an owner-controlled Android companion for compatible smart glasses. The glasses may provide camera, microphone/audio, BLE control, local Wi-Fi media transfer, battery/storage data, and device-specific display behavior. The phone supplies AI, live voice, current-information search, personal recall, media storage, transcripts, summaries, automations, cloud configuration, and safe Android actions.

Preserve every useful owner feature from the current app except Walking Aid. Do not preserve the current screen structure simply because a feature previously had its own Activity.

Non-negotiable rules:

- no Walking Aid, hazard detection, route safety, or mobility-assistance flow;
- no AD Glasses subscription, premium tier, billing, checkout, donation, trial, quota, rewards, or upgrade screen;
- no AD Glasses branding or dependency on the original author's server;
- no dark mode, theme picker, accent profiles, wallpaper, or chat-skin system;
- no invented hardware controls, protocol commands, device facts, firmware claims, metadata, encryption, or authentication;
- third-party AI services may have their own API limits or costs, but that is disclosed as external provider usage—not an AD Glasses plan;
- one active hardware session at a time, with room for remembered devices later;
- unsupported controls are omitted or explained contextually rather than shown as a large disabled dashboard.

## 3. Screen families, not dozens of unrelated pages

The complete UI uses these 12 screen families:

| # | Family | Includes |
|---:|---|---|
| 1 | Onboarding and Devices | Welcome, readiness, brand choice, scan, confirmation, connect result |
| 2 | Home | Device stage, activity, quick actions, recent outcomes |
| 3 | Assistant | Hub, conversation, visual question, grounded answer, live session, phone-action approval |
| 4 | Library | Timeline, collections, search/filter, multi-select, adaptable content detail |
| 5 | Automations | Catalog, eight built-in detail variants, community browse/detail/publish |
| 6 | Device Center | Capability-aware controls and device-specific modules |
| 7 | Sync | Local Wi-Fi media import and recovery |
| 8 | Settings | Short index linking to focused configuration |
| 9 | AI Services | On-device model, owner cloud, web grounding, routing and tests |
| 10 | Privacy and Data | Memory, inventory, retention, vault, export/import/delete |
| 11 | HeyCyan Firmware Lab | Compatibility, preflight, paired update, progress and recovery |
| 12 | Advanced and Diagnostics | Logs, device labs, action permissions and prototype runtimes |

Only Home, Assistant, Library, and Automations appear in bottom navigation. Focused flows may hide it.

## 4. Shared shell and components

Use one native Android component system:

- compact Home top bar with one approved AD Glasses lockup and Settings;
- ordinary destination top bar with page title and restrained actions;
- detail top bar with Back, title, and optional overflow;
- four-item labeled bottom navigation;
- Device Stage;
- connection status and optional fresh metrics;
- Quick Action;
- global Activity Banner;
- multimodal AI composer;
- processing/privacy boundary;
- source and citation row;
- Library item and source badge;
- Automation card and compatibility panel;
- permission/readiness row;
- transfer stepper;
- firmware preflight and stage timeline;
- inline empty/error/recovery state;
- capture/record choice sheet;
- phone-action proposal sheet;
- destructive confirmation dialog;
- focused live-session controls.

The Activity Banner is the single cross-app representation of recording, sync, live translation, captions, Visual Diary, live AI, firmware, or automation attention. Show the highest-priority activity and an additional-count indicator if compatible tasks coexist. Tap opens the owning flow. Firmware and recording have priority.

## 5. Family requirements

### 5.1 Onboarding and Devices

WELCOME

- approved symbol/wordmark and the same true-transparent glasses render used on Home;
- “Your glasses. Your AI. Your data.”;
- two short lines about phone-powered AI and owner-configured services;
- Set up my glasses;
- Explore without pairing;
- Supported devices and Privacy links;
- no account, payment, carousel, testimonials, or app-level terms acceptance.

READINESS

- Prepare → Find → Confirm progress;
- Nearby devices/Bluetooth as the initial connection requirement;
- just-in-time explanation for phone camera, microphone, notifications, media, Accessibility, location where the Android version requires it, and battery exceptions;
- initial, denied/recovery, and ready states;
- never request every permission at once.

DEVICES AND PAIRING

- active and remembered devices where applicable;
- Add glasses and Choose by brand;
- brand rows: HeyCyan Primary, Eyevue Experimental, Meta Experimental, Meizu Experimental, Generic audio Limited, and I’m not sure;
- MemoMind Research and EvenHub/Mentra Prototype live behind Advanced/Labs;
- scanning with Stop, results in place, honest signal language, safe unknown device;
- confirmation sheet with sanitized identifier, inferred family, maturity, runtime capability preview, privacy note, Connect and Cancel;
- Meta uses its DAT/provider registration path, not HeyCyan binding;
- progress: Preparing → Connecting → Reading capabilities;
- success, no devices, delayed connection, and recoverable failure.

Do not invent pairing codes, LED instructions, radar visuals, or silent automatic binding.

### 5.2 Home

The first viewport contains:

1. compact brand lockup and Settings;
2. one open Device Stage;
3. Activity Banner only when active;
4. at most four quick actions.

DEVICE STAGE

- use one true-transparent glasses source asset with page-matched background and separate shadow/halo;
- device name and state are live text;
- battery/storage appear only if supported and fresh;
- tap opens Device Center.

QUICK ACTIONS

- Ask → Assistant with active-glasses context;
- Capture → supported choices only: Ask what I see, Take photo, Start video;
- Sync → Sync;
- Record → Meeting recording with phone/Bluetooth source and HeyCyan onboard audio only when supported.

Below the first viewport show at most three recent meaningful outcomes and one contextually ready automation. No charts, streaks, promotions, decorative statistics, or storage graphs.

Prototype states: not set up, connecting, connected idle, reconnecting, disconnected/recoverable, recording, translating, syncing, and exclusive firmware session. These are states of one layout.

### 5.3 Assistant

ASSISTANT HUB

- composer-first layout with text, voice, phone/glasses image, attachment, and send;
- concise choices: Voice, What I see, Live, Text;
- Automatic routing by default;
- contextual processing boundary: On device, Your cloud, or Automatic;
- Web search is a composer tool, never a fifth tab or Google Mode;
- no more than three contextual suggestions;
- searchable recent conversations and New conversation;
- local-only fallback when cloud/search is unavailable;
- on compatible adapters, hardware AI-button/wake behavior may be Voice question or What I see.

CONVERSATION

- readable multimodal timeline, selectable text, attachments, streaming/Stop, Retry and details;
- Save to Library on useful answers;
- web-grounded answers show “Searched the web,” inline citations, source title/domain/open, follow-up, read aloud and save;
- visual lookup shows the captured source and cautious identification, prioritizing official/manual sources;
- personal recall clearly says Your Library and links to original items;
- live session shows listening/speaking, accessible waveform, transcript, mute, optional supported camera context, reconnect and End.

PHONE ACTION APPROVAL

- show app/target, exact proposed action, data used, and impact;
- Confirm, Edit request, Cancel;
- consequential actions always require explicit confirmation;
- never claim completion before Android returns an actual result;
- show success, partial, rejected, and failed outcomes honestly.

### 5.4 Library

LIBRARY

- one destination for Photos, Videos, Audio, Notes and Memories;
- search, filters, Timeline/Collections, day groups, source badges and processing state;
- collections: Recordings, Meeting notes, Daily summaries, Visual diary, Saved AI answers;
- active Sync banner;
- multi-select Share, Export, Add to collection and confirmed Delete;
- empty state with Capture, Sync and Create note.

CONTENT DETAIL

Use one adaptable detail family:

- Photo: preview, Ask, actual metadata, linked OCR/answer/note, share/collect/delete;
- Video: player, actual metadata, transcript/summary generation, Ask and linked note;
- Audio: playback, source/input, transcription state, transcript/summary links;
- Note/transcript/meeting: reading view, summary/decisions/actions when present, original media link, rename/export/ask/delete;
- Memory/saved answer: content, reason stored, source links, memory mode, edit/delete; grounded answers retain citation treatment.

Never invent EXIF, location, “authenticated,” “original unedited,” 3D meshes, or provenance.

### 5.5 Automations

CATALOG

- global Pause passive automations;
- Active now only when needed;
- all eight built-ins, grouped by outcome;
- readiness, processing boundary, device compatibility and Setup/Manage;
- first enable always opens review; later direct toggle is allowed;
- Community is secondary.

The eight built-ins are:

1. Local Agent
2. Meeting Spark Notes
3. Live Caption Relay
4. Hands-Free Translator
5. Errand Brain
6. Auto Diary
7. Auto Audio
8. Visual Diary

SHARED DETAIL TEMPLATE

- result, status, compatibility and On/Off;
- three-step How it works;
- inputs/outputs and processing boundary;
- required/optional permissions;
- configuration, safe Test, Review and enable;
- activity/history, Stop and Disable.

Specific controls:

- Local Agent: Accessibility/notification readiness, approval policy, app privacy list, pending actions, task history, screen captures, daily facts/summary, read-only test;
- Meeting Spark Notes: phone/Bluetooth input, language/provider, live transcript, summary sections, recording/transcript retention, test;
- Live Caption Relay: input/language, phone captions, compatible glasses display, text behavior, transcript retention, test;
- Hands-Free Translator: source/target language, input, spoken/compatible display output, retention, test phrase;
- Errand Brain: input/language, extracted list and reminder destination, confirm item/time/destination, notification/memory boundary, preview test;
- Auto Diary: screen context, Accessibility, interval/pause, app privacy list, OCR/name redaction, retention, facts review and safe no-store preview;
- Auto Audio: HeyCyan onboard-audio requirement, schedule/duration/power/storage/pause, sync/transcription/retention, visible test recording and unsupported explanation;
- Visual Diary: camera source compatibility, interval/pause, local/cloud analysis, retention and capture test.

COMMUNITY

- browse/search using real category, capability, compatibility, source, version, permissions and data access;
- detail reuses Automation Detail with source/trust/version, Disable and Report;
- publish form includes outcome, description, source/package, version, compatibility, manifest and processing boundary;
- use the owner's configured community service or local import only; never silently contact the original author's service.

### 5.6 Device Center

- active identity, Change device, family, maturity, transport/state, last seen;
- fresh battery/storage and version only where supported;
- reconnect/disconnect and refresh;
- capability-gated photo, video, onboard audio, Sync, battery/storage request, wearing detection, duration, volume, time sync and compatible AI activation route;
- grouped capture/recording settings;
- HeyCyan: capture, onboard media, Sync, recording settings, Firmware Lab;
- Eyevue Experimental: only runtime-implemented connection/status/capture/media/live controls;
- Meta Experimental: DAT registration/availability/session/stream/photo/display only when runtime reports them;
- Meizu Experimental: connect, notifications, teleprompter, clock, comfort brightness, display status;
- Generic audio: audio route/status only;
- compatible live preview, Advanced, and confirmed Forget device.

### 5.7 Sync

Stages:

1. Prepare glasses
2. Establish local Wi-Fi
3. Read media list
4. Transfer
5. Save to Library

Show active device, local-transfer explanation, available counts, storage readiness, progress when known, completed/total items and bytes, media counts, expandable current file, measured speed only, and reachable Cancel.

Completion distinguishes imported, duplicates and failed items. Already saved items remain. Do not delete from glasses automatically. Recovery covers BLE loss, Wi-Fi/IP/list failure, individual file failure, phone storage/permission and partial success. Never promise byte-range resume unless implemented; “Continue remaining files” is allowed when completed files are preserved.

### 5.8 Settings

Keep this a short index:

- active device summary → Device Center;
- AI readiness → AI Services;
- General: language, notifications, permissions;
- Intelligence: services/models, routing defaults, image defaults, automation provider;
- Privacy: Privacy and Data, transcript storage, passive pause;
- Data: storage, export, import, clear;
- Support: Advanced, Help, About/version;
- Prototype controls appear only in the prototype/debug build.

No Appearance, account, billing, promotion, engagement, theme, or author-server rows.

### 5.9 AI Services

OVERVIEW

- Automatic routing and Test;
- On device, Your cloud and Web grounding provider cards;
- Ready, Needs setup, Testing, Offline, Authentication failed, Model unavailable, Unsupported;
- defaults for Chat, Image, Automation and Live;
- no AD Glasses subscription; disclose possible external provider usage.

ON DEVICE

- installed/selected model, format/storage, runtime readiness, download/import/configure, test, advanced disclosure;
- do not claim accelerator use unless reported.

YOUR CLOUD

- relay base URL, optional token, optional email, dynamic model selectors, Auto, Save, Save and test, returned capabilities;
- credentials use encrypted local Android storage and never appear in logs/exports;
- in the dummy prototype, fields and tests are simulated and no secret is transmitted.

WEB GROUNDING

- Automatic, Ask before search, Off;
- supported Gemini/owner-relay Google Search grounding;
- citations required and query/context disclosure;
- never automate the consumer Google UI or name this Google Mode.

ROUTING

- personal Library for owner recall;
- on-device for suitable private/offline work;
- owner cloud for configured general/image/automation/live tasks;
- web grounding for current or obscure public information;
- confirmed Android action for actionable requests.

### 5.10 Privacy and Data

- current memory mode/data path, Pause passive automations, vault state;
- Private Local available;
- Encrypted Sync says Backend not configured until real owner backend upload exists;
- Fast Cloud and Confidential Cloud remain unavailable until their backends exist;
- actual inventory for media, recordings, transcripts, notes/summaries, memories/indexes, answers, models and logs;
- source eligibility and retention for facts, OCR, screen context, summaries, imports, transcripts and redaction;
- vault lock/passphrase, export/import, category deletion and confirmed clear all;
- contextual On device, Your cloud and Web search boundaries;
- no absolute privacy, encryption, latency, biometric, or authentication claim without evidence.

### 5.11 HeyCyan Firmware Lab

This is HeyCyan-only, Experimental, and never part of the normal daily path.

- compact device identity and runtime Wi-Fi/V821 and Bluetooth/JieLi versions;
- approved owner-service update or local recovery package;
- exact device/profile and current versions;
- paired `.swu` plus companion `.bin` only;
- expected baseline/target, size and SHA-256 when supplied;
- hardware-tested power policy, permissions, stable connection and conflict checks;
- exact-device/two-component confirmation;
- six stages: read versions, validate/stage pair, Wi-Fi update, restore/recheck, Bluetooth update, reconnect/verify;
- safe cancellation only before flashing or at a proven cancellable stage;
- incompatible/nothing flashed, preparation failure, Wi-Fi failure, partial BLE failure, verification pending, complete and interruption recovery.

The prototype simulates every stage and must contain no network, file-selection, transfer, vendor SDK, or flashing implementation.

### 5.12 Advanced and Diagnostics

This area is visually secondary.

- capability/app/device/provider/transcription summaries;
- redacted logs and diagnostic bundle preview/export;
- HeyCyan preview probes, OTA/version logs, owner-service patch request, pull mode, Wi-Fi ADB warning, listeners/scans and raw P2P details;
- AD Assistant-role, Local Agent, Accessibility readiness, and voice/image tests;
- GlassesBridge, EvenHub WebView, Mentra relay, Terminal HUD and notification forwarding, labeled Prototype/Research;
- confirmations for connectivity, debug exposure, firmware, export, or other risky actions;
- redact credentials, transcript content and unnecessary identifiers by default.

## 6. Capability maturity

| Family | Product maturity | UI rule |
|---|---|---|
| HeyCyan-compatible | Primary | Full verified capability set; firmware remains Experimental |
| Eyevue | Experimental | Runtime-implemented controls only; physical validation pending |
| Meta Ray-Ban | Experimental Android DAT | Runtime-gated registration/session/camera/photo/stream/display; physical validation pending |
| Meizu MYVU / Star Air | Experimental | Display/device controls only; no invented camera/media support |
| Generic audio | Limited | Audio routing/status only |
| MemoMind/XGIMI | Research | Advanced only |
| EvenHub/Even Realities | Prototype runtime | Advanced only |
| MentraOS | Prototype runtime | Advanced only |

## 7. Prototype state controls

The prototype must have a debug-only Prototype Controls page that can switch fixtures without rebuilding:

- device family and advertised capabilities;
- not set up, scanning, connecting, connected, reconnecting, disconnected, limited and failed;
- fresh/stale/unknown battery and storage;
- no activity, recording, syncing, translation, captions, visual capture, live AI and firmware;
- Library populated/empty/processing/search result;
- local/cloud/web providers ready, setup needed, offline, authentication failed or model unavailable;
- automation ready/setup/running/paused/incompatible/permission lost/failed;
- firmware unchecked, blocked, ready, each stage, partial, verification pending and complete;
- normal, large-text, long-copy and reduced-motion preview.

All simulated transitions are deterministic, cancellable where the real product would be, and resettable to seed data.

## 8. Journeys the prototype must complete

1. Welcome → readiness → scan → confirm → connect → Home.
2. Home/Assistant → What I see → captured image → answer → save → Library detail.
3. Assistant question → web-grounded answer → citations → source/follow-up/save.
4. Home Sync → prepare/Wi-Fi/list/transfer → complete or partial → Library.
5. Record/Meeting Spark Notes → recording banner → stop → transcript → summary/action items.
6. Translator setup → test → live banner → transcript → stop/save.
7. Settings → Your cloud → Save and test → dynamic fixture models → routing ready.
8. Device Center → Firmware → preflight → confirm → progress → complete or recovery.
9. Assistant → phone-action proposal → confirm/edit/cancel → actual simulated outcome/history.

## 9. Visual and accessibility acceptance

- one light visual language across every route;
- same true-transparent glasses source on Welcome and Home, with no pasted rectangle;
- compact branding, no duplicate logo plus giant title;
- calm graphite/cool-gray surfaces with restrained blue interaction color;
- one primary action per state and progressive disclosure;
- minimum 48dp targets, safe insets, TalkBack labels, logical focus order and visible focus;
- WCAG AA contrast, 200% font scaling and long translations without clipping;
- state never relies on color alone;
- meaningful motion only, with reduced-motion alternatives;
- no fake analytics, hardware facts, model names, metadata, locations, update severity, security, or success.

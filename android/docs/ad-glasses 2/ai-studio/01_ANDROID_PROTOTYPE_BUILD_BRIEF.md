# Google AI Studio prompt — native Android prototype

## Before pasting

In Google AI Studio Build mode, choose **Android**, not Web. Upload the curated package described in `00_INPUT_MANIFEST.md` and tell AI Studio to read `00_START_HERE.md` first.

Paste the prompt below as the first build request.

---

## Prompt

Build an installable native Android prototype named **AD Glasses Prototype** using Kotlin, Jetpack Compose, Material 3 foundations, Navigation Compose, ViewModels, StateFlow, immutable UI state, and a clean interface/repository boundary.

Read the attached files in this authority order:

1. `00_START_HERE.md`
2. `design/CANONICAL_UI_SPEC.md`
3. `design/DESIGN_SYSTEM.md`
4. `design/INTERACTION_AND_MOTION_SPEC.md`
5. `references/canonical-ui/SCREEN_MANIFEST.md`
6. visual files under `references/canonical-ui/screens/`
7. this build brief
8. `product/PRODUCT_BLUEPRINT.md`
9. `engineering/`

Ignore `archive/`, `references/stitch_variations/` and the raw `android/docs/stitch_ad_glasses/` export. Do not search them for alternative screen designs. Follow the one canonical light system and the approved canonical UI references. The reference HTML/CSS/JavaScript explains interaction and composition only; rewrite it as native Compose rather than embedding a WebView or copying it as web architecture.

### Objective

Produce a fully navigable, high-fidelity UI/UX prototype that can run in the AI Studio Android emulator and be installed on a physical Android phone. It must behave realistically using deterministic fake repositories and fixture data so we can validate the whole product before connecting the real glasses and backend.

This is not a collection of static screenshots. Buttons, navigation, sheets, dialogs, forms, progress, filters, toggles, empty states, error recovery and state transitions must work in the prototype.

### Safety boundary

Do not implement or request real:

- Bluetooth/BLE discovery, binding, vendor SDK calls, or device commands;
- Wi-Fi Direct, HTTP media transfer, camera/microphone recording, Accessibility automation, notification forwarding, or ADB;
- file deletion outside prototype fixture storage;
- Gemini, Google Search, Maps, owner-relay, Firebase, authentication, or other network calls;
- API keys, tokens, secrets, accounts, payments, subscription, billing, donation, or author-server access;
- firmware selection, download, validation, transfer, flashing, or device reset.

Do not add hardware, camera, microphone, Accessibility, location, Bluetooth, Wi-Fi, or Internet permissions to the prototype merely to make a screen look functional. Permission flows are simulated UI state. Firmware and phone actions must be visibly labeled simulated in the debug prototype while retaining production-quality owner-facing layouts.

Use application ID `com.achyutdalai.adglasses.prototype` so it can be installed beside the existing app. Use the stable Android/Kotlin/Compose versions supported by the current AI Studio Android environment. Avoid unnecessary libraries.

### App structure

The only bottom navigation items are:

1. Home
2. Assistant
3. Library
4. Automations

Implement all 12 screen families in `design/CANONICAL_UI_SPEC.md`. Treat internal steps, content-type variations and device/activity/error examples as routes or states of reusable screens—not separate visual systems.

Use a single-activity Compose architecture. Suggested boundaries:

- `DeviceRepository`
- `AssistantRepository`
- `LibraryRepository`
- `AutomationRepository`
- `AiServicesRepository`
- `PrivacyRepository`
- `FirmwareRepository`
- `PrototypeScenarioRepository`

Provide fake implementations only. Keep interfaces small enough that the fake implementations can later be replaced by this repository's real BLE, Wi-Fi, media, AI, storage, automation and OTA adapters.

### Implementation checkpoints

Build within one project in compiling checkpoints so breadth does not create a broken monolith:

1. theme, shared components, navigation shell, fixture repositories and Prototype controls;
2. onboarding/devices, Home, Assistant/conversation, Library/detail and Automations/detail;
3. Device Center, Sync, Settings, AI Services and Privacy/Data;
4. Firmware simulation, Advanced modules, all required state variants, journey tests and final polish.

At every checkpoint, keep the project buildable and preserve the established component system. Do not create competing visual directions or duplicate screens to move faster.

### Required fake behavior

ONBOARDING AND CONNECTION

- readiness statuses change through simulated actions;
- scanning reveals fixture devices;
- device family can be confirmed or changed;
- Connect advances through Preparing, Connecting and Reading capabilities;
- success updates Home and Device Center;
- delayed, denied, unknown and failed cases have working recovery.

HOME AND GLOBAL ACTIVITY

- Device Stage reflects selected device/state/capabilities;
- Capture and Record show capability-aware choice sheets;
- one global Activity Banner persists across navigation;
- recording timer runs until Stop;
- Sync/translation/firmware banners open their owning detail;
- fixture outputs appear in Today and Library after simulated completion.

ASSISTANT

- text submission creates fixture streaming then completed responses;
- Voice, What I see and Live have focused simulated states;
- one fixture answer demonstrates personal Library recall;
- one demonstrates web grounding with clearly marked prototype citations and source rows;
- one demonstrates unavailable cloud with a local fallback;
- one demonstrates a phone-action proposal with Confirm, Edit and Cancel, followed by success/partial/failure selected by scenario;
- Save to Library creates a saved-answer fixture.

LIBRARY

- populated and empty modes;
- working search, filters, Timeline/Collections and multi-select;
- photo, video, audio, meeting/note and memory/saved-answer details;
- simulated transcription and summary transitions;
- add to collection and confirmed deletion affect prototype storage only;
- Reset seed data restores everything.

AUTOMATIONS

- show all eight built-ins from the canonical spec;
- every automation uses the shared detail shell with its specific controls;
- first enable requires review and a safe Test where specified;
- running/paused/incompatible/permission-lost/failed states are switchable;
- global passive pause works;
- Community browse, detail and publish form use local fixture content only.

DEVICE, SYNC, AI, PRIVACY AND FIRMWARE

- Device Center changes controls when fixture device family/capabilities change;
- Sync visibly simulates every stage, progress, cancel, complete, duplicate, failed and partial outcomes;
- owner-cloud Save and test returns fixture capabilities/models and never transmits fields;
- AI provider health and routing states are interactive;
- privacy inventory, retention, memory modes, vault and destructive confirmation are interactive against fixture state;
- Firmware Lab simulates blocked preflight, exact two-component confirmation, six stages, partial recovery, verification pending and complete, with no real file/network/device code;
- Advanced modules have safe previews, redacted fixture logs and confirmation dialogs but no privileged implementation.

### Prototype Controls

Add `Settings → Prototype controls`, visible only in this prototype build. It must allow us to change without rebuilding:

- device family and capability set;
- connection state and metric freshness;
- global activity;
- Library populated/empty/processing;
- AI provider health;
- automation state;
- Sync result;
- firmware stage/result;
- ordinary/large-font/long-copy/reduced-motion fixture modes;
- Reset all prototype data.

Every scenario should be deterministic. Prefer a scenario dropdown or segmented list over random failure. Simulated delays should be short enough for testing and offer Skip/Complete now from Prototype controls.

### Visual requirements

- light mode only;
- use `design/DESIGN_SYSTEM.md` consistently;
- one compact AD Glasses brand lockup, no duplicate giant product heading;
- use the same isolated glasses visual on Welcome and Home; if the supplied reference is not truly transparent, create a replaceable placeholder layer whose background exactly matches the page rather than showing an image rectangle;
- use `references/canonical-ui/assets/ad-glasses-hero-v1.png`, which has a verified alpha channel, as the prototype product layer; keep halo and shadow as separate Compose layers;
- no black full-page variant, neon, glassmorphism, excessive pills, arbitrary gradients, fake telemetry, or marketing cards;
- no invented hardware/model/version/metadata/location/security claims;
- source strings and numbers come from fixture models, never baked into images;
- meaningful transitions and activity progress with reduced-motion behavior;
- implement the timing, priority, cancellation and reduced-motion rules in `design/INTERACTION_AND_MOTION_SPEC.md`.

### Accessibility and resilience

- 48dp minimum targets and safe system insets;
- TalkBack descriptions, headings, live-region announcements where appropriate and logical traversal;
- WCAG AA contrast;
- layouts survive 200% font scaling, keyboard appearance and long translated text;
- state is never color-only;
- destructive actions have confirmation and safe Back behavior;
- loading, empty, offline, denied, interrupted, partial and recoverable-error paths remain usable.

### Project quality

- no giant god ViewModel or single file containing the entire app;
- reusable components and route-scoped state holders;
- UI models do not import future vendor SDK types;
- fixture data is centralized and clearly marked;
- include unit tests for reducers/state transitions and navigation/Compose tests for the nine canonical journeys where the environment supports them;
- include a README describing architecture, prototype controls, build/install steps and every fake boundary;
- run the available Gradle build/tests and fix compile or navigation crashes before declaring completion.

### Required handoff

Return:

1. complete Kotlin/Compose source;
2. installable debug APK if AI Studio provides it;
3. route and component inventory;
4. list of fixture repositories and future integration interfaces;
5. test results and known limitations;
6. screenshots of the four destinations and the nine canonical journeys;
7. confirmation that no real hardware, network, secret, account, payment or firmware behavior was implemented.

Do not ask me to choose which features from the reference images to keep. The canonical spec already makes that decision. If references conflict, follow the authority order and record the discarded assumption in the README.

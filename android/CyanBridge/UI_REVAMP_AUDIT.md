# AD Glasses UI Revamp Audit

Status: design audit prepared after the `ui/welcome-audit-polish` pass.

This document audits the currently routed AD Glasses product UI. It intentionally excludes old compatibility Activities and dead Compose surfaces that are not reachable through the current AD navigation graph.

## Product design direction

The current monochrome direction should stay. The strongest parts of the app already feel appropriate for a smart-glasses product: quiet, technical, direct, and hardware-first. The UI does **not** need decorative colour to feel premium. The missing layer is a consistent page grammar and finishing system.

Use colour only for semantic states:

- graphite / black / white / silver for normal product chrome;
- green only for genuinely successful/connected/live-ready state;
- amber only for warning or firmware risk;
- red only for destructive/failure state.

The revamp should make the app feel more intentional, not busier.

## Active product surfaces

### Primary tabs

1. Home — `ADHomeSurface`
2. Prompt — `ADNativeConversationScreen`
3. AI — `ADNativeAiScreen`
4. Library — `ADNativeLibraryScreen`

### Routed subpages

- Device Center — `ADGlassesDeviceCenterScreen`
- Sync — `ADSyncScreen`
- Settings — `ADNativeSettingsHubScreen`
- Relay — `ADNativeRelaySettingsScreen`
- Local AI — `ADNativeLocalAiSettingsScreen`
- Assistant apps — `ADAssistantAppsScreen`
- Privacy — `ADPrivacyCenterScreen`
- Storage — `ADStorageScreen`
- Language — `ADLanguageScreen`
- Permissions — `ADPermissionsScreen`
- Advanced — `ADAdvancedScreen`
- About — `ADAboutScreen`
- Firmware — `ADFirmwareScreen`
- Capability detail — `ADNativeTaskDetailScreen`
- Captures — `ADNativeCapturesScreen`
- Recordings & transcripts — `ADNativeRecordingsScreen`
- Notes & summaries — `ADNativeNotesScreen`

### Entry / hardware setup

- Welcome — `ADWelcomeScreen`
- Pairing — `ADGlassesPairingScreen`

## Main systemic finding

The visual inconsistency is not primarily a colour problem. It is a **page-grammar problem**.

`ADPageLayout` currently gives most subpages the same back bar, 16dp horizontal padding, 18dp vertical gap and scrolling column. `ADCard` then gives almost every content type the same 18dp white card with 16dp padding. This works functionally, but it means configuration, identity, device state, forms, capabilities and media often share the same visual vocabulary when they should be related without being identical.

Meanwhile Home, Prompt and Pairing use bespoke components. This creates the opposite problem: the strongest pages look more designed, but do not always feel as if they belong to the same system as deeper pages.

The next revamp should introduce a small family of page archetypes and primitives rather than redesigning every screen independently.

## Proposed page archetypes

### 1. Hero / state page

Use for Home, Device Center, Sync and Pairing.

Characteristics:
- one dominant state object near the top;
- one clear primary action;
- secondary metrics compactly grouped;
- live state has subtle motion where meaningful;
- avoid stacking several equal-weight cards.

### 2. Capability page

Use for AI landing, capability detail and Assistant apps.

Characteristics:
- capability identity is visually distinct from settings;
- state, purpose and output are separate concepts;
- selected/default AI is clearly different from a navigation row;
- setup dependencies never replace the capability description;
- technical setup moves below product-facing controls.

### 3. Content page

Use for Prompt, Library, Captures, Recordings and Notes.

Characteristics:
- content itself carries visual weight;
- navigation rows should not make a media/library page look like Settings;
- empty states and loading states share one system;
- media, audio, code and notes each get an appropriate content treatment.

### 4. Settings / form page

Use for Settings, Relay, Local AI, Privacy, Storage, Language, Permissions and Advanced.

Characteristics:
- compact grouped controls;
- consistent field states and labels;
- primary action placement is predictable;
- setting rows, selection rows and status rows are visually different components;
- technical information can be dense without looking like a stack of generic cards.

### 5. Identity / editorial page

Use for Welcome and About.

These should **not** be forced to resemble settings pages. Their difference is intentional. They should share typography, spacing and brand tokens with the rest of the app while keeping a product/editorial composition.

## Token and primitive cleanup

Before redesigning individual screens, define these shared rules.

### Spacing

- 16dp normal phone edge inset.
- 20dp for editorial/setup hero screens where extra breathing room is useful.
- 24dp between major sections.
- 12–16dp within grouped content.
- 8–10dp between title and supporting text.
- Avoid mixing 18dp, 20dp and 24dp major gaps without semantic reason.

### Shape

Recommended hierarchy:

- 24–28dp: hero/media stages only.
- 18–20dp: primary cards.
- 14–16dp: controls, input fields and compact cards.
- 10–12dp: icon containers.
- pill/circle only for status, avatar and compact actions.

### Icon system

- Prefer one icon family per hierarchy level. Outlined icons are a good default for tools/capabilities.
- Rounded icons should be reserved for navigation or where no appropriate outlined icon exists.
- Do not repeat the same identity icon three times on one screen unless it has a real status role.
- Standard sizes: 20dp row icon, 28–30dp capability hero icon, 36–40dp hero/device mark as needed.

### Typography

The current Sans Serif / Medium / SemiBold system is suitable. Do not replace the font just to make the UI feel new.

Refine usage instead:

- Brand wordmark: its own token; avoid accidental `Bold` elsewhere.
- Page title: one consistent size/weight.
- Section title: one consistent size/weight across all archetypes.
- Card title: `titleMedium` or equivalent.
- Body: normal weight, generous line height.
- Uppercase labels should be rare and reserved for small metadata, not used as a second section-heading system.

### Surfaces

- Prefer flat surface contrast and thin outlines over shadows.
- Use elevation only where a control genuinely floats (composer, bottom navigation, modal-like control).
- Do not use a different background tint for each capability.

### CTA system

Define three levels:

1. Primary: filled graphite button.
2. Secondary: outlined or subtle surface button.
3. Destructive: red only when action is truly destructive/cancelling a risky operation.

A page should normally have one primary action at a time.

### Status system

Status should not be confused with navigation or configuration.

- connected/ready: small semantic indicator/chip;
- selected: monochrome selection state plus check;
- action-needed: neutral label plus action, not a permanent warning colour;
- error: red only after an actual error;
- permission missing: describe the capability normally, reveal setup requirement on interaction or in a secondary state line.

## Motion system

Motion should make real hardware/AI activity legible rather than decorate static pages.

Keep / expand:

- Prompt AI-working waveform.
- Prompt audio playback/recording waveform.
- Pairing scanning rings with subtle pulse/rotation.
- Sync progress with smooth progression.
- Recording playback with a small progress/waveform treatment.
- Connection state transitions with subtle state change, not continuous animation.

Avoid:

- fake listening animation when no audio session exists;
- animated cards/icons just to make a static settings page lively;
- large looping hero effects that compete with the glasses.

## Page-by-page audit

### Welcome

Current direction after polish:
- centered app mark + AD GLASSES;
- large left-aligned `YOUR GLASSES / YOUR AI / YOUR DATA` statement;
- glasses on a separate silver hero stage;
- only two actions below.

Why this is stronger:
- image no longer competes with text;
- glasses remain the product hero rather than a watermark;
- no explanatory app-centric copy;
- layout reads like a product poster.

Next review:
- validate on a real device at short/tall screen sizes;
- tune hero-stage height and statement scale based on screenshot, not source alone;
- if further exploration is needed, use Figma for 2–3 poster variants before another code pass.

Priority: **review after build**, not a wholesale redesign yet.

### Home

Strengths:
- strongest glasses-first identity in the app;
- product image and readiness state establish hardware immediately;
- action grid is understandable;
- live recording/sync state can appear contextually.

Needs finishing:
- distinguish primary actions from secondary links with clearer section grouping;
- reduce conceptual duplication between Ask, Ask what I see and Search web;
- normalize section-heading treatment (`Active` currently differs from other sections);
- make the glasses-stage gradient strictly neutral silver;
- let semantic green appear mainly in the status indicator/chip rather than the whole live-row identity;
- review whether four equal action tiles all deserve the same prominence.

Priority: **medium**.

### Prompt

Strengths:
- durable New Prompt flow exists;
- terminal identity now reads as prompting rather than messaging;
- real AI/audio state drives motion;
- assistant responses are visually quieter than user input;
- code and local media have dedicated rendering.

Needs finishing:
- align custom header height/insets with the product top-bar system without losing its special controls;
- make New Prompt more obviously discoverable while staying compact;
- avoid repeating the Terminal icon in header, empty hero and every non-web suggestion;
- reduce empty-state vertical dead space on shorter devices;
- adapt user bubble max width to available width rather than a fixed 336dp ceiling;
- standardize rich link/media card radii and inner padding with Library content cards;
- preserve composer elevation but coordinate it with bottom-nav elevation;
- code block header and media-link treatment should use the same metadata language as Library.

Priority: **medium-high**.

### AI landing

Current problem:
The page contains three different concepts—capabilities, default-model selection and connections—but all are rendered with nearly the same white-card language.

Needs revamp:
- add a small current-AI summary at the top so the glasses' active route is immediately legible;
- give capabilities a more product-like tool grid, with enough room for names and useful descriptions;
- distinguish selection rows from navigation rows structurally, not just with a checkmark;
- keep capability descriptions stable even when setup is missing;
- show setup status as secondary metadata or after interaction;
- tighten title/subtitle capitalization and naming;
- consider variable-width/featured capability cards rather than an inflexible 2x3 grid if the copy needs it.

Priority: **high**.

### Capability detail (Translate, Soundbites, Timeline, DayNote, Cron, Automation)

Strengths:
- description now has full width;
- capability icon identity is clear;
- on/off state is separate from details;
- monochrome treatment is correct.

Needs revamp:
- integrate icon, capability summary and state into one composed hero instead of three visually detached blocks;
- replace generic `DETAILS` with the common section-heading system;
- question whether every capability is best represented by a persistent On/Off switch—some may be action/workflow capabilities rather than background modes;
- add a concise `How it works` or `Used when` block when relevant;
- make output destination more tangible (e.g. Library > Notes, Timeline, transcript) rather than only a text value;
- remove boilerplate copy that says the same thing on every capability;
- allow capability-specific secondary controls only where they are real, without making every page a unique layout.

Priority: **high**.

### Library landing

Current problem:
It is a media/content destination but looks mostly like a Settings menu with Captures, Recordings and Notes rows.

Needs revamp:
- show recent or representative content, counts, or last-updated metadata;
- make Captures / Recordings / Notes feel like collections, not settings destinations;
- integrate sync state/action into the library header or collection area;
- retain one clear Sync action without placing it as an unrelated button after a settings-like card;
- consider compact collection cards with preview/count metadata.

Priority: **high**.

### Captures

Strengths:
- actual thumbnails provide visual richness naturally;
- video play treatment is clear;
- states for loading/empty/error exist.

Needs finishing:
- evaluate 16:10 single-column cards versus a two-column media grid on typical phones;
- move filename metadata to a quieter role if it is technical rather than user-meaningful;
- standardize type metadata and date/source treatment;
- share empty/loading-state primitive with other content pages.

Priority: **low-medium**.

### Recordings & transcripts

Strengths:
- playback and transcript are in one card;
- transcript expands inline;
- state chips are meaningful.

Needs finishing:
- add a lightweight audio progress/waveform treatment so playback state feels alive;
- make play target and duration hierarchy stronger;
- use transcript status without making every card badge-heavy;
- standardize date/source metadata with Notes/Captures;
- consider a compact list density for users with many recordings.

Priority: **medium**.

### Notes & summaries

Needs finishing:
- note cards currently inherit generic card/settings visual language;
- emphasize note title/content and reduce decorative icon weight;
- use an editorial note preview with clear date/source metadata;
- standardize expansion affordance;
- empty state should share the Library content system.

Priority: **medium**.

### Device Center

Strengths:
- state-first page with clear connection identity;
- meaningful connect/disconnect/change actions;
- device metrics and tools are understandable.

Needs finishing:
- connection is repeated in both hero and status metrics; consolidate;
- reduce the number of equal-weight card sections;
- `Capabilities` is currently descriptive filler rather than interactive product value; either make it useful or remove it;
- normalize icon background treatment in Device tools;
- firmware warning colour is semantically justified, but should not dominate before a warning state exists;
- present battery/storage as compact hero metrics when connected.

Priority: **medium**.

### Sync

Strengths:
- clear task and clear primary CTA;
- good conditional states for connected/offline/transferring;
- progress is visible.

Needs finishing:
- smooth progress transitions;
- visually combine percent and progress bar;
- make connection/transfer/media details slightly denser;
- ensure cancel red is used only while transfer is active.

Priority: **low-medium**.

### Settings hub

Strengths:
- familiar and easy to scan;
- grouping is logical.

Needs finishing:
- make the clickable glasses identity card visibly navigable;
- reduce the generic-card feel of every section;
- use one inset/grouped-settings visual system consistently;
- establish consistent group-title spacing and capitalization;
- do not add decorative content simply to fill space.

Priority: **medium**.

### Privacy

Strengths:
- settings are grouped by purpose;
- Automation-sensitive confirmation belongs here.

Needs finishing:
- distinguish toggle-setting rows from normal navigation rows;
- tighten long subtitle wrapping and switch alignment;
- consider a compact privacy summary/status at top only if it conveys real state;
- footer copy should remain factual and secondary.

Priority: **medium**.

### Storage

Needs finishing:
- make App data / Cache / Synced media read like storage metrics rather than plain rows;
- visually de-emphasize internal filesystem path copy;
- move Clear cache into a clearly secondary maintenance area;
- keep destructive semantics proportional—cache clearing is maintenance, not danger.

Priority: **medium**.

### Language

Current page is intentionally simple and does not need extra decoration.

Needs finishing:
- make current language the page's main value;
- reduce the generic explanatory card feeling;
- one clean system action is enough.

Priority: **low**.

### Permissions

Needs finishing:
- clarify app-runtime permissions versus special Android access such as Accessibility;
- use status icons/chips consistently without making every row visually loud;
- keep one `Manage permissions` action;
- if a permission is missing, explain which glasses capability needs it rather than only saying OFF.

Priority: **medium**.

### Relay

Current page is functional but form-like and generic.

Needs revamp:
- introduce proper field component with label, focus and validation/error state;
- make backend selection a real segmented/selection control, not another settings row;
- keep Save action position predictable;
- show current connection/configured state if it can be derived honestly;
- keep Web Search explanation secondary.

Priority: **medium-high**.

### Local AI

Current page is dense because local files and network-compatible endpoints are both presented at once.

Needs revamp:
- split into two clearly named modes: On this phone / Compatible server;
- progressively disclose server fields when enabled;
- give installed model selection a dedicated component;
- use proper text fields with focus/error states;
- simplify import status and selection status;
- avoid two large cards each containing their own primary-action language.

Priority: **high**.

### Assistant apps

Current page exposes too much bridge/automation implementation detail at the same hierarchy as the product choice.

Needs revamp:
- top of page should answer only: current assistant app, whether handoff is available, and whether it is selected;
- route selection should be the main control;
- move Tasker/bridge verification/accessibility into a clearly secondary Advanced setup section;
- collapse or de-emphasize advanced setup until needed;
- reduce repeated settings rows and technical text.

Priority: **high**.

### Advanced

The current two-row page is appropriate for an owner/diagnostic destination.

Needs finishing:
- optional short description of what belongs here;
- otherwise keep it compact and boring on purpose.

Priority: **low**.

### Firmware

Semantic amber/red is appropriate here even in the monochrome system because firmware changes are genuinely risky.

Needs revamp/polish:
- turn status, preflight, acknowledgement and action into a more linear staged flow;
- make risk acknowledgement an explicit checkbox/control rather than an entire clickable card;
- reserve amber for actual risk/preflight attention instead of using it as the page's normal decorative accent;
- keep progress and cancellation visually prominent only during an active update.

Priority: **medium-high**.

### Pairing

Strengths:
- purpose-specific page rather than generic settings;
- scanning visual makes hardware setup feel active;
- nearby-device list is clear.

Needs finishing:
- animate scan rings subtly when actively scanning;
- avoid large vertical jumps when results appear;
- make found/connecting/failed states share one scanner-stage component;
- keep the empty/failure copy from becoming another generic card stack.

Priority: **medium**.

### About

The About page **should remain structurally different** from Privacy, Relay or capability settings. It is an identity/information page, not a control page.

Keep:
- product mark and AD Glasses identity;
- `Version alpha`;
- short product description;
- product facts;
- open-source/legal note.

Polish:
- give identity area a little more editorial hierarchy;
- make product facts compact rather than settings-like;
- keep legal copy visually secondary.

Priority: **low-medium**.

## Revamp priority

### Priority 1 — redesign first

1. AI landing
2. Capability detail
3. Local AI
4. Assistant apps
5. Library landing

These pages currently create most of the feeling that the app loses polish once the user moves beyond the strongest primary surfaces.

### Priority 2 — substantial finishing pass

1. Prompt
2. Relay
3. Firmware
4. Device Center
5. Pairing

### Priority 3 — system alignment

1. Settings hub
2. Privacy
3. Storage
4. Permissions
5. Recordings
6. Notes
7. Home

### Priority 4 — light polish only

1. Sync
2. Language
3. Advanced
4. Captures
5. About

## Recommended implementation order

### Phase 0 — reference frames in Figma

Before changing Priority 1 pages again, create a compact Figma sheet containing:

- AI landing
- one capability detail (use DayNote or Cron)
- Library landing
- Local AI
- Assistant apps

The purpose is not to rebuild the whole app in Figma. These five frames should establish the page grammar, spacing, icon container, section hierarchy, selection treatment and CTA placement. Once those are approved, port the primitives to Compose and let lower-priority pages inherit them.

If Welcome still needs adjustment after an on-device screenshot, explore 2–3 poster variants in the same Figma sheet rather than coding repeated guesses.

### Phase 1 — shared primitives

Add/refactor reusable components such as:

- `ADPageHeader` / `ADPageScaffold` variants
- `ADHeroCard`
- `ADSectionHeader`
- `ADGroupedSection`
- `ADNavigationRow`
- `ADSelectionRow`
- `ADToggleSettingRow`
- `ADStatusRow`
- `ADMetricRow`
- `ADTextField`
- `ADEmptyState`
- `ADPrimaryActionArea`
- `ADCapabilityHero`

Do not create a huge design-system abstraction layer. Keep these small and Compose-native.

### Phase 2 — Priority 1 pages

Implement the approved archetypes on AI, capability detail, Local AI, Assistant apps and Library landing.

### Phase 3 — propagate, do not redesign blindly

Apply the new primitives to Priority 2/3 pages where they solve a real inconsistency. Leave already-effective content layouts alone.

### Phase 4 — motion and final polish

Add only state-driven motion, then review:

- spacing at small/large phone heights;
- text wrapping;
- touch targets;
- selected/disabled/error states;
- keyboard-safe composer/forms;
- loading/empty/error consistency;
- semantic colours;
- navigation transitions;
- status bar / bottom bar integration.

## Definition of done for the full revamp

The app should feel like one product without making every page look identical.

A finished pass should satisfy these tests visually and structurally:

- Home immediately feels like the glasses control surface.
- Prompt feels like an AI workspace, not a chat clone.
- AI capabilities feel like product features, not plugin settings.
- Library feels like saved content, not navigation settings.
- Settings feel compact and predictable.
- About and Welcome feel editorial/brand-led without pretending the app itself is the product.
- Deep pages preserve the same spacing, typography, shape and interaction rules as primary pages.
- Monochrome remains the normal visual language.
- Colour appears only when state meaning justifies it.
- Animation always corresponds to actual device, AI, audio or progress state.

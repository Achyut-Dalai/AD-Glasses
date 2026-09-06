# AD Glasses Android UI Parity Specification

Reference: native iOS app on `main` at `9fd4721d49ee34577861330a0c1cb57cf41b83be`.

This document is the visual/product contract for the Android UI. The native iOS app is the source of truth for visual hierarchy, information architecture, copy hierarchy, spacing, component states, surfaces, motion intent, and branding. Android may use platform-native controls where that improves usability or is required by the OS, but it must look and feel like the same AD Glasses product.

## 1. Non-negotiable implementation rules

1. Treat iOS as a design specification, not inspiration.
2. Baseline dimensional mapping is **1 SwiftUI point = 1 Compose dp** for authored layout values. System bars and platform-owned controls keep Android-native insets/sizing.
3. Reuse the real AD assets. Do not invent a substitute launcher icon, logo, glasses illustration, or visual identity.
4. Do not use wallpaper-derived Dynamic Color for AD-owned surfaces. Dynamic system chrome is acceptable only where Android owns it.
5. Do not put Android infrastructure features on Home just because Android can expose them. Background-link controls, pairing diagnostics, permissions, calls/SMS capability setup, and protocol diagnostics belong in Device Center/Settings.
6. Home feature set must match the iOS product hierarchy: Lens hero + Ask, Photo, Video, Translate, Soundbites, Audio.
7. Settings, Device Center, Library lists, and Lens controls should intentionally use platform-native list/form patterns. Welcome, Home, Assistant, and selected progress/sheet surfaces carry the strongest custom AD visual identity.
8. Light and dark modes must preserve the same hierarchy. Avoid custom gray slabs that replace semantic system backgrounds.
9. Accessibility is part of parity: large text must collapse adaptive grids to one column, reduce-motion must suppress nonessential animation, and reduce-transparency must fall back to opaque semantic surfaces.
10. No screen is considered visually matched until a physical Samsung screenshot is compared with the iOS reference state side-by-side.

## 2. Brand assets

The Android app must use these exact iOS assets as source material:

- App icon: `ios/ADGlasses/Resources/Assets.xcassets/AppIcon.appiconset/1024.png` — 1024×1024.
- Brand icon: `ios/ADGlasses/Resources/Assets.xcassets/BrandIcon.imageset/brand-icon.png` — same source image/blob as the iOS app icon.
- Welcome glasses hero: `ios/ADGlasses/Resources/Assets.xcassets/GlassesHero.imageset/glasses-hero.png`.
- `LensShutter.imageset/lens-shutter.png` is retained but should only be used if the current iOS UI actually uses it; do not introduce it merely because it exists.

Android launcher work must generate proper adaptive/mipmap resources from the real app icon rather than keeping the temporary black-and-white vector glasses icon.

## 3. Global visual grammar

### 3.1 Layout widths and gutters

- Standard authored screen horizontal gutter: **16 dp**.
- Welcome horizontal gutter: **20 dp**.
- Assistant floating composer outer horizontal gutter: **10 dp**.
- Home maximum content width: **700 dp**.
- Assistant maximum conversation/composer width: **720 dp**.
- Translation maximum content width: **680 dp**.
- Welcome maximum content width: **620 dp**.
- Sync progress maximum content width: **420 dp**.

Content is centered when the viewport exceeds these widths.

### 3.2 Core spacing rhythm

Use the iOS-authored values directly rather than an arbitrary Material spacing scale. Common values are:

- 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 16, 18, 20, 22, 24, 28, 32 dp.
- Primary screen section rhythm is usually **16–18 dp**.
- Card internal rhythm is usually **10–14 dp**.
- Dense list labels use **2–4 dp**.

### 3.3 Corner radii

Use continuous/smooth rounded shapes where possible.

- Welcome glasses stage: **30 dp**.
- Assistant empty-state hero: **28 dp**.
- Home Lens hero: **26 dp**.
- Assistant floating composer: **24 dp**.
- Lens image area: **24 dp**.
- Home feature tiles: **20 dp**.
- Translation cards: **18 dp**.
- Assistant text field: **18 dp**.
- Assistant chat bubbles: **18 dp** major corners with **5 dp** tail corner.
- Conversation notice: **15 dp**.
- Photo detail controls: **14 dp**.
- Translation/settings subrows: **12 dp**.
- Home feature icon well: **11 dp**.
- Welcome brand icon clip: **10 dp**.

### 3.4 Surface hierarchy

The iOS product is primarily semantic system background + glass/material, not a custom colored dashboard.

- Root background: Android semantic background matching iOS `systemBackground`/`systemGroupedBackground` intent.
- Secondary grouped cards/rows: semantic secondary surface, not a hard-coded gray.
- Glass cards: translucent/blur-capable surface where practical; otherwise use an opaque semantic fallback with the same border/shadow hierarchy.
- Home glass border: **0.75 dp**. Light appearance approximates iOS white at 72% opacity; dark appearance white at 10% opacity.
- Home glass shadow: black at about 4% light / 16% dark, **14 dp radius**, y **7 dp**.
- Connection pill border: **0.5 dp** at primary 8% opacity; shadow black 7%, **8 dp radius**, y **4 dp**.
- Welcome hero border: primary 8%, **0.75 dp**; shadow black 6%, **24 dp radius**, y **12 dp**.

### 3.5 Color usage

Use semantic colors for background/text and reserve accent colors for feature identity.

Source semantic accent names used by iOS:

- AD/Assistant: indigo + blue, with cyan secondary glow.
- Lens: indigo/purple.
- Photo: teal.
- Video: pink; recording state red.
- Translate: indigo/blue.
- Soundbites: orange.
- Audio recording: red/purple depending context.
- Success: green.
- Warning: orange.
- Error/destructive: red.

Do not use indigo/blue/cyan as the global app background palette.

### 3.6 Typography

Prefer Android system typography while preserving the iOS hierarchy and weights. Do not substitute display-sized text where iOS uses headline/body.

- iOS `largeTitle.bold()` / rounded large title: Android large display/headline with bold weight only in Welcome headline and Assistant hero.
- `title2`, `title3`: Android headline/title equivalents.
- `headline`: semibold/bold compact feature/title text.
- `subheadline`: normal secondary explanatory text.
- `body`: primary conversational/content text.
- `footnote` / `caption`: secondary helper/status text.
- Home toolbar `AD`: headline, black/heaviest weight.
- Home toolbar `GLASSES`: caption2-equivalent, bold, **1.7 dp letter spacing**, secondary color.
- Welcome headline tracking: **-1 dp** equivalent.

### 3.7 Motion

- Root Welcome → Home transition: opacity, **0.32 s ease-in-out** unless Reduce Motion.
- Lens scan line: y **-26 → +26 dp**, **1.75 s ease-in-out**, autoreverse repeat; disabled for Reduce Motion/unavailable state.
- Assistant signal: target about **30 fps**. Idle pulse speed **0.72**, listening pulse **1.9**. Wave bars idle speed **1.55**, listening **5.4**.
- Conversation auto-scroll: short/snappy movement; no animation under Reduce Motion.

## 4. Navigation/chrome

Primary app tabs, in this order:

1. Home — house icon.
2. Assistant — sparkles icon.
3. Library — stacked-rectangles/library icon.

Android should keep a compact native bottom navigation but remove the oversized Material selected indicator if it visually dominates. Selected state should be subtle and consistent with the iOS TabView hierarchy.

Home has a centered compact `AD GLASSES` toolbar mark and one Settings action. Assistant has History leading; New Conversation + Settings trailing. Library has title `Library` and Settings trailing.

Do not add Home-level Phone & text or Background-link tiles.

## 5. Welcome / onboarding

### Root

- Full-screen `systemBackground` equivalent.
- Background glow 1: top-right radial, primary at **4.5% opacity**, end radius **370 dp**.
- Background glow 2: bottom-left radial, secondary at **3.5% opacity**, end radius **320 dp**.
- Scrollable for compact/large-text devices.
- Main VStack spacing **20 dp**.
- Max width **620 dp**.
- Horizontal padding **20 dp**; top **6 dp**; bottom **22 dp**.
- Minimum content height ≈ viewport minus **24 dp**.

### Brand

- Exact `BrandIcon` asset.
- **36×36 dp**.
- Rounded clip radius **10 dp**.

### Headline

- Top spacing from brand group: **14 dp**.
- Headline VStack spacing **0**:
  - `Your glasses.`
  - `Your AI.` — blue.
  - `Your data.`
- Rounded large-title bold; tracking **-1 dp**.
- Supporting copy below with **10 dp** separation; body size, secondary color.

### Glasses stage

- Height **260 dp**.
- Glass rounded rectangle radius **30 dp**.
- Hero image: exact `GlassesHero`, aspect-fit, horizontal padding **8 dp**, vertical **16 dp**.
- Background decorative circle: **240×240**, primary 3.5%, blur **30**, offset **(+130, -80)**.
- Decorative ellipse: **300×110**, white 16%, blur **26**, offset **(-95, +90)**.
- Border primary 8%, **0.75 dp**.
- Shadow black 6%, radius **24 dp**, y **12 dp**.

### Connecting state

- VStack spacing **10 dp**.
- Large circular progress.
- `Connecting`: subheadline medium, secondary.
- Vertical padding **12 dp**.

### Choice state

- Two full-width large controls, spacing **12 dp**.
- `Connect glasses`: high-contrast prominent control.
- `Continue without glasses`: outlined secondary control.
- Button shape radius **15 dp**.

### Location-permission state

Android should move initial permission education into this onboarding instead of throwing core permission dialogs before the UI appears.

- Card radius **22 dp**, padding **18 dp**.
- Main VStack spacing **16 dp**.
- Header HStack spacing **12 dp**.
- Location icon ≈ **30 dp**, blue.
- Copy VStack spacing **4 dp**.
- Large primary Allow button + large outlined `Not now`.

## 6. Home

### Screen

- Root background = semantic system background plus a top-right radial glow.
- Glow: primary opacity about **2.5% light / 5.5% dark**, end radius **300 dp**.
- Scroll content max width **700 dp**.
- Horizontal padding **16 dp**, top **8 dp**, bottom **28 dp**.
- Primary Lazy/VStack spacing **18 dp**.
- Lens + feature-grid group spacing **12 dp**.

### Toolbar mark

Centered:

- HStack baseline spacing **5 dp**.
- `AD`: headline, heaviest/black weight.
- `GLASSES`: caption2, bold, tracking **1.7 dp**, secondary.
- Only Settings action on the trailing side.

### Lens hero tile

This is not a colored gradient banner.

- Glass surface.
- Radius **26 dp**.
- Internal padding **18 dp**.
- Minimum height **148 dp**.
- Standard layout: HStack spacing **16 dp**; copy left, visual field right.
- Accessibility text sizes: stack vertically, spacing **18 dp**.
- Copy VStack spacing **10 dp**:
  - `Look. Ask. Understand.` — headline.
  - `Explore what’s in front of you.` — subheadline secondary.

#### Lens visual field

- Outer visual frame **126×112 dp**.
- Background radial circle **124×124**.
- Viewfinder icon **76 dp**, ultra-light, indigo translucent.
- Inner radial circle **82×82**.
- Inner outline circle **58×58**, 1 dp.
- Camera aperture icon **31 dp** medium; unavailable lock **20 dp**.
- Scan line **78×2 dp**, moves between y ±26 dp.

### Feature grid

At normal text size: adaptive columns with minimum width **148 dp**, row/column gap **12 dp**. Accessibility text size: one column.

Order:

1. Ask.
2. Photo.
3. Video.
4. Translate.
5. Soundbites.
6. Audio.

Feature tile:

- Radius **20 dp**.
- Internal padding **14 dp**.
- Minimum height **118 dp**.
- VStack spacing **12 dp**.
- Icon well **38×38 dp**, radius **11 dp**, accent at 10% opacity.
- Icon typography/size ≈ title3 semibold.
- Text stack spacing **3 dp**.
- Title: headline.
- Detail: caption secondary, max 2 lines.
- Unsupported state: small lock at trailing top; do not dim the entire layout into unreadability.

State copy must match iOS semantics:

- Ask: `Ask by voice`, `Listening · sends automatically`, `Thinking…`, `Speaking…`.
- Photo: `Take a photo`.
- Video: `Record from glasses`; while recording `Recording · tap to stop` with red accent.
- Translate: `Live conversation`.
- Soundbites: `Turn speech into notes`.
- Audio: `Record from glasses`; recording state red.

### Floating connection pill

Bottom-safe-area overlay, centered above tab bar.

- Horizontal outer padding **16 dp**, bottom **10 dp**; safe-area spacing **8 dp**.
- Capsule text: caption semibold.
- Internal horizontal padding **13 dp**, vertical **9 dp**.
- Status HStack spacing **7 dp**.
- Connected/disconnected dot **8×8 dp** green/red.
- Battery separator `·`, battery label/icon; charging adds bolt.
- Material/glass surface with subtle border/shadow per global tokens.
- States: Connected (+ battery), Finding glasses, Connecting, Connect.

## 7. Assistant

### Screen and chrome

- Grouped background equivalent.
- Conversation max width **720 dp**.
- Horizontal padding **16 dp**, top **10 dp**, bottom content reserve **112 dp**.
- Main vertical spacing **18 dp**.
- Toolbar: History leading; New Conversation + Settings trailing.

### Empty-state hero

- Empty content VStack spacing **10 dp**.
- Hero radius **28 dp**.
- Internal padding **20 dp**.
- Minimum height **220 dp**.
- Gradient: indigo → blue, top-leading to bottom-trailing.
- Avatar size **54 dp**.
- Hero vertical structure spacing **22 dp**.
- Copy spacing **7 dp**.
- `Ask`: large title bold, white.
- Supporting copy: subheadline, white 82%.
- Decorative circle 1: **190×190**, white 11%, offset **(+58,-72)**.
- Decorative circle 2: **130×130**, cyan 18%, offset **(+34,+130)**.

### AD avatar

- Circular gradient indigo → blue.
- Internal white circle/fill at 17%.
- White outline 22%, **0.75 dp**.
- Sparkles icon size = approximately **38% of avatar diameter**, semibold.
- Common sizes: **54**, **34**, **28 dp**.

### Signal visual

- Minimum height **238 dp**.
- Three ring diameters: **118, 164, 210 dp**.
- Ring stroke **1 dp**.
- Center radial circle **118×118**.
- Seven vertical capsule bars, each **5 dp** wide, spacing **5 dp**, holder height **52 dp**.
- Resting heights: **14, 22, 31, 40, 31, 22, 14 dp**.
- Listening amplitude target **34 dp**, baseline **9 dp**.

### Conversation header

- HStack spacing **10 dp**.
- Avatar **34 dp**.
- Label stack spacing **1 dp**.
- `AD`: subheadline semibold.
- `Current conversation`: caption secondary.

### Conversation bubbles

- Row spacing **9 dp**.
- Opposite-side minimum spacer **42 dp**.
- Assistant avatar **28 dp**.
- Bubble inner horizontal padding **15 dp**, vertical **11 dp**.
- Major radii **18 dp**.
- Tail corner **5 dp** on assistant bottom-leading / user bottom-trailing.
- User background: blue → indigo gradient; white text.
- Assistant background: semantic secondary grouped surface; primary text.
- Image attachment: max **240×240 dp**; placeholder/loading **180×120 dp**; radius **12 dp**.

### Thinking row

- HStack spacing **9 dp**.
- Avatar **28 dp**.
- Small progress indicator.
- `Thinking`: footnote medium, secondary.

### Notice

- HStack top-aligned spacing **11 dp**.
- Padding **13 dp**.
- Orange 10% background, radius **15 dp**.
- Orange info icon; footnote secondary; close icon caption bold.

### Composer

The Android composer must stop looking like a full-width Material toolbar.

- Floating surface radius **24 dp**.
- Max width **720 dp**.
- Outer horizontal margin **10 dp**, bottom **7 dp**.
- Internal padding **10 dp**.
- Primary VStack spacing **8 dp**.
- Input/action row bottom-aligned spacing **8 dp**.
- Add action frame **38×38 dp**.
- Text field horizontal padding **12 dp**, vertical **9 dp**, radius **18 dp**, tertiary fill, 1–5 lines.
- Right action frame **38×38 dp**.
- Send action: circular blue → indigo gradient, white arrow.
- Mic action: unfilled/native icon.
- Stop action: red stop icon.
- Status lines above composer use caption semibold and small progress/waveform states.

Keep Android’s excellent system speech-recognizer UI when the phone/system mic button is used; that OS-owned screen does not need to visually imitate iOS. Returning to AD must land back in the parity composer/conversation UI.

### History sheet

- Medium/large modal sheet.
- Native list rows.
- Row HStack spacing **12 dp**.
- Copy stack spacing **4 dp**.
- Title primary; preview caption secondary; checkmark accent for current conversation.

## 8. Device Center

Use a native Android bottom sheet/full-height modal with grouped-list styling instead of an `AlertDialog`.

Title: `Glasses`, compact centered app bar, `Done` action.

Sections should mirror iOS hierarchy:

1. Connections/provider status.
2. AD Glasses connection action + discovered glasses rows + Forget.
3. Device status (battery, firmware/hardware when available).
4. Camera & Capture.
5. Audio.
6. Voice activation.
7. Learn.
8. Device controls / unavailable management placeholders.
9. Error section when needed.

Android-specific extension is allowed and required:

- Add a `Calls & audio` subsection for Classic Bluetooth/HFP/A2DP state.
- Show searching, pairing, paired, connecting, calls/media connected, retry.
- Keep raw MAC addresses out of normal product UI; expose them only in Diagnostics.

Discovered-device row:

- Native list row.
- Radio-wave icon in blue.
- Text stack spacing **2 dp**.
- Device name primary.
- Signal description caption secondary (`Strong`, `Good`, `Weak`), not raw dBm in the primary UI.
- Chevron trailing.

Voice wake must be exposed as a normal switch and **Off by default**, matching the iOS product copy/behavior.

## 9. Library

Use native grouped list semantics rather than custom rounded cards.

### Library root

- Navigation title `Library`.
- Settings trailing.
- First section: `Sync from glasses` row; copy stack spacing **4 dp**.
- Second section: `Saved on this phone` with:
  - Captures — blue photo icon.
  - Recordings — purple waveform icon.
  - Notes & transcripts — orange note icon.
- Library row vertical padding **4 dp**; label copy spacing **2 dp**.
- Final informational footnote section explaining originals/derivatives/deletion semantics.

### Collection screens

- Empty state uses native content-unavailable pattern.
- Populated state is a normal list.
- Item row HStack spacing **12 dp**.
- Icon width **28 dp**.
- Text stack spacing **3 dp**.
- Title one line; created date caption secondary; favorite star trailing.

### Item detail

- Transcript: scrolling selectable text with normal padding.
- Photo: black canvas, aspect-fit image, optional bottom controls.
- Photo controls: horizontal **16 dp**, bottom **12 dp**; inner padding **10 dp**, max width **360 dp**, material surface radius **14 dp**.
- Video: black canvas/player.
- Audio: centered stack spacing **24 dp**, waveform-circle icon about **72 dp**, prominent Play/Pause.

### Sync sheet

Android may skip iOS’s manual Wi-Fi Settings instructions when automatic Android network binding succeeds, but the visual state progression must stay calm and explicit.

Progress view:

- VStack spacing **24 dp**.
- Progress well **88×88 dp** in thin material.
- Title: title2 semibold; description subheadline secondary; copy spacing **8 dp**.
- Status capsule horizontal **14 dp**, vertical **9 dp**, footnote medium.
- Overall padding **32 dp**, max width **420 dp**.

## 10. Translation

Translation is a full modal page, not a small `AlertDialog`.

### Screen

- Grouped background.
- VStack spacing **16 dp**.
- Max width **680 dp**.
- Horizontal **16 dp**, top **12 dp**, bottom **28 dp**.
- Compact top app bar title `Translate`, `Done` trailing.

### Cards

Reusable translation card:

- Padding **16 dp**.
- Radius **18 dp**.
- Secondary grouped surface.

Engine card:

- Main VStack spacing **14 dp**.
- Heading copy spacing **3 dp**.
- Segmented engine control; disabled while running.
- Settings subgroups spacing **12 dp**.

Settings row:

- Horizontal padding **12 dp**.
- Minimum height **54 dp**.
- Radius **12 dp**.
- Label caption secondary; value subheadline semibold.

Live Translation header:

- HStack spacing **10 dp**.
- Icon well **36×36 dp**, radius **10 dp**, blue 10%.
- Text stack spacing **2 dp**.
- Main CTA uses large prominent full-width button.
- State row spacing **9 dp**.

Transcript block:

- VStack spacing **5 dp**.
- Padding **12 dp**.
- Radius **12 dp**.
- Label caption semibold secondary.
- Source/hearing text body; translated English emphasized at title3.

## 11. Lens

Lens intentionally uses native List/Form composition; do not redesign it into a dashboard.

- Modal page title `Lens`, `Done` trailing.
- Image section has no normal list insets/background.
- Image area minimum height **240 dp**, horizontal outer padding **16 dp**.
- Placeholder/image container radius **24 dp**, quaternary fill.
- Aspect-fit selected image, clipped to 24 dp.
- Preparing/reading progress capsule padding **14 dp**, regular material.

Image section actions:

- Capture with AD Glasses.
- Choose/replace photo.
- Read text locally.
- Orientation / image pixel dimensions as native labeled rows.

Question section:

- `What am I looking at?` quick action.
- 2–4 line text input.
- Voice action.
- Ask AD action with progress state.
- Footer explaining local OCR vs Cloud visual questions.

Result sections use selectable text and native buttons for Speak/Translate.

## 12. Soundbites

Use native Form/List styling.

- Modal title `Soundbite`, `Done` trailing.
- First section: optional title + Start/Stop listening.
- Active listening action uses red; inactive uses orange.
- Footer explains local note behavior.
- Transcript section uses native empty-content state until text exists, then selectable text.
- Save to Library section; disabled with empty transcript.
- Preserve saved/error dialogs.

## 13. Settings

Settings must be a native large modal/page with grouped list hierarchy, not a custom card dashboard.

Root sections:

1. Glasses.
2. Camera & Capture.
3. AD (AI & Models, Capabilities, Voice & Language).
4. Data and access (Privacy, Storage, Permissions, Language/system settings).
5. About.
6. Hardware/GATT diagnostics.
7. Diagnostics.

Android-specific capabilities may differ, but keep this hierarchy. Android system-permission and plugin/provider screens should live under the corresponding section rather than Home.

Child pages (`AI & Models`, profile editor, Web & Maps, Voice & Language, Privacy, Storage, Permissions, About) should be native list/form screens with compact centered app bars.

### Camera & Capture

Native list sections:

- Photography & Vision.
- Video Recording.
- Audio Recording.
- Hardware Architecture.

Wi-Fi architecture row uses HStack spacing **12 dp**, blue Wi-Fi icon, copy stack spacing **2 dp**, vertical **4 dp**.

## 14. Android-only capabilities without visual drift

Android may exceed iOS, but extra capability must be placed where it belongs:

- Classic calls/audio pairing: Device Center / Audio.
- Companion/background presence: Device Center or Settings, not Home.
- Direct call/SMS setup: Settings/Permissions or Assistant capabilities, not Home.
- Notification listener access: Settings/Permissions/Capabilities.
- Debug protocol details, MAC addresses, raw RSSI/dBm, app-ops: Diagnostics only.
- System recognizer UI: keep Android-native.
- Android permission dialogs: request contextually from onboarding/feature entry, not as an unexplained wall before the first screen.

## 15. Existing Android UI that must be removed/reworked

- Temporary black/white vector launcher icon.
- Global indigo/blue/cyan Material palette as the product background identity.
- Home full-width blue/purple gradient Lens banner.
- Home `Phone & text` tile.
- Home `Background link` tile.
- Large bottom device card replacing the iOS floating connection pill.
- AlertDialog Device Center.
- AlertDialog Translation.
- Custom-card Library root.
- Full-width elevated Material Assistant composer.
- Provider/model label inside the Assistant hero; iOS does not put that in the hero.
- Raw Bluetooth MAC address/dBm in normal discovery rows.

## 16. Physical screenshot acceptance matrix

For each screen/state, capture Samsung screenshots and compare against the iOS reference side-by-side.

Required states:

### Welcome
- connecting;
- connection choice;
- permission education.

### Home
- disconnected;
- scanning;
- connected + battery;
- video recording;
- audio recording;
- Ask listening;
- Ask thinking;
- Ask speaking;
- light + dark mode;
- large font one-column grid.

### Assistant
- empty idle;
- empty listening;
- conversation with user + AD bubbles;
- thinking;
- phone voice status;
- glasses voice status;
- composer typed state;
- composer voice state;
- history sheet.

### Device Center
- disconnected + scan;
- discovered JS-01;
- BLE connected;
- Classic pairing;
- calls/media connected;
- voice wake Off/On;
- error state.

### Library
- empty;
- populated;
- sync progress;
- photo detail;
- video detail;
- audio detail.

### Translation
- idle;
- engine/settings state;
- running;
- transcript/translation result;
- error.

### Lens
- empty;
- selected/captured image;
- OCR progress/result;
- visual answer.

### Soundbite
- empty;
- listening;
- transcript;
- saved.

### Settings
- root;
- AI & Models;
- Voice & Language;
- Permissions;
- Camera & Capture;
- Diagnostics.

A screen is accepted only after layout hierarchy, spacing, radii, asset use, typography hierarchy, state copy, and major color/surface treatment match this contract. Platform-owned controls may differ, but the AD-owned composition may not drift.

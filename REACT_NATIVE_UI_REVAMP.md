# AD Glasses React Native UI Revamp

This document is the product UI contract for the React Native migration. It is intentionally stricter than a theme guide: monochrome is only the stage. The design must come from hierarchy, object identity, interaction quality, state transitions, typography, motion and restraint.

## Product hierarchy

1. **The glasses are the physical hero.** When the product needs an object, use the AD glasses imagery. Do not replace the glasses with a generic Bluetooth/device glyph just because the connection is offline.
2. **The phone is the intelligence layer.** The app should feel like memory, AI, automation and control—not a remote-control utility.
3. **Content beats decoration.** A card, icon tile, divider or status chip must clarify grouping/state/action. It is not filler.
4. **Monochrome product chrome.** Black, white and neutral greys define the visual language. Success/warning/error colour is reserved for actual semantic state.
5. **Deep pages may differ in composition, not in authorship.** About can be editorial while Privacy is utilitarian, but spacing, typography, controls, motion and surfaces must still feel like one product.

## Core visual grammar

### Canvas and surfaces

- Canvas: `#F6F6F7`
- Surface: `#FFFFFF`
- Ink: `#050506`
- Graphite: `#151517`
- Grey 900: `#2C2C2E`
- Grey 700: `#636366`
- Grey 500: `#8E8E93`
- Grey 300: `#C7C7CC`
- Grey 200: `#DCDCE0`
- Grey 100: `#EFEFF1`
- Use semantic colours only for a real success/warning/error condition.

### Shape

- Icon tile: 11dp radius
- Controls: 15dp radius
- Standard card: 20dp radius
- Product/hero frame: 28dp radius
- Status chip: pill
- Avoid a screen full of identical rounded rectangles. Large cards are for objects/state; settings groups are for related controls; editorial content can sit directly on the canvas.

### Typography

React Native follows the platform sans family and a compact hierarchy:

- Poster: 44/46 medium
- Display: 34/39 semibold
- Title: 22/28 semibold
- Section: 18/24 semibold
- Card title: 16/22 semibold
- Body: 15/22 regular
- Meta: 13/18 medium
- Micro: 11/14 semibold

The product should not solve hierarchy by bolding everything. Scale, spacing and grouping do more work than weight.

## Motion system

Motion is functional and quiet.

- Instant feedback: 90ms
- Fast state change: 160ms
- Normal UI transition: 280ms
- Deliberate reveal: 420ms
- Hero/process motion: ~620ms+
- Press: small spring to ~0.982 scale plus a short opacity response
- Route change: subtle fade + ~6dp vertical continuity; no theatrical full-screen slides
- Entry sections may stagger in ~40–50ms steps
- Pairing: soft breathing/radar field around the **glasses image**, never around a generic Bluetooth icon
- Prompt: only animate real thinking/audio state
- Recording: playback state changes the transport and waveform emphasis
- Respect Android Reduce Motion through `AccessibilityInfo.isReduceMotionEnabled()`

## Global interaction rules

- Minimum comfortable touch target: 44–48dp
- One visually dominant action per decision area
- Secondary actions use outline/neutral surface rather than competing black fills
- Never label a feature with permission status (`Setup required`) as its identity. Explain permission only when the user acts.
- Loading states preserve layout whenever possible instead of replacing the whole screen with a spinner.
- Empty states explain what will appear and how to create it; they do not blame the user.
- Error messages are local to the failed action and keep recovery obvious.
- Text fields use the same neutral fill/radius and never introduce a second form style.
- Keyboard submission and hardware back behavior must match the visible action model.

---

# Page-by-page product contract

## Welcome — poster archetype

**Purpose:** first emotional impression, not product documentation.

**Elements:**
- `YOUR GLASSES / YOUR AI / YOUR DATA`
- glasses hero object in its own stage
- Connect glasses
- Continue without glasses

**Rules:**
- No AD Glasses logo/name at the top; the physical product already establishes identity.
- No explainer paragraph.
- Never place the glasses behind the statement.
- Statement appears first, glasses second, decisions third.
- Entry reveal is restrained and sequential.

**Status:** React Native implementation active.

## Home — command-center archetype

**Purpose:** answer three things immediately: are my glasses ready, what can I do now, what is active?

**Hierarchy:**
1. small product wordmark/settings action
2. large glasses readiness object
3. four direct actions (Ask, Photo, Video, Translate)
4. secondary actions (vision question, recording, web, Automation)
5. live state only when something is actually active

**Micro details:**
- disconnected state dims the product but never swaps imagery
- battery/storage appear only when real values exist
- press response on action tiles
- no decorative status colour except real connected/live state

**Status:** live native dashboard state wired; further refinement: event-driven updates instead of polling.

## Prompt — conversational archetype

**Purpose:** the detailed phone continuation of the same durable assistant session used by the glasses.

**Hierarchy:**
- compact Prompt identity + New action
- durable conversation
- thinking/error state
- composer fixed at bottom
- web is a mode on the request, not a separate page

**Rules:**
- user bubble may use a subtle grey surface; assistant output should mostly read as content on canvas
- do not fake listening/thinking motion
- New creates a real durable thread
- web toggle must visibly alter composer state
- rich output should favour readable code/media/link blocks over chat-bubble decoration

**Status:** real assistant session, new thread, web request and native orchestrator wired. Remaining: port the full Compose rich-output parser/media treatment and native audio state events.

## AI — intelligence-control archetype

**Purpose:** define how the glasses think and expose capabilities.

**Hierarchy:**
1. `Your AI` editorial lead
2. capabilities
3. Default AI selection
4. Connections

**Capability identity:**
- Translate — Live translation
- Soundbites — Audio to notes
- Timeline — Searchable visual memory
- DayNote — **Daily moments, distilled**
- Cron — Recurring scheduled work
- Automation — Apps & Android actions

**Rules:**
- Cron uses recurring-work/repeat iconography, not a generic clock
- Automation is a feature; accessibility permission is a requirement surfaced only when needed
- provider selection hydrates from native state

**Status:** live provider write/read path; capability toggles invoke native mode runtime. Remaining: hydrate each capability's active state.

## Library — memory-index archetype

**Purpose:** feel like the second-brain archive, not a settings list.

**Hierarchy:**
- editorial Library statement
- Captures / Recordings collections
- Notes & summaries
- Sync entry

**Status:** active.

## Captures — visual-memory archetype

**Rules:**
- actual image content is the visual hero when available
- videos retain a clear play affordance
- names/metadata are secondary
- empty state keeps glasses identity and tells the user to sync

**Status:** real synced media source wired; real photos render inline. Remaining: native/generated video thumbnails and richer date grouping.

## Recordings — audio-memory archetype

**Rules:**
- playback stays inside AD Glasses
- transport has a clear playing state
- waveform is low-contrast at rest, stronger while playing
- time/source metadata remains compact

**Status:** native MediaPlayer bridge added. Remaining: playback progress/scrubbing and transcript expansion on the same card.

## Notes & summaries — reading/editorial archetype

**Rules:**
- date is micro metadata
- title/content dominate
- tap expands long summary rather than navigating to a visually unrelated legacy page
- DayNote/Soundbites should feel like origins of memory, not plugin names

**Status:** real notes source + expandable summaries wired. Remaining: expose full note body where available and source-type filtering.

## Settings — configuration-index archetype

**Purpose:** calm access to configuration without looking like Android Settings.

**Hierarchy:**
1. glasses product card with live connection state
2. Privacy & data
3. General
4. AD Glasses

**Rule:** even disconnected, the device row uses the real sunglasses image—not a generic mark/device icon.

**Status:** live React Native settings family active.

## Privacy — editorial configuration archetype

**Lead:** `Private by default.`

**Controls:** native-backed redaction and transcript storage.

**Rule:** privacy language describes data behavior; avoid vague “secure” marketing claims.

**Status:** real native preferences wired. Remaining: surface additional visual-memory retention/redaction preferences where product-relevant.

## Storage — configuration/status archetype

**Purpose:** explain what occupies private app storage without becoming a file manager.

**Status:** real local model inventory/active model shown; Android storage settings available. Remaining: compute media/app-data byte totals.

## Language — choice-list archetype

**Rule:** list only languages actually supported by native locale resources. Do not invent placeholders.

**Status:** real native `AppLanguage` values wired. Hindi placeholder removed because it is not currently a supported app locale.

## Permissions — capability/status archetype

**Rule:** permission is a supporting state. Camera/mic/Bluetooth/Automation rows show real native grant status; glasses remain the product object elsewhere.

**Status:** live permission state wired. Remaining: offer targeted request/recovery action per row where Android policy permits.

## Device — product-object archetype

**Purpose:** the strongest recurring glasses object after Welcome.

**Hierarchy:**
1. glasses hero stage
2. connection state
3. battery/storage when connected
4. primary connect/disconnect action
5. device tools

**Status:** live dashboard state wired. Remaining: transition connection state from polling to events.

## Pairing — stateful process archetype

**Purpose:** keep the user inside AD Glasses during discovery/connect rather than exposing a legacy scanner screen.

**Rules:**
- breathing scan field surrounds sunglasses image
- real nearby results are sorted by signal
- each row uses a small glasses product image
- unsupported hardware is quiet/disabled rather than becoming a technical error
- connect persists the same native device profile and invokes the proven BLE transport

**Status:** native scanner/classifier/profile/transport bridge + React Native pairing UI implemented. Compatibility `DeviceBindActivity` remains only for old/native routes during migration.

## Sync — stateful process archetype

**Rules:** show connected/offline/transfer state first; details second; one action at bottom.

**Status:** native dashboard transfer state wired. Remaining: visual progress when the native transfer model exposes determinate progress through the bridge.

## Firmware — guarded process archetype

**Rules:** warnings are semantic, not decoration. Preflight readiness must be visible before file selection; cancellation is destructive only while a session exists.

**Status:** React surface active, native action bridge present. Remaining: expose the full OTA state/progress/canStart/canCancel model to React.

## Capability detail — feature archetype

Shared composition for Translate, Soundbites, Timeline, DayNote, Cron and Automation:

1. feature glyph
2. outcome/kicker
3. one clear sentence describing capability
4. on/off state
5. processing/output facts

**Rule:** sameness comes from structure; identity comes from icon, outcome and copy—not coloured feature palettes.

**Status:** native command runtime wired. Remaining: hydrate active state and permission-specific recovery without changing feature identity.

## Relay — configuration/form archetype

**Lead:** remote intelligence has one route.

**Status:** native URL/backend hydrate and save. No fake default form values.

## Local AI — model-management archetype

**Hierarchy:**
- on-device intelligence lead
- installed models and current selection
- import action
- compatible-server configuration

**Status:** installed model state/selection live. File import now opens Android document picker but returns into the React surface, copies into private model storage and selects the imported model.

## Assistant apps — advanced-integration archetype

**Purpose:** clearly secondary compatibility route for installed assistant apps.

**Rule:** never visually compete with configured AI as the default path.

**Status:** React surface active. Remaining: bridge the full native assistant-inspector readiness model instead of explanatory placeholders.

## Advanced — owner/diagnostics archetype

**Rule:** sparse by design. Advanced should not become a dumping ground for normal product settings.

**Status:** React surface active; Android settings handoff retained where system UI is the correct destination.

## About — product/editorial archetype

**Purpose:** intentionally different from utility settings pages.

**Hierarchy:** glasses image, AD Glasses name, `Version alpha`, concise product statement, product facts.

**Rule:** its editorial composition may differ from Privacy/Storage; shared typography/spacing/motion still makes it feel authored by the same system.

**Status:** React surface active.

---

# Engineering boundary during migration

React Native owns:
- all product presentation
- product navigation
- motion/interaction layer
- product-facing form/state composition

Android native continues to own:
- glasses BLE/protocol integrations
- background/foreground services
- Accessibility automation
- capture/media transfer implementation
- OTA/firmware implementation
- local model storage/runtime
- assistant orchestration
- durable database repositories

The bridge should expose product-shaped state/actions. It should **not** duplicate glasses protocol logic in JavaScript.

## Compatibility UI

Existing Compose/Activity screens may remain temporarily because manifests, plugin aliases or old entry points depend on them. They are not the design source of truth once their React route is active. Remove compatibility UI only after the React path has equivalent behavior and automated coverage.

# Remaining finishing pass

Highest-value remaining work after the current migration foundation compiles cleanly:

1. Full Prompt rich-content rendering: code, links, image/video/audio/document cards.
2. Event-driven device/audio/transfer state to replace short polling loops.
3. Video thumbnail extraction and capture date grouping.
4. Recording progress/scrub + transcript expansion.
5. Full note body/source metadata.
6. OTA progress/canStart/canCancel bridge.
7. Assistant-app readiness/verification bridge.
8. Capability active-state hydration and contextual permission recovery.
9. Native permission request actions from the React Permissions page.
10. Remove inactive duplicate React/Compose presentation code once parity tests prove the migration.
11. Screenshot/device QA at compact and large Android widths, font scaling, dark system bars and Reduce Motion.
12. Final micro-pass: truncation, long localized labels, keyboard avoidance, empty/loading transitions, press/accessibility labels and screen-reader order.

This file should be updated whenever a product route changes archetype or a native compatibility surface is retired.

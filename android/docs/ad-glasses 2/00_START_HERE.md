# AD Glasses product package — start here

## What this folder is

This is the single handoff package for designing and prototyping the AD Glasses Android app.

The app is much smaller than the number of reference images suggests. There are only **four everyday destinations**:

1. Home
2. Assistant
3. Library
4. Automations

Everything else is a focused flow opened from those destinations: setup, pairing, a conversation, content detail, Device Center, Sync, Settings, AI configuration, privacy, firmware, or diagnostics. Connected/disconnected/loading/error examples are **states of the same screen**, not additional products or navigation pages.

## Authority order

When documents or reference images disagree, use this order:

1. `design/CANONICAL_UI_SPEC.md` — what the prototype must contain and how screens are grouped.
2. `design/DESIGN_SYSTEM.md` — the one light visual system.
3. `design/INTERACTION_AND_MOTION_SPEC.md` — how navigation, state changes and activity behave.
4. `references/canonical-ui/SCREEN_MANIFEST.md` — the approved visual references and their roles.
5. `references/canonical-ui/screens/` — the canonical screen set.
6. `ai-studio/01_ANDROID_PROTOTYPE_BUILD_BRIEF.md` — how AI Studio should implement the testable prototype.
7. `product/PRODUCT_BLUEPRINT.md` — deeper product, capability, and backend context.
8. `engineering/` — implementation reality and specialist notes.
9. `references/REFERENCE_CATALOG.md` — audit of historical visual exploration.
10. `references/stitch_variations/` and `archive/` — historical material; never treat it as current input.

## What to give Google AI Studio

Upload the curated AI Studio ZIP produced from this folder. Read `ai-studio/00_INPUT_MANIFEST.md`, then select **Android** in AI Studio Build mode and paste the complete prompt in `ai-studio/01_ANDROID_PROTOTYPE_BUILD_BRIEF.md`.

Tell AI Studio to read `00_START_HERE.md` first. The approved references are under `references/canonical-ui/`. Do not upload the raw `android/docs/stitch_ad_glasses/` export or historical Stitch variations: those contain conflicting navigation, visual languages and factual claims. The canonical screenshots establish composition, while the product documents establish behavior and truth.

## Canonical visual references

`references/canonical-ui/` contains a dependency-free interactive reference, 41 rendered phone screens, the true-alpha shared glasses asset and a manifest mapping reusable screen/state families. It already covers all eight automations through four shared detail archetypes. No additional Stitch pass is required for the first Android prototype.

## What the first generated app is

The first result is an installable native Android prototype written in Kotlin and Jetpack Compose. It is navigation-complete and visually realistic, but uses deterministic fake repositories and fixture data.

It must let us test:

- whether the navigation feels natural on a real phone;
- whether Home is useful rather than crowded;
- pairing, connecting, reconnecting, and failure presentation;
- Assistant text, voice, visual, live, web-grounded, memory, and phone-action UI;
- Library, media detail, transcription, and meeting-summary flows;
- all eight automations and their setup forms;
- Sync, owner AI setup, privacy controls, firmware safety, and recovery;
- long text, empty data, permission denial, offline state, and partial success;
- light-mode appearance, motion, touch targets, and accessibility.

It must **not** perform real BLE binding, Wi-Fi transfer, recording, Accessibility actions, cloud calls, provider authentication, media deletion, or firmware flashing. Those integrations are deliberately added later from the repository's proven code.

The current no-glasses validation boundary is recorded in `engineering/HARDWARE_VALIDATION_STATUS.md`.

## Why this order is safer

We first test the full experience with harmless fake state on the emulator and physical phone. After the UI and journeys are accepted, the generated project comes back to this repository. We then connect one real vertical slice at a time to the existing device, media, AI, storage, and automation implementations.

The prototype is not throwaway artwork: reusable Compose components, navigation, UI state models, and screen layouts can be retained. Generated fake hardware/cloud implementations are replaced behind interfaces.

## Historical files

`archive/STITCH_HANDOFF.md`, `archive/STITCH_FINAL_UI_PROMPTS.md`, and `archive/STITCH_EXPORT_PRODUCT_BLUEPRINT.md` are retained only so no earlier thinking is lost. They are not inputs to the build unless a canonical document explicitly points to a small useful detail.

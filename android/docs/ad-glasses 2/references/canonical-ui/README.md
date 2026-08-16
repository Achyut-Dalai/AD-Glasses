# AD Glasses canonical UI reference

This folder is the visual authority for the Google AI Studio prototype. It replaces the unfiltered Stitch variations as the source of screen composition.

## Contents

- `prototype/` — an interactive, dependency-free reference implementation of the canonical layouts and routes.
- `screens/` — 390 × 844 phone renders exported from the prototype.
- `assets/ad-glasses-hero-v1.png` — the shared true-alpha product hero used on Welcome, Home, connection and Device Center.
- `SCREEN_MANIFEST.md` — the purpose and state represented by every exported screen.
- `ASSET_NOTES.md` — provenance and constraints for the generated product render.

The HTML/CSS/JavaScript is design reference code, not Android production code. AI Studio must implement the experience in Kotlin and Jetpack Compose using the canonical documents.

## Preview

Open `prototype/index.html?screen=home` in a browser. Change `home` to any route in `SCREEN_MANIFEST.md`. Buttons with a defined destination also navigate between references.

## Visual authority

Use this folder for:

- hierarchy;
- information grouping;
- component proportions;
- light-only color and surface treatment;
- the four-tab shell;
- focused-flow behavior;
- state, progress, approval and recovery presentation;
- motion intent.

Do not infer hardware or backend behavior from the reference prototype. Product truth remains in `../../design/CANONICAL_UI_SPEC.md` and the repository engineering notes.

## Stitch relationship

The raw Stitch export remains useful as archived exploration. It must not be uploaded to AI Studio with this canonical set because it contains competing navigation, dark screens, invented device claims, duplicate blueprints and incompatible visual languages.

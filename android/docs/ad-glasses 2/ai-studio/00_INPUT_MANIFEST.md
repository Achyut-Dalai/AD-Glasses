# Google AI Studio input manifest

## Upload this package, not the raw Stitch export

AI Studio receives the curated `ad-glasses` package containing product/design documents and `references/canonical-ui/`.

Do not upload:

- `android/docs/stitch_ad_glasses/`;
- historical `archive/` files;
- `references/stitch_variations/`;
- the two duplicate Stitch blueprints;
- old HTML exports as Android implementation code;
- existing application source in the initial visual-prototype generation.

## Authority order

1. `00_START_HERE.md`
2. `design/CANONICAL_UI_SPEC.md`
3. `design/DESIGN_SYSTEM.md`
4. `design/INTERACTION_AND_MOTION_SPEC.md`
5. `references/canonical-ui/SCREEN_MANIFEST.md`
6. `references/canonical-ui/screens/`
7. `ai-studio/01_ANDROID_PROTOTYPE_BUILD_BRIEF.md`
8. `product/PRODUCT_BLUEPRINT.md`
9. `engineering/`

If a sample screenshot and product document differ, the document wins for behavior and factual claims. The screenshot wins only for the composition described in the screen manifest.

## Expected Android output

- native Kotlin and Jetpack Compose;
- single activity with reusable route families;
- exact four-item bottom navigation;
- fixture repositories and deterministic Prototype controls;
- no real hardware, server, search, credential, deletion or firmware implementation;
- buildable source and installable debug APK;
- no screenshot-as-background implementation.

## First request

Select Android in AI Studio Build mode, upload the curated ZIP, then paste the complete prompt from `01_ANDROID_PROTOTYPE_BUILD_BRIEF.md`.

## Build strategy

AI Studio should keep one compiling project while completing the checkpoints in the build brief. If it stops before the complete app is generated, use `03_ITERATION_PROMPTS.md` in order rather than starting a second project.

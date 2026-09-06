# AI Studio continuation and review prompts

Use these only in the same Android project after the initial build prompt. Do not ask AI Studio to restart or create an alternative visual direction.

## 1 — Foundation audit

> Before adding more features, run the Gradle build and inspect the generated project against `00_START_HERE.md`, the canonical UI spec, design system, motion spec and canonical screen manifest. Confirm that the project is native Kotlin/Jetpack Compose, has exactly Home/Assistant/Library/Automations in bottom navigation, uses reusable components, and has deterministic fake repositories. Remove any dark theme, profile/account flow, fifth tab, subscription UI, fabricated device facts, real permission/hardware/network implementation, screenshot-backed screen, or competing design system. Keep the project compiling and list the corrections made.

## 2 — Complete route and state coverage

> Complete every screen family and fixture-driven state in `design/CANONICAL_UI_SPEC.md` and `references/canonical-ui/SCREEN_MANIFEST.md`. Use the canonical screenshots as composition references, not bitmap backgrounds. Ensure all nine canonical journeys are navigable. Implement all eight automations with the shared templates and their unique controls. Add any missing denied, empty, offline, incompatible, partial, recovery, long-copy and reduced-motion states. Do not create new bottom tabs or unrelated page styles. Build and fix compilation after the changes.

## 3 — Interaction and motion pass

> Apply `design/INTERACTION_AND_MOTION_SPEC.md` across the project. Keep motion restrained, semantic and reduced-motion aware. Verify persistent Activity Banner ownership, safe cancellation, progress stages, approval/result transitions, form validation and Back behavior. Remove decorative floating, glow, shimmer, particles, confetti, bouncing or perpetual animation. Run the build and relevant tests afterward.

## 4 — Visual consistency and accessibility pass

> Compare the running app route by route with `references/canonical-ui/screens/` and `design/DESIGN_SYSTEM.md`. Correct hierarchy, spacing, component reuse, compact branding, transparent product rendering, light-only surfaces and restrained blue usage. Test 200% font scaling, long fixture copy, keyboard visibility, TalkBack labels/focus order, 48dp targets, contrast and reduced motion. Do not hard-code screenshot sample values into images. Build again and report unresolved issues.

## 5 — Final functional prototype audit

> Use Prototype controls to execute all nine journeys in success and failure modes. Verify resettable deterministic data, no stranded routes, honest partial outcomes, stable Back navigation, and safe destructive confirmation. Confirm that no real BLE, Wi-Fi Direct, camera/microphone recording, Accessibility action, external network, API key, account, payment, deletion or firmware behavior exists. Run the available Gradle build/tests, fix crashes and compile errors, then provide the APK/source export, route/component inventory, test results and known limitations requested in the build brief.

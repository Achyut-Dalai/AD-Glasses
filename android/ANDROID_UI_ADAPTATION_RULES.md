# AD Glasses Android UI Adaptation Rules

This document overrides any literal dimensional-parity interpretation in `UI_PARITY_SPEC.md`.

The native iOS app remains the product and visual reference for hierarchy, identity, copy, feature priority, surface intent, motion intent, and overall composition. Android must not copy iOS coordinates mechanically.

## Geometry rules

1. iOS authored dimensions are reference proportions and starting points, not Android requirements.
2. Android window size, system insets, display cutouts, font scale, IME, and navigation mode take precedence over source-platform coordinates.
3. Never use a hard height for a text-bearing component when `heightIn(min = ...)`, intrinsic measurement, or scroll-safe layout can preserve the same design intent.
4. Phone layouts must be validated at narrow widths and with enlarged Android font scale. Home collapses to one column when text scaling makes multi-column content cramped.
5. Hero compositions may change orientation. If copy and artwork no longer fit comfortably side-by-side, stack them vertically while preserving hierarchy.
6. Top and bottom chrome must consume system insets exactly once. Do not compensate for inset bugs with arbitrary padding.
7. Keyboard-driven screens must use Android IME/inset behavior instead of fixed vertical placement.
8. Large modal product flows should be full-screen on phones when an alert/dialog width would create cramped or nested scrolling.
9. Maximum content widths are for tablets/large windows; phone content should use available width with consistent gutters.
10. A layout is accepted only after physical Samsung review confirms that nothing clips, overlaps system UI, or feels unnaturally compressed.

## Glass/material rules

1. Do not imitate Apple material with opacity alone.
2. AD-owned glass surfaces use real backdrop blur where supported, semantic tint, restrained highlights, fine borders, and low-contrast depth.
3. On platforms/devices where blur is unavailable, fall back gracefully to semantic translucent/opaque surfaces without changing information hierarchy.
4. Glass is an accent material, not a reason to make every screen custom. Settings, lists, and infrastructure-heavy screens should remain Android-native and quiet.
5. Chroma stays restrained. Indigo/blue/cyan identify AD and specific features; they should not become a generic colorful dashboard background.

## Acceptance principle

Target the same AD Glasses product, not the same screenshot coordinates.

The desired result is: recognizable iOS product identity + Android-native fit, behavior, accessibility, and system integration.

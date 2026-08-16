---
version: "alpha"
name: AD Glasses Studio Graphite
description: Light-only neutral Android design with a floating transparent product render, compact typography, restrained electric blue, and purposeful motion.
colors:
  primary: "#111318"
  on-primary: "#FFFFFF"
  accent: "#3156D3"
  on-accent: "#FFFFFF"
  accent-soft: "#EEF2FF"
  light-background: "#F6F7F9"
  light-surface: "#FFFFFF"
  light-surface-raised: "#ECEEF2"
  light-text: "#111318"
  light-text-muted: "#62666D"
  light-outline: "#D8DBE1"
  success: "#18794E"
  warning: "#9A5B00"
  error: "#C4323C"
typography:
  heading-lg:
    fontFamily: Inter
    fontSize: 1.5rem
    fontWeight: 650
    lineHeight: 1.875rem
    letterSpacing: -0.02em
  heading-md:
    fontFamily: Inter
    fontSize: 1.25rem
    fontWeight: 650
    lineHeight: 1.625rem
    letterSpacing: -0.015em
  title:
    fontFamily: Inter
    fontSize: 1rem
    fontWeight: 600
    lineHeight: 1.375rem
  body:
    fontFamily: Inter
    fontSize: 1rem
    fontWeight: 400
    lineHeight: 1.5rem
  body-sm:
    fontFamily: Inter
    fontSize: 0.875rem
    fontWeight: 400
    lineHeight: 1.25rem
  label:
    fontFamily: Inter
    fontSize: 0.875rem
    fontWeight: 600
    lineHeight: 1.125rem
rounded:
  xs: 8px
  sm: 12px
  md: 16px
  lg: 20px
  pill: 999px
spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  xxl: 32px
components:
  button-primary-light:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    height: 48px
    padding: 16px
  button-secondary-light:
    backgroundColor: "{colors.light-surface}"
    textColor: "{colors.light-text}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    height: 48px
    padding: 16px
  card-light:
    backgroundColor: "{colors.light-surface}"
    textColor: "{colors.light-text}"
    rounded: "{rounded.md}"
    padding: 16px
  status-active-light:
    backgroundColor: "{colors.accent-soft}"
    textColor: "{colors.accent}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    height: 30px
    padding: 10px
---

## Overview

Studio Graphite is a light-only system pairing Apple-like product calm with Vercel-like precision without copying either brand. The interface is premium, minimal, highly legible, and led by the glasses rather than decorative UI.

## Colors

Use white, graphite, and cool gray for almost everything. Electric blue is limited to selection, focus, links, and progress. Primary actions are graphite-black. There is no dark theme and no theme switcher.

## Typography

Use Inter with compact headings and comfortable body text. Avoid oversized marketing headlines inside the app; hierarchy comes from weight, alignment, and space.

## Layout

Use a 4px grid, 16px phone gutters, 24px between major sections, aligned edges, and minimum 48px touch targets. Prefer a few clear surfaces over a collection of small dashboard cards.

## Elevation & Depth

Use tonal separation and fine outlines. Shadows are soft and rare. Avoid glow, glassmorphism, and stacks of floating cards.

## Shapes

Use 12–16px radii for most controls and cards. Reserve pills for compact status and filters; avoid bubbly geometry.

## Components

Use crisp monochrome icons, restrained rectangular buttons, quiet status chips, and one open floating-product hero. The Home app bar uses one compact approved symbol-and-wordmark lockup when space allows, or the symbol alone at constrained widths; never place a separate logo beside a repeated or oversized plain-text product heading. The hero background is exactly the page background; shadow, halo, status, and motion are separate editable UI layers.

The canonical lockup is a reduced bridge mark: two rounded lens outlines joined by one short bridge, with a single restrained blue focal dot in the upper-right of the right lens. Pair it with the title-case wordmark `AD Glasses` in the normal app shell. Use the symbol alone only when width is genuinely constrained. This identity remains code-native/vector; never rasterize the wordmark into screenshots.

Use `../references/canonical-ui/assets/ad-glasses-hero-v1.png` as the shared replaceable prototype product asset. It has verified alpha transparency. The asset represents the product category, not a factual rendering of every supported device family.

## Motion

Use `INTERACTION_AND_MOTION_SPEC.md` as the motion authority. Default timings are 100 ms press feedback, 180 ms small state changes, 280 ms normal screen/component transitions and 420 ms emphasized product/sheet transitions. Progress animation follows measured state. Reduced motion removes translation, scale, pulsing and scanning rings while preserving concise crossfades and meaningful progress.

## Do's and Don'ts

Use one isolated high-resolution glasses render with a true transparent alpha background. Do not allow a rectangular image background, white matte, studio backdrop, vignette, baked card, or visible asset edge. Use restrained 200–420ms motion for entry, state transition, press feedback, and real activity progress; avoid perpetual floating, spinning, shimmer, particles, or animated gradients. Avoid dark app surfaces, teal themes, giant headings, excessive pills, and different visual languages between pages.

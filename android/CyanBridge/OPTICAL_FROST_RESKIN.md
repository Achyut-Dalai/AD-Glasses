# AD Glasses — Optical Frost reskin

This branch implements the light-only AD Glasses UI reskin.

## Product direction

The phone UI should feel like a companion to a precise optical instrument rather than a generic settings application. The visual system stays quiet and hardware-first, with enough fine contrast and information density to avoid the soft/overscaled feeling of the previous card-heavy UI.

There is intentionally **no dark mode** in this product theme.

## Core tokens

- Canvas: `#F6F8F9`
- Surface: `#FFFFFF`
- Secondary surface: `#EDF2F4`
- Primary ink: `#152126`
- Secondary ink: `#64757B`
- Outline: `#D4E0E3`
- AD cyan: `#087F8C`
- Cyan soft: `#E5F4F5`
- Primary CTA graphite: `#172126`
- Green: connected/success only
- Amber: warning/risk only
- Red: error/destructive only

Normal card radius is 16dp. Large 20–28dp radii are reserved for product/hero surfaces. Small UI text is kept at 12–13sp or larger.

## Page grammar

- **Hero/state:** Home, Device Center, Sync, Pairing.
- **Capability:** AI and Assistant Apps.
- **Content:** Prompt, Library, Captures, Recordings, Notes.
- **Settings/forms:** Settings, Privacy, Storage, Language, Permissions, Relay, Local AI, Advanced.
- **Editorial/product:** Welcome and About.

Different page types deliberately do not use the same card vocabulary.

## Perceived-resolution fixes

- Small brand usage now renders from `ad_glasses_mark_vector.xml` instead of the PNG source mark.
- Bottom-nav labels and metadata typography are larger and more legible.
- Normal surfaces have a thin outline instead of relying on white-on-near-white contrast and shadow.
- Capture thumbnails request 1440×900 instead of 960×600 before being displayed full-width.
- Prompt composer/suggestions use crisp outlines; user message width responds to available screen width.
- Icon family and sizing are more consistent.

The product hero image remains raster by design. Its source resolution should still be checked against the final physical display size.

## Visual verification checklist

1. Capture an emulator screenshot and inspect the PNG at exactly **100% zoom**. If text/vector icons are sharp there but soft in the emulator window, the host preview is being rescaled.
2. Compare Home and Prompt on a physical 1080p or 1440p Android phone.
3. Check compact and tall phone profiles; especially Welcome, Prompt empty state and Pairing.
4. Check Android font scale around 1.15–1.30× for truncation.
5. Verify 48dp navigation/back/settings touch targets feel comfortable.
6. Confirm semantic color use: cyan for product/selection, green for ready/connected, amber only for real warning/risk, red only for error/destructive actions.
7. Review Home hierarchy: Ask AI should be dominant; Capture secondary; Smart tools tertiary.
8. Review Settings density with both connected and disconnected glasses.
9. Review Library with empty and populated captures/recordings/notes.
10. Review Relay/Local AI with long URLs/model names and Assistant Apps with bridge setup incomplete.

## Scope

This reskin changes Compose presentation and one drawable only. Device protocols, persistence, AI orchestration, media queries, transfer logic and repository behavior are intentionally outside the change scope.

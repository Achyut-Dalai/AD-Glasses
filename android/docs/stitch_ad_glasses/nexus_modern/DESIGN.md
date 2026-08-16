---
name: Lumina Graphite
colors:
  surface: '#f8f9ff'
  surface-dim: '#d7dae2'
  surface-bright: '#f8f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f1f3fc'
  surface-container: '#ebeef6'
  surface-container-high: '#e5e8f1'
  surface-container-highest: '#dfe2eb'
  on-surface: '#181c22'
  on-surface-variant: '#424752'
  inverse-surface: '#2d3137'
  inverse-on-surface: '#eef1f9'
  outline: '#727783'
  outline-variant: '#c2c6d4'
  surface-tint: '#095db7'
  primary: '#004389'
  on-primary: '#ffffff'
  primary-container: '#005ab4'
  on-primary-container: '#c0d5ff'
  inverse-primary: '#aac7ff'
  secondary: '#465f88'
  on-secondary: '#ffffff'
  secondary-container: '#b6d0ff'
  on-secondary-container: '#3f5881'
  tertiary: '#723200'
  on-tertiary: '#ffffff'
  tertiary-container: '#964400'
  on-tertiary-container: '#ffc9ac'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d6e3ff'
  primary-fixed-dim: '#aac7ff'
  on-primary-fixed: '#001b3e'
  on-primary-fixed-variant: '#00458d'
  secondary-fixed: '#d6e3ff'
  secondary-fixed-dim: '#aec7f6'
  on-secondary-fixed: '#001b3d'
  on-secondary-fixed-variant: '#2e476f'
  tertiary-fixed: '#ffdbc9'
  tertiary-fixed-dim: '#ffb68c'
  on-tertiary-fixed: '#321200'
  on-tertiary-fixed-variant: '#753400'
  background: '#f8f9ff'
  on-background: '#181c22'
  surface-variant: '#dfe2eb'
  status-connected: '#1275e2'
  stage-halo-start: rgba(10, 115, 224, 0.15)
  background-alt: '#F6F7F9'
  surface-glass: rgba(249, 249, 255, 0.8)
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '800'
    lineHeight: 32px
    letterSpacing: -0.025em
  headline-md:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '700'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
  label-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
  label-xs:
    fontFamily: Inter
    fontSize: 10px
    fontWeight: '600'
    lineHeight: 12px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  container-margin: 1rem
  section-gap: 2rem
  element-gap: 0.75rem
  touch-target-min: 40px
---

## Brand & Style

Lumina Graphite is a refined, tech-forward design system tailored for high-end wearables and smart hardware ecosystems. It blends **Corporate Modern** reliability with subtle **Glassmorphism** to evoke a sense of premium precision and "lightweight" digital utility.

The brand personality is sophisticated and unobtrusive, designed to sit comfortably in the background while providing high-clarity information. It targets tech-savvy professionals who value sleek aesthetics and seamless integration between physical hardware and digital control. The UI should feel airy and responsive, utilizing soft glows and depth to guide the eye without overwhelming the user.

## Colors

The palette is anchored by **Deep Cobalt** (#005ab4) for primary actions and brand presence, paired with a sophisticated **Graphite Neutral** (#181c22) for high-contrast elements like icons and text. 

- **Primary:** Used for brand identity, active navigation states, and primary call-to-action buttons.
- **Surface Tones:** A range of cool greys and off-whites (derived from #f9f9ff) create "Surface Container" levels that define hierarchy without relying on heavy borders.
- **Functional Accents:** A specific "Connected Blue" (#1275e2) is used for status indicators, while "Error Red" (#ba1a1a) is reserved for critical alerts and recording states.
- **Glassmorphism:** The `surface-glass` color is applied to sticky headers and overlays to maintain spatial awareness of content scrolling beneath.

## Typography

The system uses **Inter** exclusively to lean into its utilitarian, highly-legible character. 

- **Hierarchy:** We utilize heavy weights (ExtraBold 800) for brand titles to create a distinct anchor point. 
- **Functional Body:** Content uses Medium (500) weights for better legibility on mobile screens against varying background containers.
- **Information Density:** Small labels (10px - 12px) are used for metadata like timestamps and battery percentages, ensuring the core interface remains clean and uncluttered.
- **Spacing:** Tight letter-spacing is applied to the display roles to maintain a modern, "compact" feel for the hardware interface.

## Layout & Spacing

Lumina Graphite uses a **Fluid Grid** model with a focus on safe margins for mobile-first interaction. 

- **Margins:** A standard 16px (1rem) side margin is applied to all main content blocks.
- **Stage Area:** A centered "Device Stage" utilizes vertical white space (approx 220px height) to hero the hardware, using a radial halo effect to create a focal point.
- **Grids:** Quick actions are organized into a 4-column grid for thumb-reachability. Activity cards use a horizontal-scroll (overflow-x) layout with snap points to handle variable content density without vertical bloat.
- **Safe Areas:** Bottom navigation accounts for mobile OS safe areas (pb-safe) to ensure navigation remains accessible.

## Elevation & Depth

Hierarchy is established through **Tonal Layering** and **Ambient Shadows** rather than sharp lines.

- **Base Layer:** The background uses a subtle off-white/cool-grey (#F6F7F9) to make white containers "pop."
- **Surface Containers:** Cards and buttons use `surface-container-lowest` (#ffffff) with a `shadow-sm` (low-blur, low-opacity) to appear slightly lifted.
- **Glass Effects:** Top app bars utilize a backdrop-blur (12px) with 80% opacity to signify they are the highest layer in the Z-index, persistent above the scrolling content.
- **The Stage Halo:** A soft radial gradient `rgba(10, 115, 224, 0.15)` creates a "pseudo-3D" floor for hardware images, providing depth without a literal shadow.

## Shapes

The shape language is consistently **Rounded**, reflecting the ergonomic nature of wearable hardware.

- **Standard Cards:** Use a 16px (rounded-2xl) radius to feel friendly and modern.
- **Small Buttons/Actions:** Use a 12px radius.
- **Pills:** Status indicators and specific toggle elements use a `full` (pill) radius to distinguish them from interactive card elements.
- **Interactive States:** Buttons should provide a subtle scale-down effect (active:scale-95) to mimic physical tactility.

## Components

- **Buttons (Primary Icon):** Circular 40px targets. Dark backgrounds (#181c22) with white icons. Hover states shift to a lighter graphite (#2d3037).
- **Cards (Activity):** 160px fixed-width horizontal cards. They consist of a 3:2 aspect ratio image top and a padded text area bottom.
- **Status Pills:** Small (height ~24px) containers with a 1px border and a leading colored dot to represent live states.
- **Toggles:** Minimalist pill-shaped tracks with a white circular thumb. Use `primary` blue for the "on" state.
- **Navigation (Bottom):** Active states are highlighted with a `secondary-container` background and `on-secondary-container` text color, shaped as a squircle (rounded-xl).
- **Input/Search:** Should follow the `surface-container-low` style with soft internal padding and 12px roundedness.
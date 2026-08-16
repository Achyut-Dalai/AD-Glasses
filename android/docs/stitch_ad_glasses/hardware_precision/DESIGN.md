---
name: Hardware Precision
colors:
  surface: '#f9f9ff'
  surface-dim: '#d4daea'
  surface-bright: '#f9f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f0f3ff'
  surface-container: '#e8eefe'
  surface-container-high: '#e2e8f8'
  surface-container-highest: '#dde2f2'
  on-surface: '#161c27'
  on-surface-variant: '#4c4546'
  inverse-surface: '#2a313d'
  inverse-on-surface: '#ecf0ff'
  outline: '#7e7576'
  outline-variant: '#cfc4c5'
  surface-tint: '#5e5e5e'
  primary: '#000000'
  on-primary: '#ffffff'
  primary-container: '#1b1b1b'
  on-primary-container: '#848484'
  inverse-primary: '#c6c6c6'
  secondary: '#005db8'
  on-secondary: '#ffffff'
  secondary-container: '#4090fe'
  on-secondary-container: '#002958'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#191c1e'
  on-tertiary-container: '#828486'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e2e2e2'
  primary-fixed-dim: '#c6c6c6'
  on-primary-fixed: '#1b1b1b'
  on-primary-fixed-variant: '#474747'
  secondary-fixed: '#d6e3ff'
  secondary-fixed-dim: '#aac7ff'
  on-secondary-fixed: '#001b3e'
  on-secondary-fixed-variant: '#00458d'
  tertiary-fixed: '#e1e2e4'
  tertiary-fixed-dim: '#c5c7c8'
  on-tertiary-fixed: '#191c1e'
  on-tertiary-fixed-variant: '#444749'
  background: '#f9f9ff'
  on-background: '#161c27'
  surface-variant: '#dde2f2'
  canvas: '#F6F7F9'
  surface-control: '#FFFFFF'
  electric-blue: '#1978E5'
  graphite-black: '#000000'
  success-teal: '#008080'
  warning-amber: '#FFBF00'
  cool-gray: '#717785'
typography:
  headline-lg:
    fontFamily: Hanken Grotesk
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Hanken Grotesk
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Hanken Grotesk
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Hanken Grotesk
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-lg:
    fontFamily: Hanken Grotesk
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
  label-md:
    fontFamily: Hanken Grotesk
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
  label-sm:
    fontFamily: Hanken Grotesk
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 4px
  gap-xs: 4px
  gap-sm: 8px
  margin-page: 16px
  touch-target: 48px
  hero-stage: 220px
---

## Brand & Style
The brand personality is **quietly capable, private, and precise**. It functions as a high-quality "hardware companion," prioritizing technical transparency over marketing flair. The design system adopts a **Minimalist / Architectural** style that mirrors the physical product’s "Satin Graphite" aesthetic.

The interface must evoke a sense of **calm reliability** and **"Hardware Truth."** This is achieved through a "Light Mode Only" architecture that favors structural clarity, airy whitespace, and high-precision typography. The UI is a direct reflection of the physical device state, avoiding "AI clichés" in favor of professional utility and outcome-oriented language.

## Colors
This design system utilizes a strict **Light Mode Only** palette to maintain architectural consistency. 

- **Primary Action (Graphite Black):** Reserved for the strongest actions, primary buttons, and critical headings.
- **Brand/Interactive (Electric Blue):** Used for progress, selection, focus states, and active navigation indicators.
- **Surface Strategy:** The global canvas uses a soft gray (#F6F7F9), while all meaningful controls and content cards are elevated using pure white (#FFFFFF) to create a clear "layering" effect without heavy shadows.
- **Status Indicators:** Success is represented by a professional Teal, and active/warning states (like recording) use a vibrant Amber.

## Typography
The system uses **Hanken Grotesk** for its sharp, contemporary, and technical feel, providing "Vercel-like" precision. 

- **Scale:** The hierarchy is compact. Large "marketing" sizes are avoided in favor of functional, screen-stable sizes.
- **Accessibility:** All typographic levels must support 200% font scaling.
- **Wordmark:** Title bars on the Home screen are replaced by the continuous-line glasses logo mark (24dp) rather than text.
- **Labels:** Bottom navigation labels are always visible to ensure navigational clarity.

## Layout & Spacing
The layout employs an **Open Space** philosophy for the "Device Stage" (Hero area) and a structured grid for functional content.

- **The Device Stage:** A 200–240dp dedicated region for the parallax product render. It floats directly on the #F6F7F9 canvas.
- **Functional Grid:** A standard 4-column mobile grid with 16px side margins.
- **Touch & Rhythm:** A strict minimum touch target of **48dp** is enforced for all interactive elements. Internal gaps between independent controls are a minimum of **8dp**.
- **Bottom Navigation:** A fixed bar containing 4 equally weighted items.

## Elevation & Depth
Depth is conveyed through **Tonal Elevation** and **Crisp Hairlines** rather than heavy drop shadows.

- **Tiers:** Pure white (#FFFFFF) surfaces sit subtly above the #F6F7F9 canvas.
- **Halos:** "Faint ambient halos" in cool-gray or electric-blue are used behind the product render to indicate connection status.
- **Separators:** 1px hairlines are used for section dividers and the top boundary of the bottom navigation.
- **Activity Banners:** These float just above the bottom navigation using a soft contact shadow to denote temporary presence.

## Shapes
The shape language is defined as **Rounded**, utilizing squircle-inspired curves that feel organic yet precise.

- **Adaptive Icons:** Masks follow a medium-high corner radius.
- **Interactive Elements:** Buttons and cards use a 0.5rem (8px) radius as the default.
- **Status Pills:** Status indicators (Connected, Recording) use a "Pill-shaped" full radius for quick recognition.

## Components
- **Primary Buttons:** Pure Graphite Black (#000000) background with white text. On press, they scale to 0.98.
- **Product Stage:** A containerless area for the glasses render. Features 4-6dp of parallax movement between the hardware and its shadow on scroll.
- **Status Pills:** Small, high-visibility chips. "Connected" uses a teal background; "Recording" uses an amber pulse effect.
- **Navigation Bar:** Pure white background with a 1px top hairline. Active items are indicated by an Electric Blue icon and a subtle tonal pill indicator.
- **Input Fields:** Pure white background, 1px cool-gray border, shifting to Electric Blue on focus.
- **Privacy Chips:** Distinct labels indicating "On device" vs "Cloud" to reinforce the privacy-first brand pillar.
- **Activity Specifics:** A vertical 6-stage timeline for firmware updates and a non-looping recording pulse.
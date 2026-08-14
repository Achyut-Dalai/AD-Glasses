# AD Glasses — Google Stitch UI handoff

## Master prompt

Copy the complete prompt below into Google Stitch as a new mobile app project.

```text
Design a polished native Android app named “AD Glasses”. It is a private control center and AI companion for smart glasses. The phone performs the heavy AI work while the glasses provide camera, microphone/audio, Bluetooth LE, Wi-Fi media transfer, battery/storage state, and optional display features.

This is a real app redesign, not a marketing landing page. Create high-fidelity Android mobile screens at a Pixel-size viewport, using modern Material 3 patterns, edge-to-edge layout, safe insets, and a persistent five-item bottom navigation bar:

1. Glasses
2. Chats
3. Media
4. Plugins
5. Settings

Non-negotiable product rules:
- Do not show subscriptions, upgrades, prices, payment, billing, donations, trials, premium locks, quotas, or “Pro”. All features are available to the owner.
- Do not include any Walking Aid feature, mobility hazard detection, navigation aid, or related setup.
- Do not use CyanBridge branding, its logo, donation copy, or author account/server language.
- Cloud AI must be described as infrastructure controlled by the user: relay URL, optional API token, optional email, and separate chat/image/task model choices.
- Privacy controls must be explicit. Clearly distinguish on-device processing from data sent to the user-configured cloud relay.
- Do not redesign or invent the BLE/Wi-Fi protocol. UI actions dispatch to existing native app logic.

Visual direction:
- Calm, technical, premium, and highly legible—closer to a trusted hardware companion than a social app.
- Dark-first design with a refined light theme variant.
- Near-black/navy surfaces, soft elevated cards, restrained cyan/teal accent, and one warm status accent. Avoid neon cyberpunk, excessive gradients, glassmorphism everywhere, or dense developer-dashboard styling.
- Rounded corners around 16–20dp, clear hierarchy, generous spacing, strong status colors, and large touch targets.
- Use a simple abstract eyeglasses/device glyph as a temporary logo placeholder. Keep it replaceable.
- Meet accessible contrast, minimum 48dp tap targets, scalable typography, TalkBack-friendly labels, and never rely on color alone for state.

Create these screens and state variants:

A. First-run welcome and device setup
- AD Glasses name and concise promise: “Your glasses. Your AI. Your data.”
- Device families: HeyCyan-compatible, Eyevue, Meta Ray-Ban, Meizu MYVU / Star Air, and generic audio glasses.
- A step-based permission flow for nearby Bluetooth devices, notifications, microphone, photos/media, and optional accessibility. Explain why each permission is needed before requesting it.
- Let the user skip device pairing and explore the app.

B. Glasses dashboard — disconnected state
- A prominent device status card showing Disconnected, device family Unknown, and a primary “Scan for glasses” action.
- Secondary reconnect action.
- Short explanation that pairing uses BLE and media sync may temporarily use Wi-Fi Direct or a glasses hotspot.
- Quick access to device selection and troubleshooting.

C. Glasses dashboard — connected and transferring state
- Device name/family, Connected status, battery percentage, storage, and transport indicators for BLE and Wi-Fi.
- Media sync card with flow name, photo/video/audio counts, determinate or indeterminate progress, current detail, start/cancel actions, and a clear recovery message for transfer failure.
- Meeting Spark Notes shortcut card with recording state and Start, Stop, and Summarize actions.
- Compact AI controls for phone assistant versus custom AI provider, voice question versus image question, and image quality.
- An “Advanced device controls” collapsed section for device-specific controls, live preview, OTA/firmware, diagnostics, and Wi-Fi ADB debug. Advanced/risky actions must look secondary and require confirmation.

D. Chats list and chat thread
- Searchable chat list with recent conversations, timestamps, short previews, new-chat action, and empty state.
- Chat thread with user/assistant bubbles, image/audio attachments, model badge, send/stop state, retry, and attachment picker.
- Model selector supports on-device models and models returned by the user’s cloud relay.
- Make generation, offline, relay-unconfigured, and relay-error states clear without mentioning payment or quotas.

E. Media
- Segmented views for recordings and synced photos/videos.
- Meeting recording cards show duration, source, date, transcription status, and actions to play, transcribe, view transcript, rename, or delete.
- Synced media gallery supports photo/video thumbnails, selection, filtering, empty state, and transfer-in-progress banner.

F. Plugins
- Native plugin cards with icon, name, one-sentence purpose, enabled switch, status badge, and settings action.
- Include Local Agent, Meeting Spark Notes, Live Caption Relay, Hands-Free Translator, Errand Brain, Auto Diary, Auto Audio, and Visual Diary.
- Do not include Walking Aid.
- Include a clear permissions/data-access summary before enabling a plugin.
- Community plugins are optional; do not mention prizes, subscriptions, donations, or monetization.

G. Settings
- A top Cloud AI card showing Setup or Ready and the configured relay hostname when available.
- Sections: Appearance, Language, AI provider and image questions, Memory & Privacy, Transcripts, Local Data, Support/Diagnostics, and FAQ.
- AI provider choices: Tasker, On-device Models, Cloud AI.
- Memory modes: Private Local, Encrypted Sync, Fast Cloud Memory, Confidential Cloud Beta. They must all be selectable, but unfinished backend modes should be labeled “Backend not configured” rather than locked.
- Controls for memory source eligibility, OCR retention, vault lock/passphrase, transcript storage, name redaction, export/import, and clear local data.

H. Cloud AI configuration
- Relay base URL field.
- Optional API token field with reveal/hide and secure-storage explanation.
- Optional account email.
- Separate Chat/Requests, Image/Questions, and Automation/Tasks model fields or selectors, defaulting to “auto”.
- Save and “Save & test connection” actions.
- Test result states: not configured, testing, connected with model count, authentication failed, server unreachable, and invalid URL.
- State plainly: “No subscription or author account is required.”

I. Reusable dialogs and states
- Bluetooth scan sheet with discovered devices, signal strength, device family, scanning indicator, and retry.
- Permission rationale dialog.
- Destructive confirmation dialog.
- Offline, empty, loading, success, warning, and recoverable-error components.
- Snackbar/toast patterns and a compact persistent transfer/recording banner.

Interaction and handoff requirements:
- Use consistent names for reusable components and screens.
- Keep content areas scrollable and bottom navigation fixed.
- Show realistic data, but do not embed secrets or real API tokens.
- Provide dark and light tokens for color, typography, shape, elevation, and spacing.
- Create separate frames for the important state variants, especially disconnected/connected/transferring, cloud setup/testing/success/error, and empty/populated media.
- Make the output practical to translate into Jetpack Compose. Prefer standard Android components and avoid effects that require a web runtime.
- Export the complete project/code and all original image assets. Do not flatten text into images.
```

## Recommended Stitch follow-up prompts

Run these one at a time after the first generation:

1. `Audit every generated screen against the non-negotiable rules. Remove every reference to Pro, premium, subscription, billing, donation, payment, trial, Walking Aid, CyanBridge, and the original author’s server. List what you changed.`

2. `Make the Glasses dashboard less crowded. Keep connection, battery, storage, transfer progress, and the active plugin shortcut immediately visible. Move device-specific, OTA, live-preview, diagnostics, and debug controls into a clearly labeled collapsed Advanced section.`

3. `Create complete interaction variants for Cloud AI: not configured, invalid URL, testing, connected, authentication failed, and server unreachable. Preserve the same layout to avoid UI jumping between states.`

4. `Perform an Android accessibility pass: 48dp targets, WCAG AA contrast, font scaling to 200%, content descriptions, keyboard focus order, error text not dependent on color, and safe layouts for long translated strings.`

5. `Prepare a developer handoff for Jetpack Compose. Name components and design tokens consistently, identify every reusable component, and export source plus unflattened assets.`

## What to return for integration

Download/export the Stitch project and provide the exported ZIP or its files. Screenshots alone are useful for comparison but are not enough for implementation. Keep any Stitch-generated backend or mock networking out of the handoff; the repository already owns BLE, Wi-Fi, media, AI routing, storage, and plugin logic.

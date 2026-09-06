# Native iOS migration plan

## Product audit

The Android product has three everyday destinations:

- **Home** shows glasses readiness and starts voice, camera, video, translation, soundbite, and audio actions.
- **AI** manages conversations, history, attachments, optional web search, and Cloud AI profiles.
- **Library** organizes synced captures, recordings/transcripts, and notes/summaries.

Device setup, media sync, firmware, permissions, privacy, storage, language, and provider configuration are supporting flows rather than peer destinations.

The former iOS screen combined phone transcription, BLE discovery, provider status, and build information in one long dashboard. `GlassesManager` also constructed and exposed concrete HeyCyan and Meta providers, and its public actions were HeyCyan-specific.

## Problems worth correcting

1. Concrete provider knowledge in common state made every new integration a manager and UI change.
2. A single screen obscured the product's Home, Voice/AI, and Library information architecture.
3. Fixed-delay scanning and an unbounded connection wait produced weak recovery behavior.
4. The UI could not distinguish a visible product feature from a verified provider capability.
5. Material was applied to nearly every card instead of being reserved for navigation and compact controls.
6. The Android application mixes mature product flows with Android-only services and incomplete bridge experiments; those cannot be treated as iOS protocol specifications.

## Incremental migration

### 1. Native shell and provider registry — implemented

- Use native `TabView`, `NavigationStack`, sheets, toolbars, semantic colors, Dynamic Type, and safe-area controls.
- Inject registered providers at the app composition root.
- Let `GlassesManager` operate only on `GlassesProvider`, provider IDs, summaries, and capabilities.
- Route scanning, connection, disconnection, state, and errors through the selected provider.
- Keep unsupported hardware actions visible but clearly unavailable.
- Use native Liquid Glass for the compact voice control on iOS 26, with Material/opaque accessibility fallbacks.

### 2. Local product data

- Add repositories for captures, recordings/transcripts, notes, and conversations.
- Keep storage independent of vendor transport so providers only import/export domain objects.
- Move phone transcription into recording sessions instead of treating one in-memory transcript as the library.

### 3. Verified HeyCyan media transfer

- Add a media-transfer capability adapter only after the HeyCyan GATT control/notify characteristic identifiers are verified.
- Preserve the confirmed sequence: request transfer mode over BLE, wait for the `0x08` device-IP notification (treat `0x09` as an error), establish a usable Wi-Fi route, then use the documented HTTP/IPFS surface.
- Use Network framework routing, bounded retries/timeouts, cancellation, progress, and explicit recovery states. Never hard-code a session IP, hotspot, or media filename.

### 4. Device capabilities

- Add device-information, camera, and glasses-audio adapters one at a time from verified HeyCyan behavior.
- Introduce a capability-specific protocol only when its first real implementation is added.
- Feed verified glasses audio into the existing `SpeechTranscribing` boundary rather than creating a second speech stack.

### 5. AI and polish

- Add conversation persistence and configured AI services behind an application service boundary.
- Add onboarding, permission education, localization, and real-device accessibility validation.
- Add focused unit tests for provider registration, routing, state transitions, parsing, and transfer cancellation.

## Protocol boundary

`heycyan-core/core-connectivity` contains reusable Android connectivity behavior, while its current BLE, audio, and data modules are boundary markers. The retained Android HeyCyan contract and supported implementation are references; demo downloaders, guessed parsers, unrelated device bridges, and Android-only services are not protocol truth.

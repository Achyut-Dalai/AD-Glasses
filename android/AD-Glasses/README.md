# AD Glasses — Android app

This is the primary Android application for AD Glasses.

## Stack

- Kotlin
- Jetpack Compose
- Coroutines / Android lifecycle APIs
- Java 17-compatible Android toolchain
- HeyCyan vendor integration
- Android-only Moonshine speech dependency where used by the current voice pipeline

The native iOS application is a separate SwiftUI project under `../../ios/`; this Android project is not an iOS host.

## Hardware architecture

HeyCyan is the first-class glasses path. Keep vendor BLE/Wi-Fi/media details below the app's hardware/provider boundary so Compose features work with capabilities rather than raw commands.

Meta support is optional and should remain isolated behind its own provider/SDK boundary. Do not spread Meta SDK types through general feature code.

Unsupported glasses families should not be restored as demo trees, SDK submodules, or app-wide conditionals. A future vendor should be added as a new adapter/provider.

For the confirmed HeyCyan BLE-to-Wi-Fi media flow, read:

- [`../AGENTS.md`](../AGENTS.md)
- [`../../WIFI_TRANSFER_ARCHITECTURE.md`](../../WIFI_TRANSFER_ARCHITECTURE.md)

## AI and tool architecture

The Android app contains the mature assistant stack, including cloud/local model routing, structured external tools, spatial/provider integrations, meetings, media flows, and Android assistant-role behavior.

Keep boundaries explicit:

- UI does not call provider HTTP APIs directly.
- Router/planner output stays semantic rather than encoding concrete vendor/API endpoints.
- Provider configuration failures are explicit instead of silently falling back to unrelated services.
- Hardware capability state is separate from vendor identity.

## Build

From this directory:

```bash
./gradlew assembleDebug
```

Unit tests:

```bash
./gradlew testDebugUnitTest
```

Useful targeted builds/tests should be preferred while a feature is moving; run the full app validation when the feature reaches a release/checkpoint stage.

## Development rules

- Preserve existing Android behavior unless the task targets that behavior.
- Keep Activity-owned coroutines, recognition, image, and foreground-service work lifecycle-aware.
- Do not log secrets, transcripts, tokens, media credentials, or private paths.
- Do not guess proprietary hardware commands or service identifiers.
- Keep iOS-specific UX/signing choices out of Android feature code.

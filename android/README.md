# AD Glasses — Android workspace

The active Android application lives in [`AD-Glasses/`](AD-Glasses/) and is built with Kotlin + Jetpack Compose.

HeyCyan is the primary glasses path. The Android app is currently the mature implementation for assistant features, media transfer, meetings, provider routing, and device integration. Keep those flows native to Android while the iOS app evolves independently in SwiftUI.

## Active Android pieces

- `AD-Glasses/` — application project.
- `glasses_sdk_20250723_v01.aar` — HeyCyan vendor artifact used by the supported integration.
- `HeyCyanOfficialApp/` — retained reference material for verified protocol behavior.
- `AGENTS.md` — concise HeyCyan BLE/Wi-Fi media-transfer contract and troubleshooting notes.
- `docs/` — product/UI reference material for the Android app.

Moonshine remains an Android-only speech dependency through the root `third_party/moonshine` submodule. The native iOS app does not use it.

## Build

```bash
cd android/AD-Glasses
./gradlew assembleDebug
```

Run unit tests with:

```bash
./gradlew testDebugUnitTest
```

The project targets Java 17-compatible bytecode. Use the JDK expected by the Gradle wrapper/toolchain configured in the repository.

## HeyCyan development

Before changing pairing, BLE commands, Wi-Fi handoff, media listing/download, or deletion behavior, read [`AGENTS.md`](AGENTS.md) and [`../WIFI_TRANSFER_ARCHITECTURE.md`](../WIFI_TRANSFER_ARCHITECTURE.md).

Do not guess proprietary commands, service UUIDs, hotspot credentials, OTA endpoints, or firmware behavior. Treat undocumented capabilities as unavailable until verified against the supported hardware/reference implementation.

## Credentials and OTA material

Do not place OTA tokens, API keys, guest credentials, or authenticated curl examples in repository documentation. If a vendor endpoint must be investigated, keep credentials outside the repository and document only the protocol behavior that is safe and necessary for the product.

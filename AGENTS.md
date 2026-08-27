# AGENTS.md — AD Glasses

## Purpose

AD Glasses contains native Android and iOS companion apps for smart glasses. HeyCyan is the primary hardware architecture. Meta remains a provider boundary that can be completed when its verified integration is available.

## Active project layout

- `android/AD-Glasses/` — shipped Android app. Kotlin + Jetpack Compose. Preserve its existing runtime unless the task explicitly targets Android.
- `heycyan-core/` — reusable HeyCyan-oriented architecture and protocol boundaries.
- `ios/` — native Swift + SwiftUI app. No React Native, Flutter, Kotlin Multiplatform host, Moonshine model, or bundled vendor framework.
- `third_party/moonshine/` — retained only because the Android app still uses Moonshine.
- `android/AGENTS.md` — verified HeyCyan BLE/Wi-Fi media-transfer notes.

The old cross-vendor `examples/` tree, KMP/QCSDK iOS host, and unsupported-glasses research bundles are intentionally retired. Do not restore vendor demo dumps just to add a future glasses family; add a small provider/adapter behind the existing platform boundary instead.

## Glasses scope

- Use plain vendor identities such as `HeyCyan` and `Meta`.
- Capability or configuration state must not be encoded into a vendor name (for example, do not name a provider `meta-experimental`).
- Do not guess proprietary BLE/GATT identifiers or vendor commands. Keep unverified protocol work isolated behind the provider adapter.
- New glasses families must be addable without rewriting SwiftUI/Compose feature code.

## iOS rules

- Prefer Apple frameworks and Swift concurrency.
- Keep speech behind `SpeechTranscribing` and glasses behind `GlassesProvider`.
- Design for iPhone safe areas and the home indicator; do not port Android system-navigation assumptions into SwiftUI.
- Keep signing and packaging ordinary so the app can be signed outside App Store distribution workflows when needed.

## Android data transfer

Review `android/AGENTS.md` and `WIFI_TRANSFER_ARCHITECTURE.md` before changing the confirmed HeyCyan media-transfer path.

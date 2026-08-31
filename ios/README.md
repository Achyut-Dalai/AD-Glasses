# AD Glasses — Native iOS

`ios/` is the native iPhone implementation of AD Glasses.

## Product rules

- Swift + SwiftUI only.
- No React Native or Flutter.
- No Kotlin Multiplatform host.
- No Moonshine on iOS.
- No bundled QCSDK or other vendor binary framework.
- HeyCyan is the primary glasses integration.
- Meta is a normal vendor/provider boundary; the current build simply reports it as not configured because no Meta SDK is bundled.
- New glasses vendors must be added behind `GlassesProvider` rather than leaking vendor logic into SwiftUI/features.

## iPhone UI direction

The iOS app is not a visual port of Android. It uses native navigation, typography, sheets, materials, and safe areas.

The main voice action is placed with SwiftUI's safe-area layout so it stays above the iPhone home indicator. There is no Android-style three-button/system-navigation assumption and no custom bottom tab bar unless the product eventually has enough top-level destinations to justify one.

## Speech-to-text

`SpeechTranscribing` isolates the rest of the app from Apple's speech engine.

- On iOS 26 with the modern Speech framework, `SpeechAnalyzerTranscriber` uses `SpeechAnalyzer` + `SpeechTranscriber` for Apple-native transcription.
- On iOS 17–25, `LegacySpeechTranscriber` uses `SFSpeechRecognizer` and requests on-device recognition where the selected locale/device supports it.

Both implementations currently consume the iPhone microphone. A verified HeyCyan audio transport can later feed the same speech abstraction instead of creating a second transcription stack.

## Phone wake word

Phone voice activation uses the open-source `LiveKitWakeWord` Swift package. Wake-word inference is local: the package runs ONNX models with ONNX Runtime and uses the CoreML execution provider by default. AD Glasses does not require a wake-word API key or hosted wake-word account.

The app stores one imported LiveKit/openWakeWord-compatible `.onnx` classifier in Application Support. Custom classifier training happens outside the iOS app with LiveKit's open-source training/export workflow; the exported classifier is then imported from Settings.

AD Glasses owns microphone/session continuity around the detector so an already-running foreground wake-word session can hand off cleanly to Apple Speech when the phone is locked or the user has switched apps.

## Glasses architecture

```text
SwiftUI features
      |
GlassesManager
      |
registered GlassesProvider instances
   /       \
HeyCyan     Meta
   |         |
CoreBluetooth   verified adapter when configured
```

`HeyCyanGlassesProvider` provides the native BLE discovery/connection foundation. Discovery remains intentionally broad until verified HeyCyan service/manufacturer identifiers are documented. It does **not** guess GATT UUIDs or send unverified vendor commands.

`MetaGlassesProvider` keeps the vendor seam stable without shipping a Meta SDK. It returns a clear not-configured state until a real integration is added.

To add another glasses family later:

1. Create `Integrations/<Vendor>/<Vendor>GlassesProvider.swift`.
2. Conform it to `GlassesProvider`.
3. Keep vendor SDK/protocol details inside that directory.
4. Register it at the application composition root. `GlassesManager` and feature screens do not change.

See `MIGRATION_PLAN.md` for the Android product audit, architectural findings, and staged capability plan.

## Installation and signing

The repository does not ship a signed IPA or signing identity. Build optimization and signing
capabilities are separate choices; **Release does not mean App Store**.

The project provides these shared configurations:

- `Debug Personal`: unoptimized, includes `DEBUG` and `AD_PERSONAL_TEAM_BUILD`, and has no Hotspot
  Configuration entitlement.
- `Release Personal`: optimized with normal Release settings, includes `AD_PERSONAL_TEAM_BUILD`,
  and has no Hotspot Configuration entitlement. This is the normal daily-use configuration for a
  free Apple Personal Team, direct Xcode installation, or later SideStore-oriented workflows.
- `Release Entitled`: optimized, excludes `AD_PERSONAL_TEAM_BUILD`, and attaches
  `ADGlasses.entitlements` for automatic `NEHotspotConfigurationManager` joining. Select it only
  with a provisioning team/profile that supports Hotspot Configuration.

Both Personal configurations use the existing manual media-transfer handoff: the app displays the
glasses SSID/password, the user joins it in Settings, and BLE/device-IP plus HTTP verification resume
the transfer. The entitled configuration preserves the automatic join implementation.

The iOS target uses Swift Package Manager for LiveKit WakeWord and its ONNX Runtime dependency. These are build dependencies only; phone wake-word detection remains local and does not require a paid service account.

Avoid adding App Store-only assumptions to core app features.

## Build

Open `ADGlasses.xcodeproj` in Xcode. The deployment target is iOS 17. Xcode resolves the Swift packages on first open.

For an optimized Personal Team build, use:

```bash
xcodebuild \
  -project ios/ADGlasses.xcodeproj \
  -scheme "ADGlasses Release Personal" \
  -sdk iphonesimulator \
  -configuration "Release Personal" \
  CODE_SIGNING_ALLOWED=NO \
  build
```

In Xcode, choose the `ADGlasses Release Personal` scheme and the connected iPhone, then Run. The
default `ADGlasses` scheme remains the engineering scheme: it Runs/Tests with `Debug Personal` and
Profiles/Archives with `Release Personal`. `ADGlasses Entitled` is the opt-in automatic Wi-Fi scheme.

The current GitHub workflow for this target is `.github/workflows/ios-native.yml`. The old KMP/QCSDK workflow has been retired.

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

## Glasses architecture

```text
SwiftUI features
      |
GlassesManager
      |
GlassesProvider
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
4. Register it with `GlassesManager` when the integration is real.

## Installation and signing

The project is kept as an ordinary iOS application target with no third-party package dependency. The repository does not ship a signed IPA or signing identity. Keep bundle/signing settings user-controlled so a development build can later be signed and installed with the user's preferred tooling, including SideStore-oriented workflows, without changing the app architecture.

Avoid adding App Store-only assumptions to core app features.

## Build

Open `ADGlasses.xcodeproj` in Xcode. The deployment target is iOS 17.

When the app reaches a validation checkpoint, the command-line build is:

```bash
xcodebuild \
  -project ios/ADGlasses.xcodeproj \
  -scheme ADGlasses \
  -sdk iphonesimulator \
  -configuration Debug \
  CODE_SIGNING_ALLOWED=NO \
  build
```

The current GitHub workflow for this target is `.github/workflows/ios-native.yml`. The old KMP/QCSDK workflow has been retired.

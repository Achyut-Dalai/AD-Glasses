# AD Glasses — Native iOS

This directory is the clean native iOS implementation of AD Glasses.

## Product rules

- Swift + SwiftUI only.
- No React Native or Flutter.
- No Kotlin Multiplatform host.
- No Moonshine on iOS.
- No bundled vendor SDK/framework.
- HeyCyan is the primary glasses integration.
- Meta is retained only as an experimental, SDK-free integration seam.
- New glasses vendors must be added behind `GlassesProvider` rather than leaking vendor logic into UI/features.

The previous KMP host, `QCSDK.framework`, QCSDK demo application, Objective-C demo sources and vendor PDF are intentionally not carried forward.

## Speech-to-text

`SpeechTranscribing` isolates the rest of the app from Apple's speech engine.

- On iOS 26 with a compiler that includes the modern Speech framework, `SpeechAnalyzerTranscriber` uses `SpeechAnalyzer` + `SpeechTranscriber`. The language model is managed by iOS and runs on device rather than being bundled into the app.
- On iOS 17–25 (or an older Xcode toolchain), `LegacySpeechTranscriber` uses `SFSpeechRecognizer`. It forces on-device recognition when the selected locale/device supports it; otherwise Apple may provide recognition through its system service.

Both implementations currently consume the iPhone microphone. A future HeyCyan audio transport should feed audio through the same speech abstraction rather than creating a second transcription stack.

## Glasses architecture

```text
SwiftUI features
      |
GlassesManager
      |
GlassesProvider
   /       \
HeyCyan    Meta (experimental)
   |
CoreBluetooth transport
```

`HeyCyanGlassesProvider` currently provides the native BLE discovery/connection foundation. Discovery is intentionally broad until verified HeyCyan service/manufacturer identifiers are documented in the repository. It does **not** guess GATT UUIDs or send unverified vendor commands.

When verified protocol details are available, add them inside `Integrations/HeyCyan` while preserving the `GlassesProvider` contract.

To add another glasses family later:

1. Create `Integrations/<Vendor>/<Vendor>GlassesProvider.swift`.
2. Conform it to `GlassesProvider`.
3. Keep vendor SDK/protocol details inside that directory.
4. Register it with `GlassesManager` only when the support is ready.

## Build

Open `ADGlasses.xcodeproj` in Xcode. The deployment target is iOS 17. The project has no third-party package dependencies.

Command-line check:

```bash
xcodebuild \
  -project ios/ADGlasses.xcodeproj \
  -scheme ADGlasses \
  -sdk iphonesimulator \
  -configuration Debug \
  CODE_SIGNING_ALLOWED=NO \
  build
```

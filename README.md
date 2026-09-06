# AD Glasses

AD Glasses is a native smart-glasses companion project with two platform apps:

- **Android:** Kotlin + Jetpack Compose, currently the most complete product surface.
- **iOS:** Swift + SwiftUI, using Apple-native Bluetooth, audio, and speech APIs.

HeyCyan is the primary supported glasses architecture. Meta is kept as a clean provider boundary without bundling its SDK. Other vendor demo trees and protocol research are intentionally not part of the active product anymore.

## Start here

| Goal | Path |
| --- | --- |
| Build or change the Android app | [`android/AD-Glasses/`](android/AD-Glasses/) |
| Work on confirmed HeyCyan transport/media behavior | [`android/AGENTS.md`](android/AGENTS.md) |
| Work on reusable HeyCyan architecture | [`heycyan-core/`](heycyan-core/) |
| Build or change the native iOS app | [`ios/`](ios/) |

## Hardware scope

### HeyCyan

HeyCyan is the first-class hardware path. The Android app contains the mature BLE plus Wi-Fi media-transfer implementation. Shared protocol/architecture work should stay behind the existing HeyCyan boundaries so UI and AI features do not depend on raw vendor commands.

### Meta

Meta is represented as a vendor/provider in the app architecture, but no Meta SDK is bundled in the repository. Until a verified integration is configured, the provider reports that it is unavailable rather than pretending to support capabilities it cannot execute.

Adding another glasses family later should mean adding an adapter/provider, not rewriting the app shell.

## Android

The active Android project lives at `android/AD-Glasses/` and uses Kotlin, Jetpack Compose, and Java 17+ tooling. Existing Android AI, media, meeting, assistant, provider, and HeyCyan flows remain the mature implementation and should not be removed as part of iOS development.

Build from the Android project directory:

```bash
cd android/AD-Glasses
./gradlew assembleDebug
```

Run unit tests with:

```bash
./gradlew testDebugUnitTest
```

Moonshine remains an Android-only dependency where the Android speech pipeline requires it.

## iOS

The iOS app lives at `ios/` and is native SwiftUI. It does not use React Native, Flutter, Kotlin Multiplatform, Moonshine, or the old QCSDK binary/demo host.

Current native building blocks:

- SwiftUI for UI and navigation.
- CoreBluetooth for the HeyCyan transport foundation.
- AVFoundation for audio capture/routing.
- Apple Speech APIs behind `SpeechTranscribing` for speech-to-text.
- `GlassesProvider` as the vendor boundary.

The UI is designed around iPhone safe areas and the home indicator instead of copying Android navigation chrome.

See [`ios/README.md`](ios/README.md) for architecture and build notes.

## Repository map

| Path | Purpose |
| --- | --- |
| `android/AD-Glasses/` | Android application. |
| `android/glasses_sdk_20250723_v01.aar` | HeyCyan Android vendor artifact used by the supported path. |
| `android/HeyCyanOfficialApp/` | HeyCyan protocol/reference material. |
| `heycyan-core/` | Reusable HeyCyan-oriented modules and boundaries. |
| `ios/` | Native SwiftUI iOS application. |
| `third_party/moonshine/` | Android-only Moonshine dependency. |
| `WIFI_TRANSFER_ARCHITECTURE.md` | HeyCyan transfer architecture/reference. |

The old `examples/` vendor dump, MyVu submodule, MemoMind/XGIMI/Even/Mentra research prompts, EyeVue notes, and KMP/QCSDK iOS workflow are intentionally retired from the active tree.

## Architecture rules

- UI/features depend on capability interfaces, not vendor SDK calls.
- Keep provider details inside the platform's provider/service layer.
- Do not guess proprietary commands, UUIDs, endpoints, or capabilities.
- Keep Android and iOS native to their platforms; share architecture and behavior, not UI framework code.
- Prefer a small stable contract that lets a future glasses adapter be added without rewriting app features.

## CI

- `.github/workflows/android-app.yml` validates the Android app.
- `.github/workflows/android-toolchain-updates.yml` tracks Android tooling updates.
- `.github/workflows/ios-native.yml` is the native SwiftUI build check.

The retired KMP/QCSDK iOS workflow is no longer part of the project.

## Privacy and safety

- Keep pairing, recording, transfer, microphone, and notification permissions explicit.
- Avoid logging tokens, transcripts, media paths, or credentials.
- Do not send unverified protocol or firmware commands to personal hardware.
- Treat a hardware capability as unavailable until it has a verified implementation and physical-device validation.

## Licensing

This repository contains vendor reference material and binary artifacts for the supported HeyCyan path. Their presence does not imply that the vendor components are open source or redistributable. Review the applicable vendor terms before redistribution.

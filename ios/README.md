# HeyCyan Glasses SDK - iOS

iOS SDK for controlling HeyCyan smart glasses via Bluetooth Low Energy (BLE).

## CyanBridge KMP Host

The existing Xcode project contains `CyanBridgeKMPHost`, a simulator-targeted SwiftUI host for CyanBridge's shared Kotlin Multiplatform framework. It renders the shared dashboard, Chats, Media, Plugins, and Settings destinations. `QCSDKDemo` remains an isolated vendor/device reference target. Read [CYANBRIDGE_KMP_IOS.md](CYANBRIDGE_KMP_IOS.md) before building either target.

## Quick Start

1. Open `QCSDKDemo.xcodeproj` in Xcode
2. Select `CyanBridgeKMPHost` to build the shared Kotlin shell on an iOS simulator
3. Select `QCSDKDemo` only when testing the vendor BLE path on a physical iOS device

Without macOS, run `python3 scripts/verify_kmp_host.py` from this directory to check the project wiring structurally. It does not replace an Xcode build or simulator/device validation.

## GitHub Actions CI

An automated iOS workflow (`.github/workflows/ios-kmp-host.yml`) runs on the `compose-material3-kmp-v2` branch and can be dispatched manually. It builds `CyanBridgeShared.framework` for `iosSimulatorArm64`, compiles the unsigned `CyanBridgeKMPHost` app, launches it in a simulator, and captures a screenshot as a build artifact. See [`CYANBRIDGE_KMP_IOS.md`](CYANBRIDGE_KMP_IOS.md) for complete details.

No Apple Developer Program membership or physical Mac is required to trigger or inspect CI results — only the GitHub Actions macOS runner provided by GitHub.

## Documentation

See the main [README](../README.md) for complete documentation, API reference, and usage examples.

## Requirements

- iOS 15.0+ for `CyanBridgeKMPHost`
- Xcode 15.0+ recommended
- Swift 5.0+ or Objective-C
- Physical iOS device for Bluetooth and vendor SDK validation

## Support

For technical support or questions about the iOS SDK, please see our GitHub issues or contact the HeyCyan development team.

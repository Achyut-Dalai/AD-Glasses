# HeyCyan Glasses SDK - iOS

iOS SDK for controlling HeyCyan smart glasses via Bluetooth Low Energy (BLE).

## CyanBridge KMP Host

The existing Xcode project contains `CyanBridgeKMPHost`, a simulator-targeted SwiftUI host for CyanBridge's shared Kotlin Multiplatform framework. It currently exercises portable defaults and meeting-summary formatting only. `QCSDKDemo` remains an isolated vendor/device reference target. Read [CYANBRIDGE_KMP_IOS.md](CYANBRIDGE_KMP_IOS.md) before building either target.

## Quick Start

1. Open `QCSDKDemo.xcodeproj` in Xcode
2. Select `CyanBridgeKMPHost` to build the shared Kotlin shell on an iOS simulator
3. Select `QCSDKDemo` only when testing the vendor BLE path on a physical iOS device

Without macOS, run `python3 scripts/verify_kmp_host.py` from this directory to check the project wiring structurally. It does not replace an Xcode build or simulator/device validation.

## Documentation

See the main [README](../README.md) for complete documentation, API reference, and usage examples.

## Requirements

- iOS 15.0+ for `CyanBridgeKMPHost`
- Xcode 15.0+ recommended
- Swift 5.0+ or Objective-C
- Physical iOS device for Bluetooth and vendor SDK validation

## Support

For technical support or questions about the iOS SDK, please see our GitHub issues or contact the HeyCyan development team.

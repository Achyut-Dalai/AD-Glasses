# CyanBridge KMP iOS Host

`CyanBridgeKMPHost` is the simulator-targeted iOS host for the `:shared` Kotlin Multiplatform framework. `QCSDKDemo` remains an isolated vendor/device reference target. Neither target is a claim of feature parity or vendor framework support.

## Current Scope

- Builds and links `CyanBridgeShared.framework` directly from Gradle through an Xcode run-script phase.
- Provides a simulator-targeted SwiftUI shell that calls the shared `CyanBridgeSharedBootstrap` entry point and renders portable appearance, navigation, and meeting-summary formatting defaults.
- Shares appearance models, semantic icons, navigation destinations, chat models, immutable chat-thread presentation state, meeting-summary contracts, deterministic Markdown formatting, and the offline rule-based summarizer.
- Leaves CoreBluetooth, NetworkExtension, Photos, audio, StoreKit, local inference, and QCSDK.framework calls in native adapters.

The Objective-C QCSDK demo remains protocol evidence. It is not the new app architecture and must not become a second copy of Android's state machines.

## Mac Setup

1. Install Xcode and a Java 17+ JDK. Android Studio's bundled JBR is suitable.
2. From `android/CyanBridge`, run the framework task once for the appropriate simulator or device destination:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew -PenableAppleTargets=true :shared:linkDebugFrameworkIosSimulatorArm64
```

3. Open `ios/QCSDKDemo.xcodeproj` in Xcode and select the `CyanBridgeKMPHost` scheme to build the KMP shell on a simulator.
4. The Xcode build phase invokes:

```bash
./gradlew -PenableAppleTargets=true :shared:embedAndSignAppleFrameworkForXcode
```

5. Confirm the simulator renders the CyanBridge KMP foundation screen with the shared `cyan` accent profile and `CHATS` initial destination.
6. Confirm the shared meeting-summary preview has stable Markdown headings and does not require a native transport or persistence adapter.

Use `linkDebugFrameworkIosX64` on Intel simulators and `linkDebugFrameworkIosArm64` for a physical device.

## Required Hardware Validation

- Confirm the framework links on current Xcode for arm64 device and simulator targets.
- Confirm `QCSDK.framework` has a compatible arm64 slice and can scan, connect, issue commands, and transfer media on real hardware.
- Validate Bluetooth, hotspot joining, cleartext local HTTP, Photos, microphone, background modes, and keyboard/safe-area behavior.
- Replace the demo's retry-heavy media flow only after recording successful device behavior. Never log hotspot credentials.
- Do not reuse Android Play Billing or web checkout assumptions. iOS billing requires a separate StoreKit decision.

## Known Limits

- Linux can configure the Apple targets and compile shared common code, but cannot link or run iOS binaries. A Mac with Xcode is required for those checks.
- Until macOS is available, run `python3 ios/scripts/verify_kmp_host.py` from the repository root to confirm that only `CyanBridgeKMPHost` references the shared framework and that the host has no vendor transport imports.
- `QCSDK.framework` is presently an opaque static archive from the vendor demo. Its inspected objects are arm64 only; simulator platform compatibility, current Xcode compatibility, license, and device behavior remain unverified. `CyanBridgeKMPHost` intentionally does not link it.
- `GlassesWiFiHandler` contains a legacy hard-coded hotspot-password workaround. It is reference-only until physical-device testing proves a safe replacement.

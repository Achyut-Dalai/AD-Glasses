# CyanBridge KMP iOS Host

`CyanBridgeKMPHost` is the simulator-targeted iOS host for the `:shared` Kotlin Multiplatform framework. `QCSDKDemo` remains an isolated vendor/device reference target. Neither target is a claim of feature parity or vendor framework support.

## Current Scope

- Builds and links `CyanBridgeShared.framework` directly from Gradle through an Xcode run-script phase (or via GitHub Actions CI).
- Embeds a Compose Multiplatform `ComposeUIViewController` via `UIViewControllerRepresentable` in SwiftUI. Both Android and iOS render from the same shared `@Composable` screens in `shared/commonMain`.
- Shares appearance models, semantic icons, navigation destinations, chat models, immutable chat-thread presentation state, meeting-summary contracts, deterministic Markdown formatting, the offline rule-based summarizer, and shared Compose Multiplatform UI screens.
- iOS simulator targets use a dynamic framework (`isStatic = false`) for Skiko compatibility; the device target (`iosArm64`) uses a static framework.
- Leaves CoreBluetooth, NetworkExtension, Photos, audio, StoreKit, local inference, and QCSDK.framework calls in native adapters.
- A shared `CyanBridgeKMPHost.xcscheme` is tracked in the Xcode project for reproducible CI builds.

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

5. Confirm the simulator renders the shared CMP `CyanBridgeApp` composable (Material 3 theme, shared navigation shell, shared screens).
6. Confirm the shared meeting-summary preview has stable Markdown headings and does not require a native transport or persistence adapter.

Use `linkDebugFrameworkIosX64` on Intel simulators and `linkDebugFrameworkIosArm64` for a physical device.

## GitHub Actions CI

A hosted macOS workflow (`.github/workflows/ios-kmp-host.yml`) validates the KMP iOS stack on every push to the migration branch. It runs:

1. **Static structure check** — `ios/scripts/verify_kmp_host.py` confirms the Xcode project wiring is correct and `CyanBridgeKMPHost` is isolated from vendor transport code.
2. **Portable shared tests** — `./gradlew :shared:portabilityTest` runs KMP common tests.
3. **Apple-Silicon simulator framework link** — `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` produces `CyanBridgeShared.framework`.
4. **Unsigned Xcode build** — `xcodebuild -scheme CyanBridgeKMPHost` compiles the SwiftUI host against the shared framework without code signing.
5. **Simulator smoke test** — A fresh iPhone 16 simulator is created, the app is installed and launched, and a screenshot is captured and uploaded as a build artifact.

The workflow triggers automatically on changes to KMP sources, Gradle configuration, iOS host files, or the workflow itself. It can also be dispatched manually from the GitHub Actions tab (`workflow_dispatch`).

No Apple Developer Program membership is required for simulator-only validation. The workflow uses GitHub's `macos-14` (Apple Silicon) runner with Xcode 16.2 and Java 17.

## Required Hardware Validation

- Confirm the framework links on current Xcode for arm64 device and simulator targets.
- Confirm `QCSDK.framework` has a compatible arm64 slice and can scan, connect, issue commands, and transfer media on real hardware.
- Validate Bluetooth, hotspot joining, cleartext local HTTP, Photos, microphone, background modes, and keyboard/safe-area behavior.
- Replace the demo's retry-heavy media flow only after recording successful device behavior. Never log hotspot credentials.
- Do not reuse Android Play Billing or web checkout assumptions. iOS billing requires a separate StoreKit decision.

## Compose Multiplatform Integration

The iOS host renders shared `@Composable` screens from `shared/commonMain` via a `ComposeUIViewController`. The SwiftUI host wraps this view controller using `UIViewControllerRepresentable`:

```swift
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```

The `MainViewController()` function is exported from the Kotlin/Native framework and creates a `ComposeUIViewController` that renders the shared `CyanBridgeApp` composable.

### Dynamic vs Static Framework

CMP on iOS uses Skiko (Skia) for rendering. Skiko ships as a static library (`.a`) for device targets but as a dynamic library (`.dylib`) for simulator targets. The `:shared` module is configured accordingly:

- `iosArm64` (device): `isStatic = true` — static framework, no `.dylib` embedding needed.
- `iosSimulatorArm64` (simulator): `isStatic = false` — dynamic framework, Skiko `.dylib` is embedded in the `.app` bundle.
- `iosX64` (Intel simulator): `isStatic = false` — same as above.

The `embedAndSignAppleFrameworkForXcode` Gradle task handles both cases transparently. No Xcode project changes are needed when switching between static and dynamic frameworks.

### Theme Discipline

Both Android and iOS share the same CMP `MaterialTheme` from `org.jetbrains.compose.material3`. The `:app` module also uses CMP Material 3 (not Jetpack Material 3) to ensure the theme provider is the same type across both platforms. This guarantees identical rendering of colors, typography, shapes, and components.

## Known Limits

- Linux can configure the Apple targets and compile shared common code, but cannot link or run iOS binaries. A Mac with Xcode is required for those checks.
- On Linux, run `python3 ios/scripts/verify_kmp_host.py` from the repository root to statically confirm project wiring. The full build pipeline requires the GitHub Actions macOS runner or a local Mac.
- `QCSDK.framework` is presently an opaque static archive from the vendor demo. Its inspected objects are arm64 only; simulator platform compatibility, current Xcode compatibility, license, and device behavior remain unverified. `CyanBridgeKMPHost` intentionally does not link it.
- `GlassesWiFiHandler` contains a legacy hard-coded hotspot-password workaround. It is reference-only until physical-device testing proves a safe replacement.
- The GitHub Actions CI uses GitHub's hosted Apple Silicon runners. Private repositories consume billed Actions minutes (macOS is charged at $0.062/minute after the free quota). Public repositories run macOS jobs free of charge.
- Simulator validation can verify CMP rendering, framework linking, Swift compilation, SwiftUI rendering, and app launch, but it cannot validate Bluetooth pairing, Wi-Fi hotspot joining, QCSDK behavior, or media transfer from physical glasses. Those require a real iPhone near the glasses and are deferred.
- CMP Material 3 (`org.jetbrains.compose.material3`) and Jetpack Material 3 (`androidx.compose.material3`) have 99% identical APIs but different import paths. The migration changes imports from `androidx.compose.*` to `org.jetbrains.compose.*` for shared screens. Stable components (Scaffold, NavigationBar, Card, Button, TextField, AlertDialog) are fully compatible; experimental or very new Jetpack APIs may lag in CMP.

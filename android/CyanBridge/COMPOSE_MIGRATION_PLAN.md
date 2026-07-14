# Material 3 and Kotlin Multiplatform MVP Plan

Last audited: 2026-07-14

This is the authoritative plan for restarting CyanBridge's Material 3 migration and creating a credible path to iOS. The old Compose branch remains useful as a UI prototype, but it is no longer a safe integration base.

## Executive Decision

Do not merge or rebase `compose_material3_migration` or `memomind-adapter` wholesale into current `main`.

Create a new migration branch from current `main`, preserve the old branch tips as archive references, and selectively port screen structure and visual ideas. Current business logic must remain authoritative.

Recommended branch workflow:

```bash
git branch archive/compose-material3-2026-07 compose_material3_migration
git branch archive/memomind-adapter-2026-07 memomind-adapter
git switch main
git switch -c compose-material3-kmp-v2
```

Do not delete the old branches. They contain useful screen implementations and the only full history of the earlier UI experiments.

## Audit Snapshot

Audit baseline:

- `main`: `45073d1` after preserving the current app, bridge, and research changes.
- `compose_material3_migration`: `15f810c`.
- `memomind-adapter`: `f3387b9`.
- Common ancestor: `3de11e8`.
- Compose branch divergence: 49 commits exist only on current main and 26 commits exist only on the Compose branch.
- MemoMind branch divergence: 49 commits exist only on current main and 28 commits exist only on the MemoMind branch.

This is not a small update. Current main added or substantially changed:

- BLE and Wi-Fi Direct media synchronization and retry behavior.
- Local-agent observation, safety, action execution, daily review, and summary flows.
- LiteRT/Gemma multimodal inference and expanded transcription paths.
- Auto-capture, audio ingestion, image-query, and local-model behavior.
- Asaas subscription, cancellation, quota, email, donation, and checkout behavior.
- Studio Bridge voice approval support and encrypted remote-model credentials.
- MemoMind, EvenHub, Mentra, terminal HUD, and Meta Ray-Ban bridge groundwork.
- New settings, onboarding, plugin, debugging, and media behaviors.

Any migration that starts from the old Compose branch would have to reconstruct these changes and is likely to regress production behavior.

## What The Old Compose Branch Already Implements

The old branch is a substantial prototype, not a failed empty branch. It includes:

| Area | Implemented prototype | Reuse guidance |
|---|---|---|
| Foundation | Kotlin 2.0, Compose compiler plugin, Material 3, Navigation Compose | Recreate with a currently compatible KMP toolchain; do not copy old version pins blindly |
| App shell | `ComposeMainActivity`, `MainNavScreen`, bottom navigation | Reuse route concepts only; replace inset and icon handling |
| Chat | Messages, model picker, history shortcut, input composer, loading and errors | Reuse visual decomposition; reconnect to current chat and inference logic |
| History | Thread list, search, delete, open thread | Good candidate for an early shared screen |
| Settings | Large Compose settings screen and settings ViewModel | Use as a feature checklist, not as source of truth |
| Theme | Dark/light choice and six accent presets | Replace the color generation and persistence architecture |
| Pro | Subscription and account screens | UI reference only; current billing and Asaas behavior is newer |
| Onboarding | Welcome, battery optimization, permission screens | Reconcile with current onboarding before porting |
| Glasses | Large dashboard with current-at-that-time controls | Split into capability sections; current main has newer devices and actions |
| Recordings | Recording and synced-media screens | Port after current media contracts are isolated |
| Local models | A full Compose configuration screen | Current model engines and settings have changed heavily |
| Plugins | Plugin browsing and management screen | Recheck current publish and patcher behavior |
| Notes | List and detail screens | Suitable for shared UI after repository cleanup |
| Local agent | Daily facts, summary, blacklist, captures, pending actions, synced media | Android-only capabilities must remain behind platform interfaces |

The branch has Compose test dependencies but no meaningful Compose UI test suite. The only relevant test found under the branch UI area is the existing `ChatStoreTest`. Runtime layout and accessibility regressions were therefore found manually.

## MemoMind Branch Relationship

`memomind-adapter` is based on the old Material 3 branch. It is not an independent modern base.

It adds two commits after `15f810c`:

- `fec245e`: notification channel adjustment.
- `f3387b9`: signed release build documentation.

Current main now contains most of the bridge core, protocol, runtime, audio, and notification groundwork. The old branch still contains `MemoMindDeviceAdapter.kt`, which current main does not. If that adapter is revived, port that file selectively only after validating it against the current protocol notes and current `GlassesDeviceAdapter` contract. Do not merge the MemoMind branch to obtain it.

## Existing iOS Baseline

The repository already has a native Objective-C iOS demo under `ios/QCSDKDemo/`.

Verified existing capabilities:

- `QCSDK.framework` integration.
- CoreBluetooth scan, connect, reconnect, and device state handling.
- HeyCyan command integration through `QCSDKManager` and `QCSDKCmdCreator`.
- iOS hotspot joining through `NEHotspotConfiguration`.
- HTTP media discovery and download from `/files/media.config` and `/files/<name>`.

This means the iOS path should wrap the existing native SDK and media-transfer code behind shared interfaces. Reimplementing the vendor protocol in common Kotlin is not an MVP requirement.

Before iOS release work, verify:

- The license permits bundling `QCSDK.framework` in a new app.
- The framework contains the required device and simulator architecture slices, or a device-only development workflow is documented.
- Its module map and Objective-C headers can be consumed from Swift and, if desired, Kotlin/Native cinterop.
- Apple Bluetooth, local-network, hotspot, microphone, photo-library, and background-mode declarations are complete.
- App Store billing uses StoreKit and does not assume that Play Billing or the Android web checkout is valid on iOS.
- Root ignore rules currently match `*.xcodeproj` and `QCSDK.framework`; explicitly allow the new iOS project's source files and define how the vendor binary is supplied without accidentally omitting required project metadata.
- The existing media downloader logs the hotspot password and contains aggressive retry paths. Redact all credentials and replace retries with a bounded, testable state machine before production reuse.

## Why The Earlier UI Fixes Did Not Stabilize

### Chat Composer

The final branch still uses all of the following:

- An outer navigation `Scaffold` with a bottom `NavigationBar`.
- An inner chat `Scaffold`.
- `Modifier.padding(innerPadding)` around the `NavHost`.
- `imePadding()` on the chat composer.
- A hard-coded `bottom = 68.dp` padding on the composer.

The history shows repeated changes between `navigationBarsPadding()`, `imePadding()`, `60.dp`, and `68.dp` offsets. These were compensating for multiple owners of the same bottom inset rather than fixing the layout model. The result is device-, navigation-mode-, and keyboard-dependent.

### Icons

The prototype initially used unrelated Material icons such as Home, List, Star, and Settings for camera, audio, battery, model, and device actions. Commit `a70b438` removed many of those random icons instead of defining a semantic icon system.

The final bottom bar mixes Material icons with Android-only `ImageVector.vectorResource(R.drawable...)`. That is workable on Android but unsuitable for shared Compose UI on iOS.

### Themes And Accent Profiles

The branch eventually added six accent presets, but the light scheme generates surfaces by blending 30 to 40 percent of the accent into white. This makes the whole app strongly tinted and does not guarantee readable contrast for arbitrary colors.

Theme state is also read directly from Android `SharedPreferences` in both `ComposeMainActivity` and `SettingsViewModel`. Dynamic color exists as a parameter but is not a complete user-facing policy. This architecture is Android-specific and duplicates ownership of theme state.

## MVP Product Scope

The first modernized release should not promise full Android and iOS parity.

Android MVP:

- Preserve every current production behavior.
- Replace the main shell, Chat, History, Appearance settings, and one low-risk screen with Material 3.
- Keep unported Activities reachable through explicit platform navigation.
- Add reliable adaptive layout, keyboard, icon, and accessibility tests.

iOS MVP:

- Launch a real CyanBridge app shell using shared Compose UI.
- Apply the same theme and accent profile settings.
- Connect to supported HeyCyan glasses through a native iOS adapter.
- Show connection state and basic device information.
- Send basic device commands supported by `QCSDK.framework`.
- Download and display media using the existing native iOS transfer implementation.
- Support relay-backed Chat and History after shared chat contracts exist.

Deferred on iOS:

- Android Accessibility local-agent control.
- Tasker integration.
- Android foreground services and notification-listener behavior.
- Meta DAT integration.
- Android local model runtimes until an iOS inference engine is selected.
- Play Billing; iOS requires a separate StoreKit implementation.
- MemoMind support until an iOS transport adapter is proven.

## Target Architecture

Use an incremental Kotlin Multiplatform library while retaining the current Android app module.

Suggested initial layout:

```text
android/CyanBridge/
  app/                         existing Android host and Android integrations
  shared/                      new Kotlin Multiplatform module
    src/commonMain/
      kotlin/.../domain/       pure models, contracts, use cases
      kotlin/.../presentation/ state holders and event reducers
      kotlin/.../ui/           Material 3 theme, icons, and selected screens
      composeResources/        shared strings, SVGs, and images
    src/commonTest/
    src/androidMain/           Android implementations and host adapters
    src/iosMain/               iOS implementations or native bridge hooks

ios/
  CyanBridgeApp/               new Swift/Xcode host for the shared framework
  QCSDKDemo/                   retained protocol/reference demo
```

Start with one `:shared` module. Split it only when source-set boundaries become painful. Prematurely creating many KMP modules will slow the migration.

### Common Code Rules

Allowed in `commonMain`:

- Immutable UI state and events.
- Chat, thread, theme, device-capability, and display-command models.
- Repository and platform-service interfaces.
- Coroutines and Flow.
- Serialization and HTTP client code when the selected libraries support all targets.
- Compose Material 3 UI that has no Android imports.

Not allowed in `commonMain`:

- `android.*`, Android `Context`, Activities, Services, Intents, or `R` references.
- `java.io`, `java.net`, or JVM-only utility assumptions.
- Direct `SharedPreferences` access.
- Android BLE, Wi-Fi P2P, MediaStore, Accessibility, Tasker, Play Billing, or Meta DAT calls.
- Direct Objective-C vendor SDK calls.

Prefer injected interfaces over broad `expect`/`actual` declarations. Use `expect`/`actual` only for small platform primitives such as a clock, UUID factory, filesystem path, or platform information.

### Existing Code That Is Close To Shareable

- `bridge/core/DisplayCommand.kt`.
- `bridge/core/InputEvent.kt`.
- `bridge/core/GlassesCapability.kt`.
- `bridge/core/GlassesBridgeState.kt`.
- `bridge/core/DeviceInfo.kt`.
- `bridge/core/GlassesDeviceAdapter.kt`.
- `chat/ChatModels.kt` after removing mutable fields where practical.

`GlassesBridge.kt` is not yet common-ready because it imports `android.util.Log`, owns a global singleton, and creates an IO scope internally. Replace it with an injected instance, a logger interface, and an owned lifecycle before moving it.

`ChatStore.kt` is not common-ready because it uses Android application state, Room entities, blocking calls, and `java.util.UUID`. Define a suspend `ChatRepository` contract and adapt the current store behind it before sharing Chat UI.

### Platform Adapters

Android adapters remain responsible for:

- Vendor AAR and HeyCyan BLE callbacks.
- Wi-Fi Direct process binding and media download.
- Android permissions and Activity Result APIs.
- Accessibility and local-agent services.
- MediaStore and Android audio APIs.
- Play Billing, web checkout, and deep links.
- Meta DAT and Android MemoMind transports.

iOS adapters remain responsible for:

- `QCSDK.framework` and CoreBluetooth.
- `NEHotspotConfiguration` and iOS network behavior.
- Photos and file persistence.
- AVFoundation speech/audio behavior.
- StoreKit.
- Apple deep links and lifecycle integration.

## Toolchain Gate

Current main uses Kotlin 1.9.24. The old branch moved to Kotlin 2.0.0 and old Compose/Navigation pins. Do not reuse those pins.

Before screen work:

1. Select one mutually compatible stable set of Kotlin, Compose Multiplatform, Compose compiler, AGP, KSP, coroutines, serialization, navigation, and database versions.
2. Record the versions in `libs.versions.toml` and add a short compatibility note to this document.
3. Upgrade current main without changing the launcher Activity or production UI.
4. Run Android unit tests and `assembleDebug` after each toolchain step.
5. Build `commonMain` metadata on Linux.
6. Build and link the iOS framework on macOS with a supported Xcode version.

Linux cannot complete an iOS application build. A Mac development machine or macOS CI runner is a hard requirement for iOS linking, simulator/device tests, signing, and App Store delivery.

## Migration Phases

### Phase 0: Preserve And Inventory

Tasks:

- Archive old branch tips and start the new branch from current main.
- Generate a current feature-parity checklist from Activities, manifest entries, layouts, services, receivers, and deep links.
- Mark every feature as shared UI, Android-only UI, shared domain candidate, or platform adapter.
- Capture Android baseline screenshots and manual flows before changing UI.
- Keep `/backups/compose_material3_port/` as reference only; compare every restored file against current main before use.

Exit criteria:

- No current main feature is missing from the parity checklist.
- Baseline `testDebugUnitTest` and `assembleDebug` pass.

### Phase 1: Toolchain And Empty KMP Shell

Tasks:

- Add the `:shared` KMP module with `commonMain`, `commonTest`, `androidMain`, and iOS targets.
- Add a minimal shared `AppTheme` and a static `Hello` screen.
- Render that screen inside a non-launcher Android test Activity.
- Render it from a new iOS Swift host through a Compose view controller.
- Ensure the new Xcode project and shared-framework integration files are actually tracked despite the repository's existing iOS ignore patterns.
- Add common tests and Android/iOS smoke builds.

Exit criteria:

- Existing Android launcher behavior is unchanged.
- Android can render shared Compose UI.
- iOS simulator or device can launch the shared test screen on a Mac.

### Phase 2: Shared Design System

Tasks:

- Implement semantic colors, typography, shapes, spacing, and elevation tokens.
- Add `ThemeMode.SYSTEM`, `ThemeMode.LIGHT`, and `ThemeMode.DARK`.
- Add stable accent IDs such as Cyan, Rose, Mint, Lavender, Peach, and Sky.
- Use complete reviewed light and dark color schemes for each profile.
- Add Android dynamic color as an optional Android-only profile, not the global default.
- Persist theme settings through a shared repository, backed by a KMP-capable store or small platform implementations.
- Build a shared icon registry and shared resources.

Exit criteria:

- Theme changes update immediately on Android and iOS.
- Selection survives process restart.
- Every text/background pair meets the agreed contrast threshold.
- No common UI imports Android `R` or `vectorResource`.

### Phase 3: Navigation Shell And Chat Vertical Slice

Tasks:

- Introduce typed routes and a shared shell for the routes available on both platforms.
- Keep platform-only routes behind capability checks.
- Port Chat and History against new presentation contracts connected to current main logic.
- Keep legacy Android Activities available for unported destinations.
- Implement the composer using one explicit inset owner and no fixed bottom offset.
- Add Android and iOS keyboard tests before adding more screens.

Exit criteria:

- Chat send, response, history, new thread, model choice, errors, and daily-review entrypoints retain current behavior on Android.
- Composer remains visible with gesture navigation, three-button navigation, keyboard open/closed, landscape, split screen, and 200 percent font scaling.
- iOS composer remains above the keyboard and home indicator.

### Phase 4: Appearance And Settings

Tasks:

- Port Appearance first, then settings sections one at a time.
- Drive all settings from state and events; do not access Android preferences directly from composables.
- Keep Android-only settings visible only on Android and explain unavailable features on iOS where useful.
- Add reset-to-default, live preview, selected-state labels, and high-contrast-safe choices.
- Preserve current remote model, Studio Bridge, local-agent, privacy, transcription, media, and subscription settings.

Exit criteria:

- Settings parity checklist is complete.
- Theme personalization is accessible without relying only on color.
- No secret is stored in an unencrypted shared preference.

### Phase 5: Port Remaining Screens Incrementally

Recommended order:

1. Notes.
2. Recordings and synced-media list.
3. Plugins.
4. Glasses status and capability dashboard.
5. Pro account and subscription status.
6. Local-model configuration.
7. Local-agent screens.
8. Onboarding and permission education.

Rules:

- Port from current main behavior, using the old branch only for visual reference.
- Split very large screens into state-driven sections before moving them.
- Do not delete an Activity until its Compose replacement has parity tests and deep-link coverage.
- Android-only operations stay in injected platform services.

### Phase 6: iOS HeyCyan Adapter

Tasks:

- Wrap `QCCentralManager`, `QCSDKManager`, and `QCSDKCmdCreator` behind the shared device contracts.
- Convert delegate callbacks into `StateFlow` and `Flow` through a lifecycle-owned adapter.
- Wrap `GlassesMediaDownloader` behind a shared media-sync contract.
- Add iOS permission and error mapping.
- Verify real-device scanning, connection, command, reconnect, and media transfer.

Exit criteria:

- A physical iPhone can connect to the glasses and perform the agreed iOS MVP commands.
- Media transfer uses the BLE-reported device IP and existing iOS hotspot flow.
- Disconnects and denied permissions produce recoverable UI states.

### Phase 7: Cutover And Cleanup

Tasks:

- Make the Material 3 host the Android launcher only after parity acceptance.
- Remove XML layouts and legacy Activities only when no manifest, deep link, service, or test references them.
- Replace temporary adapters with shared contracts.
- Add release builds, obfuscation checks, crash reporting policy, and migration notes.
- Keep Android and iOS feature matrices in the repository.

Exit criteria:

- Android production build passes and physical-device core flows pass.
- iOS archive builds on macOS and physical-device MVP flows pass.
- No known navigation, keyboard, icon, contrast, or secret-storage blocker remains.

## Required Fix Designs

### Chat Insets And Keyboard

Use these rules instead of another padding tweak:

- Never use a hard-coded bottom value to represent a navigation bar.
- The root shell owns the app navigation bar inset.
- The chat composer owns the IME inset.
- Apply scaffold padding once and call `consumeWindowInsets` when passing it to nested content.
- Prefer hiding app bottom navigation while the IME is visible if keeping it produces two stacked controls.
- Put the composer in the chat scaffold's `bottomBar`, not at the end of an arbitrarily padded Column.
- Use platform safe-area insets on iOS and WindowInsets APIs on Android.

Required test matrix:

| Case | Expected |
|---|---|
| Android gesture navigation, keyboard closed | Composer above system gesture area and app navigation |
| Android gesture navigation, keyboard open | Composer immediately above IME, fully tappable |
| Android three-button navigation | No overlap or double bottom gap |
| Android landscape and split screen | Composer and send action remain reachable |
| Android 200 percent font scale | Input can grow without hiding send action |
| iPhone with home indicator | Composer clears safe area |
| iPhone keyboard open | Composer follows keyboard without fixed offsets |

### Icons

Create semantic icon names instead of choosing icons inline:

```kotlin
enum class AppIcon {
    Glasses,
    Chat,
    Recordings,
    Settings,
    Plugins,
    Camera,
    Video,
    Microphone,
    Battery,
    Sync,
    Model,
    Send,
}
```

Implementation rules:

- Store custom icons in Compose Multiplatform resources and load them with the shared resource API.
- Use a Material icon only when its meaning is exact and available on all targets.
- Do not use Home for Chat, List for audio, Star for arbitrary AI actions, or emoji as control icons.
- Decorative icons have null descriptions; actionable icons have localized descriptions.
- Icon-only controls must have at least a 48 dp touch target.
- Selected navigation state must use label, color, and/or shape, not an unexplained icon swap alone.

### Theme And Accent Accessibility

Model preferences explicitly:

```kotlin
data class AppearanceSettings(
    val themeMode: ThemeMode,
    val accentProfileId: String,
    val useDynamicColor: Boolean,
    val highContrast: Boolean,
)
```

Rules:

- Keep background and surface colors neutral; accents belong primarily on actions, selection, focus, links, and small containers.
- Do not tint the entire light background 30 to 40 percent toward the accent.
- Ship curated, tested light/dark schemes before allowing arbitrary custom colors.
- If a custom color picker is added later, derive a tonal palette and reject or correct combinations that fail contrast.
- Meet at least 4.5:1 for normal text, 3:1 for large text and meaningful UI graphics, and 3:1 for control boundaries where required.
- Support system theme, font scaling, screen readers, switch control, reduced motion, and color-vision differences.
- Show a name and selected marker for each accent; color circles alone are not accessible.

## Feature Parity And Platform Matrix

Every route must be recorded in a maintained table during implementation.

Use these statuses:

- `shared`: common presentation and UI.
- `shared-ui/platform-action`: shared UI with injected platform operation.
- `android-only`: no iOS equivalent in current scope.
- `ios-native`: native iOS implementation behind a shared contract.
- `deferred`: intentionally absent from MVP.

Initial classification:

| Feature | Initial status |
|---|---|
| Theme and appearance | shared |
| Chat and history | shared after repository extraction |
| Notes | shared after repository extraction |
| Glasses capability/status UI | shared-ui/platform-action |
| HeyCyan BLE | Android adapter plus ios-native adapter |
| Media list UI | shared-ui/platform-action |
| Android Wi-Fi Direct transfer | android-only |
| iOS hotspot transfer | ios-native |
| Local agent Accessibility control | android-only |
| Tasker | android-only |
| Notifications | shared policy with platform adapters |
| Subscription/account UI | shared-ui/platform-action |
| Play Billing | android-only |
| StoreKit | ios-native |
| Meta DAT | android-only for MVP |
| MemoMind | Android experimental; iOS deferred |
| Local model engines | platform-specific and deferred on iOS |

## Verification Gates

Run on every migration phase:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest assembleDebug
```

Add as the KMP module appears:

- Common unit tests for reducers, repositories, theme selection, and capability gating.
- Compose UI tests for navigation, Chat composer placement, settings state, and content descriptions.
- Screenshot/golden tests for each theme and accent on representative phone sizes.
- Android instrumented tests for IME and system insets.
- iOS simulator UI tests for shared screens.
- Physical Android and iPhone tests for BLE and media transfer.
- macOS CI for iOS framework linking and Xcode archive smoke checks.

## Future Agent Start Checklist

Before writing migration code:

1. Confirm current `main` and re-run divergence counts.
2. Read this plan and `android/AGENTS.md`.
3. Read the old branch versions of `ChatScreen.kt`, `MainNavScreen.kt`, `CyanBridgeTheme.kt`, and the desired screen only.
4. Read the current main Activity, layout, ViewModel/service, manifest entries, and tests for that feature.
5. Update the parity matrix before deleting or replacing anything.
6. Keep old branches and `/backups/compose_material3_port/` read-only unless a file is deliberately restored and reconciled.
7. Make the smallest vertical slice build on Android before adding iOS code.
8. Do not mark a phase complete from compilation alone; run the manual and UI acceptance cases.

## Explicit Non-Goals

- No big-bang deletion of XML and Activities.
- No wholesale cherry-pick of the old Compose commits.
- No attempt to share Android framework APIs through KMP.
- No promise of identical feature availability on Android and iOS.
- No fixed pixel/dp workaround for system insets.
- No arbitrary accent generation without contrast validation.
- No icon placeholders chosen only because they compile.

## Definition Of MVP Done

The migration MVP is complete when:

- Current Android production behavior remains available.
- The Android launcher uses the modern shell for the selected MVP screens.
- Chat input is reachable across the required keyboard and navigation test matrix.
- Semantic icons are consistent and shared-resource compatible.
- Theme mode and accent profiles are persistent, accessible, and contrast-tested.
- A new iOS host launches shared UI on a Mac-built artifact.
- A physical iPhone connects to HeyCyan glasses through the native iOS adapter.
- The iOS app can perform the agreed basic commands and media transfer.
- Platform-only features are clearly gated rather than crashing or silently disappearing.

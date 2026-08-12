# Comprehensive Project Revamp: Terminology, Feature Removal, and Build Stabilization

This plan outlines the complete set of changes required to modernize the repository, standardize terminology, remove restricted features (Walking Aid, Billing, Subscriptions, Donations), and stabilize the build system (Meta SDK integration).

## User Review Required

> [!IMPORTANT]
> **Wake Word Logic**: We will retain "heycyan" or "eyevue" as internal wake word values where they directly interact with glasses firmware to ensure hardware compatibility. However, the UI will display "AD Glasses".
>
> [!WARNING]
> **Destructive Removal**: This plan involves the complete removal of the `WalkingAid` plugin and all billing/subscription code. These features will no longer be recoverable from the codebase once this is finalized.

## Proposed Changes

### 1. Build System & Dependency Management
**Goal**: Ensure the project compiles with the Meta Wearables SDK and uses the correct repository structure.

- **[MODIFY] [settings.gradle.kts](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/settings.gradle.kts)**:
    - Fix the `github_token` logic to correctly read from `local.properties`.
    - Update the composite build path for the core module to `ad_glasses-core`.
    - Use `achyutdalai` as the Maven credentials username.
- **[MODIFY] [build.gradle](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/build.gradle)**:
    - Remove all `buildConfigField` entries related toSkus, SKUSkus, SKUPrices, and Subscriptions.
    - Remove the `com.android.billingclient:billing-ktx` dependency.
- **[RENAME] `heycyan-core` to `ad_glasses-core`**:
    - Update all `build.gradle.kts` files inside the core module to use the `com.ad_glasses.core` namespace.

### 2. Core Terminology Update (Frontend & Internal Logic)
**Goal**: Transition from "HeyCyan" and "Relay" branding to "AD Glasses" and "Cloud".

- **Global String Replacement (Non-Destructive)**:
    - `HeyCyan` -> `AD Glasses` (UI strings)
    - `heycyan` -> `ad_glasses` (only in UI/packages, not in hardware protocols)
    - `Relay` -> `Cloud`
    - `Pro` / `Advanced` -> `Cloud` or `Developer`
    - `Personal` -> `Local`
- **[MODIFY] [SettingsModels.kt](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/shared/src/commonMain/kotlin/com/fersaiyan/cyanbridge/shared/settings/SettingsModels.kt)**:
    - Rename `AgentProviderType.PRO_SUBSCRIPTION` to `AgentProviderType.CLOUD`.
- **[MODIFY] [GlassesDashboardPresentation.kt](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/shared/src/commonMain/kotlin/com/fersaiyan/cyanbridge/shared/glasses/GlassesDashboardPresentation.kt)**:
    - Rename `OtaFirmwareSource.PERSONAL_FILE` to `OtaFirmwareSource.LOCAL_FILE`.

### 3. Feature Removal (Walking Aid, Billing, Donations)
**Goal**: Strip the codebase of all unused or restricted feature sets.

- **[DELETE] `com.fersaiyan.cyanbridge.plugins.walkingaid`**: Remove the entire package.
- **[DELETE] Billing & Subscription Logic**:
    - Remove `ProSubscriptionAiPrefs`, `ProSubscriptionServerPrefs`, and all billing-related classes in `android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/agent/`.
    - Remove `android/CyanBridge/shared/src/commonMain/kotlin/com/fersaiyan/cyanbridge/shared/billing/` directory.
- **[MODIFY] [AndroidManifest.xml](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/AndroidManifest.xml)**:
    - Remove all activities and services related to Walking Aid and Subscriptions.
    - Fix any "Cloudvider" typos caused by previous bulk edits.

### 4. MainActivity Stabilization
**Goal**: Fix the 10k line core activity which has become unstable due to conflicting edits.

- **[MODIFY] [MainActivity.kt](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/MainActivity.kt)**:
    - Clean and deduplicate imports at the top of the file.
    - Ensure a single, correct set of class-level variables (`imageQueryInProgress`, `isAiHijackEnabled`, etc.).
    - Fix all `when` blocks to be exhaustive after the enum renames (adding `CLOUD` and `LOCAL_FILE` branches).
    - Restore original "heycyan" strings only in the hardware notification listeners (0x02, 0x03, etc.) to maintain firmware compatibility.

### 5. UI Resource Cleanup
**Goal**: Align resources with the new branding and removed features.

- **[MODIFY] [strings.xml](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/res/values/strings.xml)** & **[strings_extra.xml](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/shared/src/commonMain/composeResources/values/strings_extra.xml)**:
    - Remove all keys related to walking aid, billing, and donations.
    - Finalize "AD Glasses" and "Cloud" terminology in all user-facing labels.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to verify compilation.
- Check generated `BuildConfig` to ensure restricted fields are gone.

### Manual Verification
- Launch the app and verify the "AD Glasses" branding in the dashboard.
- Confirm "Developer Tools" section appears instead of "Advanced".
- Verify that the Meta Ray-Ban features still function via the DAT SDK.
- Confirm the "Walking Aid" plugin is gone from the "Community Plugins" screen.

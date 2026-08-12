# Personal Glasses App Conversion Plan ("AD glasses")

This plan converts the CyanBridge project into a personal-use application called "AD glasses". It removes commercial billing/subscription dependencies while unlocking all advanced features (Pro) for personal use with your own API credentials.

## User Review Required

> [!IMPORTANT]
> This plan will hardcode your subscription status to "Active" locally. It will also bypass the company's verification servers, ensuring the app never reverts to "Free" mode.

> [!WARNING]
> We are keeping the internal package name `com.fersaiyan.cyanbridge` to ensure Tasker and system permissions remain functional. Only the displayed name and branding will change.

## Proposed Changes

### Branding & Renaming
Rename the app from "CyanBridge" to "AD glasses" across all user-visible strings and launcher labels.

#### [MODIFY] [strings.xml](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/res/values/strings.xml)
- Update `app_name` to "AD glasses".
- Replace other user-facing "CyanBridge" mentions with "AD glasses".

#### [MODIFY] [strings_compose.xml](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/res/values/strings_compose.xml)
- Update branding in the Material 3/Compose parts of the app.

---

### Pro Subscription Bypass
Hardcode the subscription status to "Active" and disable background verification.

#### [MODIFY] [ProSubscriptionPrefs.kt](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/agent/ProSubscriptionPrefs.kt)
- Modify `isActiveLocally` to always return `true`.
- Modify `isSubscribed` to always return `true`.
- Modify `getPlan` to return a high-tier plan (e.g., "max") by default.

#### [MODIFY] [ProSubscriptionVerifier.kt](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/agent/ProSubscriptionVerifier.kt)
- Modify `verifyNow` to immediately return a successful local status without making network calls.

---

### UI Cleanup (Removal of Commercial Entry Points)
Remove buttons and screens that ask the user to pay or manage a commercial subscription.

#### [MODIFY] [SettingsActivity.kt](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/ui/SettingsActivity.kt)
- Remove the "Subscription" button and the logic that opens `ProSubscriptionActivity`.
- Remove "Pro required" warnings for memory sync features.

#### [MODIFY] [MainActivity.kt](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/MainActivity.kt)
- Clean up any branding or purchase-related UI elements.

---

### Cleanup of Unused Logic
Delete files that are purely dedicated to commercial billing and checkout.

#### [DELETE] [PlayBillingManager.kt](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/agent/PlayBillingManager.kt)
#### [DELETE] [SubscriptionCheckoutPolicy.kt](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/agent/SubscriptionCheckoutPolicy.kt)
#### [DELETE] [WebSubscriptionCallbackActivity.kt](file:///Users/achyutdalai/Development/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/agent/WebSubscriptionCallbackActivity.kt)

## Verification Plan

### Manual Verification
1. Build and install the app.
2. Confirm the launcher icon is labeled "AD glasses".
3. Open Settings and verify that no "Buy Pro" button exists.
4. Verify that "Gemini Live" can be opened without a "Pro required" error.
5. Verify that advanced features like "Cloud Vision" in Walking Aid are unlocked.

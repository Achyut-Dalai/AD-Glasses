# AD Glasses Rebranding and Simplification Plan

Rebrand the app as "AD Glasses", remove all legacy subscription/billing logic, and simplify the app to support ONLY HeyCyan (white-label) glasses. Apply a modern Apple/Vercel-inspired UI.

## User Review Required

> [!IMPORTANT]
> The app is being simplified to support ONLY HeyCyan glasses. Logic for Meta Ray-Ban, Meizu MYVU, and generic audio devices will be removed to reduce complexity and app weight.

> [!NOTE]
> Wake words remain "Hey Cyan" due to hardware constraints, but the app branding will be exclusively "AD Glasses".

## Proposed Changes

### 1. Simplification: HeyCyan Only
- Remove `MetaRaybanManager`, `MeizuMyvuManager`, and associated logic.
- Simplify `DeviceClass` to focus on HeyCyan.
- Cleanup `MainActivity.kt` and `GlassesDashboardScreen.kt` to remove non-HeyCyan UI and handlers.

### 2. UI & Branding
- [DONE] Integrate new high-res logos from root folder.
- [DONE] Update `AdGlassesTheme.kt` with Vercel Dark and Apple Light themes.
- [IN PROGRESS] Update all strings and identifiers to "AD Glasses".
- Remove "Pro", "Subscription", and "Donation" UI elements and logic.

### 3. Build & Stability
- Resolve all compilation errors in `MainActivity.kt` and other files caused by renaming and feature removal.
- Ensure the app builds with Java 17+.

## Verification Plan

### Automated Tests
- `./gradlew :shared:assembleDebug`
- `./gradlew :app:assembleDebug`

### Manual Verification
- Launch the app on an emulator.
- Verify the "AD Glasses" branding and theme.
- Verify that the glasses dashboard only shows HeyCyan-relevant controls.
- Confirm "Walking Aid" is gone from the plugins list.

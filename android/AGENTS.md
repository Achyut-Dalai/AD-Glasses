# AGENTS.md — Android / HeyCyan technical contract

## Scope

This file documents the confirmed Android-side HeyCyan connection and media-transfer behavior that should survive refactors.

- Active app: `android/AD-Glasses/`
- UI: Kotlin + Jetpack Compose
- Primary glasses family: HeyCyan
- Vendor artifact: `android/glasses_sdk_20250723_v01.aar`
- Native iOS work lives separately in `ios/` and is not a Compose/KMP host.

Do not use this file as permission to guess undocumented BLE, Wi-Fi, firmware, OTA, or cloud behavior.

## Build expectations

Run commands from the Android project directory:

```bash
cd android/AD-Glasses
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The Android project is configured for Java 17-compatible source/bytecode. Match the repository's Gradle/JDK toolchain rather than changing versions ad hoc.

## Permission rules

Permission requirements vary by Android version. Keep permission requests tied to the capability being used and preserve the app's current runtime checks.

Typical relevant capabilities include:

- Bluetooth scan/connect.
- Nearby devices on modern Android versions.
- Wi-Fi/network state for the glasses media handoff.
- Microphone for speech/meeting features.
- Camera/media permissions only where the app feature actually requires them.

Do not request broad storage/location access merely to avoid implementing the version-appropriate API.

## Confirmed HeyCyan media-transfer sequence

The media-transfer path is a BLE-triggered Wi-Fi handoff followed by HTTP requests over the Wi-Fi network associated with the glasses.

### 1. Request transfer mode over BLE

The confirmed command byte sequence used to enter the transfer flow is:

```text
[0x02, 0x01, 0x04]
```

Do not replace this with a guessed command or infer neighboring command IDs.

### 2. Wait for the device notification

The confirmed notification types relevant to the handoff are:

- `0x08` — carries the glasses/device IP information needed for the transfer path.
- `0x09` — indicates a Wi-Fi/data-transfer error condition.

Do not assume a hotspot SSID, password, IP address, or subnet before the device provides the information used by the supported flow.

### 3. Wait for usable Wi-Fi

A Wi-Fi association is not enough. Wait until Android reports a usable network, including `NET_CAPABILITY_INTERNET` where required by the existing implementation.

When the app has multiple networks available, bind the media HTTP traffic to the intended Wi-Fi `Network`. Do not let requests silently escape over cellular/default routing.

### 4. Use the confirmed HTTP surface

The supported media flow uses the following endpoints on the glasses-side HTTP service:

- `GET /api/get_media_list`
- `GET /api/get_media_info` with the `media` query parameter containing the IPFS URI returned by the device
- `GET /ipfs/{cid}` to retrieve the media object
- `POST /api/delete_media`
- `GET /api/get_device_info`

Preserve response/error handling rather than assuming every successful TCP connection contains valid media data.

### 5. Media type values

Confirmed media type values include:

| Value | Meaning |
| --- | --- |
| `0x22` | Photo |
| `0x24` | Video |
| `0x26` | Lock video |
| `0x27` | Audio PCM |

Unknown values should stay unknown/forward-compatible; do not reinterpret them by proximity.

## Network implementation rules

- Keep BLE control and Wi-Fi/HTTP transfer responsibilities separate.
- Treat the device-provided IP as session data, not a hard-coded constant.
- Use the Android `Network` associated with the glasses connection for HTTP calls where the current flow requires it.
- Cancel/close work when the transfer session ends or the owning lifecycle is destroyed.
- Bound retries and timeouts. A missing device, rejected connection, malformed response, or authentication/configuration error should surface explicitly.
- Never log credentials, private media URIs, transcripts, tokens, or complete private file paths.

## Common mistakes to avoid

- Starting HTTP requests immediately after asking the glasses to enable transfer mode.
- Assuming Wi-Fi is ready because the SSID/transport changed.
- Letting media requests use the default network and accidentally fall back to cellular.
- Hard-coding an IP or hotspot name from one test session.
- Treating notification `0x09` as ordinary progress instead of an error signal.
- Downloading by a guessed filename instead of using the media/IPFS identifiers returned by the device.
- Reintroducing MyVu/EyeVue/other vendor demos into the HeyCyan transfer path.
- Copying old KMP/QCSDK iOS-host instructions into Android development docs.

## HeyCyan protocol changes

When a new HeyCyan feature is needed:

1. Verify the behavior against the supported device, vendor artifact, or retained reference app.
2. Isolate raw vendor commands/UUIDs inside the HeyCyan integration layer.
3. Expose a capability-oriented API upward so Compose/features do not depend on raw protocol details.
4. Add regression tests around parsing/state transitions where practical.
5. Document only verified values here.

## Future glasses families

A future glasses family should be a separate adapter/provider. It must not alter HeyCyan command handling or force cross-vendor branching throughout the UI. Meta follows the same rule: keep its SDK/protocol details behind its provider boundary when that integration is enabled.

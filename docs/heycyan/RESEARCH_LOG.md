# HeyCyan research log

This file records findings chronologically so later architecture changes retain their evidence and rationale.

---

## 2026-08-28 — Initial protocol audit

### Finding: BLE is control, Wi-Fi is data

**Status:** STRONG

Repository documentation and iOS demo code describe a Bluetooth-first transfer sequence:

```text
BLE connection
→ request Wi-Fi transfer mode
→ receive credentials
→ wait for device Wi-Fi readiness/IP over BLE
→ join glasses Wi-Fi
→ transfer media over local HTTP
```

This corrects the earlier loose mental model that HTTP might be replacing BLE. HTTP is a separate application protocol used after the BLE-controlled network handoff.

### Finding: `heycyan-core/core-ble` is not the full HeyCyan protocol implementation

**Status:** PROVEN from repository contents

The checked-in `core-ble` and `core-audio` modules do not themselves provide the complete application protocol needed by the native iOS app. Important command semantics are exposed through vendor SDK surfaces and other integration code.

Implication: do not treat `heycyan-core/core-ble` alone as sufficient protocol truth.

### Finding: native iOS QCSDK exists in the upstream author repository

**Status:** PROVEN

Upstream contains:

```text
ios/QCSDK.framework/
ios/QCSDKDemo/
examples/ios/GlassesFramework/
```

The framework headers expose `QCSDKCmdCreator`, and the demos exercise HeyCyan features natively on iOS.

This is important evidence that the HeyCyan control stack is not inherently Android-only.

### Finding: exposed HeyCyan operation modes

**Status:** STRONG

The upstream Swift wrapper maps operation modes including:

```text
0x00 unknown
0x01 photo
0x02 video
0x03 videoStop
0x04 transfer
0x05 ota
0x06 aiPhoto
0x07 speechRecognition
0x08 audio
```

No public HeyCyan livestream operation mode is present in that wrapper.

### Finding: iOS hotspot/AP media path exists

**Status:** STRONG

The upstream iOS demo calls the vendor Wi-Fi transfer command, waits for a device IP/readiness callback, uses `NEHotspotConfiguration`, and probes/downloads media over HTTP.

Implication: native iOS media transfer should first target this AP/hotspot path rather than assuming Android Wi-Fi Direct is mandatory.

### Finding: HeyCyan P2P is also real on Android

**Status:** STRONG

CyanBridge contains HeyCyan-specific P2P policy and route detection. It recognizes P2P/WFD interfaces and `192.168.49.*` addressing.

Implication: the ecosystem supports more than one Wi-Fi topology. We still need production evidence to establish AP/P2P selection rules.

### Finding: livestream appears to be outside the public HeyCyan SDK surface

**Status:** STRONG, not yet independently reproduced

The reverse-engineering author reports a test/dormant livestream path on the Wi-Fi processor side, but no Bluetooth command exposed by the current HeyCyan SDK to activate it. This matches the absence of a livestream mode in the inspected public QCSDK operation modes.

Implication: AD Glasses Lens must not depend on continuous live camera streaming. Prefer capture → retrieve → analyze until stronger evidence exists.

### Finding: LED modes reported by reverse-engineering author

**Status:** REPORTED / needs artifact confirmation

Author reports Wi-Fi-side kernel `led_aglink` mode callback behavior:

```text
Mode 0: flash / photo
Mode 1: steady on / video
Mode 2: off
Mode 3: breathing / audio recording
```

The author reports experimental firmware patch work routing those modes to off. This is firmware-modification research and should remain separate from the production AD Glasses integration.

Do not ship or test firmware patches as part of normal BLE/Wi-Fi implementation work.

### Finding: author reports encrypted Bluetooth firmware boundary

**Status:** REPORTED / reverse-engineering context

The author reports that the Bluetooth-side firmware is encrypted/partially decrypted and that some Wi-Fi-chip features cannot currently be invoked because the exposed Bluetooth command set does not provide an entry point.

This supports treating “hardware contains code for feature X” and “shipping app can safely activate feature X” as different claims.

### Finding: Wi-Fi password behavior is inconsistent across demos

**Status:** UNRESOLVED

One iOS demo path overrides the password returned by the SDK with a fixed value, while another transfer implementation uses the returned credential.

Do not hardcode either rule in the AD Glasses native implementation until production behavior and physical glasses settle this.

---

## 2026-08-28 — Artifact priority refinement

### Finding: official HeyCyan APK is no longer a blocker for the basic iOS architecture

**Status:** STRONG

The upstream iOS QCSDK headers and demos already prove the key supported architecture:

```text
CoreBluetooth / vendor BLE command layer
→ photo/video/audio/device commands
→ BLE-controlled Wi-Fi transfer mode
→ iOS joins accessory hotspot
→ HTTP media access
```

Therefore the official HeyCyan Android APK is **not required** to prove that BLE control + iOS hotspot + HTTP media transfer is a viable design.

The APK remains valuable as a production-truth and reverse-engineering artifact for details that the public SDK surface and demos do not settle.

### Finding: CyanBridge v2.1.1 APK is publicly available

**Status:** PROVEN

The upstream repository's current release is CyanBridge v2.1.1 and includes a signed `app-release.apk` asset. Because the CyanBridge source is already public and inspectable, its APK is secondary: useful for confirming what shipped and inspecting bundled/obfuscated dependencies, but not required for understanding CyanBridge's application logic.

---

## 2026-08-29 — Official HeyCyan production XAPK received

Artifact:

```text
HeyCyan_1.0.142_20260807_apkcombo.com.xapk
```

Package metadata:

```text
App: HeyCyan
Package: com.glasssutdio.wear
Version: 1.0.142_20260807
Version code: 142
Minimum Android SDK: 26
Target Android SDK: 36
```

The XAPK contains a base APK plus arm64, English-resource, and xxhdpi splits. The base application contains four DEX files. The arm64 split packages Microsoft Speech, Opus/Speex, Agora, VLC, OpenCV, TensorFlow Lite and other native components. Presence of a library is recorded only as artifact inventory, not proof of a specific glasses feature.

Detailed production-app evidence is maintained separately in `OFFICIAL_APP_FINDINGS.md` so earlier findings remain intact.

### Finding: official production app contains inspectable Oudmon protocol implementation

**Status:** PROVEN

The production DEX contains `com.oudmon.ble` protocol classes rather than only thin application wrappers. This includes BLE managers, large-data framing/handlers, request beans and response decoders.

Implication: a significant portion of the raw HeyCyan protocol can be reconstructed directly from production bytecode rather than guessed or inferred only from QCSDK wrappers.

### Finding: official primary GATT UUIDs are now known

**Status:** PROVEN from production DEX

```text
Primary service:
6e40fff0-b5a3-f393-e0a9-e50e24dcca9e

Notify/read characteristic:
6e400003-b5a3-f393-e0a9-e50e24dcca9e

Write characteristic:
6e400002-b5a3-f393-e0a9-e50e24dcca9e

CCCD:
00002902-0000-1000-8000-00805f9b34fb
```

A second serial-port-style UUID family is also present and requires further attribution before use.

### Finding: protocol initialization occurs after BLE connection

**Status:** PROVEN

Production initialization paths perform time/device/settings synchronization after BLE connection. This means `GATT connected` and `device ready` are distinct states.

Implication for AD Glasses: add an explicit protocol/session readiness phase before feature actions are allowed.

### Finding: production command families and CRC/framing code are visible

**Status:** PROVEN at family level; exact frame layout still under reconstruction

Observed production command-family values include:

```text
0x41 / 65   glasses control
0x42 / 66   battery sync
0x43 / 67   device info sync
0xFC / -4   IP / Wi-Fi-side information operation
```

The framing code uses a CRC16 implementation. Exact byte layout, length handling, CRC coverage, subcommands and response matching remain active audit work.

### Finding: battery/device data can support the Home hardware dashboard

**Status:** PROVEN capability; native implementation pending

Production response parsers expose battery percentage, charging state and Bluetooth/Wi-Fi firmware/hardware version data.

This validates the product direction of displaying real device telemetry instead of generic text such as “Your glasses.”

### Finding: official production app supports both AP and P2P concepts

**Status:** PROVEN

The official APK contains both normal Wi-Fi/AP helper code and Android `WifiP2pManager` integration.

This upgrades the earlier P2P conclusion from CyanBridge-only evidence to official production evidence. The exact selection rule by model, firmware and operation is still unresolved.

### Finding: error 255 is represented in the official glasses-control parser

**Status:** PROVEN existence; trigger/recovery unresolved

The production `GlassModelControlResponse` code explicitly handles error value `255`.

This confirms that the previously observed/report `255` belongs to the glasses-control/state protocol. Further tracing is needed to identify exactly which state/timeout produces it and the correct recovery/reset sequence.

### Finding: official app contains a glasses-oriented voice/Opus path

**Status:** STRONG; transport details incomplete

Production code contains a `GlassesAzureSpeechRecognizer`, Opus decoding/stream behavior, large-data package callbacks and voice heartbeat logic.

This is materially stronger evidence for a phone-side glasses voice path than the earlier public SDK mode names alone.

Still unresolved:

```text
which physical transport carries audio packets
exact packet/subcommand type
codec framing
sample rate/channels
heartbeat requirements
stream start/stop state transitions
```

Do not yet wire Assistant to a guessed audio characteristic.

### Finding: livestream remains unproven as a supported HeyCyan production feature

**Status:** UNCHANGED / experimental

The initial official-app pass has not established a supported production command that activates the dormant/test livestream capability described by the reverse-engineering author.

Lens should therefore continue to target capture → retrieve → analyze rather than continuous camera streaming.

---

## Artifact priorities

### 1. Official HeyCyan Android XAPK

**Status:** received; active audit

Current focus:

- reconstruct application framing and CRC exactly;
- map glasses-control subcommands;
- map AP/P2P selection;
- attribute Wi-Fi credentials/IP/HTTP endpoints;
- trace error 255 and cleanup;
- trace glasses audio transport;
- search for dormant livestream hooks.

### 2. QCSDK.framework / Android vendor SDK binary analysis

**Priority:** high for cross-checking and for any protocol operation obscured in production app code

These binaries/interfaces remain useful for:

```text
command/state semantics
response callbacks
DFU/OTA behavior
vendor-specific edge cases
comparison with production DEX implementation
```

### 3. CyanBridge source + public APK

**Priority:** important comparison source, APK secondary

CyanBridge remains valuable for reverse-engineered fixes, P2P routing behavior, firmware experiments and physical-device lessons. Its public APK mainly verifies what actually shipped in a release because the source is already inspectable.

### 4. Official HeyCyan iOS IPA

**Priority:** useful, especially if decrypted

A decrypted IPA could provide the strongest comparison for how the official iPhone app orchestrates QCSDK, hotspot joining, permissions/background behavior and any iOS-specific device lifecycle.

An encrypted/App-Store IPA can still be inspected for bundle metadata, resources, frameworks, entitlements and some static clues, but its main executable may be limited by FairPlay encryption.

Do not delay the audit waiting for an iOS IPA.

### 5. Physical-device verification

**Priority:** required before shipping raw protocol implementation

Static analysis tells us intended behavior; the glasses confirm compatibility, timing and model/firmware-specific behavior.

---

## Remaining questions

1. What is the exact production application frame format and CRC16 coverage?
2. Which `0x41` glasses-control payload/subcommands map to photo, video, audio, transfer and cleanup?
3. What exact condition selects AP versus P2P?
4. Is the Wi-Fi password returned, fixed, derived or model/firmware-dependent?
5. What device IP/port/path does production media sync use in each mode?
6. What is the exact media listing/manifest format?
7. What readiness checks occur before network join?
8. Exactly what produces error `255`, and what reset/cleanup is required?
9. What transport carries live glasses microphone packets?
10. What Opus framing/sample format is used?
11. How is AI-photo image data delivered in production?
12. Does production contain any callable but hidden HeyCyan livestream entry point?
13. Which behavior changes across project/customer/firmware variants?
14. Are there authentication/handshake steps beyond the currently visible initialization sequence?

---

## Physical-device verification queue

After static analysis, verify one feature at a time on a real pair of glasses:

```text
1. connect
2. notification enablement / protocol ready
3. battery
4. version/device info
5. photo capture
6. AI photo if supported
7. video start/stop
8. transfer-mode activation
9. hotspot readiness/IP
10. iOS hotspot join
11. media.config / production manifest
12. latest photo download
13. video/audio media download
14. cleanup/reconnect
15. live glasses audio path
```

For each test record:

- glasses model/firmware;
- app version;
- exact command/API path;
- observed response;
- timing;
- network state;
- failure behavior;
- whether the result is reproducible.

---

## 2026-08-29 — Deeper official-app pass resolves major unknowns

This section is intentionally additive. The earlier “livestream appears unavailable” findings above remain as historical evidence showing what was known from the public SDK/reverse-engineering work before the newer official production app was inspected.

### Finding: the official app actively supports real-time HeyCyan preview

**Status:** PROVEN from official production DEX

The production app contains:

```text
com.glasssutdio.wear.home.activity.RealTimePreviewActivity
```

The activity owns:

```text
P2P discovery/connection
AP connection fallback
BLE glasses-control requests
heartbeat/session handling
VLC media playback
cleanup on destroy
```

Its player constructs:

```text
rtsp://<glassDeviceWifiIP>:8554/ch0
```

This supersedes the earlier assumption that livestream existed only as dormant Wi-Fi-firmware code.

### Finding: live-preview Bluetooth activation payloads are visible

**Status:** PROVEN as `glassesControl` payloads; full outer frame still under reconstruction

Production code passes these byte arrays to `LargeDataHandler.glassesControl(...)`:

```text
02 01 14 01   P2P real-time-preview start path
02 01 14 02   AP real-time-preview start path
02 01 15 01   cleanup/exit payload sent when the activity is destroyed
```

The complete BLE frame around these payloads is still governed by the `LargeDataHandler` framing/CRC layer and must be reconstructed separately.

### Finding: Android live-preview mode selection is OS-dependent in the inspected flow

**Status:** PROVEN for official Android `RealTimePreviewActivity`

The permission/launch path checks `isHarmonyOSNEXT`:

```text
HarmonyOS NEXT → AP live-preview path
other inspected Android path → P2P live-preview path
```

This is not evidence that every operation chooses network mode the same way, but it proves the glasses expose both P2P and AP live-preview activation variants.

Implication for iOS: test the verified AP live path first rather than attempting to recreate Android Wi-Fi Direct.

### Finding: the serial/large-data GATT family is actively used

**Status:** PROVEN

The earlier second UUID family is no longer merely “present but unattributed.” Production code shows:

```text
BleOperateManager.enableUUID()
→ 6e40fff0 service + 6e400003 notify/read

LargeDataHandler.getWriteRequest(...)
→ de5bf728 service + de5bf72a write
```

with `de5bf729` defined as the serial notify characteristic.

Implication: native iOS needs both verified service families and should route command families instead of assuming one universal GATT characteristic pair.

### Finding: official Android production stores fixed Wi-Fi password `123456789`

**Status:** PROVEN for the inspected Android connection setup

`MyBluetoothReceiver.connectStatue(...)` derives/stores a glasses Wi-Fi name from device identity/Bluetooth address and calls the preference setter for the glasses Wi-Fi password with:

```text
123456789
```

This resolves the password question for the inspected official Android version.

The upstream iOS QCSDK demos remain inconsistent about returned-vs-overridden credentials, so the iOS port must still verify physical-glasses behavior before hardcoding this across every platform/firmware mode.

### Finding: production media endpoint call sites are now attributed

**Status:** PROVEN for the inspected app paths

`PictureFragment` initializes media/config names including:

```text
media.config
vf_list.txt
log.list
```

Production download code constructs forms including:

```text
http://<glasses-ip>/files/<name>
http://<glasses-ip>/files/log/<name>
http://<glasses-ip>:80/storage/sd0/C/DCIM/1/<name>
```

`AlbumDepository.readPhotoFile(...)` uses the stored glasses IP and `/files/` (or `/files/log/` when appropriate) for download work.

A separate `http://192.168.0.1:8080/test` string belongs to `GlassesNetworkTestActivity` and should not be confused with the normal media path.

### Questions resolved by this pass

The earlier remaining-question list can now be narrowed:

```text
RESOLVED / strongly narrowed
- callable production live-preview entry point exists
- live stream endpoint is RTSP :8554/ch0
- live preview has both P2P and AP BLE activation variants
- inspected Android live selection is HarmonyOS-NEXT AP vs other Android P2P
- inspected Android Wi-Fi password is fixed 123456789
- /files/ and /files/log/ are real production media paths
- serial/large-data de5bf GATT transport is actively used

STILL OPEN
- complete outer BLE frame format + exact CRC coverage
- exact photo/video/audio/transfer/reset subcommand map
- precise error-255 trigger/recovery
- full model/firmware compatibility matrix
- glasses microphone transport + Opus framing/sample format
- AI-photo delivery path
- iOS physical verification of AP live preview + RTSP codec/playback
```

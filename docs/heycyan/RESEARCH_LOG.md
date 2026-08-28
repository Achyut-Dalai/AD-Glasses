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

## Artifact priorities

### 1. Official HeyCyan Android APK

**Priority:** high, but no longer a blocker for basic implementation planning

Unique value:

- establish what the production HeyCyan app actually does rather than what demos intend;
- resolve model/firmware-dependent AP versus P2P selection;
- resolve SSID/password/IP behavior;
- identify exact timing, retries, readiness checks, cleanup, and error handling;
- identify production HTTP endpoints and media-list formats;
- reveal undocumented SDK calls or dormant feature references;
- clarify whether speech/audio modes provide any phone-side stream or only glasses-side recording/voice behavior;
- compare official command orchestration against QCSDK.framework and CyanBridge.

It may also help expose raw BLE protocol details if those are implemented in app-visible/vendor classes, but this is **not guaranteed**: command encoding may remain inside a proprietary SDK binary. Reverse-engineering `QCSDK.framework` / the Android vendor AAR and capturing BLE traffic are complementary paths.

### 2. QCSDK.framework / Android vendor SDK binary analysis

**Priority:** high for a pure native protocol implementation

If AD Glasses should avoid shipping the proprietary vendor binary on iOS, these binaries are likely the most direct static-analysis targets for:

```text
GATT service/characteristic UUIDs
packet framing
command IDs
response parsing
checksums/sequence numbers
notification routing
internal state machine
```

The official APK can reveal orchestration, while the SDK binaries may contain the actual encoder/decoder.

### 3. CyanBridge APK

**Priority:** secondary

Current upstream release: CyanBridge v2.1.1 (`app-release.apk`).

Source is already available, so use the APK only when we need to:

- verify release-vs-source behavior;
- inspect packaged vendor dependencies;
- inspect resources/configuration not obvious in source;
- reproduce a shipped behavior that differs from current source.

The user does not need to upload this APK separately while the public release remains available.

### 4. Official HeyCyan iOS IPA

**Priority:** optional / potentially high if decrypted

An ordinary App Store IPA may contain encrypted executable code and therefore can be less immediately useful for static analysis than the Android APK.

A **decrypted IPA** would be highly valuable for comparing official iOS orchestration against the bundled `QCSDK.framework` and demos.

Do not delay the audit waiting for an iOS IPA.

---

## Remaining questions for the official HeyCyan APK / production app

The official APK is primarily needed to answer or validate these questions:

1. Does production choose AP, P2P, or a model/firmware-specific combination for media transfer?
2. What exact condition selects each network mode?
3. Is the Wi-Fi password returned by BLE, fixed, derived, corrected by the app, or firmware-dependent?
4. What device IP, port, and HTTP paths are used in production for each model/firmware family?
5. What is the exact media listing/manifest format and how are deletions/downloads sequenced?
6. What readiness checks occur between `openWifiWithMode` and the network join?
7. What cleanup/reset command is issued after sync or timeout?
8. What produces error `255`, and does production interpret it specially?
9. Which device-info/battery/media commands are issued immediately after BLE connection?
10. Are photo, AI-photo, video, and audio actions simple `setDeviceMode` calls in production or surrounded by additional state checks?
11. Does `speechRecognition` or any other mode actually stream microphone/audio data to the phone, or only alter glasses-side behavior?
12. Are there any unused/dormant livestream or RTSP entry points in the official HeyCyan application layer?
13. Which behaviors vary across hardware project/customer IDs or firmware versions?
14. Are there extra authentication/handshake steps absent from the public demo code?

These questions improve correctness and compatibility. They do **not** invalidate the already-proven BLE-control → Wi-Fi-hotspot → HTTP-transfer architecture.

---

## Physical-device verification queue

After static analysis, verify one feature at a time on a real pair of glasses:

```text
1. connect
2. battery
3. version/device info
4. photo capture
5. AI photo if supported
6. video start/stop
7. transfer-mode activation
8. hotspot readiness/IP
9. iOS hotspot join
10. media.config / manifest
11. latest photo download
12. video/audio media download
13. cleanup/reconnect
14. audio/microphone path
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

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

Implication: the ecosystem supports more than one Wi-Fi topology. We still need the official app to establish production AP/P2P selection rules.

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

Do not hardcode either rule in the AD Glasses native implementation until the official app and physical glasses settle this.

---

## Artifacts requested next

### 1. Official HeyCyan Android APK

**Priority:** highest

Why it is valuable:

- Android APKs are usually straightforward to statically decompile with JADX/apktool.
- It can reveal the production app's command orchestration even when the proprietary SDK hides packet internals.
- It can resolve AP vs P2P selection, credential handling, retries, local HTTP endpoints, timing, and model/firmware branching.
- It provides a production-behavior baseline to compare against CyanBridge and the iOS QCSDK demo.

Desired audit areas:

```text
BLE initialization
GATT / SDK calls
photo/video/audio modes
battery/device info
AI photo
Wi-Fi transfer setup
AP vs P2P selection
SSID/password/IP handling
HTTP endpoints
media manifests
timeouts/retries
cleanup/error handling
hidden/debug/live strings
```

### 2. Official HeyCyan iOS IPA

**Priority:** useful if available decrypted

An ordinary App Store IPA may contain encrypted executable code and therefore can be less immediately useful for static analysis than the Android APK.

A **decrypted IPA** would be highly valuable for comparing official iOS orchestration against the bundled `QCSDK.framework` and demos.

Do not delay the audit waiting for an iOS IPA; the Android APK should come first.

### 3. CyanBridge APK

**Priority:** secondary

The source repository is already available, so the APK mainly helps confirm what code actually shipped in a particular release or inspect bundled/obfuscated dependencies.

---

## Questions the official APK must answer

1. Which exact production sequence follows BLE connection?
2. Does the official app call battery/device-info commands immediately after connection?
3. Which operation mode triggers normal media sync?
4. Does production use AP, P2P, or choose based on model/firmware?
5. How is the Wi-Fi peer/SSID identified?
6. Is the Wi-Fi password fixed, returned, derived, or model-specific?
7. What IP/port/path does media sync actually use?
8. What is the exact media manifest/listing format?
9. How are thumbnail and AI-photo transfers handled?
10. Are audio/speech modes local recording only or can microphone audio stream to the phone?
11. Are there dormant/unused live-preview calls in the official HeyCyan app?
12. Which cleanup command/state is required after Wi-Fi transfer?
13. Which failures produce error 255 and under what timeout/state conditions?
14. Which behavior changes across firmware/device variants?

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

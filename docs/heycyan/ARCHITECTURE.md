# HeyCyan architecture audit

Status: living document

Last updated: 2026-08-28

This document records the current best-supported architecture for communicating with HeyCyan glasses from the native iOS AD Glasses app.

It intentionally separates:

- what the product wants to expose;
- what the glasses/SDK are known to support;
- what native iOS can implement;
- what is still reverse-engineering work.

---

## 1. Source-of-truth hierarchy

When sources disagree, use this order of confidence:

1. **Observed official HeyCyan app behavior or hardware packet capture**
2. **Official/vendor QCSDK interfaces and binaries**
3. **Vendor demo code using those interfaces**
4. **CyanBridge reverse-engineered behavior and author findings**
5. **`heycyan-core` transport/connectivity helpers and repository documentation**
6. **Our own AD Glasses assumptions**

Our code must never promote an assumption from level 6 into protocol truth without evidence from a stronger level.

---

## 2. Current transport model

### 2.1 BLE is the control plane

BLE is used for low-bandwidth device control and state.

Known SDK-level operations include:

- device mode changes;
- photo capture;
- video start/stop;
- audio recording modes;
- AI photo mode;
- transfer mode;
- battery and charging state;
- firmware/hardware version information;
- media counts;
- volume;
- wearing detection;
- voice wakeup/status;
- device configuration;
- Wi-Fi activation/readiness;
- DFU/OTA-related commands.

The public iOS QCSDK command surface contains `QCSDKCmdCreator` methods for these operations.

The exposed operation modes seen in the Swift wrapper are:

```text
0x00 unknown
0x01 photo
0x02 video
0x03 video stop
0x04 transfer
0x05 OTA
0x06 AI photo
0x07 speech recognition
0x08 audio
```

No public livestream operation mode has been established for HeyCyan.

### 2.2 Wi-Fi is the high-bandwidth data plane

The documented transfer flow is Bluetooth-first:

```text
BLE connected
    ↓
request transfer mode
    ↓
receive hotspot credentials
    ↓
wait for BLE confirmation that Wi-Fi is ready
    ↓
obtain/confirm device IP
    ↓
join glasses Wi-Fi
    ↓
HTTP media/file transfer
```

This matters: the Wi-Fi transport is not a replacement for BLE. BLE prepares and synchronizes the Wi-Fi session.

### 2.3 HTTP is an application protocol over the local Wi-Fi link

Repository iOS demo code probes local paths including:

```text
/files/media.config
```

and then performs media discovery/download over `NSURLSession`/HTTP.

Do not describe HTTP as “the BLE protocol.” It is a separate higher-bandwidth phase after a BLE-controlled handoff.

---

## 3. Wi-Fi modes

### 3.1 AP/hotspot mode

The iOS QCSDK demo proves a native-iOS-compatible flow exists:

```text
QCSDKCmdCreator.openWifiWithMode(transfer)
        ↓
SSID + password
        ↓
QCSDKCmdCreator.getDeviceWifiIPSuccess(...)
        ↓
NEHotspotConfiguration
        ↓
iPhone joins glasses-hosted AP
        ↓
HTTP request to glasses device IP
```

This is the preferred iOS path unless later evidence proves a feature requires another mode.

### 3.2 Wi-Fi Direct / P2P

CyanBridge contains explicit HeyCyan P2P logic and recognizes routes/interfaces such as:

```text
p2p*
wfd*
192.168.49.*
```

This confirms that Wi-Fi Direct/P2P is part of the wider HeyCyan ecosystem on Android.

However:

- do not assume every media operation uses P2P;
- do not assume Android `WifiP2pManager` behavior has a direct iOS equivalent;
- do not block the native iOS implementation on P2P while the AP/hotspot flow remains available.

The official HeyCyan app audit must determine when AP versus P2P is selected and whether the choice depends on model, firmware, or operation.

---

## 4. iOS platform feasibility

### BLE

Native iOS CoreBluetooth is technically capable of:

- scanning;
- connecting;
- service discovery;
- characteristic discovery;
- writes;
- notifications;
- reconnect/state restoration.

The limiting factor is not CoreBluetooth itself. The limiting factor is knowing the correct HeyCyan application protocol and state machine.

### Accessory-hosted Wi-Fi

Native iOS can join an accessory hotspot using NetworkExtension/`NEHotspotConfiguration` with user/system authorization.

The author's iOS QCSDK demo already implements this path.

### Local HTTP

Once the iPhone is on the glasses network, HTTP/media operations can be implemented with `URLSession`, subject to normal iOS local-network and transport-security configuration.

---

## 5. Current AD Glasses iOS state

The current native `HeyCyanGlassesProvider` is a foundation, not a complete hardware protocol implementation.

At the time of this audit it provides the Bluetooth discovery/connection layer but does not yet constitute a complete verified implementation of:

- HeyCyan service/characteristic discovery;
- proprietary command encoding;
- command acknowledgement handling;
- device-state notifications;
- Wi-Fi transfer-mode preparation;
- AP joining;
- media HTTP transfer;
- glasses audio transport.

Do not infer hardware support from UI buttons alone.

---

## 6. Recommended native iOS layering

```text
SwiftUI feature
      │
      ▼
App / feature model
      │
      ▼
GlassesManager
      │
      ▼
HeyCyanGlassesProvider
      │
      ├──────────────┬────────────────┬────────────────┐
      ▼              ▼                ▼                ▼
HeyCyanBLE      HeyCyanProtocol   HeyCyanWiFi     HeyCyanMedia
Transport          Codec          Controller          Client
      │              │                │                │
CoreBluetooth   command/state   NetworkExtension    URLSession
```

### Responsibilities

#### `HeyCyanBLETransport`

Owns byte transport only:

- scan/connect/reconnect;
- discover services and characteristics;
- subscribe to notifications;
- write bytes;
- deliver raw responses;
- BLE timeout/retry mechanics.

It should not know product semantics such as “take photo.”

#### `HeyCyanProtocolCodec`

Owns protocol semantics:

- verified UUIDs;
- command IDs;
- framing;
- sequence fields;
- checksums/CRC if applicable;
- response parsing;
- mode/state mapping;
- error-code mapping.

No bytes should be invented here.

#### `HeyCyanWiFiController`

Owns Wi-Fi lifecycle:

- request/receive prepared hotspot information from provider/protocol layer;
- join via `NEHotspotConfiguration`;
- confirm reachability to the device IP;
- disconnect/cleanup;
- model firmware-specific rules when proven.

#### `HeyCyanMediaClient`

Owns local IP media operations:

- manifest/media configuration;
- listing;
- thumbnails;
- image/video/audio downloads;
- deletes if supported;
- transfer progress/cancellation;
- HTTP retry/error handling.

#### `HeyCyanGlassesProvider`

Coordinates user-level features without embedding transport details.

Example flow:

```text
provider.downloadLatestPhoto()
        ↓
protocol/SDK prepares transfer mode over BLE
        ↓
WiFiController joins glasses AP
        ↓
MediaClient downloads media
        ↓
provider returns application model
```

---

## 7. Product capability assessment

Legend:

- ✅ proven/strongly established
- 🟡 likely/supported at SDK surface but needs native protocol integration or physical verification
- 🔬 experimental / firmware-research territory
- ❓ unknown

| Capability | Status | Notes |
| --- | --- | --- |
| BLE discovery | ✅ | Native iOS already has foundation. |
| BLE connection | ✅ | Standard CoreBluetooth/vendor SDK flow. |
| Battery | 🟡 | QCSDK exposes it; native AD Glasses transport still needs integration. |
| Device/version info | 🟡 | QCSDK exposes it. |
| Photo capture | 🟡 | Device mode is exposed. |
| Video start/stop | 🟡 | Device modes are exposed. |
| Audio recording mode | 🟡 | Exposed by SDK, but actual audio data path must be audited separately. |
| AI photo | 🟡 | Exposed and iOS wrapper receives AI image data through SDK delegate. |
| Media counts | 🟡 | Exposed by QCSDK. |
| Wi-Fi transfer activation | ✅/🟡 | Strongly demonstrated by iOS QCSDK demo; physical verification in our app still needed. |
| Join glasses AP on iOS | ✅ | Demo uses `NEHotspotConfiguration`. |
| HTTP media transfer | ✅/🟡 | Implemented in demo; exact production endpoints/edge cases still to verify. |
| HeyCyan Wi-Fi Direct/P2P | ✅ on Android | Explicit CyanBridge support exists. Requirement on iOS remains model/feature-dependent. |
| Continuous HeyCyan camera livestream | 🔬 | Author reports dormant/test implementation on Wi-Fi side but no exposed HeyCyan Bluetooth SDK command. Do not depend on it. |
| Glasses microphone streaming to Assistant | ❓ | Public mode exists for speech/audio, but transport/encoding path is not yet established by this audit. |

---

## 8. Lens architecture implication

Until a verified HeyCyan live-camera command exists, Lens must not assume a continuous camera stream.

A safer supported architecture is:

```text
Lens request
    ↓
BLE/SDK AI-photo or photo capture
    ↓
receive image directly OR prepare transfer
    ↓
retrieve captured image
    ↓
vision/AI analysis
    ↓
result
```

A future livestream path can be added behind a capability interface if firmware/protocol research proves it safe and activatable.

---

## 9. Important unresolved contradictions

### Wi-Fi password behavior

One iOS demo path forces a fixed password after the SDK returns credentials, while another uses the returned password.

This must be resolved from the official app and physical testing before hardcoding either behavior.

### AP versus P2P selection

Both mechanisms exist in repository evidence. The official app must tell us the production selection rule for actual HeyCyan models/firmwares.

### Audio path

The existence of audio/speech modes does not prove the phone can receive a continuous microphone stream in the format our Assistant needs.

### Live camera

Do not confuse findings from other supported glasses families (for example Eyevue RTSP/live commands) with HeyCyan behavior.

---

## 10. Implementation milestones

Proceed in this order:

1. Verify official BLE initialization and service/characteristic behavior.
2. Implement/verify battery + device info.
3. Implement/verify photo command and response.
4. Implement Wi-Fi transfer preparation over BLE.
5. Implement iOS AP join and readiness checks.
6. Implement HTTP media listing/download.
7. Implement photo/video/audio product flows on top of the verified transports.
8. Audit glasses microphone/audio transport separately.
9. Add reconnect/background/error-state behavior.
10. Treat livestream/firmware modifications as a separate experimental track.

---

## 11. Rules for future changes

Before adding a HeyCyan feature:

- identify its evidence source;
- record the command/state/network sequence;
- identify model/firmware constraints;
- test failure/timeout/cleanup behavior;
- update the research log;
- only then wire the feature into consumer UI.

Never copy a protocol from another glasses family merely because the hardware looks similar.

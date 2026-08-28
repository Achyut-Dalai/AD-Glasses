# HeyCyan architecture audit

Status: living document

Last updated: 2026-08-29

This document records the current best-supported architecture for communicating with HeyCyan glasses from the native iOS AD Glasses app.

It intentionally separates:

- what the product wants to expose;
- what the glasses/SDK are known to support;
- what native iOS can implement;
- what is still reverse-engineering work.

---

## 1. Source-of-truth hierarchy

When sources disagree, use this order of confidence:

1. **Observed official HeyCyan production-app behavior or hardware packet capture**
2. **Official/vendor QCSDK interfaces and binaries**
3. **Vendor demo code using those interfaces**
4. **CyanBridge reverse-engineered behavior and author findings**
5. **`heycyan-core` transport/connectivity helpers and repository documentation**
6. **Our own AD Glasses assumptions**

Our code must never promote an assumption from level 6 into protocol truth without evidence from a stronger level.

The official Android XAPK `1.0.142_20260807` is now part of this evidence set. Its production DEX contains inspectable Oudmon BLE protocol implementation classes, which gives us a stronger source than public wrappers alone for several transport details.

---

## 2. Current transport model

### 2.1 BLE is the control plane

BLE is used for low-bandwidth device control and state.

Known SDK/application-level operations include:

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

The exposed operation modes seen in the upstream Swift wrapper are:

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

No supported public HeyCyan livestream operation mode has been established.

### 2.2 Official production GATT evidence

The official Android production package contains the Oudmon protocol implementation and exposes these primary GATT constants:

```text
Primary service
6e40fff0-b5a3-f393-e0a9-e50e24dcca9e

Notify/read characteristic
6e400003-b5a3-f393-e0a9-e50e24dcca9e

Write characteristic
6e400002-b5a3-f393-e0a9-e50e24dcca9e

CCCD
00002902-0000-1000-8000-00805f9b34fb
```

A second serial-port-style UUID family is also present:

```text
Service  de5bf728-d711-4e47-af26-65e3012a5dc7
Notify   de5bf729-d711-4e47-af26-65e3012a5dc7
Write    de5bf72a-d711-4e47-af26-65e3012a5dc7
```

The primary notification path is strongly established. The role of the second UUID family must still be attributed before it is used by the native iOS implementation.

### 2.3 BLE link state is not device-ready state

The official production app performs protocol initialization after the BLE connection is established. Evidence includes initialization paths that perform time/device/settings synchronization before normal use.

Therefore model the native iOS state machine approximately as:

```text
disconnected
    ↓
scanning
    ↓
connecting BLE
    ↓
GATT connected
    ↓
discover services / characteristics
    ↓
enable notifications
    ↓
protocol initialization
    ├─ time sync
    ├─ device info
    └─ device/settings sync
    ↓
ready
```

Do not expose a fully operational `connected` state to features merely because `CBCentralManager` reported `didConnect`.

### 2.4 Production protocol families are becoming reconstructable

The official app's `LargeDataHandler` / related Oudmon classes contain application framing and response parsing.

Confirmed command-family values seen in the production implementation include:

```text
0x41 / 65   glasses-control family
0x42 / 66   battery synchronization
0x43 / 67   device-info synchronization
0xFC / -4   IP/Wi-Fi-side information operation
```

The production encoder performs framing and CRC16 work. Exact framing layout and CRC coverage are still under audit; do not copy speculative packet bytes into Swift yet.

### 2.5 Wi-Fi is the high-bandwidth data plane

The documented transfer flow is Bluetooth-first:

```text
BLE ready
    ↓
request transfer mode
    ↓
receive network information
    ↓
wait for BLE confirmation that Wi-Fi is ready
    ↓
obtain/confirm device IP
    ↓
join glasses Wi-Fi
    ↓
HTTP media/file transfer
```

This matters: Wi-Fi is not a replacement for BLE. BLE prepares and synchronizes the Wi-Fi session.

### 2.6 HTTP is an application protocol over the local Wi-Fi link

Repository iOS demo code probes local paths including:

```text
/files/media.config
```

and then performs media discovery/download over `NSURLSession`/HTTP.

The official production APK also contains local media/network strings such as `media.config`, `/files/`, `/playlist.json`, fixed/local IP strings, and password candidates. Those values are not promoted to production constants until their call sites are fully attributed.

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

This remains the preferred iOS path unless later evidence proves a feature/model requires another mode.

### 3.2 Wi-Fi Direct / P2P

P2P is no longer supported only by CyanBridge evidence: the official HeyCyan production APK itself contains both AP helper code and `WifiP2pManager` integration.

CyanBridge additionally contains explicit HeyCyan P2P route policy and recognizes routes/interfaces such as:

```text
p2p*
wfd*
192.168.49.*
```

Therefore both AP/hotspot and Android Wi-Fi Direct/P2P are real production concepts.

However:

- do not assume every media operation uses P2P;
- do not assume Android `WifiP2pManager` behavior has a direct iOS equivalent;
- do not block the native iOS implementation on P2P while the AP/hotspot flow remains available;
- keep model/firmware-specific network selection behind capability/configuration logic once the exact selection rule is proven.

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

The official production APK now gives us concrete GATT/protocol evidence, so the task is no longer “can iOS manipulate BLE?” It is to faithfully port the proven Oudmon/HeyCyan application protocol and state machine.

### Accessory-hosted Wi-Fi

Native iOS can join an accessory hotspot using NetworkExtension/`NEHotspotConfiguration` with user/system authorization.

The author's iOS QCSDK demo already implements this path.

### Local HTTP

Once the iPhone is on the glasses network, HTTP/media operations can be implemented with `URLSession`, subject to normal iOS local-network and transport-security configuration.

---

## 5. Current AD Glasses iOS state

The current native `HeyCyanGlassesProvider` is a foundation, not a complete hardware protocol implementation.

At the time of this audit it provides Bluetooth discovery/connection foundations but does not yet constitute a complete verified implementation of:

- the official service/characteristic setup;
- post-connect protocol initialization/readiness;
- Oudmon/HeyCyan command encoding;
- command acknowledgement and response matching;
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
      ├──────────────┬────────────────┬────────────────┬────────────────┐
      ▼              ▼                ▼                ▼                ▼
HeyCyanBLE      HeyCyanProtocol   HeyCyanSession   HeyCyanWiFi     HeyCyanMedia
Transport          Codec             State         Controller          Client
      │              │                │                │                │
CoreBluetooth   command/state     readiness      NetworkExtension    URLSession
```

### Responsibilities

#### `HeyCyanBLETransport`

Owns byte transport only:

- scan/connect/reconnect;
- discover verified services and characteristics;
- subscribe to notifications;
- write bytes;
- deliver raw responses;
- BLE timeout/retry mechanics.

It should not know product semantics such as “take photo.”

#### `HeyCyanProtocolCodec`

Owns protocol semantics:

- verified UUID mapping;
- command IDs;
- framing;
- sequence/length fields;
- CRC/checksum rules;
- response parsing;
- mode/state mapping;
- error-code mapping.

No bytes should be invented here.

#### `HeyCyanSessionState`

Coordinates post-connect readiness and exclusivity:

```text
GATT connected
→ notification enabled
→ initialization commands
→ ready
→ busy/capture/transfer/audio/etc.
→ cleanup
→ ready
```

This layer prevents features from issuing conflicting commands while the glasses are busy or in another working mode.

#### `HeyCyanWiFiController`

Owns Wi-Fi lifecycle:

- receive prepared hotspot/network information from the protocol/session layer;
- join via `NEHotspotConfiguration` where AP mode is supported;
- confirm reachability to the device IP;
- disconnect/cleanup;
- apply model/firmware-specific rules only when proven.

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
session ensures glasses ready
        ↓
protocol prepares transfer mode over BLE
        ↓
WiFiController joins glasses AP
        ↓
MediaClient downloads media
        ↓
session performs cleanup / returns to ready
        ↓
provider returns application model
```

---

## 7. Product capability assessment

Legend:

- ✅ proven/strongly established
- 🟡 supported but native implementation/physical verification remains
- 🔬 experimental / firmware-research territory
- ❓ unknown

| Capability | Status | Notes |
| --- | --- | --- |
| BLE discovery | ✅ | Native iOS foundation exists. |
| BLE connection | ✅ | Standard CoreBluetooth/vendor SDK flow. |
| Official GATT control path | ✅ | Production APK exposes primary service/write/notify UUIDs. |
| Protocol readiness/init phase | ✅ | Production app performs initialization after BLE connection. |
| Battery + charging | ✅/🟡 | Production response parser/API exists; native Swift port remains. |
| Device/version info | ✅/🟡 | Production response parser/API exists; native Swift port remains. |
| Photo capture | 🟡 | Supported operation; exact raw subcommand/state flow still being mapped. |
| Video start/stop | 🟡 | Supported operation; exact raw subcommand/state flow still being mapped. |
| Audio recording mode | 🟡 | Exposed by SDK/app; distinguish file recording from live voice transport. |
| AI photo | 🟡 | QCSDK/iOS wrapper supports it; production transfer path still being mapped. |
| Media counts | ✅/🟡 | Production glasses-control parser exposes image/video/audio counts. |
| Wi-Fi transfer activation | ✅/🟡 | Demonstrated in iOS QCSDK path; raw production command/state mapping still being completed. |
| Join glasses AP on iOS | ✅ | Demo uses `NEHotspotConfiguration`. |
| HTTP media transfer | ✅/🟡 | Demonstrated; production endpoint attribution/edge cases remain. |
| HeyCyan Wi-Fi Direct/P2P | ✅ on Android | Present in official APK and CyanBridge. iOS requirement remains model/feature-dependent. |
| Continuous HeyCyan camera livestream | 🔬 | Wi-Fi-side dormant/test findings exist, but no verified supported production activation command yet. |
| Glasses voice/audio stream toward phone | 🟡 | Official app contains glasses Azure speech/Opus processing and large-data callbacks; exact transport/codec framing still being audited. |

---

## 8. Audio / Assistant implication

The official production APK increases confidence that HeyCyan has a glasses-oriented voice path. It contains a `GlassesAzureSpeechRecognizer`, Opus decoding/stream logic, protocol package callbacks, and voice heartbeat behavior.

Do **not** yet assume the transport is ordinary BLE GATT audio. The next audit must establish:

```text
source transport
→ packet type/framing
→ codec (Opus/etc.)
→ sample rate/channels
→ stream lifecycle
→ heartbeat/state requirements
```

Once verified, expose decoded PCM/audio through a glasses-neutral audio capability so Assistant does not depend on HeyCyan-specific implementation details.

---

## 9. Lens architecture implication

Until a verified supported HeyCyan live-camera activation command exists, Lens must not assume a continuous camera stream.

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

## 10. Important unresolved questions / contradictions

### Wi-Fi password behavior

One iOS demo path forces a fixed password after the SDK returns credentials, another uses the returned password, and fixed credential strings also appear in the official APK.

Call sites and physical behavior must settle the actual production rule before hardcoding anything.

### AP versus P2P selection

Both mechanisms now exist in official production evidence. The exact selection rule by model/firmware/operation remains unresolved.

### Error 255

The official `GlassModelControlResponse` parser contains explicit handling for error value `255`, confirming it belongs to the device control/state protocol. The exact triggering condition and recovery sequence still need mapping.

### Audio path

Official production evidence strongly suggests a live voice/audio flow, but the actual transport and framing are not yet fully attributed.

### Live camera

Do not confuse findings from other glasses families (for example Eyevue RTSP/live commands) with HeyCyan behavior. Dormant Wi-Fi firmware code is not the same as an exposed safe production command.

---

## 11. Implementation milestones

Proceed in this order:

1. Implement official GATT service/characteristic discovery and notification enablement.
2. Reconstruct/verify application framing + CRC against official production code.
3. Implement protocol initialization/readiness state.
4. Implement and verify battery + device info.
5. Map and verify photo command and response.
6. Map Wi-Fi transfer preparation and AP/P2P selection evidence.
7. Implement iOS AP join and readiness checks.
8. Implement HTTP media listing/download.
9. Implement photo/video/audio product flows on top of verified transports.
10. Fully audit glasses voice/audio streaming and feed decoded audio into the platform-neutral Assistant path.
11. Add reconnect/background/error-state behavior.
12. Treat livestream/firmware modifications as a separate experimental track.

---

## 12. Rules for future changes

Before adding a HeyCyan feature:

- identify its evidence source;
- record the command/state/network sequence;
- identify model/firmware constraints;
- test failure/timeout/cleanup behavior;
- update the research log;
- only then wire the feature into consumer UI.

Never copy a protocol from another glasses family merely because the hardware looks similar.

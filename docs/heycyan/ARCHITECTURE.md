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
- real-time preview activation;
- DFU/OTA-related commands.

The public iOS QCSDK command surface contains `QCSDKCmdCreator` methods for many of these operations.

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

The public QCSDK wrapper does not expose a named livestream mode, but the inspected official Android production app **does** activate real-time preview through lower-level `LargeDataHandler.glassesControl(...)` payloads. Therefore “not exposed by the public wrapper” must not be interpreted as “not supported by the production device/application protocol.”

### 2.2 Official production GATT evidence

The official Android production package contains the Oudmon protocol implementation and initializes these ordinary command-channel constants:

```text
UUID_SERVICE
6e40fff0-b5a3-f393-e0a9-e50e24dcca9e

UUID_READ / notify
6e400003-b5a3-f393-e0a9-e50e24dcca9e

UUID_WRITE
6e400002-b5a3-f393-e0a9-e50e24dcca9e

CCCD
00002902-0000-1000-8000-00805f9b34fb
```

A second serial/large-data family is actively used by production code:

```text
SERIAL_PORT_SERVICE
 de5bf728-d711-4e47-af26-65e3012a5dc7

SERIAL_PORT_CHARACTER_NOTIFY
 de5bf729-d711-4e47-af26-65e3012a5dc7

SERIAL_PORT_CHARACTER_WRITE
 de5bf72a-d711-4e47-af26-65e3012a5dc7
```

The distinction is now proven:

```text
BleOperateManager.enableUUID()
→ UUID_SERVICE + UUID_READ
→ ordinary notification path

LargeDataHandler.getWriteRequest(payload)
→ SERIAL_PORT_SERVICE + SERIAL_PORT_CHARACTER_WRITE
→ serial / large-data write path
```

Therefore the native iOS implementation must not assume one GATT write/notify pair serves every feature. Service discovery should recognize both verified transport families and route command families through the appropriate transport.

The exact per-command routing map is still being completed.

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
discover verified services / characteristics
    ↓
enable required notifications
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

The production encoder performs framing and CRC16 work. Exact full-frame layout and CRC coverage are still under audit; do not copy speculative complete frames into Swift yet.

#### Real-time preview control payloads

The official production `RealTimePreviewActivity` passes these byte payloads into `LargeDataHandler.glassesControl(...)`:

```text
02 01 14 01   start real-time preview using P2P path
02 01 14 02   start real-time preview using AP path
02 01 15 01   cleanup/exit payload sent when preview is destroyed
```

These are payloads inside the glasses-control command family, not yet documented here as complete GATT frames.

This is production-app evidence from HeyCyan `1.0.142_20260807` and supersedes the older assumption that no application-level Bluetooth activation path existed for live preview.

### 2.5 Wi-Fi is the high-bandwidth data plane

The documented transfer flow is Bluetooth-first:

```text
BLE ready
    ↓
request network-dependent mode
    ↓
receive / derive network information
    ↓
wait for device network readiness
    ↓
join glasses network
    ↓
HTTP media transfer OR RTSP live preview
```

This matters: Wi-Fi is not a replacement for BLE. BLE prepares and synchronizes the Wi-Fi session.

### 2.6 HTTP is an application protocol over the local Wi-Fi link

Repository iOS demo code probes local paths including:

```text
/files/media.config
```

The official production app now gives stronger call-site evidence for media transfer. It defines media/config filenames including:

```text
media.config
vf_list.txt
log.list
```

Production paths include forms such as:

```text
http://<glasses-ip>/files/<name>
http://<glasses-ip>/files/log/<name>
http://<glasses-ip>:80/storage/sd0/C/DCIM/1/<name>
```

`AlbumDepository` builds photo/media downloads from the stored glasses device IP using `/files/` and, when logs are enabled, `/files/log/`.

A separate test-only string `http://192.168.0.1:8080/test` exists in `GlassesNetworkTestActivity`; do not use that as the production media endpoint.

Do not describe HTTP as “the BLE protocol.” It is a separate higher-bandwidth phase after BLE-controlled mode/network preparation.

### 2.7 RTSP is the production live-preview data path

The official production app contains `RealTimePreviewActivity` and initializes VLC playback using:

```text
rtsp://<glassDeviceWifiIP>:8554/ch0
```

The live-preview sequence observed in production is therefore approximately:

```text
BLE ready
    ↓
glassesControl(live P2P or AP payload)
    ↓
P2P discovery OR AP join
    ↓
store/resolve glasses Wi-Fi IP
    ↓
RTSP player
    ↓
rtsp://<ip>:8554/ch0
```

This is separate from HTTP media synchronization.

---

## 3. Wi-Fi modes

### 3.1 AP/hotspot mode

The iOS QCSDK demo proves a native-iOS-compatible transfer flow exists:

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

The official Android production app also has an AP real-time-preview path. For real-time preview it sends the `02 01 14 02` glasses-control payload and then connects to the stored glasses Wi-Fi name/password using the AP helper.

This is important for iOS: although the Android app normally prefers P2P on Android, the glasses themselves expose an AP live-preview variant, making an iOS-native AP + RTSP implementation a realistic path to test.

### 3.2 Wi-Fi Direct / P2P

P2P is no longer supported only by CyanBridge evidence: the official HeyCyan production APK itself contains `WifiP2pManager` integration and uses it for real-time preview.

For the inspected production live-preview flow:

```text
normal Android path
→ register P2P receiver
→ start P2P discovery
→ glassesControl(02 01 14 01)
→ obtain P2P connection/IP
→ RTSP playback
```

The same activity contains an AP alternative. Its permission-selection code chooses AP when `isHarmonyOSNEXT` is true and otherwise chooses P2P.

This selection rule is specific to the inspected official Android activity and should not be generalized to every HeyCyan operation.

CyanBridge additionally contains explicit HeyCyan P2P route policy and recognizes routes/interfaces such as:

```text
p2p*
wfd*
192.168.49.*
```

Therefore both AP/hotspot and Android Wi-Fi Direct/P2P are real production concepts.

### 3.3 Production Wi-Fi credential behavior

The official Android `MyBluetoothReceiver.connectStatue(...)` builds/stores a glasses Wi-Fi name from the connected device identity and Bluetooth address and stores:

```text
123456789
```

as the glasses Wi-Fi password.

This is strong production evidence for Android HeyCyan `1.0.142_20260807` and resolves the fixed-password question for that path.

However, the upstream iOS QCSDK demos contain inconsistent credential handling, including one flow that uses credentials returned by the vendor SDK. Therefore native iOS should verify the physical-glasses behavior before assuming the Android fixed-password rule applies identically to every firmware/iOS mode.

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

The production Android live-preview AP variant strengthens the case that the glasses can expose a network topology that iOS can plausibly join for RTSP, rather than requiring Android Wi-Fi Direct exclusively.

### Local HTTP

Once the iPhone is on the glasses network, HTTP/media operations can be implemented with `URLSession`, subject to normal iOS local-network and transport-security configuration.

### RTSP live preview

The glasses production app serves real-time preview at:

```text
rtsp://<device-ip>:8554/ch0
```

On iOS we should not assume the Android VLC implementation can simply be copied. The network/control sequence can be ported, while the iOS playback implementation should use an appropriate native-compatible media stack after confirming stream codec/container characteristics on the physical device.

---

## 5. Current AD Glasses iOS state

The current native `HeyCyanGlassesProvider` is a foundation, not a complete hardware protocol implementation.

At the time of this audit it provides Bluetooth discovery/connection foundations but does not yet constitute a complete verified implementation of:

- both official GATT transport families;
- post-connect protocol initialization/readiness;
- Oudmon/HeyCyan command encoding;
- command acknowledgement and response matching;
- device-state notifications;
- Wi-Fi transfer/live-mode preparation;
- AP joining;
- media HTTP transfer;
- RTSP live preview;
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
      ├──────────────┬────────────────┬────────────────┬────────────────┬───────────────┐
      ▼              ▼                ▼                ▼                ▼               ▼
HeyCyanBLE      HeyCyanProtocol   HeyCyanSession   HeyCyanWiFi     HeyCyanMedia   HeyCyanLive
Transport          Codec             State         Controller          Client          Client
      │              │                │                │                │               │
CoreBluetooth   command/state     readiness      NetworkExtension    URLSession      RTSP player
```

### Responsibilities

#### `HeyCyanBLETransport`

Owns byte transport only:

- scan/connect/reconnect;
- discover both verified service families;
- subscribe to required notifications;
- expose ordinary-command and large-data write routes;
- deliver raw responses;
- BLE timeout/retry mechanics.

It should not know product semantics such as “take photo” or “start live preview.”

#### `HeyCyanProtocolCodec`

Owns protocol semantics:

- verified UUID mapping;
- command IDs and subcommands;
- routing to base vs serial/large-data transport;
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
→ notifications enabled
→ initialization commands
→ ready
→ busy/capture/transfer/audio/live/etc.
→ cleanup
→ ready
```

This layer prevents features from issuing conflicting commands while the glasses are busy or in another working mode.

#### `HeyCyanWiFiController`

Owns Wi-Fi lifecycle:

- receive/derive network information from the protocol/session layer;
- join via `NEHotspotConfiguration` for iOS-compatible AP modes;
- confirm reachability to the device IP;
- disconnect/cleanup;
- keep P2P-specific logic out of iOS unless an actually supported Apple-compatible mechanism is proven necessary.

#### `HeyCyanMediaClient`

Owns local HTTP media operations:

- `media.config` / media configuration;
- listing;
- thumbnails;
- image/video/audio downloads;
- deletes if supported;
- transfer progress/cancellation;
- HTTP retry/error handling.

#### `HeyCyanLiveClient`

Owns real-time-preview data transport after the session/network layers have prepared the glasses:

```text
session requests AP live mode
→ WiFiController joins glasses AP
→ resolve device IP
→ open rtsp://<ip>:8554/ch0
→ surface decoded video frames/playback state
→ cleanup command + network teardown
```

It should not itself invent/send BLE control bytes.

#### `HeyCyanGlassesProvider`

Coordinates user-level features without embedding transport details.

Example media flow:

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
```

Example live flow:

```text
provider.startLivePreview()
        ↓
session ensures glasses ready
        ↓
protocol sends verified AP live payload
        ↓
WiFiController joins glasses AP
        ↓
LiveClient opens RTSP stream
        ↓
provider exposes frames/state to Lens
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
| Base GATT command path | ✅ | Production APK exposes `6e40...` service/write/notify UUIDs. |
| Serial/large-data GATT path | ✅ | Production `LargeDataHandler` uses the `de5bf...` service/write family. |
| Protocol readiness/init phase | ✅ | Production app performs initialization after BLE connection. |
| Battery + charging | ✅/🟡 | Strict native response parser and ready-session refresh exist; physical-iPhone verification remains. |
| Device/version info | ✅/🟡 | Strict native version parser and ready-session refresh exist; physical-iPhone verification remains. |
| Clock/time-zone sync | ✅/🟡 | Exact SDK BCD/language/time-zone payload is ported and covered by the captured India vector. |
| Classic audio connection request | ✅/🟡 | Captured family `0x49` request/echo is sent after BLE setup; iOS still owns system Classic pairing/routing. |
| Photo capture | ✅/🟡 | Captured raw command and strict acknowledgement are native; physical-iPhone verification remains. |
| Video start/stop | ✅/🟡 | Captured raw commands exist beneath the provider boundary; product state validation remains. |
| Audio recording mode | ✅/🟡 | Captured raw commands exist beneath the provider boundary; distinguish local file recording from live voice transport. |
| AI photo | ✅/🟡 | Captured command exists beneath the provider boundary; delivery/product-state validation remains. |
| Media counts | ✅/🟡 | Production glasses-control parser exposes image/video/audio counts. |
| Wi-Fi transfer activation | ✅/🟡 | Native coordinator correlates work-type `04` credentials with `0x73/0x08` address and performs transactional cleanup; AP response still needs physical-iPhone validation. |
| Join glasses AP on iOS | ✅ | Demo uses `NEHotspotConfiguration`. |
| HTTP media transfer | ✅/🟡 | Production paths and repository iOS demo both establish this architecture. |
| HeyCyan Wi-Fi Direct/P2P | ✅ on Android | Present in official APK and CyanBridge. |
| Real-time HeyCyan camera preview | ✅/🟡 | Official production app activates BLE live mode and plays RTSP `:8554/ch0`; native iOS AP path still needs physical verification. |
| AP-mode live preview | ✅/🟡 | Official production app contains `02 01 14 02` AP activation path; iOS feasibility is promising but must be tested. |
| Glasses voice/audio stream toward phone | ✅/🟡 | Physical capture proves `0x73` start/stop plus fixed 40-byte family `0x59` Opus at 16 kHz mono. Native system-Opus decoding, bounded buffering and Apple Speech ingestion are implemented; physical-iPhone validation remains. |
| Firmware LED suppression / hidden firmware modification | 🔬 | Separate experimental firmware track; not required for normal app integration. |

---

## 8. Audio / Assistant implication

The physical-glasses capture now establishes the glasses-oriented voice path:

```text
on-glasses wake word or rear assistant button
→ family 0x73 subtype 0x03 start event
→ family 0x59 fixed 40-byte Opus packets
→ 16 kHz / mono decoder
→ 16-bit / 16 kHz / mono PCM
→ speech/Assistant abstraction
→ family 0x73 subtype 0x0A stop event
```

The app must not listen continuously on the iPhone microphone for “Hey Cyan”; wake detection is a
glasses firmware setting (family `0x44`). The provider must expose decoded PCM/audio through a
glasses-neutral capability so Assistant does not depend on HeyCyan-specific packet types.

---

## 9. Lens architecture implication

The official production app changes the Lens decision materially.

We now have two valid architecture tiers:

### Tier A — capture-based Lens

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

This remains the simplest first implementation.

### Tier B — live Lens

Official production evidence supports:

```text
BLE live AP payload
    ↓
glasses AP
    ↓
iPhone joins AP
    ↓
RTSP :8554/ch0
    ↓
decode frames
    ↓
Lens preview / sampled-frame analysis
```

For native iOS, Tier B should be treated as **supported hardware behavior awaiting iOS physical verification**, not as firmware-only speculation.

Do not make the entire Lens product dependent on continuous streaming until AP association, RTSP codec compatibility, cleanup, thermal/battery behavior, and reconnection are tested on the physical glasses.

---

## 10. Important unresolved questions / contradictions

### Historical livestream contradiction

Earlier reverse-engineering commentary reported a dormant/test livestream implementation on the Wi-Fi SoC but no exposed Bluetooth command through the then-known HeyCyan SDK surface. The public QCSDK wrapper inspected also lacks a named livestream mode.

The newer official HeyCyan Android production app `1.0.142_20260807` is stronger evidence and contains an active `RealTimePreviewActivity`, verified BLE live-control payloads, AP/P2P networking, and RTSP playback.

Resolution:

- preserve the earlier finding as valid historical/reverse-engineering context;
- current production architecture treats live preview as supported by the official app protocol;
- the low-level command is exposed through `LargeDataHandler.glassesControl`, not necessarily through the public QCSDK convenience wrapper.

### Wi-Fi password behavior

Official Android production `1.0.142` stores fixed password `123456789` during Bluetooth connection setup. Upstream iOS QCSDK demos are inconsistent about whether credentials are returned or overridden.

Resolution for now:

- Android production path: fixed password is strongly established;
- iOS implementation: verify against physical glasses/QCSDK behavior before hardcoding globally.

### AP versus P2P selection

For official Android real-time preview, inspected code chooses AP on HarmonyOS NEXT and P2P otherwise. Other operations may use different rules.

The native iOS architecture should target the proven AP variants first rather than trying to recreate Android `WifiP2pManager`.

### Error 255

The official `GlassModelControlResponse` parser contains explicit handling for error value `255`, confirming it belongs to the device control/state protocol. The exact triggering condition and recovery sequence still need mapping.

### Audio path

Transport, lifecycle, packet size and decoder format are attributed by the physical capture. The
native Opus decoder, bounded buffering and Apple Speech input seam are now implemented. Remaining
work is end-to-end validation on a physical iPhone and promotion of the provider capability only
after that succeeds.

---

## 11. Implementation milestones

Proceed in this order:

1. Implement both official GATT service families and notification setup.
2. Reconstruct/verify application framing + CRC against official production code.
3. Implement protocol initialization/readiness state.
4. Implement and verify battery + device info.
5. Map and verify photo command and response.
6. Map normal media-transfer preparation and HTTP paths.
7. Implement iOS AP join and readiness checks.
8. Implement HTTP media listing/download.
9. Implement the verified live-preview AP control flow.
10. Connect to `rtsp://<ip>:8554/ch0` and verify codec/playback on iOS hardware.
11. Implement photo/video/audio product flows on top of verified transports.
12. Fully audit glasses voice/audio streaming and feed decoded audio into the platform-neutral Assistant path.
13. Add reconnect/background/error-state behavior.
14. Keep firmware patches/LED suppression as a separate experimental track.

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

# AGENTS.md — Android / HeyCyan implementation contract

## Scope

- Active Android app: `android/AD-Glasses/`
- UI: Kotlin + Jetpack Compose
- Product/UI reference: native iOS app on `main`, except the active-conversation transcript is intentionally redesigned on Android.
- Primary glasses family: HeyCyan / AM01-class hardware evidenced in `docs/heycyan/`.
- Retained vendor artifact: `android/glasses_sdk_20250723_v01.aar`.

The old Android application architecture and its old IPFS-style media assumptions are retired. Do not reintroduce them without fresh hardware evidence.

## Source-of-truth order

When claims conflict, prefer:

1. physical hardware captures and official production-app behavior;
2. official/vendor SDK interfaces and binaries;
3. vendor demo code;
4. documented reverse-engineering findings;
5. helper libraries/repository documentation;
6. our own assumptions.

Never promote an assumption into protocol truth.

## Verified GATT transports

Base channel:

```text
service 6e40fff0-b5a3-f393-e0a9-e50e24dcca9e
write   6e400002-b5a3-f393-e0a9-e50e24dcca9e
notify  6e400003-b5a3-f393-e0a9-e50e24dcca9e
```

Serial/large-data channel:

```text
service de5bf728-d711-4e47-af26-65e3012a5dc7
write   de5bf72a-d711-4e47-af26-65e3012a5dc7
notify  de5bf729-d711-4e47-af26-65e3012a5dc7
```

The app must discover and subscribe to both verified notification paths before reporting the transport ready.

## Application frame

Verified production frame:

```text
BC CMD LEN_LO LEN_HI CRC_LO CRC_HI PAYLOAD...
```

- length is payload length only, unsigned little-endian;
- CRC is CRC-16/MODBUS over payload bytes only, little-endian on wire;
- null/empty payload uses `FF FF` in CRC positions;
- notification boundaries are not application-frame boundaries;
- the stream decoder must recover at the next CRC-valid `BC` marker after malformed data.

## Confirmed command families

```text
0x40 time synchronization
0x41 glasses-control family
0x42 battery
0x43 device information
0x44 AI voice-wake setting
0x49 Classic Bluetooth audio/control request
0x51 glasses music/call/system volume
0x59 glasses microphone Opus packet
0x73 unsolicited device notification
0xFD thumbnail transfer
0xFC Wi-Fi-side/IP operation
```

Confirmed user-facing `0x41` payloads used by the Android reboot:

```text
02 01 01       photo
02 01 02       video start
02 01 03       video stop
02 01 08       glasses-local audio recording start
02 01 0C       glasses-local audio recording stop
02 01 06 Q Q   AI photo quality Q (0..5)
02 01 04 01    prepare media using P2P
02 01 04 02    prepare media using AP
02 01 09       media-transfer finish/cleanup
```

Other observed payloads are not automatically executable merely because a production call site exists.

## Destructive/firmware boundary

Do not expose or automatically send:

- factory reset;
- restart/forced restart;
- OTA/DFU/bootloader commands;
- firmware LED suppression/modification;
- undocumented ANC/noise controls;
- guessed wake-phrase commands.

Evidence of a command code is not proof that recovery, rollback, failure handling, or model applicability is understood.

## Ready-session sequence

A BLE link is not a ready product session. After GATT + notifications:

1. synchronize the captured time/language/time-zone record;
2. refresh battery/charging state;
3. refresh device/firmware information;
4. refresh glasses volume state where available;
5. send the captured Classic Bluetooth audio/control connection request;
6. only then expose product controls as ready.

Disconnect and Forget remain different actions. Unexpected loss uses bounded reconnect backoff.

## Assistant audio

Verified glasses voice path:

```text
0x73 / 03 01 -> Assistant listening begins
0x59          -> complete fixed 40-byte Opus packets
0x73 / 0A 01 -> Assistant listening ends
```

Codec evidence:

```text
Opus, 16 kHz, mono, 40-byte packet payloads -> 16-bit mono PCM
```

Do not label activation as voice-wake versus rear-button: the captured start event does not contain that source field.

Do not add denoise/EQ/gain by default. Measure recognition quality first.

## Media and Wi-Fi

BLE prepares and coordinates the network session. Wi-Fi is the high-bandwidth data plane.

For a transfer session:

1. issue a verified work-type `04` prepare request;
2. correlate only the matching work-type `04` response;
3. strictly parse mode + little-endian SSID/passphrase lengths + strings;
4. obtain the device IP only from the related asynchronous `0x73/0x08` event;
5. join/bind the selected Android network;
6. keep BLE alive during local HTTP work;
7. perform local cleanup and the verified finish command when BLE remains ready.

Production media surfaces include:

```text
http://<glasses-ip>/files/media.config
http://<glasses-ip>/files/<name>
http://<glasses-ip>/files/log/<name>
http://<glasses-ip>:80/storage/sd0/C/DCIM/1/<name>
```

Do not restore the retired `/api/get_media_list` + IPFS contract as HeyCyan production truth.

Android production evidence contains both AP and `WifiP2pManager` flows. Keep them as separate network strategies. P2P must be promoted only after its discovery, connection-info, route binding, timeout and cleanup sequence is validated on the connected Samsung.

## Live preview

Production evidence establishes:

```text
02 01 14 01  live preview via P2P
02 01 14 02  live preview via AP
02 01 15 01  live-preview cleanup
rtsp://<glasses-ip>:8554/ch0
```

The control and network sequence is supported evidence; player/codec behavior still needs target-device validation before becoming a guaranteed product capability.

## Android platform strategy

This is a private/sideloaded app, so Play Store policy is not a design constraint. Android OS security still is. Respect runtime permissions, roles, foreground-service types, background-start restrictions, notification access and user-visible system controls.

Use a connected-device foreground service for persistent BLE/accessory work. Add companion-device presence integration where it improves system-approved background relaunch behavior. Microphone, call and SMS permissions remain capability-tied rather than requested merely for convenience.

## Translation and speech

Keep provider interfaces replaceable.

- baseline offline translation: ML Kit on-device models;
- higher-fidelity/cloud translation can be added as another provider;
- a real hands-free translator must implement listen -> segment -> translate -> speak -> resume and prevent TTS from being re-transcribed;
- baseline TTS: best installed offline Android voice;
- target high-quality offline provider: Sherpa-ONNX with a phone-profiled Kokoro model;
- do not make F5-TTS or another model the default until Android inference, latency, memory and thermals are measured on the target device.

## Notifications / call / text

Notification access uses `NotificationListenerService` and requires explicit user enablement in system settings. Assistant actions over notifications, calling and SMS should be exposed through capability adapters, not UI-specific Android APIs scattered through Compose.

When a call or SMS permission is unavailable, use the system dialer/composer fallback rather than silently failing.

## Build expectations

```bash
cd android/AD-Glasses
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

JDK 17 is required. Keep protocol regression tests around captured byte vectors and parser/state behavior.

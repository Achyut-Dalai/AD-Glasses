# Official HeyCyan app findings

This document records evidence extracted from the user-supplied official HeyCyan Android package. It is intentionally separate from `RESEARCH_LOG.md` so earlier QCSDK, CyanBridge, `heycyan-core`, and reverse-engineering findings are preserved rather than overwritten.

When official-app behavior conflicts with an earlier source, record both claims and the resolution status. Do not silently replace historical findings.

## Artifact under audit

User-supplied XAPK:

```text
HeyCyan_1.0.142_20260807_apkcombo.com.xapk
```

Package metadata from the XAPK manifest:

```text
App name: HeyCyan
Android package: com.glasssutdio.wear
Version name: 1.0.142_20260807
Version code: 142
Minimum SDK: 26
Target SDK: 36
```

Artifact SHA-256:

```text
XAPK: d93363ee94c54b853a915813d4596e08ae29eec70517043413d2a19bea584197
Base APK: 69912f1898e9301a1830224a00f7175b1fca1aef290bf636de2182b1be2cef39
arm64 split: 7655f52f8c94d349704164e00a8af6ee89e3725d2e08332b64e1052118be0699
English split: f5830167e75fa9c957d9d6a9a79875e320091b035dbb2a55fe568816d8acf0aa
xxhdpi split: fa53c5055804a53c0dad5db34ba2e3aba42d65213607deecf9d19fd37ef199c3
```

XAPK contents:

```text
com.glasssutdio.wear.apk      base application
config.arm64_v8a.apk          native arm64 libraries
config.en.apk                 English resources
config.xxhdpi.apk             display resources
```

The base APK contains four DEX files. The arm64 split contains Microsoft Speech, Agora, VLC, Opus/Speex, image-processing, TensorFlow Lite, OpenCV, and other native libraries. Library presence alone is not treated as proof that a specific glasses feature uses that library.

---

## 2026-08-29 — Official production static findings

### Finding: the production APK contains inspectable Oudmon BLE protocol code

**Status: PROVEN**

The application DEX contains the `com.oudmon.ble` implementation itself, including classes such as:

```text
com.oudmon.ble.base.bluetooth.BleBaseControl
com.oudmon.ble.base.bluetooth.BleOperateManager
com.oudmon.ble.base.communication.LargeDataHandler
com.oudmon.ble.base.communication.CommandHandle
com.oudmon.ble.base.communication.bigData.bean.GlassModelControl
com.oudmon.ble.base.communication.bigData.resp.GlassModelControlResponse
com.oudmon.ble.base.communication.bigData.resp.BatteryResponse
com.oudmon.ble.base.communication.bigData.resp.DeviceInfoResponse
```

This is a major improvement over relying only on public wrapper APIs: the official APK exposes enough implementation to reconstruct significant parts of the raw GATT protocol directly from DEX bytecode.

### Finding: official GATT UUID constants

**Status: PROVEN from production DEX**

`com.oudmon.ble.base.communication.Constants` initializes the ordinary command-channel UUIDs as:

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

The same constants class defines a second serial-port-style UUID family:

```text
SERIAL_PORT_SERVICE
 de5bf728-d711-4e47-af26-65e3012a5dc7

SERIAL_PORT_CHARACTER_NOTIFY
 de5bf729-d711-4e47-af26-65e3012a5dc7

SERIAL_PORT_CHARACTER_WRITE
 de5bf72a-d711-4e47-af26-65e3012a5dc7
```

The app also includes the standard Device Information service and firmware/hardware/software revision characteristics.

### Finding: official app uses two distinct GATT transport families

**Status: PROVEN from production DEX**

The production code shows both channels in active protocol paths:

```text
BleOperateManager.enableUUID()
→ Constants.UUID_SERVICE
→ Constants.UUID_READ
→ EnableNotifyRequest
```

while:

```text
LargeDataHandler.getWriteRequest(payload)
→ LargeDataHandler.SERIAL_PORT_SERVICE
→ LargeDataHandler.SERIAL_PORT_CHARACTER_WRITE
→ WriteRequest
```

`LargeDataHandler` initializes those serial-port fields to the `de5bf728/de5bf729/de5bf72a` UUID family.

Current architectural interpretation:

```text
6e40fff0 / 6e400002 / 6e400003
    ordinary Oudmon BLE command/notification transport

                plus

de5bf728 / de5bf72a / de5bf729
    LargeDataHandler / serial-style large-data transport
```

This means the native iOS implementation should not be designed around one write characteristic and one notify characteristic for every feature. Service discovery should recognize both verified transport families, and the protocol layer should route a command through the transport required by that command family.

The exact division of every command between the two transports is still being mapped.

### Finding: connection initialization is command-driven, not just a GATT link

**Status: PROVEN**

The official application performs initialization after BLE connection. Relevant observed call paths include time/device/settings synchronization before normal use.

This confirms that AD Glasses should not treat `CBCentralManager.didConnect` as a fully ready device state. A protocol initialization/readiness phase belongs above the raw BLE connection.

### Finding: several protocol command families are directly visible

**Status: PROVEN, complete packet details still under reconstruction**

The production `LargeDataHandler` builds framed requests using `addHeader(command, payload)`.

Observed command-family values include:

```text
0x41 / 65   glassesControl(...)
0x42 / 66   battery synchronization
0x43 / 67   device-info synchronization
0xFC / -4   write IP information to the glasses Wi-Fi SoC
```

`addHeader(...)` performs application framing and CRC16-related work before the bytes reach the BLE write queue. Exact full-frame byte layout and CRC coverage are still being reconstructed and should not yet be copied into Swift.

### Finding: battery and device-info are first-class protocol responses

**Status: PROVEN**

The production protocol contains explicit response decoders for:

```text
battery percentage
charging state
Bluetooth firmware version
Bluetooth hardware version
Wi-Fi firmware version
Wi-Fi hardware version
```

The app has a battery callback/synchronization path through `LargeDataHandler.syncBattery()`.

This means the AD Glasses device hero can eventually show real battery/charging data rather than a guessed placeholder once this protocol is implemented natively.

### Finding: the glasses-control response contains Wi-Fi/P2P state and media/device state

**Status: PROVEN**

`GlassModelControlResponse` contains parsers/getters for fields including:

```text
glass work type
current work type
image count
video count
record/audio count
P2P IP address
error code
OTA status
video angle/duration
photo resolution
AI-photo resolution
video resolution lists
AOV-related settings
```

The decoder explicitly contains handling for numeric error code `255`.

This is strong evidence that the previously reported `error 255` belongs to the glasses-control protocol/state machine rather than merely being an Android networking exception.

The exact condition that produces `255` is still under analysis.

### Finding: both normal Wi-Fi/AP and Android Wi-Fi Direct code exist in the official production app

**Status: PROVEN**

The official APK contains both:

```text
com.glasssutdio.wear.wifi.ap.TempWifiHelper
com.glasssutdio.wear.wifi.p2p.WifiP2pManagerSingleton
```

and uses Android `WifiP2pManager` APIs. This confirms that AP and P2P are both real production concepts, not only CyanBridge inventions.

### Finding: official Android connection setup stores a fixed glasses Wi-Fi password

**Status: PROVEN for HeyCyan Android 1.0.142**

`MyBluetoothReceiver.connectStatue(...)` derives/stores the glasses Wi-Fi name from the connected device name and normalized Bluetooth address and stores:

```text
123456789
```

as the glasses Wi-Fi password.

This resolves the password question for the inspected official Android path.

The upstream iOS QCSDK demos remain inconsistent about returned-versus-overridden credentials, so native iOS must still verify physical-glasses behavior before applying this Android rule globally.

### Finding: official app has an explicit local glasses network layer

**Status: PROVEN**

The production app contains `GlassesNetworkManager`, whose API includes:

```text
isConnectedToGlasses(...)
waitForConnection(...)
testConnection(...)
sendGetRequest(...)
sendHttpRequest(...)
uploadImage(...)
getWifiLocalAddress()
```

This reinforces the multi-stage architecture: BLE commands prepare/coordinate the device and Wi-Fi state, while normal IP requests are used once a glasses network is available.

### Finding: production media endpoint call sites are attributed

**Status: PROVEN for inspected media paths**

`PictureFragment` initializes media/config filenames including:

```text
media.config
vf_list.txt
log.list
```

Production code constructs paths including:

```text
http://<glasses-ip>/files/<name>
http://<glasses-ip>/files/log/<name>
http://<glasses-ip>:80/storage/sd0/C/DCIM/1/<name>
```

`AlbumDepository.readPhotoFile(...)` retrieves the stored glasses IP and constructs `/files/` URLs, with `/files/log/` used for the log path.

A separate string:

```text
http://192.168.0.1:8080/test
```

belongs to `GlassesNetworkTestActivity` and should not be mistaken for the normal media endpoint.

### Finding: official production app actively supports real-time HeyCyan preview

**Status: PROVEN**

The official XAPK contains:

```text
com.glasssutdio.wear.home.activity.RealTimePreviewActivity
```

This activity owns BLE live-control requests, AP/P2P setup, heartbeat/session behavior and VLC playback.

Its player constructs the production stream URL:

```text
rtsp://<glassDeviceWifiIP>:8554/ch0
```

This is stronger evidence than the older public-QCSDK/reverse-engineering conclusion that livestream was only dormant/test firmware functionality.

### Finding: live-preview Bluetooth activation payloads are visible

**Status: PROVEN as `glassesControl` payloads; complete outer frame still under reconstruction**

The official app passes these byte arrays into `LargeDataHandler.glassesControl(...)`:

```text
02 01 14 01   P2P real-time-preview start path
02 01 14 02   AP real-time-preview start path
02 01 15 01   cleanup/exit payload sent when preview is destroyed
```

These are payloads within the `0x41` glasses-control family, not complete BLE frames. The outer framing, length and CRC remain separate protocol work.

### Finding: official Android live-preview selection uses P2P normally and AP on HarmonyOS NEXT

**Status: PROVEN for the inspected `RealTimePreviewActivity` flow**

The activity's permission/launch branch checks `isHarmonyOSNEXT`:

```text
HarmonyOS NEXT
→ AP path
→ glassesControl(02 01 14 02)
→ connect with TempWifiHelper using stored Wi-Fi name/password

other inspected Android path
→ register P2P receiver
→ start P2P discovery
→ glassesControl(02 01 14 01)
```

This does not prove every HeyCyan operation selects AP/P2P the same way, but it proves the glasses expose an AP live-preview variant in the official app.

That is highly relevant to native iOS because AP mode is a plausible Apple-compatible path to the RTSP stream.

### Finding: official app has a glasses-oriented voice/audio pipeline

**Status: STRONG; transport semantics still under audit**

The production app includes `GlassesAzureSpeechRecognizer`, Microsoft Speech libraries and Opus/Speex native components. The embedded Oudmon protocol layer also exposes operations including:

```text
LargeDataHandler.realAudioToText(...)
LargeDataHandler.aiVoiceWake(...)
LargeDataHandler.aiVoicePlay(...)
LargeDataHandler.syncHeartBeat(...)
```

This substantially raises confidence that HeyCyan supports an application-level glasses voice/audio path beyond simply recording an audio file on the glasses.

We still need to trace:

```text
incoming transport
packet/subcommand type
codec framing
sample rate/channels
stream lifecycle
heartbeat/state requirements
```

before defining the native iOS audio transport.

### Finding: official Oudmon command surface is broader than the current AD Glasses provider

**Status: PROVEN from method surface; hardware/model applicability still requires verification**

The embedded `LargeDataHandler` exposes operations including:

```text
syncBattery
syncDeviceInfo
syncTime
glassesControl
getPictureThumbnails
wearCheck
wearFunctionSupport
get/set volume control
AI shortcut / voice wake / voice playback
heartbeat
classic Bluetooth synchronization
writeIpToSoc
```

The official model-control response also exposes configurable photo/video/audio properties and a P2P IP field.

This tells us the current native iOS provider's Bluetooth-only foundation is intentionally incomplete; the hardware ecosystem exposes substantially more functionality once the verified protocol transport is implemented.

Do not assume every embedded SDK method applies to every HeyCyan hardware revision without model/firmware verification.

---

## Cross-source contradiction: livestream

Earlier evidence said:

- the public QCSDK Swift wrapper has no named livestream operation mode;
- reverse-engineering commentary described livestream as present on the Wi-Fi processor but inaccessible through the then-known exposed Bluetooth SDK surface.

The official HeyCyan Android `1.0.142_20260807` production app now proves an active lower-level path through `LargeDataHandler.glassesControl(...)`, AP/P2P networking and RTSP playback.

Current resolution:

```text
public convenience SDK surface: no named livestream mode found
production lower-level Oudmon protocol: live preview is supported
production stream: RTSP :8554/ch0
```

The historical finding remains preserved in `RESEARCH_LOG.md` because it may describe an older SDK/app/firmware state and explains why CyanBridge's earlier reverse-engineering work reached a different conclusion.

---

## Preservation / conflict rules

The existence of this file does **not** invalidate earlier findings.

Use these rules as the audit continues:

1. Keep `RESEARCH_LOG.md` as chronological history.
2. Keep this file focused on official-app evidence.
3. Keep `ARCHITECTURE.md` as the current implementation model, not a raw notebook.
4. If official production behavior contradicts QCSDK demo or CyanBridge behavior, record both before resolving the conflict.
5. Model- or firmware-specific behavior must remain explicitly scoped.
6. Do not copy raw packet bytes into the native iOS implementation until framing, direction, response matching, retries, and hardware behavior are verified.

---

## Next official-app audit targets

```text
1. Reconstruct addHeader() framing byte-for-byte and CRC16 coverage.
2. Map remaining GlassModelControl payload subcommands to photo/video/audio/transfer/reset operations.
3. Complete the routing map between the 6e40 base channel and de5bf large-data channel.
4. Trace error 255 to exact initiating state/timeout and recovery command.
5. Determine media.config/vf_list/log.list formats and download semantics.
6. Trace incoming glasses audio packets through GlassesAzureSpeechRecognizer and the Opus decoder.
7. Identify whether AI-photo image data uses BLE large-data packets or Wi-Fi/media transfer.
8. Determine RTSP codec/profile/frame size/fps and AP-mode readiness timing.
9. Compare every production finding with QCSDK.framework and CyanBridge.
10. Verify the resulting protocol incrementally on a physical pair before shipping it in Swift.
```

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

## 2026-08-28 — Initial official-app static findings

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

`com.oudmon.ble.base.communication.Constants` initializes these UUIDs:

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

The same constants class also defines a serial-port-style service family:

```text
Service: de5bf728-d711-4e47-af26-65e3012a5dc7
Notify:  de5bf729-d711-4e47-af26-65e3012a5dc7
Write:   de5bf72a-d711-4e47-af26-65e3012a5dc7
```

Do not yet assume the serial-port family is used for the same HeyCyan command channel; usage is still being traced.

### Finding: official app enables notifications on the primary service/read UUID pair

**Status: PROVEN**

`BleOperateManager.enableUUID()` constructs an `EnableNotifyRequest` using `Constants.UUID_SERVICE` and `Constants.UUID_READ` and enables it. This establishes the main notification path used by the Oudmon protocol layer.

### Finding: connection initialization is command-driven, not just a GATT link

**Status: PROVEN**

The official application performs initialization after BLE connection. Relevant observed call paths include:

```text
MyBluetoothReceiver.initCmd()
→ BleOperateManager.classicBluetoothStartScan()
→ DeviceCmdInit.initDeviceSetting()
```

and `DeviceCmdInit.init()` performs:

```text
FileHandle.clearCallback()
→ LargeDataHandler.syncTime(...)
→ LargeDataHandler.syncDeviceInfo(...)
→ syncDeviceSetting()
```

This confirms that AD Glasses should not treat `CBCentralManager.didConnect` as a fully ready device state. A protocol initialization/readiness phase belongs above the raw BLE connection.

### Finding: several protocol command families are directly visible

**Status: PROVEN, packet details still under reconstruction**

The production `LargeDataHandler` builds framed requests using `addHeader(command, payload)`.

Observed command-family values include:

```text
0x41 / 65   glassesControl(...)
0x42 / 66   battery synchronization
0x43 / 67   device-info synchronization
0xFC / -4   write IP information to the glasses Wi-Fi SoC
```

`addHeader(...)` builds the application frame and calls a CRC16 implementation before enqueueing bytes to the BLE write queue. Exact byte positions, length endianness, and CRC coverage are still being reconstructed and should not yet be copied into Swift.

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

The app's `YourGlassActivity.batteryValue()` registers a battery callback and calls `LargeDataHandler.syncBattery()`.

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

The model/operation-specific rule selecting AP versus P2P is not yet established.

### Finding: production APK contains local media/network constants that need attribution

**Status: PRESENT IN ARTIFACT / usage still being traced**

Strings present in the production DEX include:

```text
123456789
media.config
/files/
/files/log/
/playlist.json
http://192.168.0.1:8080/test
```

These are important leads, but presence in the string table is not sufficient to claim each value is used by the normal media-sync path. Call sites are being traced before promoting any of these to architecture truth.

### Finding: official app has a glasses-oriented Opus speech pipeline

**Status: STRONG; transport semantics still under audit**

The production app includes `GlassesAzureSpeechRecognizer`. Its `start()` path:

```text
starts an Opus path
initializes/restarts audio playback
creates an Opus decoder
starts stream decoding
registers LargeDataHandler package notifications
starts a heartbeat timer
```

The Oudmon protocol layer also exposes `LargeDataHandler.realAudioToText(...)`, which frames text/audio-related protocol messages.

This substantially raises confidence that HeyCyan supports an application-level glasses voice/audio path beyond simply recording an audio file on the glasses. We still need to trace the incoming packet type, codec framing, sample rate, and whether the microphone audio arrives over BLE, classic Bluetooth/SPP, or another channel before defining the native iOS audio transport.

### Finding: no official-app livestream conclusion yet

**Status: OPEN**

The initial static pass has not yet established an exposed production HeyCyan livestream command. The earlier QCSDK/reverse-engineering finding therefore remains unchanged: do not make Lens depend on continuous live video until the official application or firmware evidence proves a supported activation path.

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
2. Map GlassModelControl payload subcommands to photo/video/audio/transfer/reset operations.
3. Trace AP-versus-P2P selection and the exact transfer command.
4. Attribute 123456789, media.config, /files/, playlist.json, and local IP/ports to call sites.
5. Determine the exact condition/error path for error 255.
6. Trace incoming glasses audio packets through GlassesAzureSpeechRecognizer and the Opus decoder.
7. Identify whether AI-photo image data uses BLE large-data packets or Wi-Fi/media transfer.
8. Search production code for dormant livestream/RTSP activation paths.
9. Compare every production finding with QCSDK.framework and CyanBridge.
10. Verify the resulting protocol incrementally on a physical pair before shipping it in Swift.
```

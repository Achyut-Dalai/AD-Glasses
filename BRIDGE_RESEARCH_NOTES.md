# BRIDGE_RESEARCH_NOTES.md — XGIMI/MemoMind Glasses Protocol Investigation

## 1. App Identity & Overview

- **Package**: `com.memomind.ai.aphrodite` (MemoMind app, branded for XGIMI glasses)
- **Version**: 1.3.50 (versionCode 80)
- **Platform**: Flutter app — all BLE protocol, display, and audio logic is in **Dart compiled into `libapp.so`** (21 MB, ARM64 ELF)
- **Java layer**: Thin Flutter plugin bridges only (no protocol logic)
- **Decompiled sources**: `android/XGIMIGlassesApp/decompiled/` (17,787 Java files via jadx)
- **Flutter engine ID**: `xgimi_glasses_flutter_engine`
- **SDK**: minSdk=24, targetSdk=36

### Java Layer Plugins (thin bridges)
| Plugin | Package | Purpose |
|--------|---------|---------|
| `flutter_blue_plus` | `com.lib.flutter_blue_plus` | BLE LE GATT bridge |
| `flutter_blue_classic` | `dev.lenhart.flutter_blue_classic` | Classic Bluetooth RFCOMM/SPP |
| `flutter_pcm_player` | `com.example.flutter_pcm_player` | PCM audio playback via AudioTrack |
| `record` | `com.llfbandit.record` | Audio recording with BT SCO support |
| `NavigationPlugin` | `com.memomind` | Channel: `com.xgimi.glasses.navigation/method` |
| `AlipayPlugin` | `com.memomind` | Channel: `com.memomind.ai.aphrodite/alipay` |

---

## 2. BLE Protocol Architecture

### Protocol Stack
```
┌─────────────────────────────────────────────────────────┐
│  Application Layer (Dart)                                │
│  9 Command Types + Billboard/Display + Recording + OTA   │
├─────────────────────────────────────────────────────────┤
│  MQTT-like Serialization Layer                           │
│  Header(1B msgType + varint length) + Variable Hdr + Payload │
│  BluePackageSerializer (fragmentation/reassembly)        │
├─────────────────────────────────────────────────────────┤
│  Transport Adaptation Layer                              │
│  ┌─────────────┬──────────────┬──────────────┬─────────┐ │
│  │ BLE Command  │ BLE Record   │ BLE OTA      │ SPP     │ │
│  │ (0x2001/02)  │ (0x20xx)     │ (0x20xx)     │(RFCOMM) │ │
│  ├─────────────┼──────────────┼──────────────┼─────────┤ │
│  │ Cosonic/WQ  │ Cosonic/WQ   │ WQ OTA       │ Cosonic │ │
│  │ Protocol     │ Protocol V2   │ Protocol     │         │ │
│  └─────────────┴──────────────┴──────────────┴─────────┘ │
├─────────────────────────────────────────────────────────┤
│  IAP2 (alternative transport wrapper for iOS)            │
│  ql.iap2.protocol[00-03]                                 │
├─────────────────────────────────────────────────────────┤
│  Hardware: BLE GATT + RFCOMM SPP                         │
└─────────────────────────────────────────────────────────┘
```

### BLE Service/Characteristic UUID Map

| UUID (16-bit) | Full UUID | Function |
|---|---|---|
| **0x2001** | `00002001-0000-1000-8000-00805F9B34FB` | **Command Write** characteristic |
| **0x2002** | `00002002-0000-1000-8000-00805F9B34FB` | **Command Notify** characteristic |
| **0x2020** | `00002020-0000-1000-8000-00805f9b34fb` | Record data (WQ protocol) |
| **0x2024** | `00002024-0000-1000-8000-00805F9B34FB` | Record notify |
| **0x2025** | `00002025-0000-1000-8000-00805f9b34fb` | Record write |
| **0x2026** | `00002026-0000-1000-8000-00805F9B34FB` | Record (additional) |
| **0x7033** | `00007033-0000-1000-8000-00805F9B34FB` | OTA/upgrade characteristic |
| **0x1101** | `00001101-0000-1000-8000-00805F9B34FB` | SPP RFCOMM (Serial Port Profile) |
| **0x2902** | (standard CCCD) | Client Characteristic Configuration Descriptor |

### Dart Service/Characteristic Configuration Constants
| Dart constant | Meaning |
|---|---|
| `_command_service_guid` | Primary command service (contains 0x2001/0x2002) |
| `_write_client_characteristics_config_guid` | CCC descriptor for write characteristic |
| `_notify_client_characteristics_config_guid` | CCC descriptor for notify characteristic |
| `_record_ble_service_guid` | Recording BLE service |
| `_record_ble_write_client_characteristics_config_guid` | CCC descriptor for record write |
| `_record_ble_notify_client_characteristics_config_guid` | CCC descriptor for record notify |
| `_record_spp_guid` | Recording transport over SPP |
| `ota_ble_service_guid` | OTA BLE service |
| `ota_ble_write_client_characteristics_config_guid` | CCC descriptor for OTA write |
| `ota_ble_notify_client_characteristics_config_guid` | CCC descriptor for OTA notify |
| `_command_uuid_spp` | Commands via SPP fallback |

### BLE Connection Flow
```
startScan (SppBleHybridScanner — BLE + SPP simultaneously)
  → device found
  → connectDevice
  → onConnectionStateChange(connected)
  → discoverServices
  → locateCharacteristic(uuid 0x2001, 0x2002)
  → setNotify for 0x2002 (enable notifications)
  → writeDescriptor(CCCD 0x2902, 0x0001)
  → optional SPP fallback if BLE fails
```

### SPP (Classic Bluetooth) Connection
- UUID: `00001101-0000-1000-8000-00805F9B34FB` (standard SPP)
- Used as fallback when BLE unavailable
- RFCOMM socket via `createRfcommSocketToServiceRecord`
- Read buffer: 1024 bytes
- ACL sequencing: waits for HFP ready before SPP

### Reconnect Strategy
- `AndroidReconnectStrategy` — platform-specific
- Business-level retries: max 3 attempts
- Auto-reconnect with exponential backoff
- `_attemptSppReconnect` — SPP fallback reconnection

---

## 3. Packet Format & Serialization

### BluePackageSerializer
- `splitToPackets()` — fragments large messages into MTU-sized chunks
- `_buildPartialPacket()` — wraps each chunk with header + sequencing
- Default MTU: 23 bytes (ATT default), effective payload: 20 bytes
- After MTU negotiation: up to 512 bytes (with `requestMtu`)

### Packet Structure
```
┌──────────┬──────────────┬─────────────────┐
│ Header   │ Length       │ Payload         │
│ (1 byte) │ (varint)     │ (variable)      │
└──────────┴──────────────┴─────────────────┘
```

### ACK Tracking (`ack_bluetooth.dart`)
- Each transmission gets an `eventId` for correlation
- Pure ACK packets: `ACK-only packet, eventId=[id]`
- Write flow tracked via `mWriteChr` map (key: `address:serviceUuid:chrUuid:primaryUuid`)
- Errors: `sendAckTracked: error svc=[x]`

### Write Flow (App → Glasses)
```
Dart Command object
  → BluePackageSerializer.splitToPackets()
  → _buildPartialPacket() [header + length]
  → sendAckTracked() [eventId tracking]
  → writeCharacteristic(UUID 0x2001, data)
  → FlutterBluePlusPlugin.java → BluetoothGatt.writeCharacteristic()
  → onCharacteristicWrite callback → OnCharacteristicWritten event → Dart
```

### Response Flow (Glasses → App)
```
Glasses notification on UUID 0x2002
  → onCharacteristicChanged callback
  → OnCharacteristicReceived event → Dart
  → BluePackage deserialization
```

---

## 4. Command Types (9 total)

| # | Command | Dart Class | Purpose |
|---|---------|-----------|---------|
| 1 | adviser | `AdviserCommand` | AI adviser/assistant interaction |
| 2 | ai_chat | `AIChatCommand` | AI chat (MemoMind AI) |
| 3 | billboard | `BillboardCommand` | Display/layout/dashboard content |
| 4 | device_service | `DeviceServiceCommand` | Device management (brightness, volume, info) |
| 5 | notification | `NotificationCommand` | Phone notification relay to glasses |
| 6 | recorder | `RecorderCommand` | Audio recording start/stop/pause/resume |
| 7 | teleprompter | `TeleprompterCommand` | Teleprompter display |
| 8 | translate | `TranslateCommand` | Translation display |
| 9 | wizard | `WizardCommand` | Setup wizard |

### Command Serialization
- Commands are serialized to binary with MQTT-like header
- Each command has an incrementing `commandId`
- Duplicate commands are detected and removed: `Removing duplicate command: commandId = <id>`
- Data flow: `MapTrack -> ready to send data, commandId: <id>`

---

## 5. Protocol Variants: Cosonic vs WQ

| Aspect | Cosonic | WQ |
|--------|---------|-----|
| UUIDs | 0x2001/0x2002 (command) | 0x2020/0x2024/0x2025/0x2026 (record), 0x7033 (OTA) |
| Receiver | `cosonic_receiver_bluetooth.dart` | `wq_receiver_bluetooth.dart` |
| Parser | `CosonicBluetoothProtocolParser` | `WQBluetoothProtocolParser` |
| Packet class | `WrapperBlueProtocol` via `wrapper_blue.dart` | `WQBluetoothPackage` via `wrapper_blue_wq.dart` |
| IAP2 wrapper | `wrapper_blue.dart` | `wrapper_blue_wq.dart` |
| Magic bytes | Not found | Uses magic byte validation |
| Frame counting | Not found | Uses `frameCnt` |
| Record proto | Generic record manager | V2 parser + offline parser |
| OTA support | Not found | Dedicated WQ OTA classes |

### WQ Sub-protocols
- `WQRecordBluetoothProtocolParserV2` — live recording (magic byte + frameCnt validation)
- `WQOfflineRecordBluetoothProtocolParser` — offline recording download
- `WQOtaBluetoothProtocolParser` — OTA firmware updates

---

## 6. Display & Rendering Pipeline

### Display Architecture
```
┌──────────────────────────────────────────────┐
│  Status Area (A area)                         │
│  [Time] [Date] [Weather]                      │
├──────────────────────────────────────────────┤
│  Content Area (C area)                        │
│  [Calendar / News / Stocks / Notifications /  │
│   Media / Memory / To-Do / Teleprompter /     │
│   Translate / ...]                            │
│  (switchable by user)                         │
└──────────────────────────────────────────────┘
```

### BillboardCommand (Top-level Layout)
| Method | Purpose |
|--------|---------|
| `pushArea` | Push content to a specific area type |
| `pushAreaTimeWeather` | Combined time/weather to status area |
| `selectCAreaType` | Select component type for C-area |
| `pushSelectCAreaType` | Activate selected C-area component |
| `pushCAreaNotificationToDevice` | Push notification to content area |
| `setAreaAType` | Set status bar area type (JSON) |
| `setStatusAreaType` | Set status area type |
| `setComponentAreaType` | Set component type for area |
| `registerNextBillboardCategoryRequest` | Pagination through categories |

### Dashboard Components
| Component | Provider | Service |
|-----------|----------|---------|
| Calendar | `schedule_provider.dart` | `calendar_service.dart` |
| News | `news_provider.dart` | `news_list_service.dart` |
| Stocks | `stock_provider.dart` | `stocks_service.dart` |
| Weather | `weather_provider.dart` | `weather_service.dart` |
| Time | `time_provider.dart` | — |
| Notifications | `notify_provider.dart` | `notification_service.dart` |
| Media | `media_provider.dart` | `media_service.dart` |
| Memory | `memory_provider.dart` | `memory_list_service.dart` |
| To-Do | `todo_list_provider.dart` | `todo_list_service.dart` |

### DrawCommand (Low-level Rendering)
Canvas operations serialized as path commands:
| Operation | Usage |
|-----------|-------|
| `translate(x, y)` | Canvas origin translation |
| `save()` / `restore()` | Canvas state stack |
| `clipRect` / `clipPath` / `clipRRect` | Clipping regions |
| `addImage(imageData)` | Render pre-encoded image |
| `addText(fontData)` | Render text via glyph bitmaps |
| `addTexture(textureData)` | Render cached texture |

### Text Rendering Pipeline
```
text string
  → _splitGlyphTextIntoLines / _splitTextIntoLinesV2 (line breaking)
    → _splitTextIntoPages (page breaks for glasses display)
      → FontGlyphBitmapData (per-char bitmap extraction)
        → _sendBitmapCommands (serialize + BLE write)
```

- `FontGlyphBitmapData(boxW: <width>, ...)` — per-character bitmap
- `sendFontBitmapData`, `getSendBitmapCommands` — bitmap transmission
- `sendUnicodeBitmap`, `GlyphSet`, `UnicodeGlyphSet` — Unicode support
- `liblv_font.so` — LVGL font library on glasses
- Font OTA: `FontOtaStatus`, `inquireFontOtaIfCanUpdate`

### Image Rendering
- Monochrome for text glyphs and icons
- Grayscale supported via `_grayscaleDstInPaint`
- RGBA/RGB565 for full-color images
- `_decodeRgba4bpp` — 4 bits-per-pixel format
- Images sent via `addImage` / `addTexture` / `sendImage` DrawCommands

### Display Parameters (configurable)
- `deviceScreenHeightProvider` — screen height adjustment (-4 to +4)
- `deviceHeadAngleProvider` — head angle (0°-70°)
- Auto-brightness: `device_display_auto_brightness_on/off`
- Head detection light-up duration
- `DeviceDisplayPositionPreview` — phone preview of glasses display

### Display States
- Screen on: complete information shown
- Screen off: only icons displayed
- Look up or click button → view on glasses

---

## 7. Notification Display

### Flow
```
Android NotificationListenerService
  → intent "xgimi.notification.listener.service.intent"
  → extras: package_name, title, message, notification_id, extras_picture, contain_image, is_removed, can_reply_to_it
  → EventChannel → Dart
  → NotificationCommand → BillboardCommand → BLE write
```

### Notification Styles
- `style_icon` — icon only (screen-off mode)
- `style_single` — single line
- `style_full` — full content

### Notification Speed
- `fast` / `slow` — controls scrolling rate

### Behaviors
- Deduplication: `Notification deduplicated (recently sent)`
- Device check: `Device is not connected, ignoring notification`
- Disabled check: `Notification is disabled, ignoring`

---

## 8. Teleprompter Display

### Modes (`TeleprompterPlayMode`)
- `scroll` — auto-scroll
- `manual` — gesture-controlled
- `ai` — AI-assisted pacing

### Parameters
- `scrollSpeed` — user-adjustable
- `teleprompter_fontSize` (small/medium/large)
- `teleprompter_lineSpace` (narrow/medium/wide)

### State Management
- `TeleprompterStateData`, `TeleprompterPlayNotifier`, `TeleprompterProgressNotifier`, `TeleprompterDetailNotifier`
- `sendDevicePrompterMode` — sets scrolling mode on glasses

---

## 9. Translation Display

### Display Modes (`TranslateDisplayMode`)
- `bilingual` — source + translation
- `mobile` — phone screen
- `headAction` — glasses-specific action
- `chat` — conversation mode

### Subtitle Display
- `TranslateSubtitlePage`, `TranslateSubtitlePlayPage`
- `TranslateSubtitleConfig` — font size, display settings

---

## 10. Audio Pipeline

### Opus Codec Parameters
| Parameter | Value |
|-----------|-------|
| MIME type | `audio/opus` |
| Sample rates | 8000, 12000, 16000, **24000**, 48000 Hz |
| Channels | 1 (mono) |
| Container | OGG (via MediaMuxer, API 29+) |
| Encoding | Android MediaCodec (HW/SW) |
| Native lib | `libopus.so` (full encoder/decoder) |

### Native libopus Functions
- `opus_encoder_create`, `opus_encode`, `opus_encode_float`, `opus_encoder_ctl`
- `opus_decoder_create`, `opus_decode`, `opus_decode_float`, `opus_decoder_ctl`
- `opus_multistream_encoder_create`, `opus_multistream_decoder_create`
- `opus_packet_parse`, `opus_packet_get_nb_samples`

### Recording Pipeline
```
Glasses mic → Opus encoded → BLE (WQ Record Protocol V2)
  → RecordBlueManager → RecordOpusDecoder → PCM
  → File: /offline_record_#date#.pcm
  → Optional: PcmToM4aConverter (ffmpeg) or PcmToAacPlugin2
```

### Recording Commands
- `startRecorderCommand`, `stopRecorderCommand`, `pauseRecorderCommand`, `resumeRecorderCommand`
- `sendRecordCommand`, `sendRecorderType`, `sendErrorCodeCommand`
- Recording types: `DeviceRecorder` (glasses mic), `PhoneRecorder` (phone mic), `SpeechRecorder` (ASR)

### WQ Record Protocol
- Magic byte validation: `[wq_record_v2] !! magic byte mismatch: expected [X]`
- Frame count validation: `[wq_record_v2] !! frameCnt out of range: [N]`
- Offline: `[wq_offline_record] !! ignore package length mismatch`
- Data models: `OfflineFrameInfo`, `OfflineRecordingInfo{type: ...}`

### Audio Playback (Phone → Glasses)
- `flutter_pcm_player` plugin: raw PCM via `AudioTrack`
- PCM types: pcm8 (8-bit), pcm16 (16-bit), pcm32 (float)
- Channel: mono or stereo
- Mode: `MODE_STREAM` (continuous feed)
- Volume: stub (no-op in current implementation)

### Bluetooth Audio Profiles
| Profile | Purpose |
|---------|---------|
| A2DP | High-quality audio playback to glasses speakers |
| HFP | Hands-Free Profile for voice calls/recording |
| SCO | Synchronous voice link (managed by `BluetoothReceiver`) |
| SPP | Serial data transport (fallback) |

### ACL Sequencing
```
ACL: waiting for HFP to be ready before SPP...
ACL: HFP wait timeout, proceeding with SPP anyway
ACL/A2DP/HFP event timeout, falling back to periodic retry
```

### TTS (Text-to-Speech)
- Server-side: `jwt/tts` endpoint
- Control: `playTts`, `setTtsVolume`, `isTtsEnabled`
- Voice features: `aiVoiceReply`, `setAIVoiceBroadcastEnabled`, `VoiceWakeUp` ("Hi memo")
- Voiceprint recognition: `saveVoiceprintRecorded`, `submitVoiceprint`

### Volume Management
| API | Purpose |
|-----|---------|
| `setGlassOutputVolume` / `getGlassOutputVolume` | Glasses speaker volume |
| `setAIAudioVolume` / `getAIAudioVolume` | AI voice volume |
| `setTtsVolume` / `getTtsVolume` | TTS volume |
| `setSpeechVolume` | Speech volume |
| `Adaptive Volume` / `setAutoVolume` | Auto-adjustment |
| Head movement controls | `settings_head_move_mute/volume_increase/volume_reduce` |

Hardware commands: `AudioVolumeUp`, `AudioVolumeDown`, `AudioVolumeMute`, `MicrophoneVolumeUp/Down/Mute`, `AudioTrebleUp/Down`, `AudioBassBoostToggle`

---

## 11. MQTT Cloud Protocol

### Brokers
```
mqtts://mqtt.memo-mind.com:8883
mqtts://mqtt.qa.memo-mind.com:8883
mqtts://oversea-mqtt.memo-mind.com:8883
mqtts://oversea-mqtt.qa.memo-mind.com:8883
```

### Topics
- `user/+/cmd/memomind.control` — cloud-to-device commands (subscribe)
- `memomind.control` — device-to-cloud commands (publish)
- `memomind.notify` — notifications (publish)

### Auth
- `MqttAuthInterceptor` injects credentials
- Token-based, refreshed automatically
- No hardcoded credentials found

---

## 12. Key Dart Package Map

```
common_blue/
├── command/
│   ├── adviser_command.dart
│   ├── ai_chat_command.dart
│   ├── billboard_command.dart
│   ├── device_service_command.dart
│   ├── notification_command.dart
│   ├── recorder_command.dart
│   ├── teleprompter_command.dart
│   ├── translate_command.dart
│   └── wizard_command.dart
├── data/
│   ├── data_blue.dart
│   ├── device_bluetooth.dart
│   ├── wrapper_blue.dart              # Cosonic protocol
│   └── wrapper_blue_wq.dart           # WQ protocol
├── manager/
│   ├── base/
│   │   ├── ack_bluetooth.dart
│   │   ├── base_bluetooth_manager.dart
│   │   └── spp_manager.dart
│   ├── receiver/
│   │   ├── cosonic_receiver_bluetooth.dart
│   │   ├── protocol_parser_bluetooth.dart
│   │   └── wq_receiver_bluetooth.dart
│   └── scan/
│       └── spp_ble_hybrid_scanner.dart
├── protocol/
│   ├── blue_config_default.dart
│   ├── interface_blue_manager.dart
│   ├── state_bluetooth.dart
│   └── wrapper_protocol_bluetooth.dart
├── record/
│   ├── data/model/
│   │   ├── offline_frame_info.dart
│   │   └── offline_recording_info.dart
│   ├── decoder/
│   │   └── record_opus_decoder.dart
│   ├── manager/
│   │   ├── record_blue_manager.dart
│   │   ├── record_manager.dart
│   │   └── offline_record_*.dart
│   ├── parser/
│   │   ├── wq_offline_record_parser_bluetooth.dart
│   │   └── wq_record_parser_bluetooth_v2.dart
│   └── protocol/
│       ├── protocol_record_manager.dart
│       ├── record_command.dart
│       └── record_config_default.dart
└── util/
    ├── bytes_util.dart
    ├── font_glyph_bitmap_data.dart
    └── split_text_into_pages.dart

module_ota/
├── data/
│   ├── ota_attach_device_info.dart
│   ├── ota_file_wrapper.dart
│   └── wq_ota_blue_wrapper.dart
├── manager/
│   ├── ota_manager.dart
│   ├── ota_receiver_service.dart
│   └── wq_ota_receiver_bluetooth.dart
├── protocol/
│   ├── ota_command.dart
│   ├── ota_config_default.dart
│   └── state_upgrade_protocol.dart
└── ota_provider.dart

biz_dashboard/
├── utils/
│   └── component_area_controller.dart
├── service/
│   └── base_component_area_service.dart
└── device_billboard_repository.dart

module_teleprompter/
└── teleprompter_play_view.dart
```

---

## 13. Implications for AD Glasses Bridge

### What we can implement now (from Java layer)
1. **BLE scanning** — `flutter_blue_plus` is a standard BLE plugin; AD Glasses can use Android BLE APIs directly
2. **BLE connection** — connect to device, discover services, subscribe to 0x2002 notifications
3. **SPP fallback** — RFCOMM connection via 0x1101 UUID
4. **PCM playback** — `PcmPlayer` architecture is reusable

### What requires protocol reverse-engineering (Dart layer)
1. **Command serialization format** — the exact binary format of each command type
2. **MQTT-like packet framing** — header byte meanings, message type codes
3. **BillboardCommand encoding** — how area types and component data are serialized
4. **DrawCommand encoding** — how canvas operations are packed into BLE packets
5. **Font glyph bitmap format** — how rasterized text is transmitted
6. **Opus audio streaming** — how audio frames are packetized over BLE
7. **WQ Record Protocol V2** — magic bytes, frame count, data format

### Recommended approach for AD Glasses MemoMind adapter
1. **Phase 1**: Connect via BLE, discover services (0x2001/0x2002 for commands, 0x2020-0x2026 for recording, 0x7033 for OTA)
2. **Phase 2**: Sniff BLE traffic between official app and glasses using nRF Connect or similar
3. **Phase 3**: Map command bytes by observing patterns (start with simple commands like notification push)
4. **Phase 4**: Implement `MemoMindDeviceAdapter` for basic text display
5. **Phase 5**: Add audio streaming support

### Key differences from HeyCyan protocol
- HeyCyan uses simple byte-array commands (e.g., `0x02, 0x01, 0x04` for transfer mode)
- MemoMind uses a structured MQTT-like serialization with fragmentation and ACK tracking
- MemoMind has two protocol variants (Cosonic and WQ) for different hardware
- MemoMind supports richer display: area-based layouts, dashboard widgets, font glyph bitmaps
- MemoMind has full audio pipeline: Opus recording, PCM playback, TTS, A2DP/HFP/SCO

---

## 14. Android Permissions Required

From AndroidManifest.xml:
- `BLUETOOTH`, `BLUETOOTH_ADMIN`
- `BLUETOOTH_ADVERTISE`, `BLUETOOTH_SCAN` (neverForLocation), `BLUETOOTH_CONNECT`
- `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
- `READ_CALENDAR`, `WRITE_CALENDAR`
- `POST_NOTIFICATIONS`
- `READ_MEDIA_AUDIO`
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_LOCATION`
- `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`
- `INTERNET`
- `QUERY_ALL_PACKAGES`
- `READ_PHONE_STATE`
- `RECEIVE_BOOT_COMPLETED`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `VIBRATE`, `WAKE_LOCK`
- `USE_BIOMETRIC`, `USE_FINGERPRINT`

---

## 15. Native Libraries

| Library | Size | Purpose |
|---------|------|---------|
| `libapp.so` | 21 MB | Dart VM + compiled app code |
| `libflutter.so` | 10.6 MB | Flutter engine |
| `libopus.so` | — | Opus codec (encoder/decoder) |
| `libffmpegkit.so` | — | FFmpeg for media conversion |
| `liblv_font.so` | — | LVGL font rendering (on glasses) |
| `libapp_BaiduNaviApplib.so` | 25 MB | Baidu navigation |
| `libAMapSDK_NAVI_v10_1_302.so` | 58 MB | AMap navigation |

---

## 16. Verifiable Source Evidence

The following facts were cross-checked against decompiled Java sources in `android/XGIMIGlassesApp/decompiled/sources/` and APK resources:

| Fact | Source File | Status |
|------|-------------|--------|
| Package `com.memomind.ai.aphrodite` | APK strings / `AndroidManifest.xml` | ✅ Confirmed |
| Flutter engine `xgimi_glasses_flutter_engine` | `MyApp.java` L23 | ✅ Confirmed |
| `flutter_blue_plus` plugin at `com.lib.flutter_blue_plus` | `FlutterBluePlusPlugin.java` | ✅ Confirmed |
| `flutter_blue_classic` plugin at `dev.lenhart.flutter_blue_classic` | `FlutterBlueClassicPlugin.java` | ✅ Confirmed |
| `flutter_pcm_player` plugin at `com.example.flutter_pcm_player` | `FlutterPcmPlayerPlugin.java` | ✅ Confirmed |
| `record` plugin at `com.llfbandit.record` | `GeneratedPluginRegistrant.java` | ✅ Confirmed |
| `NavigationPlugin` channel `com.xgimi.glasses.navigation/method` | `NavigationPlugin.java` L28 | ✅ Confirmed |
| `AlipayPlugin` channel `com.memomind.ai.aphrodite/alipay` | `AlipayPlugin.java` | ✅ Confirmed |
| `MainActivity` registers `NavigationPlugin` and `AlipayPlugin` | `MainActivity.java` L55-60 | ✅ Confirmed |
| `libopus.so` present in native libs | `resources/lib/arm64-v8a/libopus.so` | ✅ Confirmed |
| A2DP disconnect support in `flutter_blue_classic` | `FlutterBlueClassicPlugin.disconnectA2dp` | ✅ Confirmed |
| HFP disconnect support in `flutter_blue_classic` | `FlutterBlueClassicPlugin.disconnectHfp` | ✅ Confirmed |
| ACL connect receiver | `AclConnectReceiver.java` | ✅ Confirmed |
| SPP RFCOMM UUID `00001101-...` | `BluetoothConnection.java` / `BlueClassicHelper.java` | ✅ Confirmed (standard SPP UUID) |
| PCM types (pcm8/pcm16/pcm32) and AudioTrack | `PcmPlayer.java` / `FlutterPcmPlayerPlugin.java` | ✅ Confirmed |
| Bluetooth permissions (scan, connect, advertise) | `AndroidManifest.xml` | ✅ Confirmed |
| Foreground service types (microphone, connected_device, data_sync, location) | `AndroidManifest.xml` | ✅ Confirmed |
| Calendar/notification/audio permissions | `AndroidManifest.xml` | ✅ Confirmed |

---

## 17. Deep Binary Analysis — libapp.so String Extraction (2026-06-07)

Analysis performed via parallel string extraction from `libapp.so` (21MB ARM64 AOT-compiled Dart binary).

### 17.1 MQTT Message Type Codes (CONFIRMED)

The app uses the standard `mqtt_client` Dart package (MQTT 3.1.1 spec):

| Byte | Message Type | Dart Class in Binary |
|------|-------------|---------------------|
| 0x01 | CONNECT | `MqttConnectMessage` |
| 0x02 | CONNACK | `connectAck` |
| 0x03 | PUBLISH | `MqttPublishMessage` |
| 0x04 | PUBACK | `MqttPublishAckMessage` |
| 0x05 | PUBREC | `MqttPublishReceivedMessage` |
| 0x06 | PUBREL | `MqttPublishReleaseMessage` |
| 0x07 | PUBCOMP | `MqttPublishCompleteMessage` |
| 0x08 | SUBSCRIBE | `MqttSubscribeMessage` |
| 0x09 | SUBACK | `MqttSubscribeAckVariableHeader` |
| 0x0A | UNSUBSCRIBE | `MqttUnsubscribePayload` |
| 0x0B | UNSUBACK | (confirmed via class refs) |
| 0x0C | PINGREQ | `MqttPingRequestMessage` |
| 0x0D | PINGRESP | `MqttPingResponseMessage` |
| 0x0E | DISCONNECT | `MqttDisconnectMessage` |

**Evidence**: `MqttMessageType` enum at offset `0x1bc514`, `0x1d35a1`. Header format string `Header: MessageType = ` at `0x1a4777`.

### 17.2 DataPackage Wrapper Format (CONFIRMED)

```
DataPackage {
  serviceId:  1 byte (command type identifier)
  commandId:  incrementing counter (varint or fixed-width)
  payload:    variable-length command data
}
```

**Evidence**: Format string `DataPackage{serviceId: 0x<hex>}` at offset `0xed4ab`. Log `sendAckTracked: serviceId=` at `0x92d6e`.

### 17.3 Command Type Classes (CONFIRMED)

All 9 command classes verified in binary with source paths:

| # | Command | Source Path | Binary Offset |
|---|---------|------------|---------------|
| 1 | adviser | `package:common_blue/command/adviser_command.dart` | `0x1140e0` |
| 2 | ai_chat | `package:common_blue/command/ai_chat_command.dart` | `0xc3896` |
| 3 | billboard | `package:common_blue/command/billboard_command.dart` | `0x8fd4f` |
| 4 | device_service | `package:common_blue/command/device_service_command.dart` | `0x1b8a25` |
| 5 | notification | `package:common_blue/command/notification_command.dart` | `0xff4d2` |
| 6 | recorder | `package:common_blue/command/recorder_command.dart` | `0x108bea` |
| 7 | teleprompter | `package:common_blue/command/teleprompter_command.dart` | `0xdadfb` |
| 8 | translate | `package:common_blue/command/translate_command.dart` | `0x1d7263` |
| 9 | wizard | `package:common_blue/command/wizard_command.dart` | `0xc032d` |

**NOTE**: serviceId byte values are compiled as ARM64 immediates — requires disassembly (see §17.10).

### 17.4 Area Types (CONFIRMED)

| Area | Name | Provider Path |
|------|------|--------------|
| A area | StatusArea | `provider/status_area/` (time, weather) |
| C area | ContentArea | `provider/component_area/` (calendar, news, stocks, etc.) |

Conversion method: `_statusAreaTypeFromValue@2526246552` at offset `0x228050`.

### 17.5 Dashboard Component Types (CONFIRMED)

Enum types found: `DashboardComponentType` (offset `0xf15e3`), `VComponentType` (offset `0x1d7c1f`).

| Component | Provider | Service |
|-----------|----------|---------|
| Calendar | `schedule_provider.dart` | `calendar_service.dart` |
| News | `news_provider.dart` | `news_list_service.dart` |
| Stocks | `stock_provider.dart` | `stocks_service.dart` |
| Weather | `weather_provider.dart` | `weather_service.dart` |
| Notifications | `notify_provider.dart` | `notification_service.dart` |
| Media | `media_provider.dart` | `media_service.dart` |
| Memory | `memory_provider.dart` | `memory_list_service.dart` |
| To-Do | `todo_list_provider.dart` | `todo_list_service.dart` |

### 17.6 DrawCommand Operations (CONFIRMED)

Enum: `DrawCommandType` at offset `0x129742`.

Deserialization dispatchers (all share hash `@3044314182`):
- `_readDrawImage@3044314182` (offset `0xbe3cd`) — image rendering
- `_readDrawText@3044314182` (offset `0xeea57`) — text/glyph rendering
- `_readDrawPath@3044314182` (offset `0x144ba7`) — clipping/path operations
- `_readDrawVertices@3044314182` (offset `0x20df33`) — vertex-based shapes
- `_addCommandsTag@3044314182` (offset `0x22c436`) — writes opcode byte

FFI canvas operations (hash `@17065589`):
- `_translate`, `_save`, `_restore`, `__clipRect`, `__clipPath`, `__clipRRect`, `__addText`, `__addTexture`, `__saveLayer`

### 17.7 Font Glyph Bitmap Format (PARTIAL)

| Field | Status | Evidence |
|-------|--------|----------|
| `boxW` | CONFIRMED | `FontGlyphBitmapData(boxW: ` at `0x1c43a7` |
| `boxH` | CONFIRMED | `, boxH: ` at `0x66290` |
| Class name | CONFIRMED | `_GlyphBitmapData@879203449` at `0x1f7970` |
| Cache | CONFIRMED | `_bitmapDataCache@879203449` |
| Pixel format | MEDIUM | `image/x-xbitmap` (XBM-like monochrome), `ImageDataUint1` |
| LVGL bridge | CONFIRMED | FFI to `liblv_font.so`: `lv_font_get_glyph_bitmap`, `get_glyph_bitmap_array` |

### 17.8 WQ Protocol Details (PARTIAL)

**WQ Record V2 packet format**:
```
┌─────────┬────────────┬──────────┬──────────────────────┐
│ Magic   │ frameCnt   │ Payload  │ [Optional CRC32]     │
│ (1 byte)│ (uint?)    │ (Opus)   │ (if needCRC==true)   │
└─────────┴────────────┴──────────┴──────────────────────┘
```

**CRC32**: Uses `package:archive/src/util/crc32.dart` with `getCrc32`, `getCrc32Uint8List`, `_crc32Table`. Optional via `needCRC`/`needCRC32` flag.

**WQ MIME type**: `application/vnd.wqd` (custom format).

**Validation error strings**:
- `[wq_record_v2] !! magic byte mismatch: expected ` (offset `0x22f743`)
- `[wq_record_v2] !! frameCnt out of range: ` (offset `0x22f743`)
- `[wq_offline_record] !! ignore package length mismatch: expected:`

**Offline data models**: `OfflineFrameInfo` (has `isOpus` field), `OfflineRecordingInfo{type: ...}`.

### 17.9 Teleprompter Sub-commands (CONFIRMED)

12 distinct SI commands found:
`sendTextSICommand`, `sendStartSICommand`, `sendWordSICommand`, `sendUpdateSICommand`, `sendEndSICommand`, `sendPauseSICommand`, `sendResumeSICommand`, `sendDisplayModeSICommand`, `sendFontSizeSICommand`, `sendLineSpaceSICommand`, `sendHideSICommand`, `sendTeleModeSICommand`

### 17.10 Remaining Gaps — Requires ARM64 Disassembly

| Gap | Target Function | Offset |
|-----|----------------|--------|
| serviceId byte values (command type → byte) | Each `*Command` constructor | Various |
| Area type enum values (StatusArea/ContentArea → byte) | `_statusAreaTypeFromValue@2526246552` | `0x228050` |
| Component type enum values (Calendar/News/etc. → byte) | `_getComponentType@1406308660` | `0x1ebacc` |
| DrawCommand opcode bytes | `_addCommandsTag@3044314182` | `0x22c436` |
| WQ magic byte constant | `WQRecordBluetoothProtocolParserV2.parsePackage()` | — |
| frameCnt encoding (uint8/16/32, endianness) | `WQRecordBluetoothProtocolParserV2` range check | — |
| Font bitmap header byte layout | `getSendBitmapCommands` / `_sendBitmapCommands` | — |

---

## 18. Ghidra Disassembly — Command Dispatch Table (2026-06-07)

Analysis performed via Ghidra headless + ghidra-mcp on extracted `.text` section from `libapp.so`.

### 18.1 Methodology

The `.text` section (12.7MB, VA `0x7f0000`–`0x1414a00`) was extracted as a raw binary and imported into Ghidra as AArch64 at base address `0x7f0000`. This preserves the original virtual address layout so ADRP/ADD references resolve correctly. Ghidra auto-analysis created 4,108 functions. The `ghidra-mcp` headless server was used for decompilation and function inspection.

Key discovery technique: searching for `CMP wN, #imm ; B.HI` patterns (switch dispatch) in the raw binary. The pattern `CMP w31, #0x9 ; B.HI` at `0x01027028` immediately identified the 9-command-type dispatch.

### 18.2 Command Type Dispatch Table (CONFIRMED)

**Location**: `0x01026f00` (function `FUN_01026f00`)

**Encoding**: The command type is a 5-bit value extracted from bits 8–12 of a packed integer returned by `FUN_01027088`:
```asm
bl   FUN_01027088      ; returns packed value in x0
asr  x1, x0, #0x8     ; shift right 8 bits
ubfx x1, x1, #0, #32  ; zero-extend to 32-bit
and  w2, w1, #0x1f    ; extract bits 0-4 (5-bit command type)
```

**Dispatch logic** (binary search tree on w2):

| Command Type | w2 Value | Pool Offset | Likely Identity |
|-------------|----------|-------------|-----------------|
| 0 | 0 | `x27 + 0x487f8` | Default/null handler |
| 1 | 1 | `x27 + 0x48800` | adviser |
| 2 | 2 | `x27 + 0x48808` | ai_chat |
| 3 | 3 | `x27 + 0x48810` | billboard |
| 4 | 4 | `x27 + 0x48818` | device_service |
| 5 | 5 | `x27 + 0x48820` | notification |
| 6 | 6 | `x27 + 0x48828` | recorder |
| 7 | 7 | `x27 + 0x48830` | teleprompter |
| 8 | 8 | `x27 + 0x48838` | translate |
| 9 | 9 | `x27 + 0x48840` | wizard |
| Default | >9 | `x27 + 0x48850` | fallback |
| Special | 0x1f (31) | `x27 + 0x48848` | special case |

**Note**: The identity mapping (type 1=adviser, 2=ai_chat, etc.) is inferred from the sequential order matching §4. The actual assignment requires runtime verification or further decompilation of `FUN_01027088`.

### 18.3 Object Pool Access Pattern

All command dispatch returns load from the **Dart object pool** via x27 (the Dart VM thread-local pool pointer):
```asm
add  x0, x27, #0x48, LSL #12   ; x0 = x27 + 0x48000
ldr  x0, [x0, #OFFSET]          ; load pool entry at computed address
```

Each pool entry is an 8-byte tagged pointer to a Dart object (command handler closure or class). The pool is populated at isolate startup from the snapshot data.

**Pool entries** (10 command types + default + special):
- Range: `x27 + 0x487f8` to `x27 + 0x48850`
- Spacing: 8 bytes per entry
- Total: 12 entries × 8 bytes = 96 bytes

### 18.4 String Reference Mechanism (CONFIRMED)

**No direct ADRP+ADD references** to `.rodata` string addresses exist in `.text`. All string references go through the Dart object pool (`ldr x?, [x27, #offset]`). This is standard Dart AOT behavior — strings are heap objects accessed via pool entries, not native C-style string pointers.

The ADRP+ADD pattern is used for:
- Code-to-code references (function calls, jump targets)
- Snapshot data structure references (like `0x22d11c` → `biz_dashboard_inject.config.dart`)

### 18.5 Key Code Addresses

| Address | Function | Purpose |
|---------|----------|---------|
| `0x01026f00` | `FUN_01026f00` | **Command type dispatch** — extracts 5-bit type, dispatches to pool |
| `0x01027088` | `FUN_01027088` | Returns packed value containing command type |
| `0x00808d78` | `FUN_00808d78` | Dart lazy compile stub (loads class ID `0x220d11c`) |
| `0x00906940` | `FUN_00906940` | Large function (648 code units) — likely Dart isolate entry |
| `0x00c6c368` | `FUN_00c6c368` | Small thunk (mov/ldp/ret) |

### 18.6 Snapshot Data Layout

| Symbol | VA | Size | Content |
|--------|-----|------|---------|
| `_kDartVmSnapshotData` | `0x340` | 18,256 bytes | VM-level snapshot (class table, object pool roots) |
| `_kDartIsolateSnapshotData` | `0x4ac0` | ~6.8MB | Isolate snapshot (object pool, heap objects, strings) |
| `_kDartVmSnapshotInstructions` | `0x7f0000` | 92,480 bytes | VM instruction stubs |
| `_kDartIsolateSnapshotInstructions` | `0x806940` | ~12.6MB | Dart compiled code |

Snapshot header format: `f5f5 dcdc` (magic) + 8-byte size + 8-byte version/flags + build ID string.

### 18.7 Remaining Gaps — Updated

| Gap | Status | Next Step |
|-----|--------|-----------|
| serviceId byte values (command type → BLE byte) | **PARTIAL** — 5-bit type encoding found, BLE mapping unknown | Decompile `FUN_01027088` or BLE sniff |
| Area type enum values | UNCHANGED | Decompile `_statusAreaTypeFromValue` |
| Component type enum values | UNCHANGED | Decompile `_getComponentType` |
| DrawCommand opcode bytes | UNCHANGED | Decompile `_addCommandsTag` |
| WQ magic byte constant | UNCHANGED | Decompile `WQRecordBluetoothProtocolParserV2.parsePackage()` |
| frameCnt encoding | UNCHANGED | Decompile range check |
| Font bitmap header layout | UNCHANGED | Decompile `getSendBitmapCommands` |
| Object pool → string mapping | **NEW** | Trace pool entries at `0x487f8`–`0x48850` to identify which command handler each maps to |

### 18.8 Ghidra MCP Setup (Working)

The `ghidra-mcp` headless server was successfully configured and used:

```bash
# Launch (requires full Ghidra classpath + -Dghidra.home)
GHIDRA_HOME="tools/ghidra_env/installs/ghidra_12.0.2_PUBLIC"
CLASSPATH="tools/mcp/ghidra-mcp/target/GhidraMCPHeadless.jar"
for jar in "$GHIDRA_HOME"/Ghidra/Framework/*/lib/*.jar; do CLASSPATH="$CLASSPATH:$jar"; done
for jar in "$GHIDRA_HOME"/Ghidra/Features/*/lib/*.jar; do CLASSPATH="$CLASSPATH:$jar"; done
for jar in "$GHIDRA_HOME"/Ghidra/Processors/*/lib/*.jar; do CLASSPATH="$CLASSPATH:$jar"; done
for jar in tools/mcp/ghidra-mcp/lib/*.jar; do CLASSPATH="$CLASSPATH:$jar"; done

java -Dghidra.home="$GHIDRA_HOME" -Dapplication.name=GhidraMCP \
  -classpath "$CLASSPATH" com.xebyte.headless.GhidraMCPHeadlessServer \
  --port 8096 --project /tmp/ghidra_projects/libapp_raw --program /libapp_text_raw.bin
```

**Key endpoints used**: `decompile_function`, `list_functions`, `get_function_callers`, `search_byte_patterns`, `get_assembly_context`, `get_current_program_info`, `list_segments`.

**Limitation**: The headless MCP server does not expose `create_function`, `disassemble_bytes`, or raw memory dump endpoints. Function seeding must be done via `analyzeHeadless` with Ghidra scripts, then MCP can be used for decompilation/inspection.

---

## 19. Ghidra Decompilation — FUN_01027088 and Callers (2026-06-07)

### 19.1 FUN_01027088 — Returns Packed Command Value (CONFIRMED)

**Decompiled** via ghidra-mcp headless server on port 8096.

```c
long FUN_01027088(undefined8 param_1, int param_2, undefined8 param_3, undefined8 param_4)
{
  // Stack overflow check
  if (in_x15 - 0x20U <= *(ulong *)(unaff_x26 + 0x48)) {
    FUN_013df160();  // Stack overflow handler
    param_2 = extraout_w1;
  }

  if (param_2 != unaff_w22) {
    lVar1 = FUN_008117fc();           // Get object
    uVar3 = *(ulong *)(lVar1 + -1);   // Read tagged value
    // Dynamic dispatch via class table
    lVar1 = (**(code **)(unaff_x21 + ((uVar3 >> 0xc & 0xfffff) - 0xffe) * 8))();

    if (*(int *)(lVar1 + 7) != 0) {
      // Extract value from object
      uVar2 = FUN_0081b19c(...);
      // Bit manipulation to extract command type
      uVar3 = FUN_008216c8(...);
      if ((int)uVar3 == unaff_w22) {
        lVar1 = 0;
      } else {
        lVar1 = (long)(uVar3 << 0x20) >> 0x21;  // Shift right 1
        if ((uVar3 & 1) != 0) {
          lVar1 = *(long *)(uVar3 + 7);  // Read from object
        }
      }
      return lVar1;
    }
    return 0;
  }
  return 0;
}
```

**Key insight**: The function extracts a command type from a Dart object via dynamic dispatch. The command type is encoded in bits 8-12 of the return value (extracted by the caller `FUN_01026f00` as `(uVar2 >> 8) & 0x1f`).

### 19.2 FUN_01026e2c — Dispatch + Sub-type Routing (CONFIRMED)

**Caller of FUN_01026f00** (the command dispatch function).

```c
undefined8 FUN_01026e2c(void)
{
  lVar1 = FUN_01026f00();              // Get command handler object
  uVar3 = *(ulong *)(lVar1 + 7);      // Read type tag from object+7

  if ((long)uVar3 < 3) {
    if (1 < (long)uVar3) {
      return *(undefined8 *)(unaff_x27 + 0x316c8);  // Type 2
    }
    // Type 0-1: extract value
    uVar2 = -(uVar3 >> 0x1e & 1) & 0xffffffff00000000 | (uVar3 & 0x7fffffff) << 1;
    if ((int)uVar2 == 2) {
      return *(undefined8 *)(unaff_x27 + 0x487d8);  // Type 0→2
    }
  }
  else if (3 < (long)uVar3) {
    if ((long)uVar3 < 5) {
      return *(undefined8 *)(unaff_x27 + 0x487e0);  // Type 4
    }
    if (6 < (long)uVar3) {
      // Type 7+: extract value
      uVar2 = -(uVar3 >> 0x1e & 1) & 0xffffffff00000000 | (uVar3 & 0x7fffffff) << 1;
      if ((int)uVar2 == 0xe) {
        return *(undefined8 *)(unaff_x27 + 0x487e8);  // Type 7→0xe
      }
    }
  }
  return *(undefined8 *)(unaff_x27 + 0x487f0);  // Default
}
```

**Key insight**: After the command dispatch returns a handler object, this function reads a type tag from offset 7 of the object and routes to different pool entries based on the tag value. This is a second-level dispatch for sub-types within a command class.

### 19.3 Source File Identification (CONFIRMED)

From string analysis of the snapshot data:

- **Source file**: `package:common_blue/manager/base/ack_bluetooth.dart`
- **Package**: `package:common_blue` (not `common_bl` as previously thought)
- **Method name**: `_sendAckTracked@2776141992` (hash: 0xA58B0E50)

Related strings found:
- `sendAckTracked: serviceId=` at `0x092d5e` — log message showing serviceId value
- `sendAckTracked: svc=` at `0x0ca1a0` — log message showing service value
- `ACK received svc=` at `0x079af7` — ACK reception log
- `DataPackage{serviceId: 0x` at `0x0ed4ab` — DataPackage toString() format
- `No listener registered for serviceId:` at `0x0f7ae6` — error message
- `,serviceId:` at `0x1dc15d` — MqttConnectionHandlerBase context

### 19.4 BLE Protocol Flow (Inferred)

Based on the decompiled code and string analysis:

1. **Command dispatch** (`FUN_01026f00`): Extracts 5-bit command type from bits 8-12 of packed value
2. **Sub-type routing** (`FUN_01026e2c`): Reads type tag from handler object at offset 7
3. **ACK sending** (`_sendAckTracked`): Constructs DataPackage with serviceId and sends via BLE
4. **ACK receiving** (`ACK received svc=`): Parses incoming ACK and extracts service value

The serviceId is logged as hex (`0x%02x` format) in the `sendAckTracked` method, confirming it's a byte value.

### 19.5 Ghidra MCP Setup (Updated — Working with JDK21)

**Critical**: The ghidra-mcp headless server requires Java 21, not the system Java 11.

```bash
# Correct launch command
export JAVA_HOME="/home/fertroll10/Documents/ML/HeyCyanSmartGlassesSDK/tools/ghidra_env/installs/jdk21"
export PATH="$JAVA_HOME/bin:$PATH"
export GHIDRA_HOME="/home/fertroll10/Documents/ML/HeyCyanSmartGlassesSDK/tools/ghidra_env/installs/ghidra_12.0.2_PUBLIC"

# Build classpath
CLASSPATH="tools/mcp/ghidra-mcp/target/GhidraMCPHeadless.jar"
for jar in "$GHIDRA_HOME"/Ghidra/Framework/*/lib/*.jar; do CLASSPATH="$CLASSPATH:$jar"; done
for jar in "$GHIDRA_HOME"/Ghidra/Features/*/lib/*.jar; do CLASSPATH="$CLASSPATH:$jar"; done
for jar in "$GHIDRA_HOME"/Ghidra/Processors/*/lib/*.jar; do CLASSPATH="$CLASSPATH:$jar"; done
for jar in tools/mcp/ghidra-mcp/lib/*.jar; do CLASSPATH="$CLASSPATH:$jar"; done

# Launch server (project path must include .gpr file)
java -Xmx4g -Dghidra.home="$GHIDRA_HOME" -Dapplication.name=GhidraMCP \
  -classpath "$CLASSPATH" com.xebyte.headless.GhidraMCPHeadlessServer \
  --port 8096 --project /tmp/ghidra_projects/libapp_raw.gpr --program /libapp_text_raw.bin
```

**Key findings**:
- The MCP server requires Java 21 (class file version 65.0)
- The project path must point to the `.gpr` file, not the directory
- The server locks the project; headless scripts fail with `LockException` while MCP is running
- Must kill MCP, run headless scripts, then restart MCP

### 19.6 Remaining Gaps — Updated

| Gap | Status | Next Step |
|-----|--------|-----------|
| serviceId byte values (command type → BLE byte) | **PARTIAL** — 5-bit type encoding found, BLE mapping unknown | BLE sniff or find `sendAckTracked` function entry point |
| Area type enum values | UNCHANGED | Decompile `_statusAreaTypeFromValue` |
| Component type enum values | UNCHANGED | Decompile `_getComponentType` |
| DrawCommand opcode bytes | UNCHANGED | Decompile `_addCommandsTag` |
| WQ magic byte constant | UNCHANGED | Decompile `WQRecordBluetoothProtocolParserV2.parsePackage()` |
| frameCnt encoding | UNCHANGED | Decompile range check |
| Font bitmap header layout | UNCHANGED | Decompile `getSendBitmapCommands` |
| `sendAckTracked` function entry point | **NEW** | Find class descriptor for `_sendAckTracked@2776141992` |
| Sub-type dispatch mapping | **NEW** | Map type tags 0-7 to specific command sub-types |

---

## 20. Command Class Names and Protocol Parser (2026-06-07)

### 20.1 Command Class Names (CONFIRMED)

Found in the snapshot data via string search:

| Command Class | String Location | Evidence |
|--------------|-----------------|----------|
| `AdviserCommand` | `0x0ccc1b` | `module_glasses.dart.AdviserCommand` |
| `BillboardCommand` | `0x0c8f45` | `BillboardCommand selectCAreaType type:` |
| `NotificationCommand` | `0x1e0862` | `NotificationCommand` |
| `WizardCommand` | `0x1e71bd` | `WizardCommand._handleIncoming: no sendingTask for key` |
| `RecorderCommand` | `0x07cdb0` | `startRecorderCommand`, `stopRecorderCommand`, `pauseRecorderCommand`, `resumeRecorderCommand` |
| `DeviceServiceCommand` | `0x1fd77a` | `DeviceServiceCommand._layoutTitleLines` |

### 20.2 WQ Protocol Parser (CONFIRMED)

**Source file**: `package:common_blue/record/parser/wq_record_parser_bluetooth_v2.dart` (at `0x1d8715`)

**Class name**: `WQRecordBluetoothProtocolParserV2` (at `0x1d484e`)

**Key log messages found**:
- `[wq_record_v2] !! magic byte mismatch: expected` at `0x22f744` — **magic byte validation**
- `[wq_record_v2] !! frameCnt out of range:` at `0x0b9b69` — frame count validation
- `[wq_record_v2] !! ignore package(` at `0x13d8e6` — package rejection
- `sendAckTracked: packet` at `0x1d8752` — packet sending log

**Key methods found**:
- `_formatBytes@835438829` at `0x22f7a6` — formats bytes for display
- `_parseFrame@2230473238` at `0x1216c6` — parses WQ frames

### 20.3 DrawCommand Read Methods (CONFIRMED)

All share hash `@3044314182`:

| Method | Location | Purpose |
|--------|----------|---------|
| `_readDrawImage@3044314182` | `0x0be3cd` | Image rendering |
| `_readDrawText@3044314182` | `0x0eea57` | Text/glyph rendering |
| `_readDrawPath@3044314182` | `0x144ba7` | Clipping/path operations |
| `_readDrawVertices@3044314182` | `0x20df33` | Vertex-based shapes |

### 20.4 CMP with Magic Byte Candidates

Searched for CMP instructions with common magic byte values:

| Value | Hex | Occurrences | Notes |
|-------|-----|-------------|-------|
| 0x55 | CMP #85 | 1 at `0x00c6553c` | Range check (not magic byte) |
| 0xAA | CMP #170 | 1 at `0x012a9998` | Checks `0xaa` or `0x24ec` |
| 0x5A | CMP #90 | 18 | Common in Dart runtime |
| 0xA5 | CMP #165 | 4 | Near `0x01123350`-`0x01123838` |

**Note**: The magic byte is likely a Dart constant loaded from the object pool, not an immediate value in the code. The CMP instructions found are for other purposes.

### 20.5 Method Name References Found

The method name `_sendAckTracked@2776141992` (at `0x0721f2`) is referenced as a 32-bit value at file offset `0x116636b` in the snapshot data. This is in a region containing method table entries for the `common_blue` package.

### 20.6 Remaining Gaps — Final

| Gap | Status | Next Step |
|-----|--------|-----------|
| serviceId byte values (command type → BLE byte) | **PARTIAL** — 5-bit type encoding found, BLE mapping unknown | BLE sniff or find `sendAckTracked` function entry point |
| WQ magic byte constant | **PARTIAL** — error string found, value unknown | Decompile `WQRecordBluetoothProtocolParserV2.parsePackage()` via MCP |
| Area type enum values | UNCHANGED | Decompile `_statusAreaTypeFromValue` |
| Component type enum values | UNCHANGED | Decompile `_getComponentType` |
| DrawCommand opcode bytes | UNCHANGED | Decompile `_addCommandsTag` |
| frameCnt encoding | UNCHANGED | Decompile range check |
| Font bitmap header layout | UNCHANGED | Decompile `getSendBitmapCommands` |

---

## 21. Area Type and Component Type Enum Analysis (2026-06-07)

### 21.1 Area Type Enum (PARTIAL)

**Function**: `_statusAreaTypeFromValue@2526246552` at `0x228050`

**Source**: `package:material_color_util` (nearby string context)

**Related strings**:
- `setStatusAreaType` at `0x0bf48c` — method that sets the area type
- `_statusAreaTypeFromValue@2526246552` at `0x228050` — converts value to area type enum

**Area types identified** (from string analysis):
- `StatusArea` — status bar area (confirmed at `0x0bf48c`)
- `ContentArea` — main content area (referenced in `BillboardCommand selectCAreaType type:`)

**Note**: The actual enum byte values (0, 1, 2, etc.) are stored in the Dart object pool and loaded at runtime. The `_statusAreaTypeFromValue` function likely maps integer values to enum instances.

### 21.2 Component Type Enum (PARTIAL)

**Function**: `_getComponentType@1406308660` at `0x1ebacc`

**Related strings**:
- `CalendarComponent` at `0x0a31d1` and `0x0e4ceb` — calendar component
- `componentType not change` at `0x07b6c1` — log message

**Component types identified** (from string analysis):
- `CalendarComponent` — calendar display
- Other component types (News, Weather, Music, etc.) are referenced in the snapshot but not individually found as strings

**Note**: The actual enum byte values are stored in the Dart object pool. The `_getComponentType` function likely maps integer values to enum instances.

### 21.3 DrawCommand Opcode Bytes (PARTIAL)

**Function**: `_addCommandsTag@3044314182` at `0x22c436`

**Read methods** (all share hash `@3044314182`):
- `_readDrawImage@3044314182` at `0x0be3cd` — opcode for image
- `_readDrawText@3044314182` at `0x0eea57` — opcode for text
- `_readDrawPath@3044314182` at `0x144ba7` — opcode for path
- `_readDrawVertices@3044314182` at `0x20df33` — opcode for vertices

**Note**: The `_addCommandsTag` function writes the opcode byte to the command stream. The actual opcode values (0, 1, 2, 3, etc.) are likely stored in the Dart object pool or hardcoded in the function.

### 21.4 WQ Magic Byte (PARTIAL)

**Error string**: `[wq_record_v2] !! magic byte mismatch: expected` at `0x22f744`

**Source file**: `package:common_blue/record/parser/wq_record_parser_bluetooth_v2.dart`

**Note**: The magic byte value is likely a Dart constant loaded from the object pool. The error message uses Dart string interpolation (`expected ${expectedValue}`), so the actual value is not in the string literal.

### 21.5 Summary of Findings

| Gap | Status | Evidence |
|-----|--------|----------|
| serviceId byte values | **PARTIAL** | 5-bit type encoding found, BLE mapping unknown |
| WQ magic byte constant | **PARTIAL** | Error string found, value in object pool |
| Area type enum values | **PARTIAL** | `StatusArea` and `ContentArea` identified, byte values unknown |
| Component type enum values | **PARTIAL** | `CalendarComponent` identified, byte values unknown |
| DrawCommand opcode bytes | **PARTIAL** | Read methods found, opcode values unknown |
| frameCnt encoding | **PARTIAL** | Error string found, encoding unknown |
| Font bitmap header layout | **PARTIAL** | `FontGlyphBitmapData` found, layout unknown |

### 21.6 Recommendations for Further Analysis

1. **BLE sniffing**: Capture actual BLE traffic to see the serviceId byte values and magic byte
2. **Ghidra MCP decompilation**: Use the MCP server to decompile `_statusAreaTypeFromValue`, `_getComponentType`, and `_addCommandsTag` to extract enum values
3. **Object pool analysis**: Trace the object pool entries that hold the enum values and magic byte constant
4. **Runtime analysis**: Use Flutter DevTools or Dart DevTools to inspect the runtime values of the enums

---

## 22. Ghidra Full ELF Import & Dispatch Chain Analysis

### 22.1 ELF Memory Layout

| Segment | Offset | VA | Size | Flags |
|---------|--------|-----|------|-------|
| LOAD (rodata) | `0x0` | `0x0` | `0x7e1ec2` | R |
| LOAD (text) | `0x7f0000` | `0x7f0000` | `0xc24a00` | R E |

**Key insight**: File offset = VA for both segments. All string addresses (`0x092d5e`, `0x0ed4ab`, `0x22f744`, etc.) are in the `.rodata` segment, NOT in `.text`. The `.text` segment contains only compiled code.

### 22.2 Ghidra Project Setup

**Two projects created**:

1. **`libapp_raw`** (`.text`-only): Base `0x7f0000`, 333 functions auto-analyzed. Contains the dispatch chain.
2. **`libapp_full`** (full ELF): Base `0x100000`, only 3 seeded functions. Auto-analysis failed because `.rodata` confuses Ghidra.

**Critical**: The `.text`-only project is the working one. The full ELF project is NOT useful for auto-analysis.

**MCP server command** (port 8096):
```bash
setsid bash -c '
JAVA_HOME="tools/ghidra_env/installs/jdk21"
PATH="$JAVA_HOME/bin:$PATH"
GHIDRA_HOME="tools/ghidra_env/installs/ghidra_12.0.2_PUBLIC"
CLASSPATH="tools/mcp/ghidra-mcp/target/GhidraMCPHeadless.jar"
for jar in "$GHIDRA_HOME"/Ghidra/Framework/*/lib/*.jar; do CLASSPATH="$CLASSPATH:$jar"; done
for jar in "$GHIDRA_HOME"/Ghidra/Features/*/lib/*.jar; do CLASSPATH="$CLASSPATH:$jar"; done
for jar in "$GHIDRA_HOME"/Ghidra/Processors/*/lib/*.jar; do CLASSPATH="$CLASSPATH:$jar"; done
for jar in tools/mcp/ghidra-mcp/lib/*.jar; do CLASSPATH="$CLASSPATH:$jar"; done
exec java -Xmx4g -Dghidra.home="$GHIDRA_HOME" -Dapplication.name=GhidraMCP \
  -classpath "$CLASSPATH" com.xebyte.headless.GhidraMCPHeadlessServer \
  --port 8096 --project /tmp/ghidra_projects/libapp_raw.gpr --program /libapp_text_raw.bin
' >/tmp/ghidra_projects/ghidra_mcp_8096.log 2>&1 &
```

### 22.3 Dispatch Chain (Confirmed)

The full call chain for command dispatch:

```
FUN_01025f48 (entry point, called indirectly)
  └── FUN_01026e10 (sub-type dispatch)
        └── FUN_01026f00 (command type dispatch)
              └── FUN_01027088 (packed value extraction)
```

**FUN_01026f00** (command type dispatch):
- Calls FUN_01027088 to get packed value
- Extracts 5-bit command type: `(packed >> 8) & 0x1f`
- Dispatches via CMP+branch (not jump table) to pool entries:

| Type | CMP | Pool Offset (LDR) | Likely Command |
|------|-----|-------------------|----------------|
| 0 | `#0` | `[x0, #0x7f8]` | null/default |
| 1 | `#1` | `[x0, #0x800]` | adviser |
| 2 | `#2` | `[x0, #0x808]` | ai_chat |
| 3 | `#3` | `[x0, #0x810]` | billboard |
| 4 | `#4` | `[x0, #0x818]` | device_service |
| 5 | `#5` | `[x0, #0x820]` | notification |
| 6 | `#6` | `[x0, #0x828]` | recorder |
| 7 | `#7` | `[x0, #0x830]` | teleprompter |
| 8 | `#8` | `[x0, #0x838]` | translate |
| 9 | `#9` | `[x0, #0x840]` | wizard |
| 0x1f | `#0x1f` | `[x0, #0x848]` | special |
| default | — | `[x0, #0x850]` | fallback |

**Note**: The LDR offsets are from the return value of FUN_01027088, NOT from x27. The Ghidra decompiler shows them as `unaff_x27 + 0x487f8` because it can't resolve the indirect computation (`ADD x0, x27, #0x100000; LDR x0, [x0, #0x7f8]`).

**FUN_01026e10** (sub-type dispatch):
- Calls FUN_01026f00 to get handler object
- Reads tag from handler at offset 7: `*(ulong *)(handler + 7)`
- Sub-dispatches based on tag value:

| Tag | Pool Offset | Purpose |
|-----|-------------|---------|
| 2 | `0x487d8` | Sub-type A |
| 3-4 | `0x487e0` | Sub-type B |
| 7 | `0x487e8` | Sub-type C |
| 0xe (14) | `0x487e8` | Sub-type C (same) |
| default | `0x487f0` | Default handler |

**FUN_01025f48** (entry point):
- Calls FUN_01026e10 to get command handler
- Reads multiple fields from handler object (offsets 0x23, 0x27, 0x17, 0x0f, 0x13, 0x1b)
- Calls func_0x01026ca4 or func_0x010260b8 depending on flag at offset 0x1b
- Calls func_0x00d6f8a0 to create result object
- Sets result fields including pool value from `x27 + 0x32900`

### 22.4 Why Static Analysis Hit a Wall

**Fundamental limitation**: In Dart AOT, all string and constant access goes through the object pool (`x27`). The pool is a runtime structure populated at isolate startup — its contents are NOT statically determinable from the binary alone.

Specifically:
- **String references**: `ldr x0, [x27, #pool_offset]` — the pool entry contains a pointer to the String object, but the pool is populated at runtime
- **Enum values**: Stored as Dart objects in the pool, not as immediate values in code
- **Magic byte**: A Dart constant interpolated into an error string — the value is in the pool, not in the string literal
- **No ADRP+ADD**: Dart AOT does NOT use ADRP+ADD for string references (confirmed by searching .text for ADRP instructions targeting string pages — zero matches)

### 22.5 What CAN Be Found via Static Analysis

1. **Dispatch chain structure**: The 5-bit command type encoding and dispatch table offsets are fully recovered
2. **Function call graph**: BL callers of dispatch functions found (FUN_01026e2c → FUN_01026f00, FUN_01026f00 → FUN_01027088)
3. **String locations**: All relevant strings found in .rodata
4. **Source file mapping**: Method names with hashes map to source files
5. **Class descriptors**: Command class names found (AdviserCommand, BillboardCommand, etc.)

### 22.6 Remaining Gaps & Recommended Approaches

| Gap | Static Analysis | BLE Sniffing | Runtime Analysis |
|-----|----------------|--------------|-----------------|
| serviceId byte values | ❌ Pool-dependent | ✅ **Best** | ✅ Flutter DevTools |
| WQ magic byte | ❌ Pool-dependent | ✅ **Best** | ✅ Flutter DevTools |
| Area type enum | ❌ Pool-dependent | ⚠️ Need traffic | ✅ Flutter DevTools |
| Component type enum | ❌ Pool-dependent | ⚠️ Need traffic | ✅ Flutter DevTools |
| DrawCommand opcodes | ❌ Pool-dependent | ⚠️ Need traffic | ✅ Flutter DevTools |
| frameCnt encoding | ❌ Pool-dependent | ✅ WQ packets | ✅ Flutter DevTools |
| Font bitmap header | ❌ Pool-dependent | ✅ BLE data | ✅ Flutter DevTools |

**Recommended next steps** (in order of reliability):
1. **BLE sniffing** (requires glasses): Capture actual BLE traffic with HCI snoop log or nRF Sniffer
2. **Runtime analysis** (requires Flutter app running): Use `flutter attach` + DevTools to inspect Dart objects
3. **Decompile FUN_01025f48 callers**: Find the function that calls the dispatch entry point (called indirectly, likely through a function pointer or vtable)

### 22.7 Files Created/Modified

| File | Purpose |
|------|---------|
| `/tmp/ghidra_projects/libapp_raw.gpr` | Ghidra project (.text only, 333 functions) |
| `/tmp/ghidra_projects/libapp_full.gpr` | Ghidra project (full ELF, 3 functions — NOT useful) |
| `/tmp/ghidra_projects/libapp_text_raw.bin` | Extracted .text section (13MB) |
| `/tmp/ghidra_scripts/SeedFunctionsByAddress_Generic.java` | Function seeding script |
| `/tmp/ghidra_scripts/DumpAddressesListing.java` | Address dump script |
| `BINARY_ANALYSIS_MVP_PLAN.md` | Detailed plan for next agent |

---

*Research completed: 2026-06-06*
*Deep binary analysis: 2026-06-07*
*Ghidra disassembly: 2026-06-07*
*Ghidra decompilation: 2026-06-07*
*Command class analysis: 2026-06-07*
*Area type and component type analysis: 2026-06-07*
*Dispatch chain analysis: 2026-06-07*
*Full ELF import & MCP setup: 2026-06-07*
*Live Frida capture #1 (Samsung SM-F956B): 2026-06-09*
*Live Frida capture #2 (clean session, ordered actions): 2026-06-09*
*Sources: jadx decompilation of xgimi-glasses.apk, strings extraction from libapp.so, Flutter plugin source analysis, ARM64 Ghidra headless + ghidra-mcp disassembly of .text section, live Frida instrumentation on rooted Samsung SM-F956B*

---

## 23. Frida Live Capture Results (2026-06-09)

### 23.1 Setup
**Device**: Samsung SM-F956B (rooted with Magisk)
**Method**: Frida spawn on `com.memomind.ai.aphrodite` with Bluetooth socket hooks
**Frida version**: 17.11.0
**frida-server**: Running as root on device

### 23.2 Transport Discovery
The app opens **three RFCOMM sockets** simultaneously — NOT BLE GATT as previously assumed:
- UUID `00001101-0000-1000-8000-00805f9b34fb` (main control)
- UUID `00002026-0000-1000-8000-00805f9b34fb` (secondary)
- UUID `00002024-0000-1000-8000-00805f9b34fb` (record/audio)

No `BluetoothGatt.writeCharacteristic` calls were observed in either capture session, confirming RFCOMM SPP is the active transport.

### 23.3 Wire Protocol (Confirmed)

Control frames follow this format:
```
fa 00 00 <len:uint16 BE> <seq:uint8> <group:uint8> <opcode:uint8> <type:uint8> [payload...] [crc:uint16 BE?]
```

Message type byte (`type`):
- `0x01` = request (empty payload)
- `0x02` = response (with payload)
- `0x06` = success ACK
- `0x08` = push/write (with payload)
- `0x07` = push/write variant

Binary audio stream (glasses → phone):
```
52 91/92 00 00 <frame_id:uint32 LE> <...audio data...>
```
Typically 404 or 808 byte chunks.

### 23.4 Confirmed Command Mappings

#### Group 0x01 — Device & Battery
| Opcode | Type | Direction | Payload |
|--------|------|-----------|---------|
| 0x02 request | 0x01 | phone→glass | (empty) |
| 0x02 response | 0x02 | glass→phone | JSON: model, sn, mac, ver, cver, hver, fver, name, pver |
| 0x06 request | 0x01 | phone→glass | (empty) |
| 0x06 response | 0x02 | glass→phone | JSON: full settings + bat, charging, c_bat etc. |

#### Group 0x04 — Teleprompter
| Opcode | Type | Direction | Meaning | Preceded by |
|--------|------|-----------|---------|-------------|
| 0x01 | 0x08 | phone→glass | Config (layout/viewport e.g. `[464,350,1,3,1,3,1]`) | — |
| 0x05 | 0x08 | phone→glass | Push text content | — |
| 0x04 | 0x08 | phone→glass | Display params (e.g. `[6,80]` = speed/font?) | — |
| 0x0b | 0x08 | phone→glass | Start playback | `52 01 00` |
| 0x0a | 0x08 | phone→glass | Pause | `52 00 00` |
| 0x02 | 0x08 | phone→glass | Stop (and possibly clear) | — |

#### Group 0x05 — Notifications
| Opcode | Type | Direction | Meaning |
|--------|------|-----------|---------|
| 0x01 | 0x08 | phone→glass | Push notification JSON (`id`, `a`, `type`, `ts`, `c`, `ti`, `pkg_name`) |

#### Group 0x02 — High-Level Cards / Components
| Route | Direction | Meaning | Example payload |
|------|-----------|---------|-----------------|
| 0x02 / 0x08 / inner `0x04 0x05` | phone→glass | Stock card | `[{"c":"GOOGL", ..., "pts":[...]}]` |
| 0x02 / 0x08 / inner `0x06 0x05` | phone→glass | News card | `[{"c":"NATO invites research proposals..."}]` |
| 0x02 / 0x08 / inner `0x09 0x05` | phone→glass | Schedule / to-do card | `[{"ti":"pet to do", ...}]` |
| 0x02 / 0x08 / inner `0x0a 0x05` | phone→glass | Calendar single-entry card | `[{"ti":"Calendar entry test 1", ...}]` |

These captures strongly suggest the phone often sends structured component/card data and the glasses render the UI internally, instead of receiving low-level line/curve primitives.

#### Group 0x03 — Side-Button Utility Menu (partial)
| Route | Direction | Meaning | Evidence |
|------|-----------|---------|----------|
| 0x03 / 0x01 / type `0x02` | phone→glass | Translation option / language pair | Payload `EN > EN` |
| 0x03 / 0x0a | phone→glass | Recorder-related request | Seen after `52 01 00` |
| 0x03 / 0x0b | phone→glass | Recorder-related follow-up | Immediate ACK + binary stream |
| 0x03 / 0x0c | phone→glass | Utility action trigger | Seen between menu interaction and recorder start |
| 0x03 / 0x0d | phone→glass | Recorder-related state/control | Seen after binary stream begins |

The double-tap side-button menu itself still did not appear as readable labels in the captured text path. The best current interpretation is that the menu is rendered by the opaque full-screen renderer, while specific utility actions leak out as `group 0x03` control frames.

#### Group 0x0c — Recorder / Voice / ASR
| Opcode | Type | Direction | Meaning | Preceded by |
|--------|------|-----------|---------|-------------|
| 0x01 | 0x08 | phone→glass | Start record session | `52 01 00` |
| 0x07 | 0x08 | phone→glass | Begin capture | — |
| 0x03 | 0x08 | phone→glass | Pause | `52 00 00` |
| 0x02 | 0x08 | phone→glass | Stop | — |
| 0x24 | 0x07 | phone→glass | ASR partial transcription | — |
| 0x25 | 0x07 | phone→glass | ASR final transcription | — |
| 0x26 | 0x07 | phone→glass | Assistant reply streaming text | — |

### 23.5 The `52 xx xx` Toggle Markers
These short 3-byte markers precede certain actions:

| Bytes | Meaning |
|-------|---------|
| `52 01 00` | Start/enable |
| `52 00 00` | Stop/pause/disable |
| `52 11 00 00 xx xx` | Secondary status (seen after recorder start) |

### 23.6 Audio / Microphone Stream
When recording, the glasses push binary data back:
- Format: `52 91/92 00 00 <frame_id:uint32 LE> <payload:404 or 808 bytes>`
- Frame IDs count sequentially: `00 00 00 00`, `00 00 00 01`, `00 00 00 02`...
- Payload appears to be raw Opus or PCM audio data
- Further analysis needed to confirm codec and decode

### 23.7 Remaining Gaps (Frida-Era)

| Gap | Why Still Unknown | How to Resolve Next |
|-----|------------------|---------------------|
| **Display primitives** (DrawImage, DrawText, DrawPath, DrawVertices) | Not triggered in captured sessions; weather/news/stock/calendar/to-do all resolved to higher-level card/component payloads instead | Trigger a truly custom-drawing surface, or decode the opaque binary full-screen mode |
| **DrawCommand opcode values** | Untriggered | Same as above — capture when app draws to screen |
| **Area type enum** (StatusArea, ContentArea) | No billboard area switching observed | Trigger billboard display with different area settings |
| **Full-screen lyric renderer** | Spotify/lyrics mode did not send lyric text as JSON; instead it coincided with large opaque `52 91 ...` bursts | Compare side-button vs look-up sessions and decode the binary stream |
| **Utility menu labels and play/pause semantics** | Double-tap menu actions partially mapped to `group 0x03`, but the menu labels themselves were not readable | Capture each menu option in isolation and correlate with visible UI |
| **WQ magic byte** | Audio stream captured but no WQ header found in binary output | Examine binary stream payload structure; decompile WQ parser |
| **Explicit clear-display** | Not observed as a separate command; may be teleprompter stop or a different opcode | Test clearing display from different app screens |
| **MQTT serviceId byte mapping** | The RFCOMM transport does not use MQTT framing — the BLE path may be secondary | Compare BLE GATT traffic if BLE path is ever used |

### 23.8 Adapter Implications

For the first working MemoMind bridge, we should stop treating raw draw primitives as a prerequisite. The highest-probability path for useful output is:
- use `group 0x02` high-level card payloads for news, stock, reminders, to-do, and calendar surfaces
- use `group 0x05` notifications for alerts and media metadata
- use `group 0x04` teleprompter for long-form text when a card layout is not a good fit

This gives Even Hub and MentraOS a practical near-term target without waiting for a fully decoded low-level graphics pipeline.

### 23.9 Frida Script Repository
The working Frida script is maintained at:
- `android/AD-Glasses/app/src/main/java/com/ad_glasses/bridge/devices/memomind/FRIDA_CAPTURE_GUIDE.md`

The latest logger output from the clean session is at:
- `/tmp/opencode/memomind_bt_frida.log` (on the development PC)

---

## 24. OTA Protocol Deep Dive (from Ghidra Decompiled Sources)

### 24.1 OTA State Machine

The MemoMind app implements a full OTA state machine in Dart (`module_ota` package):

| State | Class | Role |
|-------|-------|------|
| `IdleOtaState` | idle_state_ota.dart | Waiting for trigger |
| `CheckingOtaState` | checking_state_ota.dart | Calls server API |
| `ConnectStateOta` | connect_state_ota.dart | BLE/SPP connection to glasses |
| `DownloadOtaState` | download_state_ota.dart | Fetches firmware binary |
| `VerifyingStateOta` | verifying_state_ota.dart | MD5 verification |
| `RebootOtaState` | reboot_state_ota.dart | Triggers glasses reboot |
| `FinishOtaState` | finish_state_ota.dart | Success |
| `ErrorOtaState` | error_state_ota.dart | Failure handling |
| `PauseStateOta` | pause_state_ota.dart | Paused mid-transfer |

Additional states: `AbUpgradeOTAState`, `UpgradeOTAState`

### 24.2 OTA API Endpoints (from `libapp.so` strings)

```
jwt/ota/check-update       — POST, JWT-authenticated, checks if update available
jwt/ota/get-release-note   — POST, fetches release notes
/ota/check                 — Check endpoint
/ota/update                — Update endpoint
/ota/releaseNote           — Release notes endpoint
/debug/ota                 — Debug OTA page
```

API client: `OtaXgimiApi` in `package:biz_ota_info/data/network/ota_xgimi_api.dart`
Repository: `OtaRemote` in `package:biz_ota_info/data/repository/ota_remote.dart`
Model: `OtaFirmwareModel` in `package:biz_ota_info/data/model/ota_firmware_model.dart`

### 24.3 OTA Firmware Transfer Protocol

Transfer is block-based over BLE or SPP:

- `sendFirmwareUpdateBlock` / `sendOtaFileBlock` — sends firmware chunks
- `analysisOtaFileBlockResponse` — parses ACK from glasses
- `WQOtaBluetoothProtocolParser` — parses OTA BLE protocol
- `WQOtaBluetoothUpgradeFilePackage` — wraps firmware file for transfer
- `WQOtaBluetoothPackage` — individual OTA BLE package

Key strings from `libapp.so`:
```
WQOta receive package:
sendFirmwareUpdateBlock failed
sendFirmwareUpdateBlockResponse:
Firmware data verification error
Firmware exceeds update space
Firmware update info error
No firmware URL available.
download firmware timeout url=
download firmware dio error url=
firmware_md5 / firmwareMd5
firmware MD5 mismatch: expected=
isEnterUpgradeMode:
```

### 24.4 OTA BLE Characteristics

```
ota_ble_service_guid                    — OTA BLE service
ota_ble_write_client_characteristics_config_guid  — OTA write CCCD
ota_ble_notify_client_characteristics_config_guid — OTA notify CCCD
ota_spp                                 — OTA over SPP path
```

Known characteristic: `0x7033` (`00007033-0000-1000-8000-00805F9B34FB`)

### 24.5 OTA Error Codes

```
ota_error_disconnect     — BLE/SPP disconnected during transfer
ota_error_enterMode      — Failed to enter upgrade mode
ota_error_file           — Firmware file error
ota_error_firmwareInfo   — Firmware info mismatch
ota_error_infoTimeout    — Info request timed out
ota_error_keyMismatch    — Encryption key mismatch
ota_error_lowBattery     — Battery too low for OTA
ota_error_reboot         — Reboot failed
ota_error_space          — Not enough storage on glasses
ota_error_uboot          — U-Boot error
ota_error_updateFail     — General update failure
ota_error_verify         — Verification failed
```

### 24.6 OTA Status Codes

```
ota_status_check         — Checking for update
ota_status_continue      — Continue transfer
ota_status_download      — Downloading firmware
ota_status_downloading   — Download in progress
ota_status_finish        — OTA complete
ota_status_reboot        — Rebooting glasses
ota_status_waiting       — Waiting for glasses
```

### 24.7 Font OTA

Separate from firmware OTA:
- `FontOtaStatus`, `inquireFontOtaIfCanUpdate`
- Font OTA error codes: alloc_buf, erase_partition, get_base_addr, lzma_write, read_cus8, verify_failed
- Uses same BLE transport but different protocol

### 24.8 OTA Capture Strategy

The OTA flow cannot be triggered when glasses are on latest firmware. Options to capture:
1. **MITM proxy** — intercept `jwt/ota/check-update` at network level
2. **Firmware version spoofing** — modify device info response to report older `ver` field
3. **Glasses with older firmware** — find a unit that hasn't been updated

---

## 25. Fullscreen Binary Renderer Analysis (`52 91/92 ...`)

### 25.1 Dual-Purpose Format

The `52 91/92` prefix serves two purposes:
1. **Audio streaming** — microphone data from glasses (small 404/808 byte frames)
2. **Fullscreen rendering** — draw command frames for lyrics, menu, immersive UI (larger bursts)

### 25.2 Frame Structure (from Frida capture)

```
52 91 00 00 <counter:uint16 BE> 08 <seq:uint16 BE> <ts:uint24 BE>
<payload: 8 blocks × 43 bytes (40 data + 3-byte separator 00 00 00 00 28)>
```

Header (12 bytes):
| Offset | Size | Field | Observed |
|--------|------|-------|----------|
| 0-1 | 2 | Magic | `52 91` or `52 92` |
| 2-3 | 2 | Flags | `00 00` |
| 4-5 | 2 | Counter | `09 ff` (2559) |
| 6 | 1 | Type | `08` |
| 7-8 | 2 | Sequence | Increments per frame |
| 9-11 | 3 | Timestamp | `3d 28 6a` etc. |

Payload (392 bytes):
- 8 blocks of 40 bytes each
- Separated by `00 00 00 00 28` (5 bytes)
- `0x28` = 40 = block size (self-describing separator)
- `b8` appears at byte 4 of most blocks — likely a type/flag marker

### 25.3 Block Structure (hypothesis)

Each 40-byte block may represent one draw element:
```
<id:4 bytes> 00 b8 <type:1 byte> <data:32 bytes> <trailing:2 bytes>
```

The `b8` byte at offset 4 appears in ~90% of blocks. Possible interpretations:
- Fixed marker byte for draw elements
- Part of a packed coordinate format
- Canvas state flag

### 25.4 Ghidra DrawCommand System

From `libapp.so` string analysis:

**DrawCommandType enum** at offset `0x129742`

**Deserialization dispatchers** (all share hash `@3044314182`):
| Method | Offset | Purpose |
|--------|--------|---------|
| `_readDrawImage@3044314182` | `0xbe3cd` | Image rendering |
| `_readDrawText@3044314182` | `0xeea57` | Text/glyph rendering |
| `_readDrawPath@3044314182` | `0x144ba7` | Clipping/path operations |
| `_readDrawVertices@3044314182` | `0x20df33` | Vertex-based shapes |
| `_addCommandsTag@3044314182` | `0x22c436` | Writes opcode byte |

**FFI canvas operations** (hash `@17065589`):
`_translate`, `_save`, `_restore`, `__clipRect`, `__clipPath`, `__clipRRect`, `__addText`, `__addTexture`, `__saveLayer`

**Critical limitation**: DrawCommand opcode byte values are Dart constants stored in the object pool (`x27` register). NOT statically recoverable from the binary.

### 25.5 Head Movement Display System

From `libapp.so` strings, the glasses support head-gesture-triggered display:

| Gesture | Action |
|---------|--------|
| Head left/right | Navigate |
| Head center | Confirm |
| Head up | Brightness increase |
| Head down | Brightness reduce |
| Double-click | Open head movement page |
| DND gesture | Do not disturb |
| Mute gesture | Mute audio |
| Next/Previous/Play-Pause | Media control |
| Reboot/Shutdown | Power actions |

Settings class: `HeadMoveSetting`, `HeadMoveSettingNotifier`, `HeadMoveShortcuts`
Enum: `HeadMoveDirection`, `HeadMoveType`

### 25.6 Lyrics / Fullscreen Mode

From UI strings:
- "Play music to view scrolling lyrics"
- "Auto Fullscreen" — "Stay 3 seconds during playback to enter fullscreen"
- Lyrics are rendered via the opaque `52 91 ...` binary path, NOT as JSON text
- The fullscreen renderer is triggered automatically during music playback

### 25.7 Decoding Strategy

**Option A — Runtime hooking (recommended)**:
1. Use Frida to hook `_addCommandsTag` and `_readDrawVertices` in `libapp.so`
2. Capture the opcode byte when each draw command is dispatched
3. Correlate with visible screen state

**Option B — Correlation capture**:
1. Trigger known visual states (blank, text, image, lyrics)
2. Capture `52 91` frames for each state
3. Compare frame structures to reverse-engineer opcode mapping

**Option C — Ghidra object pool extraction**:
1. Find object pool base address in `libapp.so`
2. Enumerate pool entries at the DrawCommandType offset
3. Extract enum values statically

## Section 26: Universal Patcher System

### 26.1 Concept

Since we cannot hook the MemoMind draw command opcodes (they're Dart object-pool constants in `libapp.so`), we need to **repurpose existing MemoMind screens** to display content from third-party app runtimes (Even Hub and MentraOS).

The **Universal Patcher** lets users:
1. Select an Even Hub or Mentra app
2. Select which MemoMind screen to repurpose (translation card, notification, stock, news, schedule, calendar, ASR, recorder)
3. Configure field mappings (which app data fields map to which screen fields)
4. Test the patch live
5. Save the patch as a JSON config file

### 26.2 Target Screens (MemoMind)

| Screen | Description | Best For |
|--------|-------------|----------|
| **Translation Card** | Scrolling text + microphone access | Terminal output, live captions, continuous text streams |
| **Notification** | Phone-style notification card | Alerts, messages, status updates |
| **Stock Ticker** | Key-value data card | Financial data, metrics, data with labels |
| **News Card** | Headline + body | Articles, updates, feeds |
| **Schedule Card** | Agenda items with times | Calendars, task lists, timelines |
| **Calendar Card** | Event details | Event info with time/location |
| **ASR Display** | Speech recognition text | Live transcription, voice input display |
| **Recorder** | Audio capture with start/pause/stop | Voice capture, dictation |

The **Translation Card** is the most versatile — it accepts continuous scrolling text and exposes microphone activity. This makes it ideal for:
- A Claude Code / Codex CLI / opencode terminal display
- Microphone access for voice instructions to agents
- Permission granting when agents request it

### 26.3 Architecture

```
┌─────────────────┐     ┌──────────────┐     ┌─────────────────┐
│  Plugin Source   │────▶│  PatchEngine │────▶│  MemoMind       │
│  (Even Hub /    │     │  (field      │     │  GlassesBridge  │
│   Mentra)       │     │   mapping +  │     │  (RFCOMM →      │
│                 │     │   routing)   │     │   glasses)      │
└─────────────────┘     └──────────────┘     └─────────────────┘
        ▲
        │
┌───────────────┐
│  PatchConfig  │  (JSON file in app storage)
│  - source     │
│  - target     │
│  - mappings   │
│  - settings   │
└───────────────┘
```

### 26.4 Files Created

| File | Purpose |
|------|---------|
| `ui/plugins/PluginDataSource.kt` | `PluginCardData` model, `PluginSource` enum, `PluginLaunchType` enum, `PluginDataSource` interface, `EvenHubPluginSource`, `MentraPluginSource` |
| `ui/plugins/PluginsViewModel.kt` | Multi-source loading, source filtering, `launchPlugin()` routing |
| `ui/plugins/PluginsScreen.kt` | Source filter chips, period filters, platform login cards, plugin cards with source badges |
| `ui/plugins/patcher/PatchConfig.kt` | `PatchSource`, `TargetScreen`, `FieldMapping`, `PatchConfig` with JSON serialization |
| `ui/plugins/patcher/PatchStore.kt` | JSON file storage in `app/filesDir/patches/`, index file, export/import |
| `ui/plugins/patcher/PatchEngine.kt` | Runtime: Even Hub polling, Mentra proxy (port 8099), field mapping, routing to `GlassesBridge` |
| `ui/plugins/patcher/PatchEditorScreen.kt` | Compose UI: source app, name, target screen selector, field mappings, test/save buttons |
| `ui/plugins/patcher/PatchListScreen.kt` | Compose UI: list of saved patches, toggle start/stop, edit/delete |
| `ui/plugins/patcher/PlatformTokenStore.kt` | SharedPreferences token storage for Even Hub and Mentra platforms |
| `ui/plugins/patcher/AuthCaptureActivity.kt` | WebView login flow with cookie/token/localStorage interception |
| `layout/activity_auth_capture.xml` | WebView layout for auth capture |

### 26.5 How to Use the Patcher

1. **Open Apps & Plugins** in AD Glasses
2. **Log in** to Even Hub and/or Mentra using the "Login" buttons in the Platform Accounts card
3. **Browse apps** using the source filter chips (All / Even Hub / Mentra)
4. **Tap a plugin card** → launches through the appropriate runtime
5. **Tap "Patches"** in the top bar → opens the patch list
6. **Tap "+"** → opens the patch editor
7. **Configure**: select target screen, add field mappings, set poll interval
8. **Tap "Test"** → starts the patch engine, routes data to glasses
9. **Tap "Save"** → persists the patch as a JSON file

## Section 27: Endpoint Discovery via Frida

### 27.1 Problem

Both Even Hub and Mentra gate their app catalogs behind authentication:
- **Even Hub**: Portal at `hub.evenrealities.com/hub`, login at `hub.evenrealities.com/login` with email/password from the Even Realities App. No public app listing API.
- **Mentra**: Developer console at `console.mentraglass.com`, app store at `apps.mentra.glass` (SPA). No public app listing API.

### 27.2 Strategy

Two-pronged approach:
1. **Frida instrumentation** of the official apps to discover the actual API endpoints they use
2. **WebView auth capture** to intercept and store auth tokens for those endpoints

### 27.3 Frida Scripts

**`/tmp/opencode/evenrealities_endpoints.js`** — Instruments the Even Realities app:
- `URL.openConnection` — logs all HTTP requests
- `HttpURLConnection` — method, headers (auth/token/cookie/bearer), response code, body
- `OkHttp` — Request.Builder, RealCall.execute with response body peek
- `Retrofit.create` — discovers API service interfaces
- `SharedPreferences` / `MMKV` — extracts auth tokens
- `WebView.loadUrl` / `CookieManager` — web-based auth tokens
- Filters for `app|catalog|store|plugin|hub|api`

**`/tmp/opencode/mentra_endpoints.js`** — Instruments the Mentra app:
- Same hooks as above, plus React Native `NetworkingModule` hook
- Filters for `app|catalog|store|plugin|mentra|api`

### 27.4 How to Run Frida Discovery

```bash
# Ensure frida-server is running on device as root
adb -s RQCX700KSDF shell 'su -c "/data/local/tmp/frida-server -D &"'

# Even Realities app — navigate to Hub/Store section
timeout 300 /tmp/opencode/frida-venv/bin/frida -D RQCX700KSDF -f com.even.realities -l /tmp/opencode/evenrealities_endpoints.js --runtime=v8

# Mentra app — navigate to Apps/Store section
timeout 300 /tmp/opencode/frida-venv/bin/frida -D RQCX700KSDF -f com.mentra.app -l /tmp/opencode/mentra_endpoints.js --runtime=v8
```

**What to look for in the logs:**
- `HTTP_REQ` / `OKHTTP_REQ` — the actual API endpoint URLs
- `HTTP_HEADER` / `OKHTTP_HEADER` — auth tokens, API keys, session cookies
- `HTTP_BODY` / `OKHTTP_BODY` — response JSON with app catalog data
- `SP_GET` / `MMKV_GET` — stored auth tokens
- `COOKIE_SET` / `COOKIE_GET` — session cookies
- `RETROFIT_CREATE` — API service interfaces (reveals endpoint patterns)

### 27.5 WebView Auth Capture

**`AuthCaptureActivity.kt`** — WebView that:
1. Loads the platform portal (Even Hub or Mentra)
2. Lets the user log in manually
3. Intercepts cookies from `CookieManager` on every page load
4. Intercepts `Authorization` headers from `shouldInterceptRequest`
5. Extracts tokens from URL fragments (OAuth implicit flow) and query params
6. Injects JavaScript to read `localStorage` and `sessionStorage`
7. Auto-detects login success (URL matches portal pattern)
8. "Done — Save Tokens" button to manually finish

Tokens are stored in `PlatformTokenStore` (SharedPreferences, key = `{platform}_{token_name}`).

### 27.6 Package Names (verified)

| App | Package | Verified |
|-----|---------|----------|
| Even Realities | `com.even.realities` (TBD) | Not yet instrumented |
| Mentra | `com.mentra.mentra` | Confirmed via `adb shell pm list packages` |

### 27.7 Mentra API — Full Endpoint Map (discovered 2026-06-16)

**Backend**: `api.mentra.glass`
**Auth**: Supabase (Google OAuth) → Mentra Bearer JWT
**App type**: React Native (Expo) with OkHttp + WebView store

#### Auth Flow

```
Google OAuth → Supabase JWT (ykbiunzfbbtwlzdprmeh.supabase.co)
    → POST /auth/exchange-token (Supabase JWT → Mentra Bearer)
    → POST /api/auth/generate-webview-token (Bearer → temp token)
    → POST /api/auth/generate-webview-signed-user-token (Bearer → signed JWT)
    → WebView loads apps.mentra.glass with aos_temp_token + aos_signed_user_token
    → GET /api/auth/exchange-store-token (signed JWT → store session)
```

#### Client API (native app, `Authorization: Bearer <mentra-jwt>`)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/api/client/min-version` | Minimum app version check |
| `GET` | `/api/client/apps` | List user's installed apps (polled every ~8s) |
| `POST` | `/api/client/device/state` | Report device state (JSON body) |
| `GET` | `/api/client/user/settings` | Get user settings |
| `POST` | `/api/client/user/settings` | Update user settings (JSON body) |
| `POST` | `/api/client/audio/configure` | Configure audio settings (JSON body) |
| `GET` | `/glasses-ws?token=...&livekit=true&udpEncryption=true` | WebSocket for glasses communication |

#### Store API (WebView, store session auth)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/api/store/published-apps` | All published apps (public, no auth) |
| `GET` | `/api/store/published-apps-loggedin` | Published apps with user context |
| `GET` | `/api/store/installed` | User's installed apps |
| `GET` | `/api/store/{packageName}` | App details (e.g. `com.mentra.captions`) |

#### CDN & Frontend

| Host | Purpose |
|------|---------|
| `apps.mentra.glass` | App store frontend (React SPA) |
| `mentra-store-cdn.mentraglass.com` | App assets, icons |
| `ykbiunzfbbtwlzdprmeh.supabase.co` | Auth provider (Google OAuth) |

#### App Response Schema (`/api/client/apps`)

```json
{
  "success": true,
  "data": [
    {
      "packageName": "com.mentra.captions",
      "name": "Captions",
      "description": "Overcome hearing challenges with subtitles for the real world.",
      "logoUrl": "https://imagedelivery.net/.../square",
      "webviewUrl": "https://captions.mentraglass.com",
      "type": "background",
      "permissions": [{"type": "MICROPHONE"}],
      "hardwareRequirements": [{"type": "DISPLAY", "level": "REQUIRED"}],
      "running": false,
      "healthy": true,
      "installedDate": "2026-06-16T16:45:04.301Z"
    }
  ]
}
```

#### Store App Detail Schema (`/api/store/{packageName}`)

```json
{
  "success": true,
  "data": {
    "packageName": "com.mentra.captions",
    "name": "Captions",
    "description": "...",
    "logoURL": "https://imagedelivery.net/.../square",
    "webviewURL": "https://captions.mentraglass.com",
    "publicUrl": "https://captions.mentraglass.com",
    "developerId": "isaiah@mentra.glass",
    "developerName": "Mentra",
    "orgName": "Mentra",
    "appStoreStatus": "PUBLISHED",
    "tpaType": "background",
    "visibility": "private",
    "permissions": [...],
    "hardwareRequirements": [...],
    "settings": [],
    "version": "2.0.0"
  }
}
```

#### Auth Token Structure (Mentra Bearer JWT)

```json
{
  "sub": "665512ab-d975-472a-9273-fdde7fbf5a5b",
  "email": "fernandosaiyan10@gmail.com",
  "organizations": ["6a317d907b3f51e01f457fec"],
  "defaultOrg": "6a317d907b3f51e01f457fec",
  "iat": 1781631081
}
```

#### Verified API Calls (curl)

```bash
# List installed apps
curl -H "Authorization: Bearer $TOKEN" https://api.mentra.glass/api/client/apps

# List all published apps (public)
curl https://api.mentra.glass/api/store/published-apps

# Get app details
curl https://api.mentra.glass/api/store/com.mentra.captions

# Get user settings
curl -H "Authorization: Bearer $TOKEN" https://api.mentra.glass/api/client/user/settings
```

## Section 29: Even Realities API Discovery (2026-06-16)

### 29.1 App Details

- **Package**: `com.even.sg`
- **App type**: Flutter (Dart AOT compiled)
- **Anti-tampering**: Jiagu packer (`libjiagu_64.so`)
- **APK location**: `android/EvenRealitiesApp/even-realities.apk`
- **Decompiled**: `android/EvenRealitiesApp/decompiled/` (only Jiagu stub — real code encrypted)
- **Native libs**: `android/EvenRealitiesApp/native_libs/lib/arm64-v8a/`

### 29.2 API Base URLs (from `strings` on `libapp.so`)

| URL | Purpose |
|-----|---------|
| `https://api.evenrealities.com` | Primary API |
| `https://api2.evenis.co` | Secondary API |
| `https://api2.ev3n.co` | Secondary API |
| `https://api2.evenreal.co` | Secondary API |
| `https://api.evenis.co` | Secondary API |
| `https://cdn2.evenreal.co` | CDN for EHPK packages |
| `https://cdn-pub.evenhub.evenrealities.com` | Even Hub public CDN |
| `https://cdn-pub-dev.evenhub.evenrealities.com` | Even Hub dev CDN |
| `https://cdn-priv-dev.evenhub.evenrealities.com` | Even Hub private dev CDN |
| `https://cdn-az.even-realities.com` | Azure CDN |
| `https://cdn-az.evenrealities.com` | Azure CDN |
| `https://cdn.evenreal.co` | CDN |
| `https://evenapp.evenrealities.com` | Portal/landing pages |
| `https://evenhub.evenrealities.com` | Even Hub landing |

### 29.3 API Endpoints (from `strings` on `libapp.so`)

All endpoints use base `https://api.evenrealities.com` (or alternatives).

#### Even Hub Endpoints (`/v2/evenhub/`)

| Endpoint | Purpose |
|----------|---------|
| `/v2/evenhub/storefront_config` | Storefront configuration |
| `/v2/evenhub/ranking` | App rankings |
| `/v2/evenhub/search` | Search apps |
| `/v2/evenhub/installed` | User's installed apps |
| `/v2/evenhub/app/detail` | App details |
| `/v2/evenhub/app/download` | Download app (EHPK) |
| `/v2/evenhub/app/install` | Install app |
| `/v2/evenhub/app/uninstall` | Uninstall app |
| `/v2/evenhub/app/like` | Like app |
| `/v2/evenhub/app/unlike` | Unlike app |
| `/v2/evenhub/app/user_status` | User's app status |
| `/v2/evenhub/app/history` | App history |
| `/v2/evenhub/app/created` | User's created apps |
| `/v2/evenhub/app/bulk_latest_branch_versions` | Bulk version check |
| `/v2/evenhub/app/bulk_uninstall` | Bulk uninstall |
| `/v2/evenhub/app/bulk_remove_beta_access` | Bulk beta removal |
| `/v2/evenhub/app/remove_beta_access` | Remove beta access |
| `/v2/evenhub/beta_access` | Beta access management |
| `/v2/evenhub/developer_info` | Developer information |
| `/v2/evenhub/feedback` | Submit feedback |
| `/v2/evenhub/leaderboard` | Leaderboard |
| `/v2/evenhub/menu_order` | Menu ordering |
| `/v2/evenhub/storefront_config` | Storefront configuration |

#### General API Endpoints (`/v2/g/`)

| Endpoint | Purpose |
|----------|---------|
| `/v2/g/login` | User login |
| `/v2/g/register` | User registration |
| `/v2/g/check_reg` | Check registration status |
| `/v2/g/check_password` | Check password |
| `/v2/g/send_code` | Send verification code |
| `/v2/g/pre_check_code` | Pre-check verification code |
| `/v2/g/reset_passwd` | Reset password |
| `/v2/g/user_info` | Get user info |
| `/v2/g/set_profile` | Set user profile |
| `/v2/g/upload_avatar` | Upload avatar |
| `/v2/g/account_del` | Delete account |
| `/v2/g/account_logout` | Logout |
| `/v2/g/bind_device` | Bind device |
| `/v2/g/unbind_device` | Unbind device |
| `/v2/g/unbind_terminal` | Unbind terminal |
| `/v2/g/check_bind` | Check bind status |
| `/v2/g/list_devices` | List devices |
| `/v2/g/set_device_remark` | Set device remark |
| `/v2/g/check_firmware` | Check firmware |
| `/v2/g/check_latest_firmware` | Check latest firmware |
| `/v2/g/check_app` | Check app |
| `/v2/g/set_on_boarded` | Set onboarding status |
| `/v2/g/get_user_prefs` | Get user preferences |
| `/v2/g/set_user_prefs` | Set user preferences |
| `/v2/g/update_glasses_settings` | Update glasses settings |
| `/v2/g/update_set` | Update settings |
| `/v2/g/update_ios_app_list` | Update iOS app list |
| `/v2/g/func_conf` | Function configuration |
| `/v2/g/get_nv` | Get NV data |
| `/v2/g/upload_nv` | Upload NV data |
| `/v2/g/get_privacy_urls` | Get privacy URLs |
| `/v2/g/i18n_keys` | Internationalization keys |
| `/v2/g/is_sn_in_blacklist` | Check SN blacklist |
| `/v2/g/asr_sconf` | ASR configuration |
| `/v2/g/weather` | Weather data |
| `/v2/g/news_categories` | News categories |
| `/v2/g/news_list` | News list |
| `/v2/g/news_sources` | News sources |
| `/v2/g/news_favorites_settings` | News favorites |
| `/v2/g/news_favorites_settings_save` | Save news favorites |
| `/v2/g/stock_tickers` | Stock tickers |
| `/v2/g/stock_intraday` | Stock intraday |
| `/v2/g/stock_favorite_list` | Stock favorites |
| `/v2/g/stock_favorite_create` | Create stock favorite |
| `/v2/g/stock_favorite_updateT` | Update stock favorite |
| `/v2/g/stock_favorite_del` | Delete stock favorite |
| `/v2/g/translate_create` | Create translation |
| `/v2/g/translate_get` | Get translation |
| `/v2/g/translate_update` | Update translation |
| `/v2/g/translate_delete` | Delete translation |
| `/v2/g/translate_ai_summary` | AI translation summary |
| `/v2/g/inbox/list` | Inbox list |
| `/v2/g/inbox/unread_count` | Unread count |
| `/v2/g/inbox/mark_as_read` | Mark as read |
| `/v2/g/inbox/delete` | Delete inbox item |
| `/v2/g/filelogs/feedback` | File logs feedback |

#### AI/Jarvis Endpoints (`/v2/g/jarvis/`)

| Endpoint | Purpose |
|----------|---------|
| `/v2/g/jarvis/chat` | AI chat |
| `/v2/g/jarvis/conversate/list` | Conversation list |
| `/v2/g/jarvis/conversate/detail` | Conversation detail |
| `/v2/g/jarvis/conversate/messages` | Conversation messages |
| `/v2/g/jarvis/conversate/create` | Create conversation |
| `/v2/g/jarvis/conversate/update` | Update conversation |
| `/v2/g/jarvis/conversate/remove` | Remove conversation |
| `/v2/g/jarvis/conversate/finish` | Finish conversation |
| `/v2/g/jarvis/conversate/heartbeat` | Conversation heartbeat |
| `/v2/g/jarvis/conversate/ws` | Conversation WebSocket |
| `/v2/g/jarvis/conversate/background/create` | Background conversation |
| `/v2/g/jarvis/conversate/background/list` | Background list |
| `/v2/g/jarvis/conversate/background/status` | Background status |
| `/v2/g/jarvis/conversate/background/update` | Background update |
| `/v2/g/jarvis/conversate/background/remove` | Background remove |
| `/v2/g/jarvis/message/list` | Message list |
| `/v2/g/jarvis/message/sentiment` | Message sentiment |
| `/v2/g/jarvis/session/action/delete` | Delete session action |
| `/v2/g/jarvis/knowledge-base/*` | Knowledge base management |
| `/v2/g/jarvis/app_log/report` | App log reporting |

#### Health Endpoints (`/v2/g/health/`)

| Endpoint | Purpose |
|----------|---------|
| `/v2/g/health/push` | Push health data |
| `/v2/g/health/get_info` | Get health info |
| `/v2/g/health/update_info` | Update health info |
| `/v2/g/health/get_latest_data` | Get latest health data |
| `/v2/g/health/query_window` | Query health window |
| `/v2/g/health/batch_query_window` | Batch query |
| `/v2/g/health/export` | Export health data |
| `/v2/g/health/get_pkey` | Get health public key |

### 29.4 Required Headers

From `strings` analysis of `libapp.so`:

| Header | Purpose |
|--------|---------|
| `getx-client` | Client identification (likely device type) |
| `Authorization` | Bearer token |
| `Content-Type` | Request content type |
| `User-Agent` | Client user agent |
| `evenrealities-trace-id` | Request tracing |
| `X-Even-Mock` | Mock mode flag (debug) |
| `api_key` | API key |

### 29.5 API Behavior

- **All endpoints return 403** with `{"code": 403, "msg": "Your device went wrong", "data": null}` when called without proper device headers
- **HTTP status is always 200** — the error is in the JSON body, not the HTTP status code
- **Device registration required** — the API requires a registered device serial number (SN)
- **Dart API service**: `package:even/common/api/api_service.dart`
- **Auth interceptor**: `package:even/common/api/interceptors/auth_interceptor.dart`
- **Login expired interceptor**: `package:even/common/api/interceptors/login_expired_interceptor.dart`

### 29.6 Dart Package Structure (from `strings` on `libapp.so`)

Key packages found:
- `package:even/common/api/api_service.dart` — Main API service
- `package:even/common/api/api_service.ex.dart` — API service extensions
- `package:even/common/api/interceptors/auth_interceptor.dart` — Auth interceptor
- `package:even/common/api/interceptors/login_expired_interceptor.dart` — Login expired handler
- `package:even/common/api/mock/api_mock_settings.dart` — Mock settings
- `package:even/common/api/models/even_hub_app_detail.dart` — Even Hub app detail model
- `package:even/common/api/models/even_hub_app_info.dart` — Even Hub app info model
- `package:even/common/api/models/app_update.dart` — App update model
- `package:even/common/api/models/device_ota_info.dart` — Device OTA info model

### 29.7 Ghidra Analysis Setup

- **Ghidra project**: `/tmp/ghidra_projects_even/even_libapp.gpr`
- **MCP server**: Port 8097 (running)
- **Base address**: `0x010b0000` (.text section)
- **Size**: 17 MB (.text section)
- **Status**: Analysis complete, MCP server running for interactive decompilation

### 29.8 Key Differences from Mentra

| Aspect | Mentra | Even Realities |
|--------|--------|----------------|
| App type | React Native (Expo) | Flutter (Dart AOT) |
| HTTP client | OkHttp (Java) | Dart `http` (native) |
| Anti-tampering | None | Jiagu packer |
| API base | `api.mentra.glass` | `api.evenrealities.com` |
| Auth | Supabase JWT → Bearer | Device SN + Bearer |
| Public endpoints | Yes (`/api/store/published-apps`) | No (all device-gated) |
| Frida effectiveness | High (Java hooks work) | Low (Dart VM, Jiagu) |

### 29.9 Next Steps for Even Realities

1. **Find device header requirements** — use Ghidra to decompile `commonRequestHeader` function
2. **Understand auth flow** — decompile `AuthInterceptor` to find token acquisition
3. **Test with registered device** — if Even G2 glasses become available
4. **Use Ghidra MCP** — decompile API service functions to understand header construction
5. **Consider mitmproxy** — intercept real app traffic if device pairing is possible

## Section 30: Next Steps for Continuation (Updated)

### 30.1 Immediate

1. ~~**Connect device** and verify package names~~ ✅ Done
2. ~~**Run Frida endpoint discovery** on Mentra~~ ✅ Done — full API map captured
3. ~~**Run Frida endpoint discovery on Even Realities**~~ ✅ Done — API map from `strings` on `libapp.so`
4. **Update `MentraPluginSource`** with real API data:
   - Use `GET /api/store/published-apps` (public, no auth needed)
   - Parse response and map to `PluginCardData`
5. **Test WebView auth capture**:
   - Open AD Glasses → Apps & Plugins → Login
   - Verify cookies/tokens are captured correctly

### 30.2 Short-term

6. **Decompile Even Realities API service** with Ghidra:
   - Use MCP server on port 8097
   - Find `commonRequestHeader` function
   - Understand device header requirements
   - Find auth token acquisition flow

7. **Create `AppCatalogFetcher`** — uses discovered endpoints + captured tokens:
   - `fetchMentraPublicCatalog()` → `GET /api/store/published-apps` (no auth)
   - `fetchMentraInstalledApps(token)` → `GET /api/client/apps` (Bearer auth)
   - `fetchEvenHubCatalog(token)` → `/v2/evenhub/storefront_config` (device-gated)

### 30.3 Medium-term

8. **Test patches end-to-end**:
   - Create a patch for a real Mentra app (e.g., Captions → Translation Card)
   - Verify data flows from the app through PatchEngine to the glasses

9. **Add Mentra relay proxy**:
   - The existing `MentraLocalRelay` already accepts `POST /display`
   - The PatchEngine's proxy mode (port 8099) can intercept and translate
   - Configure Mentra apps to point at the proxy instead of the real relay

10. **Translation Card as terminal**:
    - The translation card has scrolling text + microphone access
    - Can be repurposed as a terminal for Claude Code / Codex CLI / opencode
    - Microphone provides voice instructions to the agent
    - Agent output scrolls on the glasses display

### 30.4 Long-term

11. **Universal patcher marketplace**:
    - Users can create, share, and import patch configs
    - Export/import as JSON files
    - Community patches for popular Even Hub / Mentra apps

12. **Draw command reverse engineering** (still blocked):
    - If `libapp.so` draw opcodes are ever decoded, patches can render arbitrary content
    - Until then, the screen-repurposing approach is the only viable path

13. **OTA firmware investigation** (blocked — glasses on latest version):
    - Firmware version spoofing script exists (`/tmp/opencode/memomind_firmware_spoof.js`)
    - Needs glasses with older firmware or server-side version manipulation

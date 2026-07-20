# Live Preview — Test Mode Report

**Date:** 2026-07-16
**Author:** CyanBridge App Team
**Status:** Passive lab probe only; BLE activation blocked pending protocol confirmation

---

## 1. Background

Analyzed V821 firmware images include a dedicated live streaming application (`ai_glass_livestream`), and the mode dispatcher script (`/etc/media/rtc_init.sh`) maps mode 8 to that binary. Static analysis indicates that it is built on the live555 RTSP/RTP stack and is intended to stream encoded camera video over Wi-Fi; runtime behavior remains unverified.

The current CyanBridge implementation deliberately does not trigger this mode. It arms BLE/P2P discovery and probes the RTSP stream after the hardware team activates mode 8 through an independently verified lab procedure.

---

## 2. Firmware Findings

### 2.1 Mode Dispatcher

The script `/etc/media/rtc_init.sh` reads `/sys/kernel/aglink_mode` and launches the corresponding application:

| Mode | Application | Description |
|---|---|---|
| 0 | `ai_glass_photo` | Photo capture |
| 1 | `ai_glass_video` | Video recording |
| 2 | `ai_glass_download` | Media transfer (HTTP server) |
| 3 | `ai_glass_ota` | OTA firmware update |
| 4 | *(no app)* | AI-only mode |
| 5, 7 | `ai_glass_normal` | Normal/standby |
| 6 | `ai_glass_audio` | Audio recording |
| **8** | **`ai_glass_livestream`** | **Live streaming (RTSP)** |

Mode 8 explicitly loads the WLAN kernel module before launching the livestream binary, confirming it is designed for network streaming.

### 2.2 `ai_glass_livestream` Binary Analysis

The binary was found in the analyzed WIFIAM01G1, WIFIAM01C, WIFIAM01W, and WIFIA02E02 images. Key findings from static analysis:

- **Protocol:** Contains live555 RTSP/RTP server components
- **Port candidate:** Static analysis found RISC-V `ADDI` instructions materializing **554** (the standard RTSP port) at two locations. No CLI flag, config file, or environment variable changing it was identified.
- **Stream-name candidate:** `testH264VideoStreamer` appears as a live555 session name. The first URL to test is `rtsp://<ip>:554/testH264VideoStreamer`.
- **Codec:** CLI strings indicate H.264, JPEG, and H.265 selections via `--encode_format0` / `--encode_format1` (0=H.264, 1=JPEG, 2=H.265)
- **Transport:** Includes live555 code for RTP over UDP and RTP over TCP; enabled runtime transports are unverified
- **Capture stack:** References the Allwinner CedarX `AWVideoInput` API, consistent with a camera-to-encoder-to-RTSP pipeline
- **WiFi:** Links against `libwifimg-v2.0.so` and `libaglink.so` for P2P/AP management
- **Log string:** The binary contains a log format for `rtsp://<ip>:554/testH264VideoStreamer`; only a device test can confirm that startup reaches it and serves the stream

The binary accepts video encoder configuration arguments (`--bitrate`, `--srcsize`, `--dstfps`, `--rotate`, etc.) but has no RTSP-specific options — the port and stream name are compile-time constants.

### 2.3 Mode String Table

The `libaglink.so` library contains a mode-to-string table used by `aglink_mode_to_string()`. The strings in order are:

```
Unknown, PHTOTO, DOWNLAOD, OTA, AI, NORMAL, AG_MODE_RECORD_AUDIO, IDLE, LIVESTREAM, VIDEO
```

`LIVESTREAM` appears at index 8 in this table, confirming it corresponds to `aglink_mode = 8`.

---

## 3. BLE Command Rationale

### 3.1 Command Pattern

The existing media control buttons use the following BLE command pattern via `LargeDataHandler.glassesControl()`:

```
Bytes: [0x02, 0x01, CMD]
       ^       ^     ^
       |       |     └─ Command/action byte
       |       └─────── Sub-command type
       └─────────────── Glasses control command
```

### 3.2 Known Command Mapping

| CMD byte | Function | System mode |
|---|---|---|
| `0x01` | Photo capture | Mode 0 |
| `0x02` | Video start | Mode 1 |
| `0x03` | Video stop | — |
| `0x04` | Transfer mode (P2P sync) | Mode 2 |
| `0x05` | OTA mode | Mode 3 |
| `0x06` | AI photo (with preview) | Mode 4 |
| `0x08` | Audio recording start | Mode 6 |
| `0x09` | Exit transfer mode | — |
| `0x0b` | Stop AI audio stream | — |
| `0x0c` | Audio recording stop | — |
| `0x0f` | P2P reset | — |

`workTypeIng` describes a work state for command types supported by the SDK parser. It must not be treated as a universal command echo or as proof that an unsupported command entered a particular firmware mode.

### 3.3 Unsafe Candidates and SDK Parsing

There is no confirmed BLE command for livestream mode. Numeric gaps in the command table are not safe candidates:

- `0x09` is confirmed in the official app as exit-transfer after media download.
- `0x0a` is confirmed in the official app as factory reset.
- `0x0e` is confirmed in the official app as device restart.
- `0x0d` remains unknown and must not be sent without firmware-side confirmation.

The shipped SDK's `GlassModelControlResponse` parser only populates `errorCode` and `workTypeIng` for an allowlist that excludes command types `0x09`, `0x0a`, `0x0d`, and `0x0e`. A command can therefore execute while the parsed response retains its default error value. Parser-driven fallback retries are unsafe and are prohibited in the Live Preview flow.

The source-level regression test `LivePreviewSourceSafetyTest` fails if `glassesControl()` is added to `LivePreviewManager`.

---

## 4. Implementation

### 4.1 Flow

The debug-only "Passive RTSP lab probe" follows the transport portion of the OTA P2P pattern:

1. **Arm only:** Register the BLE IP listener. No BLE mode-control command is sent.
2. **External activation:** The hardware team activates mode 8 through an independently verified procedure.
3. **P2P connection:** Discover and connect to the glasses via Wi-Fi Direct.
4. **IP discovery:** Wait for the glasses to report their P2P IP via BLE notification type `0x08` (45s timeout).
5. **RTSP probe:** Connect to `rtsp://<ip>:554/testH264VideoStreamer` using Media3 RTSP support.
6. **Playback:** If the stream is found, display it in a dialog.

Stopping the probe releases ExoPlayer, restores Android's default network, and attempts to tear down the phone's P2P group. If Android does not confirm group removal, the implementation retains the preview lease rather than claiming cleanup succeeded. It does not send a device-mode exit command because no livestream exit command is confirmed. The hardware procedure must include a separately verified recovery to normal/idle mode.

### 4.2 Probe Strategy

Since we cannot confirm the exact runtime behavior without a physical device test, the implementation tries multiple combinations:

| Priority | Port | Stream path | Rationale |
|---|---|---|---|
| 1 | 554 | `testH264VideoStreamer` | From binary analysis (hardcoded) |
| 2 | 554 | `live` | Common convention |
| 3 | 554 | `stream` | Common convention |
| 4 | 554 | `video` | Common convention |
| 5 | 554 | `ch0` | IPC convention |
| 6 | 554 | `h264` | Codec-named |
| 7 | 554 | *(root)* | Bare URL |
| 8–14 | 8554 | *(same paths)* | Fallback port |

### 4.3 Dependencies

- `androidx.media3:media3-exoplayer:1.5.1` — media player engine
- `androidx.media3:media3-exoplayer-rtsp:1.5.1` — RTSP client support
- `androidx.media3:media3-ui:1.5.1` — player UI components

### 4.4 Logging

All livestream activity is logged under the `LivePreview` tag. To monitor during testing:

```bash
adb logcat -s LivePreview
```

Every step logs elapsed time, P2P events, BLE notification raw bytes, RTSP probe attempts with timing, and ExoPlayer state transitions. The passive flow has no mode-command response to log.

---

## 5. Open Questions

The following items are unclear and will need to be resolved during field testing. Doubts may be forwarded to the CyanBridge engineering team for clarification:

1. **What exact JieLi-side command enters mode 8?** This must be recovered from the command dispatcher or supplied by the hardware team. Android SDK parser output is not sufficient confirmation.

2. **Does the glasses' P2P stack activate automatically when entering livestream mode?** The `ai_glass_livestream` binary links against the WiFi/P2P libraries, but we don't know if it starts P2P discovery on its own or waits for the phone to initiate it. The implementation mirrors the OTA flow where the phone drives P2P.

3. **Is port 554 accessible from the P2P network?** Port 554 is privileged (< 1024). The glasses run as root so binding should work, but we need to verify the RTSP server binds to the P2P interface (`p2p-wlan0-0`) and not only `wlan0`.

4. **Is `testH264VideoStreamer` the runtime stream name?** The string is present in the analyzed binary, but only a running-device probe can confirm that the exposed session uses it.

5. **What happens to the livestream binary when the P2P connection drops?** Does it exit gracefully, or does it keep running? This affects whether we need to send a stop command when the user closes the preview.

6. **What exits livestream mode safely?** The current implementation sends no exit command. Do not use transfer command `0x04` as an exit; it enters transfer mode.

---

## 6. Test Procedure

### Prerequisites
- Glasses connected to phone via BLE (standard CyanBridge app connection)
- A recoverable lab device whose exact firmware image has been checked for the `ai_glass_livestream` binary

### Steps
1. Use a recoverable lab device and ensure the official HeyCyan app is force-stopped.
2. Open a debug CyanBridge build and navigate to the Glasses dashboard.
3. Run `adb logcat -s LivePreview WifiP2pManagerSingleton`, tap "Arm passive probe", and verify it contains `PASSIVE MODE: no BLE mode-control command will be sent` and does not log `resetDeviceP2p called`.
4. Have the hardware team activate mode 8 using its approved procedure.
5. Observe: Awaiting mode 8 → P2P connecting → Waiting for IP → Probing → Playing.
6. If the stream plays, verify the video is the camera feed from the glasses.
7. Tap "Close" or "Stop", then use the hardware team's verified procedure to return the device to normal mode.

### Expected Outcomes

| Outcome | Likely cause | Next step |
|---|---|---|
| Stream plays successfully | External mode activation and RTSP transport work | Record firmware/hardware versions and lifecycle behavior |
| "No RTSP found" after IP received | Mode activation, RTSP startup, port, or URL may be wrong | Check logcat for which ports/paths were tried; try VLC manually |
| "No IP" timeout | External activation did not produce a BLE IP notification | Confirm the approved activation procedure; try Sync data first to verify P2P works |
| No LivePreview mode command or P2P-reset command in logs | Passive safety behavior is working | Continue with external mode activation |
| P2P discovery finds no peers | Glasses not advertising P2P | Verify BLE is connected; try toggling Wi-Fi on phone |

---

## 7. Files

| File | Purpose |
|---|---|
| `app/src/main/java/com/fersaiyan/cyanbridge/ota/LivePreviewManager.kt` | Core logic: passive BLE notification listener, P2P, IP discovery, RTSP probe, ExoPlayer |
| `shared/src/commonMain/.../GlassesDashboardPresentation.kt` | `LivePreviewUiState`, `StartLivePreview`/`StopLivePreview` actions |
| `shared/src/commonMain/.../GlassesDashboardScreen.kt` | "Live preview" UI section in HeyCyanControls |
| `app/src/main/java/com/fersaiyan/cyanbridge/MainActivity.kt` | Action wiring, dialog display |
| `app/build.gradle` | media3 ExoPlayer + RTSP dependencies |

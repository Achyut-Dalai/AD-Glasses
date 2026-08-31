# HeyCyan protocol reference

Status: living protocol map derived from verified sources

Last updated: 2026-08-30

This file contains byte-level protocol findings that are sufficiently supported to be useful for a native implementation. It is intentionally separate from `ARCHITECTURE.md` (system design), `RESEARCH_LOG.md` (history), and `OFFICIAL_APP_FINDINGS.md` (artifact notebook).

Do not add guessed commands here.

---

## 1. Verified GATT transports

### Base command channel

```text
Service
6e40fff0-b5a3-f393-e0a9-e50e24dcca9e

Write
6e400002-b5a3-f393-e0a9-e50e24dcca9e

Notify/read
6e400003-b5a3-f393-e0a9-e50e24dcca9e

CCCD
00002902-0000-1000-8000-00805f9b34fb
```

### Serial / LargeDataHandler channel

```text
Service
de5bf728-d711-4e47-af26-65e3012a5dc7

Write
de5bf72a-d711-4e47-af26-65e3012a5dc7

Notify
de5bf729-d711-4e47-af26-65e3012a5dc7
```

The official production application actively uses the `de5bf...` channel for `LargeDataHandler` requests. Native iOS should discover and subscribe to both verified families as required by the protocol implementation.

---

## 2. LargeDataHandler outer frame

The official production implementation of:

```text
LargeDataHandler.addHeader(command, payload)
```

constructs:

```text
offset  size  meaning
0       1     0xBC frame marker
1       1     command-family byte
2       2     payload length, unsigned 16-bit little-endian
4       2     CRC16 of payload, little-endian
6       N     payload bytes
```

Equivalent notation:

```text
BC CMD LEN_LO LEN_HI CRC_LO CRC_HI PAYLOAD...
```

If the payload is null/empty, length remains zero and the CRC bytes are `FF FF`.

### CRC

The official `CRC16.calcCrc16()` implementation is CRC-16/MODBUS style:

```text
initial value: 0xFFFF
polynomial:    0xA001
input:         payload bytes only
result:        16-bit value
wire order:    low byte, then high byte
```

The frame marker, command byte and payload-length bytes are **not** included in this CRC calculation.

### Length byte order

`DataTransferUtils.shortToBytes()` writes the low byte first and high byte second, confirming little-endian encoding for both length and CRC fields.

---

## 3. Confirmed command families

Values observed in official production code and the physical-glasses capture:

```text
0x41  glasses-control family
0x42  battery synchronization
0x43  device-info synchronization
0x44  AI voice-wake setting
0x51  music/call/system volume control
0x59  glasses microphone Opus packet
0x73  unsolicited device notification
0xFD  picture-thumbnail transfer
0xFC  IP / Wi-Fi-side information operation
```

Additional command families exist and are still being mapped.

---

## 4. Confirmed `0x41` glasses-control payloads

The values below are payloads passed by production application call sites into `LargeDataHandler.glassesControl(...)`.

They are written in hexadecimal.

| Payload | Production call-site meaning | Evidence status |
| --- | --- | --- |
| `02 01 01` | Take picture | PROVEN |
| `02 01 02` | Start video recording | PROVEN |
| `02 01 03` | Stop video recording | PROVEN |
| `02 01 08` | Start audio recording | PROVEN |
| `02 01 0C` | Stop audio recording | PROVEN |
| `02 01 06 Q Q` | AI photo at quality `Q` (`00...05`) | PROVEN |
| `02 01 04 01` | Prepare/import album using P2P | PROVEN |
| `02 01 04 02` | Prepare/import album using AP | PROVEN |
| `02 01 09` | Media/file download complete notification/cleanup | PROVEN |
| `02 01 14 01` | Start real-time preview using P2P | PROVEN |
| `02 01 14 02` | Start real-time preview using AP | PROVEN |
| `02 01 15 01` | Real-time-preview cleanup/exit | PROVEN |
| `02 01 0A` | Factory-reset action | PROVEN call site |
| `02 01 0E` | Restart action | PROVEN call site |
| `02 01 0F` | Reset device P2P state | PROVEN call site |
| `02 01 05` | Wi-Fi SoC OTA start/control | PROVEN call site |
| `02 0C 01` | Translation/voice session heartbeat start | PROVEN call site |
| `02 0C 02` | Translation/voice session heartbeat stop | PROVEN call site |
| `02 04` | Read album/media counts | PROVEN call site |
| `01 0A` | Query glasses work type | PROVEN call site |

There are additional feature-specific payloads in production code (music transfer, delayed recording, AI/meeting functions, etc.) that should be added only after their surrounding state semantics are fully traced.

---

## 5. Example complete frames

Using the verified `0x41` command family and official framing/CRC algorithm:

### Take picture

Payload:

```text
02 01 01
```

CRC16(payload):

```text
0x5010
```

Complete frame:

```text
BC 41 03 00 10 50 02 01 01
```

### Video request

```text
BC 41 03 00 50 51 02 01 02
```

### Audio-record request

```text
BC 41 03 00 D0 56 02 01 08
```

### Media transfer — P2P

```text
BC 41 04 00 93 5C 02 01 04 01
```

### Media transfer — AP

```text
BC 41 04 00 D3 5D 02 01 04 02
```

The physical P2P capture proves that a successful work-type `04` response has a distinct credential
shape rather than the generic control acknowledgement:

```text
RESPONSE DATA_TYPE=01 WORK_TYPE=04 MODE
SSID_LENGTH_LE PASSWORD_LENGTH_LE SSID_BYTES PASSWORD_BYTES
```

The captured response used a 22-byte SSID and 9-byte passphrase. Native iOS parses these lengths
strictly, obtains the device address only from the subsequent `0x73/0x08` notification, validates
all three values, and only then constructs the `NEHotspotConfiguration`. It does not ask a caller
to pre-supply or hard-code credentials. AP-mode response behavior still needs a physical-iPhone
capture before the sync UI is promoted.

Physical iPhone testing on AM01CY firmware additionally proved that an AP-mode request
(`02 01 04 02`) can return a valid credential payload whose reported mode is `01`. Response
correlation therefore accepts either documented mode, preserves the device-selected mode, and
validates the credential lengths instead of requiring the request byte to be echoed. The manual
development flow exposes those credentials before waiting for the later `0x73/0x08` address event,
because that event may depend on the phone first joining the advertised network.

### Connection initialization

The captured official-app ready sequence now has native equivalents for:

```text
0x40  local clock/language/time-zone synchronization
0x43  device and firmware information
0x42  battery and charging state
0x51  music/call/system volume profile
0x49  request Classic Bluetooth audio/control connection
```

The `0x40` payload is the SDK's exact nine-byte `SyncTime` record: six BCD wall-clock fields,
language code, encoded GMT offset, and final clock-status byte. The official implementation adds
one second before serialization. The captured India vector `26 08 29 23 44 03 01 0C 01` is covered
by a native protocol test.

### Live preview — P2P

```text
BC 41 04 00 9E 9C 02 01 14 01
```

### Live preview — AP

```text
BC 41 04 00 DE 9D 02 01 14 02
```

### Live-preview cleanup

```text
BC 41 04 00 9F 0C 02 01 15 01
```

These byte sequences are deterministic reconstructions of the official production encoder. The
same frame format and representative command families are now validated in the physical-glasses
capture. Each product operation must still wait for its matching work-type acknowledgement and use
bounded timeout/cancellation/cleanup handling.

On physical AM01 firmware `AM01CY_2.20.10_260411`, successful Photo (`0x01`) and AI Photo
(`0x06`) actions can return `0xFF` in the control response's error/status position while the
physical shutter executes. The official `GlassModelControlResponse` parser reads this byte as
unsigned `255` and treats it as a terminal sentinel without reading `workTypeIng`; native clients
must not sign-extend it to `-1` or report it as a rejected command.

---

## 6. Confirmed assistant and device events

Voice-wake setting family `0x44`:

```text
01 00       read setting
01 01       captured response: enabled
02 00/01    write disabled/enabled
```

Unsolicited device-notification family `0x73` uses the first payload byte as a subtype. Confirmed
assistant lifecycle values are:

```text
03 01       glasses recognition/listening started
0A 01       glasses recognition/listening ended
```

Both on-glasses “Hey Cyan” detection and the rear assistant button converge on the start event. No
captured source discriminator exists.

During that interval, family `0x59` carries complete fixed-size 40-byte Opus packets. The official
decoder uses 16 kHz, mono and a 40-byte packet size, producing 16-bit/16 kHz/mono PCM.
Native iOS now decodes this container with the system Opus codec and forwards native PCM buffers to
Apple Speech through a provider-neutral input seam. Physical-iPhone end-to-end recognition remains
the promotion gate.

### Volume control family `0x51`

Read request:

```text
01
```

Read and write responses expose three ranges at payload offsets `2...12` after the outer frame is
removed. The fixed channel markers are `01` music, `02` call, and `03` system:

```text
OP 01 MUSIC_MIN MUSIC_MAX MUSIC_CURRENT
   02 CALL_MIN  CALL_MAX  CALL_CURRENT
   03 SYSTEM_MIN SYSTEM_MAX SYSTEM_CURRENT CURRENT_TYPE [reserved...]
```

Write payload:

```text
02 01 MUSIC_MIN MUSIC_MAX MUSIC_CURRENT
   02 CALL_MIN  CALL_MAX  CALL_CURRENT
   03 SYSTEM_MIN SYSTEM_MAX SYSTEM_CURRENT CURRENT_TYPE
```

The captured device reported music `0...16`, call `0...15`, and system `0...16`. Treat those as
per-device response values. Perform read-modify-write and preserve the latest ranges, unchanged
currents, and current-type byte.

---

## 7. Production Wi-Fi / IP facts

For the inspected official Android application version:

```text
stored glasses Wi-Fi password: 123456789
```

Normal media paths observed at production call sites include:

```text
http://<glasses-ip>/files/<name>
http://<glasses-ip>/files/log/<name>
http://<glasses-ip>:80/storage/sd0/C/DCIM/1/<name>
```

Known filenames/config files include:

```text
media.config
vf_list.txt
log.list
```

Real-time preview uses:

```text
rtsp://<glasses-ip>:8554/ch0
```

The inspected Android live-preview activity chooses P2P normally and its AP variant on HarmonyOS NEXT. The existence of the AP variant is especially important for native iOS feasibility.

Do not assume the fixed password or every Android network-selection rule applies identically to every firmware/iOS path until verified on physical hardware.

---

## 8. Still unresolved before a complete Swift port

```text
- exact callback/status semantics for still-unmapped glasses-control subcommands
- precise error-255 trigger and recovery sequence
- complete command-family map beyond 0x41/0x42/0x43/0xFC
- AI-photo delivery path
- physical-iPhone validation of native Opus decoding and Apple Speech ingestion
- RTSP codec/profile/resolution/fps
- model/firmware-specific behavior
- iPhone-compatible AP readiness/credential response sequence
```

The next implementation work should be driven by these verified protocol rules plus physical-device tests, not by UI assumptions.

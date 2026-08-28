# HeyCyan protocol reference

Status: living protocol map derived from verified sources

Last updated: 2026-08-29

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

Values observed in official production `LargeDataHandler` code:

```text
0x41  glasses-control family
0x42  battery synchronization
0x43  device-info synchronization
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
| `02 01 02` | Start/request video operation | PROVEN call-site; exact toggle/stop semantics still being mapped |
| `02 01 08` | Start/request audio recording operation | PROVEN call-site; exact toggle/stop semantics still being mapped |
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

These byte sequences are deterministic reconstructions of the official production encoder and observed application payloads. Physical-glasses testing is still required before shipping a native Swift sender, because correct packet bytes alone do not establish timing, readiness, response matching, exclusivity, or cleanup requirements.

---

## 6. Production Wi-Fi / IP facts

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

## 7. Still unresolved before a complete Swift port

```text
- response/notification outer-frame parsing and correlation rules
- exact callback/status semantics for every glasses-control subcommand
- whether video/audio control payloads are start-only, toggle, or mode-dependent
- precise error-255 trigger and recovery sequence
- complete command-family map beyond 0x41/0x42/0x43/0xFC
- AI-photo delivery path
- glasses microphone packet transport and Opus framing
- RTSP codec/profile/resolution/fps
- model/firmware-specific behavior
- timing/retry/exclusivity rules for physical glasses
```

The next implementation work should be driven by these verified protocol rules plus physical-device tests, not by UI assumptions.

# HeyCyan BLE wire format

Status: production-app evidence

Last updated: 2026-08-29

This document records byte-level protocol details that have been reconstructed from the user-supplied official HeyCyan Android production package `1.0.142_20260807`.

Only byte layouts marked **PROVEN** should be implemented in Swift. Higher-level command payload/subcommand meanings remain separate until their call sites and responses are mapped.

---

## 1. Primary GATT channel

**Status: PROVEN from production DEX**

```text
Service
6e40fff0-b5a3-f393-e0a9-e50e24dcca9e

Write characteristic
6e400002-b5a3-f393-e0a9-e50e24dcca9e

Notify/read characteristic
6e400003-b5a3-f393-e0a9-e50e24dcca9e

CCCD
00002902-0000-1000-8000-00805f9b34fb
```

The official app enables notifications on the primary service + notify/read characteristic before normal protocol use.

A second serial-port-style UUID family exists in the artifact but its role is not yet attributed sufficiently to use in AD Glasses.

---

## 2. Application frame

**Status: PROVEN from `LargeDataHandler.addHeader(int, byte[])` production bytecode**

For a non-empty payload, the production app constructs:

```text
Offset  Size  Meaning
0       1     magic/header = 0xBC
1       1     command family / command byte
2       2     payload length, unsigned 16-bit little-endian
4       2     CRC16 of payload, little-endian
6       N     payload bytes
```

Equivalent representation:

```text
BC CMD LEN_LO LEN_HI CRC_LO CRC_HI PAYLOAD...
```

Total frame size:

```text
payload.length + 6
```

The length is the number of payload bytes only; it does not include the six-byte frame prefix.

### Empty/null payload behavior

When the payload is null or has zero length:

```text
BC CMD 00 00 FF FF
```

The implementation leaves the two length bytes zero and explicitly writes `0xFF 0xFF` into the CRC positions.

Do not replace that behavior with the computed CRC of an empty payload unless physical/protocol evidence proves the device accepts both forms. Match production behavior.

---

## 3. Integer byte order

**Status: PROVEN from `DataTransferUtils.shortToBytes(short)` production bytecode**

16-bit values are serialized little-endian:

```text
byte[0] = value & 0xFF
byte[1] = (value >> 8) & 0xFF
```

Therefore both payload length and payload CRC in the application header are little-endian.

---

## 4. CRC16 algorithm

**Status: PROVEN from `CRC16.calcCrc16(byte[])` production bytecode**

The official app uses the standard reflected CRC-16/Modbus-style loop:

```text
initial CRC = 0xFFFF
polynomial = 0xA001
```

For each payload byte:

```text
crc ^= (byte & 0xFF)
repeat 8 times:
    if (crc & 1) != 0:
        crc = (crc >> 1) ^ 0xA001
    else:
        crc >>= 1
return crc & 0xFFFF
```

Important:

- CRC is calculated over the **payload only**.
- It does not include `0xBC`, the command byte, or the two length bytes.
- The resulting 16-bit CRC is written little-endian into offsets 4–5.
- Null/empty payloads use the production special case `FF FF` rather than this calculated value.

Example calculation:

```text
payload: 00 00
CRC16:   0xB001
wire:    01 B0
```

---

## 5. Confirmed command families

**Status: PROVEN at family level; subcommands remain under audit**

Observed `LargeDataHandler` framing calls include:

```text
0x41 / 65   glasses-control family
0x42 / 66   battery synchronization
0x43 / 67   device-info synchronization
0xFC / 252  IP / Wi-Fi-SoC information operation
```

### Battery request

The production `syncBattery()` path frames command `0x42` with a two-byte zero-initialized payload before enqueueing it to the BLE write queue.

Conceptually:

```text
command = 0x42
payload = 00 00
crc     = 0xB001
frame   = BC 42 02 00 01 B0 00 00
```

This byte sequence is a static-analysis reconstruction. Before relying on it as a physical-device test vector, verify it against an actual BLE capture or a controlled request to the glasses.

### Device-info request

The production `syncDeviceInfo()` path likewise builds command `0x43` around a two-byte zero-initialized payload. The exact response decoder is already present in the production protocol implementation and exposes Bluetooth/Wi-Fi firmware and hardware version fields.

### Glasses-control family

`glassesControl(byte[], callback)` wraps the supplied payload with command family `0x41`. The meaning of each payload/subcommand is still being mapped and must not be guessed.

### Wi-Fi SoC / IP operation

`writeIpToSoc(String, callback)` builds a `WifiInfoReq` payload and wraps it with command byte `0xFC`. Exact payload field semantics remain under audit.

---

## 6. Native Swift design implication

Implement the protocol codec separately from CoreBluetooth transport.

Conceptually:

```swift
struct HeyCyanFrameCodec {
    func encode(command: UInt8, payload: Data?) -> Data
    func decodeNotification(_ data: Data) throws -> HeyCyanPacket
}
```

The encoder can now be based on verified production evidence for:

```text
magic byte
length placement
little-endian representation
CRC algorithm
empty-payload special case
```

But the application should still wait before exposing arbitrary higher-level commands until these are verified:

```text
0x41 payload/subcommand meanings
response frame matching
fragmentation / MTU behavior
notification reassembly
busy-state rules
request timeout rules
error-code recovery
```

---

## 7. Next byte-level targets

1. Map the `0x41` payload structure and all subcommands used by the production app.
2. Reconstruct incoming notification framing and response-family dispatch.
3. Determine BLE MTU/chunking behavior and whether large frames are segmented.
4. Map `GlassModelControlResponse` fields and error `255` to exact offsets/states.
5. Reconstruct `WifiInfoReq` payload for `0xFC`.
6. Trace glasses voice/audio packet family and transport.
7. Validate representative frames against physical BLE traffic before shipping the raw Swift implementation.

#!/usr/bin/env python3
"""Read-only extractor for HeyCyan traffic from an Android BTSnoop capture.

The script does not connect to a device or replay packets. It reassembles HCI ACL/L2CAP
fragments, extracts ATT values, and then reassembles complete CRC-validated 0xBC application
frames across ATT notification/write boundaries. Use --raw-att-values to inspect the lower-level
ATT chunks instead, and --all-values when inspecting GATT setup or an unknown stream header.
"""

from __future__ import annotations

import argparse
import collections
import datetime as dt
import struct
import sys
from dataclasses import dataclass
from pathlib import Path


BTSNOOP_EPOCH_DELTA_US = 62_168_256_000_000_000
ATT_CID = 0x0004
VALUE_OPCODES = {
    0x0B: "read-response",
    0x12: "write-request",
    0x16: "prepare-write-request",
    0x17: "prepare-write-response",
    0x1B: "notification",
    0x1D: "indication",
    0x52: "write-command",
    0xD2: "signed-write-command",
}


@dataclass(frozen=True)
class ATTValue:
    timestamp_us: int
    incoming: bool
    connection_handle: int
    opcode: int
    attribute_handle: int | None
    value: bytes


@dataclass(frozen=True)
class HeyCyanFrame:
    timestamp_us: int
    completed_timestamp_us: int
    incoming: bool
    connection_handle: int
    opcode: int
    attribute_handle: int | None
    fragment_count: int
    value: bytes


def records(path: Path):
    with path.open("rb") as source:
        header = source.read(16)
        if len(header) != 16 or header[:8] != b"btsnoop\0":
            raise ValueError("not a BTSnoop file")
        version, _datalink = struct.unpack(">II", header[8:])
        if version != 1:
            raise ValueError(f"unsupported BTSnoop version {version}")

        while record_header := source.read(24):
            if len(record_header) != 24:
                raise ValueError("truncated BTSnoop record header")
            _original, included, flags, _drops, timestamp = struct.unpack(">IIIIQ", record_header)
            packet = source.read(included)
            if len(packet) != included:
                raise ValueError("truncated BTSnoop record")
            yield timestamp, bool(flags & 1), packet


def l2cap_pdus(path: Path):
    fragments: dict[tuple[bool, int], tuple[int, int, bytearray]] = {}
    for timestamp, incoming, packet in records(path):
        if len(packet) < 5 or packet[0] != 0x02:  # H4 ACL packet
            continue
        handle_flags, acl_length = struct.unpack_from("<HH", packet, 1)
        acl = packet[5 : 5 + acl_length]
        connection_handle = handle_flags & 0x0FFF
        boundary = (handle_flags >> 12) & 0x03
        key = (incoming, connection_handle)

        if boundary in (0, 2):
            if len(acl) < 4:
                continue
            expected, cid = struct.unpack_from("<HH", acl)
            value = bytearray(acl[4:])
            if len(value) >= expected:
                fragments.pop(key, None)
                yield timestamp, incoming, connection_handle, cid, bytes(value[:expected])
            else:
                fragments[key] = (expected, cid, value)
        elif boundary == 1 and key in fragments:
            expected, cid, value = fragments[key]
            value.extend(acl)
            if len(value) >= expected:
                fragments.pop(key, None)
                yield timestamp, incoming, connection_handle, cid, bytes(value[:expected])


def att_values(path: Path):
    for timestamp, incoming, connection_handle, cid, pdu in l2cap_pdus(path):
        if cid != ATT_CID or not pdu or pdu[0] not in VALUE_OPCODES:
            continue
        opcode = pdu[0]
        attribute_handle = None
        value_offset = 1
        if opcode in (0x12, 0x1B, 0x1D, 0x52, 0xD2):
            if len(pdu) < 3:
                continue
            attribute_handle = struct.unpack_from("<H", pdu, 1)[0]
            value_offset = 3
        elif opcode in (0x16, 0x17):
            if len(pdu) < 5:
                continue
            attribute_handle = struct.unpack_from("<H", pdu, 1)[0]
            value_offset = 5
        yield ATTValue(
            timestamp_us=timestamp,
            incoming=incoming,
            connection_handle=connection_handle,
            opcode=opcode,
            attribute_handle=attribute_handle,
            value=pdu[value_offset:],
        )


def timestamp_text(timestamp_us: int) -> str:
    unix_us = timestamp_us - BTSNOOP_EPOCH_DELTA_US
    # Android's BTSnoop writer records device wall-clock fields in this epoch without first
    # converting them to UTC. Keep the value timezone-naive so it lines up with logcat exactly.
    return dt.datetime.fromtimestamp(unix_us / 1_000_000, dt.timezone.utc).replace(
        tzinfo=None
    ).isoformat(timespec="milliseconds")


def crc16_modbus(value: bytes) -> int:
    crc = 0xFFFF
    for byte in value:
        crc ^= byte
        for _ in range(8):
            crc = ((crc >> 1) ^ 0xA001) if crc & 1 else crc >> 1
    return crc & 0xFFFF


def has_valid_crc(frame: bytes) -> bool:
    if len(frame) < 6 or frame[0] != 0xBC:
        return False
    payload_length = int.from_bytes(frame[2:4], "little")
    if len(frame) != 6 + payload_length:
        return False
    encoded_crc = int.from_bytes(frame[4:6], "little")
    if payload_length == 0:
        return encoded_crc == 0xFFFF
    return encoded_crc == crc16_modbus(frame[6:])


def heycyan_frames(values):
    """Reassemble application frames without treating binary payload bytes as frame starts."""

    streams: dict[tuple[bool, int, int | None], dict[str, object]] = {}
    for item in values:
        if not item.value:
            continue
        key = (item.incoming, item.connection_handle, item.attribute_handle)
        stream = streams.setdefault(
            key,
            {
                "buffer": bytearray(),
                "timestamp_us": item.timestamp_us,
                "opcode": item.opcode,
                "fragments": 0,
            },
        )
        buffer = stream["buffer"]
        assert isinstance(buffer, bytearray)

        if not buffer:
            marker = item.value.find(b"\xBC")
            if marker < 0:
                continue
            stream["timestamp_us"] = item.timestamp_us
            stream["opcode"] = item.opcode
            stream["fragments"] = 0
            buffer.extend(item.value[marker:])
        else:
            buffer.extend(item.value)
        stream["fragments"] = int(stream["fragments"]) + 1

        while True:
            marker = buffer.find(b"\xBC")
            if marker < 0:
                buffer.clear()
                break
            if marker:
                del buffer[:marker]
                stream["timestamp_us"] = item.timestamp_us
                stream["opcode"] = item.opcode
                stream["fragments"] = 1
            if len(buffer) < 6:
                break

            payload_length = int.from_bytes(buffer[2:4], "little")
            frame_length = 6 + payload_length
            if len(buffer) < frame_length:
                break

            frame = bytes(buffer[:frame_length])
            if not has_valid_crc(frame):
                # This can occur when a capture begins in the middle of a binary media payload.
                # Advance one byte and find the next CRC-valid marker instead of interpreting a
                # JPEG/Opus 0xBC byte as a protocol command.
                del buffer[0]
                continue

            yield HeyCyanFrame(
                timestamp_us=int(stream["timestamp_us"]),
                completed_timestamp_us=item.timestamp_us,
                incoming=item.incoming,
                connection_handle=item.connection_handle,
                opcode=int(stream["opcode"]),
                attribute_handle=item.attribute_handle,
                fragment_count=int(stream["fragments"]),
                value=frame,
            )
            del buffer[:frame_length]
            stream["timestamp_us"] = item.timestamp_us
            stream["opcode"] = item.opcode
            stream["fragments"] = 1 if buffer else 0


def protocol_description(value: bytes) -> str:
    if len(value) < 6 or value[0] != 0xBC:
        return ""
    payload_length = int.from_bytes(value[2:4], "little")
    expected = 6 + payload_length
    suffix = "" if len(value) == expected else f" frame-bytes={len(value)}/{expected}"
    return f" family=0x{value[1]:02X} payload-length={payload_length}{suffix}"


def print_raw_att_values(args) -> tuple[collections.Counter, collections.Counter, int]:
    stats = collections.Counter()
    protocol_stats = collections.Counter()
    shown = 0
    for item in att_values(args.capture):
        stats[(item.incoming, item.opcode, item.attribute_handle)] += 1
        if item.value:
            protocol_stats[item.value[0]] += 1
        if not args.all_values and not item.value.startswith((b"\xBC", b"\xA5")):
            continue
        direction = "RX" if item.incoming else "TX"
        handle = "----" if item.attribute_handle is None else f"{item.attribute_handle:04X}"
        captured = item.value[: max(args.maximum_hex_bytes, 0)]
        suffix = " …" if len(captured) != len(item.value) else ""
        print(
            f"{timestamp_text(item.timestamp_us)} {direction} conn={item.connection_handle:04X} "
            f"att={handle} {VALUE_OPCODES[item.opcode]} bytes={len(item.value)} "
            f"{captured.hex(' ').upper()}{suffix}{protocol_description(item.value)}"
        )
        shown += 1
    return stats, protocol_stats, shown


def print_frames(args) -> tuple[collections.Counter, collections.Counter, int]:
    stats = collections.Counter()
    protocol_stats = collections.Counter()
    shown = 0
    for frame in heycyan_frames(att_values(args.capture)):
        family = frame.value[1]
        stats[(frame.incoming, frame.opcode, frame.attribute_handle)] += 1
        protocol_stats[family] += 1
        direction = "RX" if frame.incoming else "TX"
        handle = "----" if frame.attribute_handle is None else f"{frame.attribute_handle:04X}"
        captured = frame.value[: max(args.maximum_hex_bytes, 0)]
        suffix = " …" if len(captured) != len(frame.value) else ""
        duration_ms = (frame.completed_timestamp_us - frame.timestamp_us) / 1_000
        print(
            f"{timestamp_text(frame.timestamp_us)} {direction} conn={frame.connection_handle:04X} "
            f"att={handle} {VALUE_OPCODES[frame.opcode]} bytes={len(frame.value)} "
            f"fragments={frame.fragment_count} duration-ms={duration_ms:.1f} "
            f"{captured.hex(' ').upper()}{suffix}{protocol_description(frame.value)}"
        )
        shown += 1
    return stats, protocol_stats, shown


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("capture", type=Path)
    parser.add_argument("--all-values", action="store_true")
    parser.add_argument("--raw-att-values", action="store_true")
    parser.add_argument("--summary", action="store_true")
    parser.add_argument("--maximum-hex-bytes", type=int, default=512)
    args = parser.parse_args()

    try:
        if args.raw_att_values:
            stats, protocol_stats, shown = print_raw_att_values(args)
        else:
            stats, protocol_stats, shown = print_frames(args)
    except BrokenPipeError:
        # Make common inspection pipelines such as `... | head` exit quietly once their reader
        # has enough data. The capture is read-only, so no cleanup or partial write is involved.
        return 0

    if args.summary:
        summary_name = "ATT value summary" if args.raw_att_values else "HeyCyan frame summary"
        print(f"\n{summary_name}", file=sys.stderr)
        for (incoming, opcode, handle), count in sorted(stats.items(), key=lambda item: str(item[0])):
            direction = "RX" if incoming else "TX"
            handle_text = "----" if handle is None else f"{handle:04X}"
            print(
                f"{direction} att={handle_text} {VALUE_OPCODES[opcode]} count={count}",
                file=sys.stderr,
            )
        label = "first-byte-counts" if args.raw_att_values else "family-counts"
        print(f"shown={shown} {label}={dict(protocol_stats)}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

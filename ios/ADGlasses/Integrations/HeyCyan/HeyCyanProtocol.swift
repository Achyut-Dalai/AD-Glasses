import Foundation

/// A verified HeyCyan application frame carried by the serial/large-data GATT channel.
struct HeyCyanFrame: Equatable, Sendable {
    let command: UInt8
    let payload: Data
    let crc: UInt16
    let rawData: Data
}

enum HeyCyanProtocolError: LocalizedError, Equatable, Sendable {
    case frameTooShort(actual: Int)
    case invalidMarker(actual: UInt8)
    case invalidLength(expected: Int, actual: Int)
    case payloadTooLarge(actual: Int)
    case checksumMismatch(expected: UInt16, actual: UInt16)

    var errorDescription: String? {
        switch self {
        case .frameTooShort(let actual):
            return "The AD Glasses response is too short (\(actual) bytes)."
        case .invalidMarker(let actual):
            return String(format: "The AD Glasses response marker is invalid (0x%02X).", actual)
        case .invalidLength(let expected, let actual):
            return "The AD Glasses response length is invalid (expected \(expected), received \(actual))."
        case .payloadTooLarge(let actual):
            return "The AD Glasses command is too large (\(actual) bytes)."
        case .checksumMismatch(let expected, let actual):
            return String(
                format: "The AD Glasses response checksum is invalid (expected 0x%04X, received 0x%04X).",
                expected,
                actual
            )
        }
    }
}

/// Production framing reconstructed from the official HeyCyan Android application.
///
/// Keeping byte order here (rather than at call sites) makes a hardware-driven correction local
/// if a different glasses firmware is ever proven to serialize the checksum differently.
struct HeyCyanFrameCodec: Sendable {
    enum ChecksumByteOrder: Sendable {
        case littleEndian
        case bigEndian
    }

    static let marker: UInt8 = 0xBC
    static let headerLength = 6
    static let maximumPayloadLength = Int(UInt16.max)
    static let production = HeyCyanFrameCodec(checksumByteOrder: .littleEndian)

    let checksumByteOrder: ChecksumByteOrder

    init(checksumByteOrder: ChecksumByteOrder = .littleEndian) {
        self.checksumByteOrder = checksumByteOrder
    }

    func encode(command: UInt8, payload: Data = Data()) throws -> Data {
        guard payload.count <= Self.maximumPayloadLength else {
            throw HeyCyanProtocolError.payloadTooLarge(actual: payload.count)
        }

        let payloadLength = UInt16(payload.count)
        let checksum = payload.isEmpty ? UInt16.max : Self.crc16Modbus(payload)
        var bytes = [UInt8]()
        bytes.reserveCapacity(Self.headerLength + payload.count)
        bytes.append(Self.marker)
        bytes.append(command)
        bytes.append(UInt8(payloadLength & 0x00FF))
        bytes.append(UInt8((payloadLength >> 8) & 0x00FF))
        append(checksum, to: &bytes)
        bytes.append(contentsOf: payload)
        return Data(bytes)
    }

    func decode(_ data: Data) throws -> HeyCyanFrame {
        let bytes = [UInt8](data)
        guard bytes.count >= Self.headerLength else {
            throw HeyCyanProtocolError.frameTooShort(actual: bytes.count)
        }
        guard bytes[0] == Self.marker else {
            throw HeyCyanProtocolError.invalidMarker(actual: bytes[0])
        }

        let payloadLength = Int(UInt16(bytes[2]) | (UInt16(bytes[3]) << 8))
        let expectedFrameLength = Self.headerLength + payloadLength
        guard bytes.count == expectedFrameLength else {
            throw HeyCyanProtocolError.invalidLength(
                expected: expectedFrameLength,
                actual: bytes.count
            )
        }

        let payload = Data(bytes.dropFirst(Self.headerLength))
        let receivedChecksum = checksum(fromLowOrFirstByte: bytes[4], secondByte: bytes[5])
        let expectedChecksum = payload.isEmpty ? UInt16.max : Self.crc16Modbus(payload)
        guard receivedChecksum == expectedChecksum else {
            throw HeyCyanProtocolError.checksumMismatch(
                expected: expectedChecksum,
                actual: receivedChecksum
            )
        }

        return HeyCyanFrame(
            command: bytes[1],
            payload: payload,
            crc: receivedChecksum,
            rawData: data
        )
    }

    static func crc16Modbus(_ data: Data) -> UInt16 {
        var crc = UInt16.max
        for byte in data {
            crc ^= UInt16(byte)
            for _ in 0 ..< 8 {
                if crc & 0x0001 != 0 {
                    crc = (crc >> 1) ^ 0xA001
                } else {
                    crc >>= 1
                }
            }
        }
        return crc
    }

    private func append(_ checksum: UInt16, to bytes: inout [UInt8]) {
        switch checksumByteOrder {
        case .littleEndian:
            bytes.append(UInt8(checksum & 0x00FF))
            bytes.append(UInt8((checksum >> 8) & 0x00FF))
        case .bigEndian:
            bytes.append(UInt8((checksum >> 8) & 0x00FF))
            bytes.append(UInt8(checksum & 0x00FF))
        }
    }

    private func checksum(fromLowOrFirstByte firstByte: UInt8, secondByte: UInt8) -> UInt16 {
        switch checksumByteOrder {
        case .littleEndian:
            return UInt16(firstByte) | (UInt16(secondByte) << 8)
        case .bigEndian:
            return (UInt16(firstByte) << 8) | UInt16(secondByte)
        }
    }
}

enum HeyCyanFrameStreamEvent: Equatable, Sendable {
    case frame(HeyCyanFrame)
    case discarded(Data)
    case malformed(rawData: Data, error: HeyCyanProtocolError)
}

/// Conservatively reassembles application frames across arbitrary BLE notification boundaries.
/// It does not correlate responses or assign meaning to notification payload fields.
struct HeyCyanFrameStreamDecoder: Sendable {
    private(set) var buffer = Data()

    let codec: HeyCyanFrameCodec
    let maximumBufferedBytes: Int

    init(
        codec: HeyCyanFrameCodec = .production,
        maximumBufferedBytes: Int = 256 * 1_024
    ) {
        self.codec = codec
        self.maximumBufferedBytes = max(maximumBufferedBytes, HeyCyanFrameCodec.headerLength)
    }

    mutating func append(_ data: Data) -> [HeyCyanFrameStreamEvent] {
        guard !data.isEmpty else { return [] }
        buffer.append(data)

        var events = [HeyCyanFrameStreamEvent]()
        if buffer.count > maximumBufferedBytes {
            let overflow = buffer.count - maximumBufferedBytes
            let discarded = Data(buffer.prefix(overflow))
            buffer.removeFirst(overflow)
            events.append(.discarded(discarded))
        }

        while !buffer.isEmpty {
            if buffer.first != HeyCyanFrameCodec.marker {
                if let nextMarker = buffer.firstIndex(of: HeyCyanFrameCodec.marker) {
                    let discarded = Data(buffer[..<nextMarker])
                    buffer.removeFirst(discarded.count)
                    events.append(.discarded(discarded))
                } else {
                    let discarded = buffer
                    buffer.removeAll(keepingCapacity: true)
                    events.append(.discarded(discarded))
                    break
                }
            }

            guard buffer.count >= HeyCyanFrameCodec.headerLength else { break }
            let bytes = [UInt8](buffer.prefix(HeyCyanFrameCodec.headerLength))
            let payloadLength = Int(UInt16(bytes[2]) | (UInt16(bytes[3]) << 8))
            let frameLength = HeyCyanFrameCodec.headerLength + payloadLength
            guard buffer.count >= frameLength else { break }

            let rawFrame = Data(buffer.prefix(frameLength))
            do {
                events.append(.frame(try codec.decode(rawFrame)))
                buffer.removeFirst(frameLength)
            } catch let error as HeyCyanProtocolError {
                events.append(.malformed(rawData: rawFrame, error: error))
                // Do not discard the entire claimed frame after a checksum failure. A damaged
                // length or one missing BLE byte can otherwise consume the marker of the next
                // valid frame. Advancing one byte lets the normal marker scan recover at the
                // next CRC-valid boundary, matching the hardware-capture parser.
                buffer.removeFirst()
            } catch {
                // The codec currently throws only HeyCyanProtocolError. Preserve the raw bytes
                // and use a stable protocol error if that invariant changes in the future.
                events.append(
                    .malformed(
                        rawData: rawFrame,
                        error: .invalidLength(expected: frameLength, actual: rawFrame.count)
                    )
                )
                buffer.removeFirst()
            }
        }

        return events
    }

    mutating func reset() {
        buffer.removeAll(keepingCapacity: false)
    }
}

enum HeyCyanTransportChannel: String, Sendable {
    case base
    case largeData
}

enum HeyCyanNetworkMode: UInt8, Sendable {
    case peerToPeer = 0x01
    case accessPoint = 0x02
}

enum HeyCyanAIPhotoQuality: UInt8, CaseIterable, Sendable {
    case instant = 0
    case quick = 1
    case smooth = 2
    case fine = 3
    case clearer = 4
    case detailed = 5
}

struct HeyCyanVolumeLevel: Equatable, Sendable {
    let minimum: UInt8
    let maximum: UInt8
    let current: UInt8
}

struct HeyCyanVolumeProfile: Equatable, Sendable {
    let music: HeyCyanVolumeLevel
    let calls: HeyCyanVolumeLevel
    let system: HeyCyanVolumeLevel
    /// `1`, `2`, and `3` are the SDK's music, call, and system channel identifiers.
    /// Preserve an unknown value so a read-modify-write never silently changes device state.
    let activeChannelCode: UInt8
}

/// The nine-byte clock record built by the supplied Android SDK's `SyncTime` class.
///
/// The SDK advances the wall clock by one second before serializing it, uses BCD for the first
/// six fields, and encodes the current GMT offset as `((24 + hours) % 24) * 2 + 1`. Keeping this
/// behavior in one value type prevents time-zone or language guesses from leaking into callers.
struct HeyCyanTimeSynchronization: Equatable, Sendable {
    let date: Date
    let timeZoneSecondsFromGMT: Int
    let languageCode: UInt8
    let clockStatus: UInt8

    init(
        date: Date,
        timeZoneSecondsFromGMT: Int,
        languageCode: UInt8,
        clockStatus: UInt8 = 1
    ) {
        self.date = date
        self.timeZoneSecondsFromGMT = timeZoneSecondsFromGMT
        self.languageCode = languageCode
        self.clockStatus = clockStatus
    }

    static func current(
        referenceDate: Date = Date(),
        timeZone: TimeZone = .current,
        locale: Locale = .current
    ) -> HeyCyanTimeSynchronization {
        HeyCyanTimeSynchronization(
            date: referenceDate.addingTimeInterval(1),
            timeZoneSecondsFromGMT: timeZone.secondsFromGMT(for: referenceDate),
            languageCode: languageCode(for: locale)
        )
    }

    var payload: Data {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: timeZoneSecondsFromGMT) ?? .gmt
        let components = calendar.dateComponents(
            [.year, .month, .day, .hour, .minute, .second],
            from: date
        )
        let timeZoneHours = Double(timeZoneSecondsFromGMT) / 3_600
        let wrappedHours = (24 + timeZoneHours).truncatingRemainder(dividingBy: 24)
        let encodedTimeZone = UInt8(clamping: Int(wrappedHours * 2 + 1))

        return Data([
            Self.bcd((components.year ?? 2000) % 2000),
            Self.bcd(components.month ?? 1),
            Self.bcd(components.day ?? 1),
            Self.bcd(components.hour ?? 0),
            Self.bcd(components.minute ?? 0),
            Self.bcd(components.second ?? 0),
            languageCode,
            encodedTimeZone,
            clockStatus
        ])
    }

    private static func languageCode(for locale: Locale) -> UInt8 {
        let language = locale.language.languageCode?.identifier ?? "en"
        let key: String
        if language == "zh" {
            key = "zh_\(locale.region?.identifier.uppercased() ?? "CN")"
        } else {
            key = language
        }

        let codes: [String: UInt8] = [
            "zh_CN": 0, "en": 1, "zh_HK": 2, "zh_TW": 2, "el": 3,
            "fr": 4, "de": 5, "it": 6, "es": 7, "nl": 8, "pt": 9,
            "ru": 10, "tr": 11, "ja": 12, "ko": 13, "pl": 14, "ro": 15,
            "ar": 16, "th": 17, "vi": 18, "id": 19, "in": 19, "hi": 20,
            "cs": 21, "sk": 22, "hu": 23, "he": 24, "iw": 24, "hr": 25,
            "sl": 26
        ]
        return codes[key] ?? 1
    }

    private static func bcd(_ value: Int) -> UInt8 {
        UInt8(((value / 10) << 4) | (value % 10))
    }
}

/// Only commands supported by the supplied official-app/QCSDK/CyanBridge evidence belong here.
/// Response correlation and device work-state transitions remain intentionally separate.
enum HeyCyanCommand: Equatable, Sendable {
    case synchronizeTime(HeyCyanTimeSynchronization)
    case takePhoto
    case startVideoRecording
    case stopVideoRecording
    case startAudioRecording
    case stopAudioRecording
    case requestAIPhoto(quality: HeyCyanAIPhotoQuality)
    case prepareMediaTransfer(mode: HeyCyanNetworkMode)
    case finishMediaTransfer
    case resetPeerToPeerState
    case requestPictureThumbnail(index: UInt16)
    case synchronizeBattery
    case synchronizeDeviceInfo
    case openClassicBluetooth
    case readGlassesVoiceWake
    case setGlassesVoiceWake(Bool)
    case readVolumeControl
    case setVolumeControl(HeyCyanVolumeProfile)

    var family: UInt8 {
        switch self {
        case .synchronizeTime:
            return 0x40
        case .requestPictureThumbnail:
            return 0xFD
        case .synchronizeBattery:
            return 0x42
        case .synchronizeDeviceInfo:
            return 0x43
        case .openClassicBluetooth:
            return 0x49
        case .readGlassesVoiceWake, .setGlassesVoiceWake:
            return 0x44
        case .readVolumeControl, .setVolumeControl:
            return 0x51
        default:
            return 0x41
        }
    }

    var payload: Data {
        switch self {
        case .synchronizeTime(let synchronization):
            return synchronization.payload
        case .takePhoto:
            return Data([0x02, 0x01, 0x01])
        case .startVideoRecording:
            return Data([0x02, 0x01, 0x02])
        case .stopVideoRecording:
            return Data([0x02, 0x01, 0x03])
        case .startAudioRecording:
            return Data([0x02, 0x01, 0x08])
        case .stopAudioRecording:
            return Data([0x02, 0x01, 0x0C])
        case .requestAIPhoto(let quality):
            return Data([0x02, 0x01, 0x06, quality.rawValue, quality.rawValue])
        case .prepareMediaTransfer(let mode):
            return Data([0x02, 0x01, 0x04, mode.rawValue])
        case .finishMediaTransfer:
            return Data([0x02, 0x01, 0x09])
        case .resetPeerToPeerState:
            return Data([0x02, 0x01, 0x0F])
        case .requestPictureThumbnail(let index):
            return Data([
                0x01,
                UInt8(index & 0x00FF),
                UInt8((index >> 8) & 0x00FF)
            ])
        case .synchronizeBattery, .synchronizeDeviceInfo:
            return Data([0x00, 0x00])
        case .openClassicBluetooth:
            return Data([0x02, 0x01])
        case .readGlassesVoiceWake:
            return Data([0x01, 0x00])
        case .setGlassesVoiceWake(let enabled):
            return Data([0x02, enabled ? 0x01 : 0x00])
        case .readVolumeControl:
            return Data([0x01])
        case .setVolumeControl(let profile):
            return Data([
                0x02,
                0x01,
                profile.music.minimum,
                profile.music.maximum,
                profile.music.current,
                0x02,
                profile.calls.minimum,
                profile.calls.maximum,
                profile.calls.current,
                0x03,
                profile.system.minimum,
                profile.system.maximum,
                profile.system.current,
                profile.activeChannelCode
            ])
        }
    }

    var channel: HeyCyanTransportChannel { .largeData }

    /// Response matching is intentionally stricter than outer-family matching. Most control
    /// operations share family 0x41, and the glasses may emit unrelated 0x41 traffic while a
    /// request is pending. The captured acknowledgements echo data type 0x01 and the requested
    /// work type at payload offsets 1 and 2.
    func matchesResponse(_ frame: HeyCyanFrame) -> Bool {
        guard frame.command == family else { return false }

        switch self {
        case .synchronizeTime:
            return frame.payload.first == 0x00
        case .takePhoto:
            return frame.matchesControlAcknowledgement(workType: 0x01)
        case .startVideoRecording:
            return frame.matchesControlAcknowledgement(workType: 0x02)
        case .stopVideoRecording:
            return frame.matchesControlAcknowledgement(workType: 0x03)
        case .prepareMediaTransfer(let mode):
            return frame.matchesNetworkPreparation(mode: mode)
        case .requestAIPhoto:
            return frame.matchesControlAcknowledgement(workType: 0x06)
        case .startAudioRecording:
            return frame.matchesControlAcknowledgement(workType: 0x08)
        case .finishMediaTransfer:
            return frame.matchesControlAcknowledgement(workType: 0x09)
        case .stopAudioRecording:
            return frame.matchesControlAcknowledgement(workType: 0x0C)
        case .resetPeerToPeerState:
            return frame.matchesControlAcknowledgement(workType: 0x0F)
        case .requestPictureThumbnail, .synchronizeBattery, .synchronizeDeviceInfo:
            return true
        case .openClassicBluetooth:
            return frame.payload == payload
        case .readGlassesVoiceWake:
            return frame.payload.first == 0x01
        case .setGlassesVoiceWake:
            return frame.payload.first == 0x02
        case .readVolumeControl:
            return frame.payload.first == 0x01
        case .setVolumeControl:
            return frame.payload.first == 0x02
        }
    }
}

private extension HeyCyanFrame {
    func matchesControlAcknowledgement(workType: UInt8) -> Bool {
        payload.count >= 3 && payload[payload.startIndex + 1] == 0x01 &&
            payload[payload.startIndex + 2] == workType
    }

    func matchesNetworkPreparation(mode: HeyCyanNetworkMode) -> Bool {
        guard matchesControlAcknowledgement(workType: 0x04), payload.count >= 4 else {
            return false
        }
        // Successful preparation echoes the selected mode. The official failure form uses FF;
        // accept it for correlation so the response decoder can fail immediately instead of
        // turning a device rejection into a timeout.
        let responseMode = payload[payload.startIndex + 3]
        return responseMode == mode.rawValue || responseMode == 0xFF
    }
}

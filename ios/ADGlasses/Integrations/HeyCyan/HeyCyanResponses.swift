import Foundation

struct HeyCyanBatteryStatus: Equatable, Sendable {
    let level: Int
    let isCharging: Bool
}

struct HeyCyanDeviceInformation: Equatable, Sendable {
    /// The first response payload byte is present in the supplied SDK, but its meaning is not
    /// established. Preserve it for hardware comparison instead of assigning a guessed meaning.
    let responseCode: UInt8
    let bluetoothFirmwareVersion: String
    let bluetoothHardwareVersion: String
    let wifiFirmwareVersion: String
    let wifiHardwareVersion: String
}

struct HeyCyanControlAcknowledgement: Equatable, Sendable {
    let responseCode: UInt8
    let requestedWorkType: UInt8
    let errorCode: Int
    let activeWorkType: UInt8?
}

struct HeyCyanNetworkPreparation: Equatable, Sendable {
    let responseCode: UInt8
    let mode: HeyCyanNetworkMode
    let ssid: String
    let passphrase: String
}

struct HeyCyanThumbnailChunk: Equatable, Sendable {
    let responseCode: UInt8
    let totalChunks: UInt16
    let index: UInt16
    let imageData: Data
}

enum HeyCyanDeviceEvent: Equatable, Sendable {
    case battery(HeyCyanBatteryStatus)
    case aiPhotoReady
    case assistantListeningStarted
    case assistantListeningEnded
    case wifiAddress(String)
    case wifiError(Int)
    case unknown(type: UInt8, payload: Data)
}

enum HeyCyanResponseDecodingError: LocalizedError, Equatable, Sendable {
    case unexpectedCommand(expected: UInt8, actual: UInt8)
    case payloadTooShort(expectedAtLeast: Int, actual: Int)
    case invalidBatteryLevel(Int)
    case invalidChargingValue(UInt8)
    case invalidDeviceInformationLengths(expectedAtLeast: Int, actual: Int)
    case unexpectedControlDataType(UInt8)
    case unexpectedControlWorkType(expected: UInt8, actual: UInt8)
    case controlRejected(errorCode: Int)
    case invalidThumbnailSequence(total: UInt16, index: UInt16)
    case invalidVolumeChannelMarker(expected: UInt8, actual: UInt8)
    case invalidVolumeRange(channel: UInt8, minimum: Int, maximum: Int, current: Int)
    case networkPreparationRejected
    case unexpectedNetworkMode(expected: UInt8, actual: UInt8)
    case invalidNetworkCredentialLengths(expected: Int, actual: Int)
    case invalidNetworkCredentialEncoding
    case unexpectedVoiceWakeOperation(expected: UInt8, actual: UInt8)
    case invalidVoiceWakeValue(UInt8)

    var errorDescription: String? {
        switch self {
        case .unexpectedCommand(let expected, let actual):
            return String(
                format: "Expected AD Glasses response 0x%02X, received 0x%02X.",
                expected,
                actual
            )
        case .payloadTooShort(let expectedAtLeast, let actual):
            return "The AD Glasses response is too short (expected at least \(expectedAtLeast) bytes, received \(actual))."
        case .invalidBatteryLevel(let value):
            return "The glasses reported an invalid battery level (\(value))."
        case .invalidChargingValue(let value):
            return "The glasses reported an unknown charging value (\(value))."
        case .invalidDeviceInformationLengths(let expectedAtLeast, let actual):
            return "The AD Glasses device-information response requires at least \(expectedAtLeast) bytes, but contains \(actual)."
        case .unexpectedControlDataType(let value):
            return "The glasses returned control data type \(value) instead of a command acknowledgement."
        case .unexpectedControlWorkType(let expected, let actual):
            return String(
                format: "Expected glasses work type 0x%02X, received 0x%02X.",
                expected,
                actual
            )
        case .controlRejected(let errorCode):
            return "The glasses rejected the command (error \(errorCode))."
        case .invalidThumbnailSequence(let total, let index):
            return "The glasses returned an invalid thumbnail chunk index \(index) of \(total)."
        case .invalidVolumeChannelMarker(let expected, let actual):
            return String(
                format: "Expected glasses volume channel 0x%02X, received 0x%02X.",
                expected,
                actual
            )
        case .invalidVolumeRange(let channel, let minimum, let maximum, let current):
            return "The glasses returned an invalid volume range for channel \(channel): \(minimum)...\(maximum), current \(current)."
        case .networkPreparationRejected:
            return "The glasses rejected the requested media network mode."
        case .unexpectedNetworkMode(let expected, let actual):
            return String(
                format: "Expected glasses network mode 0x%02X, received 0x%02X.",
                expected,
                actual
            )
        case .invalidNetworkCredentialLengths(let expected, let actual):
            return "The glasses network response requires \(expected) bytes, but contains \(actual)."
        case .invalidNetworkCredentialEncoding:
            return "The glasses returned network credentials that are not valid UTF-8."
        case .unexpectedVoiceWakeOperation(let expected, let actual):
            return String(
                format: "Expected glasses voice-wake operation 0x%02X, received 0x%02X.",
                expected,
                actual
            )
        case .invalidVoiceWakeValue(let value):
            return "The glasses returned an invalid voice-wake value (\(value))."
        }
    }
}

/// Strict decoders translated from the supplied HeyCyan Android SDK response classes.
///
/// The Android decoders receive the complete application frame. `HeyCyanFrameCodec` has already
/// validated and removed its six-byte header, so the offsets below are the Android offsets minus
/// six. No response field is interpreted beyond behavior present in that SDK.
struct HeyCyanResponseDecoder: Sendable {
    static let deviceNotificationFamily: UInt8 = 0x73

    func decodeGlassesVoiceWake(
        _ frame: HeyCyanFrame,
        expectedOperation: UInt8
    ) throws -> Bool {
        guard frame.command == HeyCyanCommand.readGlassesVoiceWake.family else {
            throw HeyCyanResponseDecodingError.unexpectedCommand(
                expected: HeyCyanCommand.readGlassesVoiceWake.family,
                actual: frame.command
            )
        }
        guard frame.payload.count >= 2 else {
            throw HeyCyanResponseDecodingError.payloadTooShort(
                expectedAtLeast: 2,
                actual: frame.payload.count
            )
        }
        let bytes = [UInt8](frame.payload)
        guard bytes[0] == expectedOperation else {
            throw HeyCyanResponseDecodingError.unexpectedVoiceWakeOperation(
                expected: expectedOperation,
                actual: bytes[0]
            )
        }
        guard bytes[1] <= 1 else {
            throw HeyCyanResponseDecodingError.invalidVoiceWakeValue(bytes[1])
        }
        return bytes[1] == 1
    }

    func decodeControlAcknowledgement(
        _ frame: HeyCyanFrame,
        expectedWorkType: UInt8
    ) throws -> HeyCyanControlAcknowledgement {
        guard frame.command == HeyCyanCommand.takePhoto.family else {
            throw HeyCyanResponseDecodingError.unexpectedCommand(
                expected: HeyCyanCommand.takePhoto.family,
                actual: frame.command
            )
        }
        // Full-frame offsets in GlassModelControlResponse are:
        // dataType[7], workType[8], error[9], activeWorkType[10].
        guard frame.payload.count >= 4 else {
            throw HeyCyanResponseDecodingError.payloadTooShort(
                expectedAtLeast: 4,
                actual: frame.payload.count
            )
        }
        let bytes = [UInt8](frame.payload)
        guard bytes[1] == 1 else {
            throw HeyCyanResponseDecodingError.unexpectedControlDataType(bytes[1])
        }
        guard bytes[2] == expectedWorkType else {
            throw HeyCyanResponseDecodingError.unexpectedControlWorkType(
                expected: expectedWorkType,
                actual: bytes[2]
            )
        }

        // The official parser converts this byte to an unsigned value and special-cases 255 by
        // ending the acknowledgement without reading an active work type. Physical AM01 testing
        // confirms that photo and AI-photo requests execute while returning this 0xFF sentinel;
        // it must not be sign-extended to -1 or surfaced as a rejection.
        let errorCode = Int(bytes[3])
        guard errorCode == 0 || errorCode == 0xFF else {
            throw HeyCyanResponseDecodingError.controlRejected(errorCode: errorCode)
        }

        return HeyCyanControlAcknowledgement(
            responseCode: bytes[0],
            requestedWorkType: bytes[2],
            errorCode: errorCode,
            activeWorkType: errorCode == 0 && bytes.count > 4 ? bytes[4] : nil
        )
    }

    func decodeBattery(_ frame: HeyCyanFrame) throws -> HeyCyanBatteryStatus {
        guard frame.command == HeyCyanCommand.synchronizeBattery.family else {
            throw HeyCyanResponseDecodingError.unexpectedCommand(
                expected: HeyCyanCommand.synchronizeBattery.family,
                actual: frame.command
            )
        }
        guard frame.payload.count >= 2 else {
            throw HeyCyanResponseDecodingError.payloadTooShort(
                expectedAtLeast: 2,
                actual: frame.payload.count
            )
        }

        let level = Int(frame.payload[frame.payload.startIndex])
        guard (0 ... 100).contains(level) else {
            throw HeyCyanResponseDecodingError.invalidBatteryLevel(level)
        }

        let chargingValue = frame.payload[frame.payload.index(after: frame.payload.startIndex)]
        guard chargingValue == 0 || chargingValue == 1 else {
            throw HeyCyanResponseDecodingError.invalidChargingValue(chargingValue)
        }
        return HeyCyanBatteryStatus(level: level, isCharging: chargingValue == 1)
    }

    func decodeNetworkPreparation(
        _ frame: HeyCyanFrame,
        expectedMode: HeyCyanNetworkMode
    ) throws -> HeyCyanNetworkPreparation {
        guard frame.command == HeyCyanCommand.prepareMediaTransfer(mode: expectedMode).family else {
            throw HeyCyanResponseDecodingError.unexpectedCommand(
                expected: HeyCyanCommand.prepareMediaTransfer(mode: expectedMode).family,
                actual: frame.command
            )
        }
        // Captured payload:
        // response, data-type=1, work-type=4, mode, SSID length LE, password length LE, strings.
        guard frame.payload.count >= 8 else {
            throw HeyCyanResponseDecodingError.payloadTooShort(
                expectedAtLeast: 8,
                actual: frame.payload.count
            )
        }
        let bytes = [UInt8](frame.payload)
        guard bytes[1] == 0x01 else {
            throw HeyCyanResponseDecodingError.unexpectedControlDataType(bytes[1])
        }
        guard bytes[2] == 0x04 else {
            throw HeyCyanResponseDecodingError.unexpectedControlWorkType(
                expected: 0x04,
                actual: bytes[2]
            )
        }
        guard bytes[3] != 0xFF else {
            throw HeyCyanResponseDecodingError.networkPreparationRejected
        }
        guard let actualMode = HeyCyanNetworkMode(rawValue: bytes[3]) else {
            throw HeyCyanResponseDecodingError.unexpectedNetworkMode(
                expected: expectedMode.rawValue,
                actual: bytes[3]
            )
        }

        let ssidLength = Int(UInt16(bytes[4]) | (UInt16(bytes[5]) << 8))
        let passphraseLength = Int(UInt16(bytes[6]) | (UInt16(bytes[7]) << 8))
        let requiredLength = 8 + ssidLength + passphraseLength
        guard ssidLength > 0, passphraseLength > 0, frame.payload.count >= requiredLength else {
            throw HeyCyanResponseDecodingError.invalidNetworkCredentialLengths(
                expected: requiredLength,
                actual: frame.payload.count
            )
        }

        let ssidRange = 8 ..< 8 + ssidLength
        let passphraseRange = ssidRange.upperBound ..< requiredLength
        guard let ssid = String(bytes: bytes[ssidRange], encoding: .utf8),
              let passphrase = String(bytes: bytes[passphraseRange], encoding: .utf8) else {
            throw HeyCyanResponseDecodingError.invalidNetworkCredentialEncoding
        }
        return HeyCyanNetworkPreparation(
            responseCode: bytes[0],
            mode: actualMode,
            ssid: ssid,
            passphrase: passphrase
        )
    }

    func decodeDeviceEvent(_ frame: HeyCyanFrame) throws -> HeyCyanDeviceEvent {
        guard frame.command == Self.deviceNotificationFamily else {
            throw HeyCyanResponseDecodingError.unexpectedCommand(
                expected: Self.deviceNotificationFamily,
                actual: frame.command
            )
        }
        guard let type = frame.payload.first else {
            throw HeyCyanResponseDecodingError.payloadTooShort(
                expectedAtLeast: 1,
                actual: frame.payload.count
            )
        }
        let bytes = [UInt8](frame.payload)

        switch type {
        case 0x02:
            return .aiPhotoReady
        case 0x03 where bytes.count >= 2 && bytes[1] == 0x01:
            // Observed immediately before family 0x59 Opus audio begins. The same event is
            // reached after the on-glasses "Hey Cyan" detector and the rear assistant button;
            // the captured notification does not include which activation source was used.
            return .assistantListeningStarted
        case 0x05:
            guard bytes.count >= 3 else {
                throw HeyCyanResponseDecodingError.payloadTooShort(
                    expectedAtLeast: 3,
                    actual: bytes.count
                )
            }
            let level = Int(bytes[1])
            guard (0 ... 100).contains(level) else {
                throw HeyCyanResponseDecodingError.invalidBatteryLevel(level)
            }
            guard bytes[2] == 0 || bytes[2] == 1 else {
                throw HeyCyanResponseDecodingError.invalidChargingValue(bytes[2])
            }
            return .battery(
                HeyCyanBatteryStatus(level: level, isCharging: bytes[2] == 1)
            )
        case 0x08:
            guard bytes.count >= 5 else {
                throw HeyCyanResponseDecodingError.payloadTooShort(
                    expectedAtLeast: 5,
                    actual: bytes.count
                )
            }
            return .wifiAddress(bytes[1 ... 4].map { String($0) }.joined(separator: "."))
        case 0x09:
            guard bytes.count >= 2 else {
                throw HeyCyanResponseDecodingError.payloadTooShort(
                    expectedAtLeast: 2,
                    actual: bytes.count
                )
            }
            return .wifiError(Int(bytes[1]))
        case 0x0A where bytes.count >= 2 && bytes[1] == 0x01:
            // Official-app code names this device-recognition stop, and every captured voice
            // stream terminates with it.
            return .assistantListeningEnded
        default:
            return .unknown(type: type, payload: frame.payload)
        }
    }

    func decodeDeviceInformation(_ frame: HeyCyanFrame) throws -> HeyCyanDeviceInformation {
        guard frame.command == HeyCyanCommand.synchronizeDeviceInfo.family else {
            throw HeyCyanResponseDecodingError.unexpectedCommand(
                expected: HeyCyanCommand.synchronizeDeviceInfo.family,
                actual: frame.command
            )
        }

        // Response byte + four little-endian UInt16 string lengths.
        let fixedPayloadLength = 9
        guard frame.payload.count >= fixedPayloadLength else {
            throw HeyCyanResponseDecodingError.payloadTooShort(
                expectedAtLeast: fixedPayloadLength,
                actual: frame.payload.count
            )
        }

        let bytes = [UInt8](frame.payload)
        let lengths = stride(from: 1, through: 7, by: 2).map { offset in
            Int(UInt16(bytes[offset]) | (UInt16(bytes[offset + 1]) << 8))
        }
        let requiredLength = fixedPayloadLength + lengths.reduce(0, +)
        guard frame.payload.count >= requiredLength else {
            throw HeyCyanResponseDecodingError.invalidDeviceInformationLengths(
                expectedAtLeast: requiredLength,
                actual: frame.payload.count
            )
        }

        var offset = fixedPayloadLength
        func readString(length: Int) -> String {
            defer { offset += length }
            return String(decoding: bytes[offset ..< offset + length], as: UTF8.self)
        }

        return HeyCyanDeviceInformation(
            responseCode: bytes[0],
            bluetoothFirmwareVersion: readString(length: lengths[0]),
            bluetoothHardwareVersion: readString(length: lengths[1]),
            wifiFirmwareVersion: readString(length: lengths[2]),
            wifiHardwareVersion: readString(length: lengths[3])
        )
    }

    func decodeVolumeControl(_ frame: HeyCyanFrame) throws -> HeyCyanVolumeProfile {
        guard frame.command == HeyCyanCommand.readVolumeControl.family else {
            throw HeyCyanResponseDecodingError.unexpectedCommand(
                expected: HeyCyanCommand.readVolumeControl.family,
                actual: frame.command
            )
        }

        // VolumeControlResponse reads full-frame offsets 8...19. The outer six-byte frame has
        // already been removed, so these are payload offsets 2...13. Captured reads and writes
        // use fixed channel markers 1 (music), 2 (call), and 3 (system).
        guard frame.payload.count >= 14 else {
            throw HeyCyanResponseDecodingError.payloadTooShort(
                expectedAtLeast: 14,
                actual: frame.payload.count
            )
        }
        let bytes = [UInt8](frame.payload)
        for (offset, expected) in [(1, UInt8(0x01)), (5, UInt8(0x02)), (9, UInt8(0x03))] {
            guard bytes[offset] == expected else {
                throw HeyCyanResponseDecodingError.invalidVolumeChannelMarker(
                    expected: expected,
                    actual: bytes[offset]
                )
            }
        }

        func level(at offset: Int, channel: UInt8) throws -> HeyCyanVolumeLevel {
            let minimum = Int(bytes[offset])
            let maximum = Int(bytes[offset + 1])
            let current = Int(bytes[offset + 2])
            guard minimum <= current, current <= maximum else {
                throw HeyCyanResponseDecodingError.invalidVolumeRange(
                    channel: channel,
                    minimum: minimum,
                    maximum: maximum,
                    current: current
                )
            }
            return HeyCyanVolumeLevel(
                minimum: bytes[offset],
                maximum: bytes[offset + 1],
                current: bytes[offset + 2]
            )
        }

        return try HeyCyanVolumeProfile(
            music: level(at: 2, channel: 1),
            calls: level(at: 6, channel: 2),
            system: level(at: 10, channel: 3),
            activeChannelCode: bytes[13]
        )
    }

    func decodeThumbnailChunk(_ frame: HeyCyanFrame) throws -> HeyCyanThumbnailChunk {
        let expectedFamily = HeyCyanCommand.requestPictureThumbnail(index: 0).family
        guard frame.command == expectedFamily else {
            throw HeyCyanResponseDecodingError.unexpectedCommand(
                expected: expectedFamily,
                actual: frame.command
            )
        }
        // Full-frame offsets in the supplied SDK handler are total[7...8], index[9...10],
        // image bytes[11...]. `HeyCyanFrame` has already removed the six-byte frame header.
        guard frame.payload.count >= 5 else {
            throw HeyCyanResponseDecodingError.payloadTooShort(
                expectedAtLeast: 5,
                actual: frame.payload.count
            )
        }
        let bytes = [UInt8](frame.payload)
        let total = UInt16(bytes[1]) | (UInt16(bytes[2]) << 8)
        let index = UInt16(bytes[3]) | (UInt16(bytes[4]) << 8)
        guard total > 0, index < total else {
            throw HeyCyanResponseDecodingError.invalidThumbnailSequence(
                total: total,
                index: index
            )
        }
        return HeyCyanThumbnailChunk(
            responseCode: bytes[0],
            totalChunks: total,
            index: index,
            imageData: Data(bytes.dropFirst(5))
        )
    }
}

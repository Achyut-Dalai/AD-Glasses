import XCTest
@testable import ADGlasses

final class HeyCyanResponseTests: XCTestCase {
    private let codec = HeyCyanFrameCodec.production
    private let decoder = HeyCyanResponseDecoder()

    func testBatteryResponseUsesConfirmedPayloadOffsets() throws {
        let frame = try decodedFrame(command: 0x42, payload: Data([85, 1]))

        XCTAssertEqual(
            try decoder.decodeBattery(frame),
            HeyCyanBatteryStatus(level: 85, isCharging: true)
        )
    }

    func testControlAcknowledgementUsesConfirmedOffsets() throws {
        let frame = try decodedFrame(
            command: 0x41,
            payload: Data([0x02, 0x01, 0x01, 0x00, 0x01])
        )

        XCTAssertEqual(
            try decoder.decodeControlAcknowledgement(frame, expectedWorkType: 0x01),
            HeyCyanControlAcknowledgement(
                responseCode: 0x02,
                requestedWorkType: 0x01,
                errorCode: 0,
                activeWorkType: 0x01
            )
        )
    }

    func testControlAcknowledgementPreservesSignedErrorCode() throws {
        let frame = try decodedFrame(
            command: 0x41,
            payload: Data([0x02, 0x01, 0x04, 0xFF])
        )

        XCTAssertThrowsError(
            try decoder.decodeControlAcknowledgement(frame, expectedWorkType: 0x04)
        ) { error in
            XCTAssertEqual(
                error as? HeyCyanResponseDecodingError,
                .controlRejected(errorCode: -1)
            )
        }
    }

    func testCapturedNetworkPreparationShapeDecodesCredentialLengthsAndStrings() throws {
        // Same 22-byte SSID / 9-byte passphrase layout as the physical capture, using synthetic
        // values so a real accessory credential is never committed to source control.
        let ssid = "Test-Glasses-Network01"
        let passphrase = "testpass9"
        var payload = Data([0x02, 0x01, 0x04, 0x01, 0x16, 0x00, 0x09, 0x00])
        payload.append(contentsOf: ssid.utf8)
        payload.append(contentsOf: passphrase.utf8)

        XCTAssertEqual(
            try decoder.decodeNetworkPreparation(
                decodedFrame(command: 0x41, payload: payload),
                expectedMode: .peerToPeer
            ),
            HeyCyanNetworkPreparation(
                responseCode: 0x02,
                mode: .peerToPeer,
                ssid: ssid,
                passphrase: passphrase
            )
        )
    }

    func testNetworkPreparationRejectsFailureFormWithoutTreatingItAsCredentials() throws {
        let frame = try decodedFrame(
            command: 0x41,
            payload: Data([0x02, 0x01, 0x04, 0xFF, 0x00, 0x00, 0x00, 0x00])
        )

        XCTAssertThrowsError(
            try decoder.decodeNetworkPreparation(frame, expectedMode: .accessPoint)
        ) { error in
            XCTAssertEqual(
                error as? HeyCyanResponseDecodingError,
                .networkPreparationRejected
            )
        }
    }

    func testBatteryResponseRejectsUnknownChargingValue() throws {
        let frame = try decodedFrame(command: 0x42, payload: Data([50, 2]))

        XCTAssertThrowsError(try decoder.decodeBattery(frame)) { error in
            XCTAssertEqual(
                error as? HeyCyanResponseDecodingError,
                .invalidChargingValue(2)
            )
        }
    }

    func testDeviceInformationUsesLittleEndianLengthsAndUTF8Strings() throws {
        let values = ["BT-FW", "BT-HW", "WIFI-FW", "WIFI-HW"]
        var payload = Data([0x7A])
        for value in values {
            let length = UInt16(value.utf8.count)
            payload.append(UInt8(length & 0x00FF))
            payload.append(UInt8((length >> 8) & 0x00FF))
        }
        for value in values {
            payload.append(contentsOf: value.utf8)
        }

        let response = try decoder.decodeDeviceInformation(
            decodedFrame(command: 0x43, payload: payload)
        )

        XCTAssertEqual(response.responseCode, 0x7A)
        XCTAssertEqual(response.bluetoothFirmwareVersion, "BT-FW")
        XCTAssertEqual(response.bluetoothHardwareVersion, "BT-HW")
        XCTAssertEqual(response.wifiFirmwareVersion, "WIFI-FW")
        XCTAssertEqual(response.wifiHardwareVersion, "WIFI-HW")
    }

    func testDeviceEventDecodesBatteryAndWifiAddressAtConfirmedOffsets() throws {
        XCTAssertEqual(
            try decoder.decodeDeviceEvent(
                decodedFrame(command: 0x73, payload: Data([0x05, 64, 1]))
            ),
            .battery(HeyCyanBatteryStatus(level: 64, isCharging: true))
        )
        XCTAssertEqual(
            try decoder.decodeDeviceEvent(
                decodedFrame(command: 0x73, payload: Data([0x08, 192, 168, 49, 2]))
            ),
            .wifiAddress("192.168.49.2")
        )
    }

    func testCapturedAssistantLifecycleEventsDecodeWithoutGuessingActivationSource() throws {
        XCTAssertEqual(
            try decoder.decodeDeviceEvent(
                decodedFrame(command: 0x73, payload: Data([0x03, 0x01]))
            ),
            .assistantListeningStarted
        )
        XCTAssertEqual(
            try decoder.decodeDeviceEvent(
                decodedFrame(command: 0x73, payload: Data([0x0A, 0x01]))
            ),
            .assistantListeningEnded
        )
    }

    func testDeviceEventPreservesUnknownSubtypes() throws {
        let payload = Data([0x7F, 0xAA, 0xBB])
        XCTAssertEqual(
            try decoder.decodeDeviceEvent(decodedFrame(command: 0x73, payload: payload)),
            .unknown(type: 0x7F, payload: payload)
        )
    }

    func testDeviceInformationRejectsLengthsBeyondPayload() throws {
        // First string claims 16 bytes; no string data follows the fixed fields.
        let payload = Data([0x00, 0x10, 0x00, 0, 0, 0, 0, 0, 0])
        let frame = try decodedFrame(command: 0x43, payload: payload)

        XCTAssertThrowsError(try decoder.decodeDeviceInformation(frame)) { error in
            XCTAssertEqual(
                error as? HeyCyanResponseDecodingError,
                .invalidDeviceInformationLengths(expectedAtLeast: 25, actual: 9)
            )
        }
    }

    func testThumbnailChunkUsesConfirmedLittleEndianOffsets() throws {
        let frame = try decodedFrame(
            command: 0xFD,
            payload: Data([0x01, 0x03, 0x00, 0x01, 0x00, 0xFF, 0xD8])
        )

        XCTAssertEqual(
            try decoder.decodeThumbnailChunk(frame),
            HeyCyanThumbnailChunk(
                responseCode: 0x01,
                totalChunks: 3,
                index: 1,
                imageData: Data([0xFF, 0xD8])
            )
        )
    }

    func testVolumeResponseUsesCapturedSDKOffsets() throws {
        let frame = try decodedFrame(
            command: 0x51,
            payload: Data([
                0x01, 0x01,
                0x00, 0x10, 0x0B,
                0x02, 0x00, 0x0F, 0x0F,
                0x03, 0x00, 0x10, 0x0A,
                0x03, 0, 0, 0, 0, 0, 0
            ])
        )

        XCTAssertEqual(
            try decoder.decodeVolumeControl(frame),
            HeyCyanVolumeProfile(
                music: HeyCyanVolumeLevel(minimum: 0, maximum: 16, current: 11),
                calls: HeyCyanVolumeLevel(minimum: 0, maximum: 15, current: 15),
                system: HeyCyanVolumeLevel(minimum: 0, maximum: 16, current: 10),
                activeChannelCode: 3
            )
        )
    }

    private func decodedFrame(command: UInt8, payload: Data) throws -> HeyCyanFrame {
        try codec.decode(codec.encode(command: command, payload: payload))
    }
}

final class HeyCyanDiagnosticsTests: XCTestCase {
    func testProtocolTraceIsCompletelyOptIn() async throws {
        let suiteName = "ADGlasses-DiagnosticsTests-\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defaults.removePersistentDomain(forName: suiteName)
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(suiteName, isDirectory: true)
        addTeardownBlock {
            UserDefaults(suiteName: suiteName)?.removePersistentDomain(forName: suiteName)
            try? FileManager.default.removeItem(at: directory)
        }
        let recorder = HeyCyanDiagnosticRecorder(
            directoryURL: directory,
            defaults: defaults
        )

        await recorder.recordState("connected")
        let exportURL = try await recorder.exportURL()
        XCTAssertEqual(try Data(contentsOf: exportURL).count, 0)

        await recorder.setPacketCaptureEnabled(true)
        await recorder.recordState("connected")
        XCTAssertGreaterThan(try Data(contentsOf: exportURL).count, 0)
    }
}

final class HeyCyanWiFiValidationTests: XCTestCase {
    func testAccessPointAcceptsOnlyPrivateUnicastIPv4Addresses() throws {
        XCTAssertEqual(
            try HeyCyanAccessPoint(
                ssid: "Glasses",
                passphrase: "password",
                deviceIPv4Address: " 192.168.49.2 "
            ).deviceIPv4Address,
            "192.168.49.2"
        )
        XCTAssertNoThrow(
            try HeyCyanAccessPoint(
                ssid: "Glasses",
                passphrase: "password",
                deviceIPv4Address: "10.0.0.7"
            )
        )
        XCTAssertNoThrow(
            try HeyCyanAccessPoint(
                ssid: "Glasses",
                passphrase: "password",
                deviceIPv4Address: "172.31.4.8"
            )
        )

        for address in ["8.8.8.8", "192.168.49.0", "192.168.49.255", "192.168.49.2.evil"] {
            XCTAssertThrowsError(
                try HeyCyanAccessPoint(
                    ssid: "Glasses",
                    passphrase: "password",
                    deviceIPv4Address: address
                ),
                "Expected \(address) to be rejected"
            )
        }
    }
}

final class HeyCyanOpusDecoderTests: XCTestCase {
    @MainActor
    func testCapturedFortyBytePacketDecodesWithNativeAppleOpusConverter() throws {
        // Captured padded-silence packet from the physical AM01 family 0x59 stream.
        let packet = Data([
            0x4B, 0x41, 0x1E, 0x0B, 0xE4, 0xC1, 0x22, 0x23, 0x61, 0xF0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        ])
        let decoder = try HeyCyanOpusDecoder()
        let pcm = try decoder.decode(packet)

        XCTAssertEqual(pcm.format.sampleRate, 16_000)
        XCTAssertEqual(pcm.format.channelCount, 1)
        // AudioConverter trims codec startup samples from the first packet (280 in this run).
        XCTAssertGreaterThan(pcm.frameLength, 0)
        XCTAssertLessThanOrEqual(pcm.frameLength, 320)
    }

    @MainActor
    func testDecoderRejectsAnythingOtherThanCompleteCapturedPacketSize() throws {
        let decoder = try HeyCyanOpusDecoder()

        XCTAssertThrowsError(try decoder.decode(Data(repeating: 0, count: 39))) { error in
            XCTAssertEqual(
                error as? HeyCyanOpusDecodingError,
                .invalidPacketSize(expected: 40, actual: 39)
            )
        }
    }
}

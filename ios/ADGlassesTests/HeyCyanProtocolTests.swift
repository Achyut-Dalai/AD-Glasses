import XCTest
@testable import ADGlasses

final class HeyCyanProtocolTests: XCTestCase {
    private let codec = HeyCyanFrameCodec.production

    func testOfficialPhotoVector() throws {
        let frame = try codec.encode(command: 0x41, payload: Data([0x02, 0x01, 0x01]))
        XCTAssertEqual(frame, Data([0xBC, 0x41, 0x03, 0x00, 0x10, 0x50, 0x02, 0x01, 0x01]))
    }

    func testOfficialVideoVector() throws {
        let frame = try codec.encode(command: 0x41, payload: Data([0x02, 0x01, 0x02]))
        XCTAssertEqual(frame, Data([0xBC, 0x41, 0x03, 0x00, 0x50, 0x51, 0x02, 0x01, 0x02]))
    }

    func testOfficialAudioRecordingVector() throws {
        let frame = try codec.encode(command: 0x41, payload: Data([0x02, 0x01, 0x08]))
        XCTAssertEqual(frame, Data([0xBC, 0x41, 0x03, 0x00, 0xD0, 0x56, 0x02, 0x01, 0x08]))
    }

    func testOfficialAccessPointTransferVector() throws {
        let frame = try codec.encode(command: 0x41, payload: Data([0x02, 0x01, 0x04, 0x02]))
        XCTAssertEqual(
            frame,
            Data([0xBC, 0x41, 0x04, 0x00, 0xD3, 0x5D, 0x02, 0x01, 0x04, 0x02])
        )
    }

    func testCapturedClassicBluetoothConnectionVector() throws {
        let command = HeyCyanCommand.openClassicBluetooth
        XCTAssertEqual(command.family, 0x49)
        XCTAssertEqual(command.payload, Data([0x02, 0x01]))
        XCTAssertEqual(
            try codec.encode(command: command.family, payload: command.payload),
            Data([0xBC, 0x49, 0x02, 0x00, 0xC1, 0x10, 0x02, 0x01])
        )
    }

    func testVerifiedGlassesVoiceWakeReadAndWritePayloads() throws {
        XCTAssertEqual(HeyCyanCommand.readGlassesVoiceWake.family, 0x44)
        XCTAssertEqual(HeyCyanCommand.readGlassesVoiceWake.payload, Data([0x01, 0x00]))
        XCTAssertEqual(HeyCyanCommand.setGlassesVoiceWake(false).family, 0x44)
        XCTAssertEqual(HeyCyanCommand.setGlassesVoiceWake(false).payload, Data([0x02, 0x00]))
        XCTAssertEqual(HeyCyanCommand.setGlassesVoiceWake(true).payload, Data([0x02, 0x01]))

        let readResponse = try codec.decode(
            codec.encode(command: 0x44, payload: Data([0x01, 0x01]))
        )
        let writeResponse = try codec.decode(
            codec.encode(command: 0x44, payload: Data([0x02, 0x00]))
        )
        XCTAssertTrue(HeyCyanCommand.readGlassesVoiceWake.matchesResponse(readResponse))
        XCTAssertFalse(HeyCyanCommand.readGlassesVoiceWake.matchesResponse(writeResponse))
        XCTAssertTrue(HeyCyanCommand.setGlassesVoiceWake(false).matchesResponse(writeResponse))
    }

    func testBatteryRequestVector() throws {
        let frame = try codec.encode(command: 0x42, payload: Data([0x00, 0x00]))
        XCTAssertEqual(frame, Data([0xBC, 0x42, 0x02, 0x00, 0x01, 0xB0, 0x00, 0x00]))
    }

    func testCapturedIndiaClockSynchronizationVector() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try XCTUnwrap(TimeZone(secondsFromGMT: 19_800))
        let date = try XCTUnwrap(calendar.date(from: DateComponents(
            year: 2026,
            month: 8,
            day: 29,
            hour: 23,
            minute: 44,
            second: 3
        )))
        let synchronization = HeyCyanTimeSynchronization(
            date: date,
            timeZoneSecondsFromGMT: 19_800,
            languageCode: 1
        )
        let command = HeyCyanCommand.synchronizeTime(synchronization)

        XCTAssertEqual(
            command.payload,
            Data([0x26, 0x08, 0x29, 0x23, 0x44, 0x03, 0x01, 0x0C, 0x01])
        )
        XCTAssertEqual(
            try codec.encode(command: command.family, payload: command.payload),
            Data([
                0xBC, 0x40, 0x09, 0x00, 0x63, 0xED,
                0x26, 0x08, 0x29, 0x23, 0x44, 0x03, 0x01, 0x0C, 0x01
            ])
        )
    }

    func testOfficialThumbnailChunkRequestPayload() {
        let command = HeyCyanCommand.requestPictureThumbnail(index: 0x1234)
        XCTAssertEqual(command.family, 0xFD)
        XCTAssertEqual(command.payload, Data([0x01, 0x34, 0x12]))
    }

    func testEmptyPayloadUsesProductionFFFFSpecialCase() throws {
        let frame = try codec.encode(command: 0x45)
        XCTAssertEqual(frame, Data([0xBC, 0x45, 0x00, 0x00, 0xFF, 0xFF]))
        XCTAssertEqual(try codec.decode(frame).payload, Data())
    }

    func testDecodeRejectsChecksumMismatch() throws {
        var frame = try codec.encode(command: 0x41, payload: Data([0x02, 0x01, 0x01]))
        frame[4] ^= 0x01

        XCTAssertThrowsError(try codec.decode(frame)) { error in
            guard case HeyCyanProtocolError.checksumMismatch = error else {
                return XCTFail("Expected checksumMismatch, got \(error)")
            }
        }
    }

    func testStreamDecoderReassemblesEveryByteBoundary() throws {
        let expectedFrame = try codec.encode(
            command: 0x41,
            payload: Data([0x02, 0x01, 0x04, 0x02])
        )
        var decoder = HeyCyanFrameStreamDecoder(codec: codec)
        var decodedFrames = [HeyCyanFrame]()

        for byte in expectedFrame {
            for event in decoder.append(Data([byte])) {
                if case .frame(let frame) = event {
                    decodedFrames.append(frame)
                }
            }
        }

        XCTAssertEqual(decodedFrames.count, 1)
        XCTAssertEqual(decodedFrames.first?.rawData, expectedFrame)
        XCTAssertTrue(decoder.buffer.isEmpty)
    }

    func testStreamDecoderHandlesNoiseAndBackToBackFrames() throws {
        let photo = try codec.encode(command: 0x41, payload: Data([0x02, 0x01, 0x01]))
        let battery = try codec.encode(command: 0x42, payload: Data([0x00, 0x00]))
        var input = Data([0x00, 0x7F])
        input.append(photo)
        input.append(battery)

        var decoder = HeyCyanFrameStreamDecoder(codec: codec)
        let events = decoder.append(input)
        let frames = events.compactMap { event -> HeyCyanFrame? in
            guard case .frame(let frame) = event else { return nil }
            return frame
        }
        let discarded = events.compactMap { event -> Data? in
            guard case .discarded(let data) = event else { return nil }
            return data
        }

        XCTAssertEqual(discarded, [Data([0x00, 0x7F])])
        XCTAssertEqual(frames.map(\.command), [0x41, 0x42])
        XCTAssertTrue(decoder.buffer.isEmpty)
    }

    func testStreamDecoderRecoversAfterCorruptedFrameWithoutEatingNextFrame() throws {
        var corrupted = try codec.encode(
            command: 0x41,
            payload: Data([0x02, 0x01, 0x01])
        )
        corrupted[4] ^= 0x01
        let battery = try codec.encode(command: 0x42, payload: Data([77, 0]))
        corrupted.append(battery)

        var decoder = HeyCyanFrameStreamDecoder(codec: codec)
        let events = decoder.append(corrupted)

        XCTAssertTrue(events.contains { event in
            if case .malformed(_, .checksumMismatch(_, _)) = event { return true }
            return false
        })
        XCTAssertTrue(events.contains { event in
            guard case .frame(let frame) = event else { return false }
            return frame.command == 0x42 && frame.payload == Data([77, 0])
        })
        XCTAssertTrue(decoder.buffer.isEmpty)
    }

    func testCommandPayloadsStayTyped() {
        XCTAssertEqual(
            HeyCyanCommand.requestAIPhoto(quality: .detailed).payload,
            Data([0x02, 0x01, 0x06, 0x05, 0x05])
        )
        XCTAssertEqual(
            HeyCyanCommand.prepareMediaTransfer(mode: .accessPoint).payload,
            Data([0x02, 0x01, 0x04, 0x02])
        )
    }

    func testMediaPreparationCorrelatesDocumentedReturnedModeOrFailure() throws {
        let command = HeyCyanCommand.prepareMediaTransfer(mode: .accessPoint)
        let peerToPeer = try codec.decode(codec.encode(
            command: 0x41,
            payload: Data([0x02, 0x01, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00])
        ))
        let accessPoint = try codec.decode(codec.encode(
            command: 0x41,
            payload: Data([0x02, 0x01, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00])
        ))
        let rejected = try codec.decode(codec.encode(
            command: 0x41,
            payload: Data([0x02, 0x01, 0x04, 0xFF, 0x04])
        ))

        XCTAssertTrue(command.matchesResponse(peerToPeer))
        XCTAssertTrue(command.matchesResponse(accessPoint))
        XCTAssertTrue(command.matchesResponse(rejected))
    }

    func testSharedControlFamilyMatchesOnlyTheRequestedWorkType() throws {
        let photo = HeyCyanCommand.takePhoto
        let videoResponse = try codec.decode(
            codec.encode(command: 0x41, payload: Data([0x02, 0x01, 0x02, 0x00]))
        )
        let photoResponse = try codec.decode(
            codec.encode(command: 0x41, payload: Data([0x02, 0x01, 0x01, 0x00]))
        )

        XCTAssertFalse(photo.matchesResponse(videoResponse))
        XCTAssertTrue(photo.matchesResponse(photoResponse))
    }

    func testCapturedVolumeReadAndWriteVectors() throws {
        XCTAssertEqual(HeyCyanCommand.readVolumeControl.payload, Data([0x01]))
        XCTAssertEqual(
            try codec.encode(
                command: HeyCyanCommand.readVolumeControl.family,
                payload: HeyCyanCommand.readVolumeControl.payload
            ),
            Data([0xBC, 0x51, 0x01, 0x00, 0x7E, 0x80, 0x01])
        )

        let profile = HeyCyanVolumeProfile(
            music: HeyCyanVolumeLevel(minimum: 0, maximum: 16, current: 12),
            calls: HeyCyanVolumeLevel(minimum: 0, maximum: 15, current: 15),
            system: HeyCyanVolumeLevel(minimum: 0, maximum: 16, current: 10),
            activeChannelCode: 3
        )
        XCTAssertEqual(
            HeyCyanCommand.setVolumeControl(profile).payload,
            Data([0x02, 0x01, 0x00, 0x10, 0x0C, 0x02, 0x00, 0x0F, 0x0F, 0x03, 0x00, 0x10, 0x0A, 0x03])
        )
    }
}

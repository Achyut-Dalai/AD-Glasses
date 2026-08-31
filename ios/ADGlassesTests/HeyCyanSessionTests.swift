import CoreBluetooth
import XCTest
@testable import ADGlasses

@MainActor
final class HeyCyanSessionTests: XCTestCase {
    func testPhysicalAM01AdvertisementIsAdmittedByVerifiedNameFamily() {
        XCTAssertTrue(
            HeyCyanBLETransport.matchesSupportedAdvertisement(
                localName: "JS-01 Pro_B0B1",
                peripheralName: nil,
                advertisedServices: nil
            )
        )
    }

    func testVerifiedServiceAdvertisementIsAdmittedWithoutName() {
        XCTAssertTrue(
            HeyCyanBLETransport.matchesSupportedAdvertisement(
                localName: nil,
                peripheralName: nil,
                advertisedServices: [HeyCyanBLETransport.GATT.baseService]
            )
        )
    }

    func testUnrelatedAdvertisementIsNotAdmitted() {
        XCTAssertFalse(
            HeyCyanBLETransport.matchesSupportedAdvertisement(
                localName: "Living Room Speaker",
                peripheralName: nil,
                advertisedServices: nil
            )
        )
    }

    func testRequestCompletesOnlyAfterMatchingFamilyResponse() async throws {
        let transport = FakeHeyCyanByteTransport(state: .ready(name: "Test glasses"))
        let session = HeyCyanSession(
            transport: transport,
            responseTimeout: .seconds(1)
        )

        let request = Task { try await session.send(.takePhoto) }
        await Task.yield()

        XCTAssertEqual(transport.writes.count, 1)
        let unrelated = try HeyCyanFrameCodec.production.encode(
            command: 0x42,
            payload: Data([0x01])
        )
        transport.emit(unrelated)
        XCTAssertFalse(request.isCancelled)

        let responseData = try HeyCyanFrameCodec.production.encode(
            command: 0x41,
            payload: Data([0x02, 0x01, 0x01, 0x00])
        )
        transport.emit(responseData)

        let response = try await request.value
        XCTAssertEqual(response.command, 0x41)
        XCTAssertEqual(response.payload, Data([0x02, 0x01, 0x01, 0x00]))
    }

    func testRequestIgnoresDifferentWorkTypeInSharedControlFamily() async throws {
        let transport = FakeHeyCyanByteTransport(state: .ready(name: "Test glasses"))
        let session = HeyCyanSession(
            transport: transport,
            responseTimeout: .seconds(1)
        )

        let request = Task { try await session.send(.takePhoto) }
        await Task.yield()

        transport.emit(
            try HeyCyanFrameCodec.production.encode(
                command: 0x41,
                payload: Data([0x02, 0x01, 0x02, 0x00])
            )
        )
        transport.emit(
            try HeyCyanFrameCodec.production.encode(
                command: 0x41,
                payload: Data([0x02, 0x01, 0x01, 0x00])
            )
        )

        let response = try await request.value
        XCTAssertEqual(response.payload, Data([0x02, 0x01, 0x01, 0x00]))
    }

    func testCleanupWriteDoesNotCreatePendingResponseTransaction() throws {
        let transport = FakeHeyCyanByteTransport(state: .ready(name: "Test glasses"))
        let session = HeyCyanSession(transport: transport)

        try session.writeForCleanup(.finishMediaTransfer)
        try session.writeForCleanup(.finishMediaTransfer)

        XCTAssertEqual(transport.writes.count, 2)
    }

    func testTimedOutFamilyCanBeUsedAgain() async throws {
        let transport = FakeHeyCyanByteTransport(state: .ready(name: "Test glasses"))
        let session = HeyCyanSession(
            transport: transport,
            responseTimeout: .milliseconds(20)
        )

        do {
            _ = try await session.send(.takePhoto)
            XCTFail("Expected the first request to time out")
        } catch let error as HeyCyanSessionError {
            guard case .responseTimedOut(0x41) = error else {
                return XCTFail("Unexpected error: \(error)")
            }
        }

        let secondRequest = Task { try await session.send(.takePhoto) }
        await Task.yield()
        let responseData = try HeyCyanFrameCodec.production.encode(
            command: 0x41,
            payload: Data([0x02, 0x01, 0x01, 0x00])
        )
        transport.emit(responseData)
        _ = try await secondRequest.value
        XCTAssertEqual(transport.writes.count, 2)
    }

    func testDisconnectFailsPendingRequest() async throws {
        let transport = FakeHeyCyanByteTransport(state: .ready(name: "Test glasses"))
        let session = HeyCyanSession(
            transport: transport,
            responseTimeout: .seconds(1)
        )

        let request = Task { try await session.send(.takePhoto) }
        await Task.yield()
        transport.changeState(to: .idle)

        do {
            _ = try await request.value
            XCTFail("Expected disconnect to fail the pending request")
        } catch let error as HeyCyanSessionError {
            guard case .disconnectedWhileAwaitingResponse(0x41) = error else {
                return XCTFail("Unexpected error: \(error)")
            }
        }
    }
}

@MainActor
private final class FakeHeyCyanByteTransport: HeyCyanByteTransport {
    var state: HeyCyanBLETransportState
    var onStateChange: ((HeyCyanBLETransportState) -> Void)?
    var onNotification: ((HeyCyanTransportChannel, Data) -> Void)?
    private(set) var writes = [(Data, HeyCyanTransportChannel)]()

    init(state: HeyCyanBLETransportState) {
        self.state = state
    }

    func write(_ data: Data, to channel: HeyCyanTransportChannel) throws {
        writes.append((data, channel))
    }

    func emit(_ data: Data, channel: HeyCyanTransportChannel = .largeData) {
        onNotification?(channel, data)
    }

    func changeState(to state: HeyCyanBLETransportState) {
        self.state = state
        onStateChange?(state)
    }
}

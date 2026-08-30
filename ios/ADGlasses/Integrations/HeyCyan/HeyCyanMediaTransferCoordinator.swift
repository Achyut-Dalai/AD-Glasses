import Foundation

enum HeyCyanMediaTransferState: Equatable, Sendable {
    case idle
    case preparingBluetooth
    case joiningNetwork(ssid: String)
    case verifyingMediaServer
    case ready(items: [HeyCyanMediaItem])
    case downloading(fileName: String)
    case finishing
    case failed(reason: String)
}

enum HeyCyanMediaTransferError: LocalizedError, Sendable {
    case operationInProgress
    case notPrepared
    case mediaServerNotReady(String)
    case networkAddressTimedOut
    case networkPreparationFailed(Int)

    var errorDescription: String? {
        switch self {
        case .operationInProgress:
            return "Another glasses media-transfer operation is already running."
        case .notPrepared:
            return "Prepare the glasses media connection before downloading."
        case .mediaServerNotReady(let reason):
            return "The glasses media server did not become ready: \(reason)"
        case .networkAddressTimedOut:
            return "The glasses did not report their Wi-Fi address in time."
        case .networkPreparationFailed(let code):
            return "The glasses reported Wi-Fi preparation error \(code)."
        }
    }
}

/// Coordinates the verified BLE → glasses AP → local HTTP shape.
///
/// Credentials come only from the work-type 0x04 response and the device address comes only from
/// the asynchronous 0x73/0x08 notification. This layer never derives or hard-codes either value.
/// The AP transfer command remains behind the HeyCyan provider's hardware-validation boundary
/// until the physical glasses confirm this firmware's AP acknowledgement/readiness sequence.
@MainActor
final class HeyCyanMediaTransferCoordinator {
    var onStateChange: ((HeyCyanMediaTransferState) -> Void)?

    private(set) var state: HeyCyanMediaTransferState = .idle {
        didSet {
            guard state != oldValue else { return }
            onStateChange?(state)
        }
    }

    private let session: HeyCyanSession
    private let wifi: HeyCyanWiFiCoordinator
    private let media: HeyCyanMediaClient
    private let responseDecoder = HeyCyanResponseDecoder()
    private var activeAccessPoint: HeyCyanAccessPoint?
    private var activeOperationID: UUID?
    private var reportedDeviceIPv4Address: String?
    private var reportedNetworkError: Int?
    private var addressWaitID: UUID?
    private var addressWaitContinuation: CheckedContinuation<String, Error>?
    private var addressTimeoutTask: Task<Void, Never>?

    convenience init(session: HeyCyanSession) {
        self.init(
            session: session,
            wifi: HeyCyanWiFiCoordinator(),
            media: HeyCyanMediaClient()
        )
    }

    init(
        session: HeyCyanSession,
        wifi: HeyCyanWiFiCoordinator,
        media: HeyCyanMediaClient
    ) {
        self.session = session
        self.wifi = wifi
        self.media = media
    }

    func prepare(readinessTimeout: Duration = .seconds(20)) async throws -> [HeyCyanMediaItem] {
        let operationID = try beginOperation()
        defer { endOperation(operationID) }
        var didIssueBluetoothPrepare = false

        do {
            reportedDeviceIPv4Address = nil
            reportedNetworkError = nil
            state = .preparingBluetooth
            didIssueBluetoothPrepare = true
            let response = try await session.send(.prepareMediaTransfer(mode: .accessPoint))
            let preparation = try responseDecoder.decodeNetworkPreparation(
                response,
                expectedMode: .accessPoint
            )
            let deviceAddress = try await waitForDeviceAddress(
                timeout: readinessTimeout,
                operationID: operationID
            )
            let accessPoint = try HeyCyanAccessPoint(
                ssid: preparation.ssid,
                passphrase: preparation.passphrase,
                deviceIPv4Address: deviceAddress
            )

            state = .joiningNetwork(ssid: accessPoint.ssid)
            try await wifi.join(accessPoint)
            try ensureOperationIsActive(operationID)
            activeAccessPoint = accessPoint

            state = .verifyingMediaServer
            let items = try await waitForMediaServer(
                accessPoint: accessPoint,
                timeout: readinessTimeout,
                operationID: operationID
            )
            state = .ready(items: items)
            return items
        } catch {
            recoverTransferMode(sendBluetoothFinish: didIssueBluetoothPrepare)
            state = error is CancellationError
                ? .idle
                : .failed(reason: error.localizedDescription)
            throw error
        }
    }

    func refresh() async throws -> [HeyCyanMediaItem] {
        let operationID = try beginOperation()
        defer { endOperation(operationID) }
        guard let activeAccessPoint else {
            throw HeyCyanMediaTransferError.notPrepared
        }
        state = .verifyingMediaServer
        do {
            let items = try await media.mediaList(on: activeAccessPoint)
            try ensureOperationIsActive(operationID)
            state = .ready(items: items)
            return items
        } catch {
            recoverTransferMode(sendBluetoothFinish: true)
            state = .failed(reason: error.localizedDescription)
            throw error
        }
    }

    func download(_ item: HeyCyanMediaItem, to destinationURL: URL) async throws {
        let operationID = try beginOperation()
        defer { endOperation(operationID) }
        guard let activeAccessPoint else {
            throw HeyCyanMediaTransferError.notPrepared
        }
        state = .downloading(fileName: item.fileName)
        do {
            try await media.download(item, from: activeAccessPoint, to: destinationURL)
            try ensureOperationIsActive(operationID)
            let items = try await media.mediaList(on: activeAccessPoint)
            state = .ready(items: items)
        } catch {
            recoverTransferMode(sendBluetoothFinish: true)
            state = .failed(reason: error.localizedDescription)
            throw error
        }
    }

    func finish() async throws {
        let operationID = try beginOperation()
        defer { endOperation(operationID) }
        state = .finishing
        defer {
            wifi.leave()
            activeAccessPoint = nil
        }

        do {
            if session.state.isReady {
                _ = try await session.send(.finishMediaTransfer)
            }
            state = .idle
        } catch {
            state = .failed(reason: error.localizedDescription)
            throw error
        }
    }

    /// Local cleanup used when BLE disappears. It performs no speculative command write.
    func abandonNetworkAssociation() {
        activeOperationID = nil
        finishAddressWait(with: .failure(CancellationError()))
        wifi.leave()
        activeAccessPoint = nil
        state = .idle
    }

    /// Cancels an in-flight or prepared transfer and returns both transports to their normal
    /// state. The exit command is a confirmed, non-destructive cleanup command; no reset, delete,
    /// factory, or firmware operation is performed.
    func cancel() {
        activeOperationID = nil
        finishAddressWait(with: .failure(CancellationError()))
        recoverTransferMode(sendBluetoothFinish: true)
        state = .idle
    }

    func receiveDeviceEvent(_ event: HeyCyanDeviceEvent) {
        switch event {
        case .wifiAddress(let address):
            reportedDeviceIPv4Address = address
            finishAddressWait(with: .success(address))
        case .wifiError(let code):
            reportedNetworkError = code
            finishAddressWait(
                with: .failure(HeyCyanMediaTransferError.networkPreparationFailed(code))
            )
        default:
            break
        }
    }

    private func waitForMediaServer(
        accessPoint: HeyCyanAccessPoint,
        timeout: Duration,
        operationID: UUID
    ) async throws -> [HeyCyanMediaItem] {
        let clock = ContinuousClock()
        let deadline = clock.now.advanced(by: timeout)
        var lastError: Error?

        repeat {
            try Task.checkCancellation()
            try ensureOperationIsActive(operationID)
            guard session.state.isReady else { throw HeyCyanSessionError.notReady }
            do {
                return try await media.mediaList(on: accessPoint)
            } catch {
                lastError = error
            }
            if clock.now >= deadline { break }
            try await Task.sleep(for: .milliseconds(350))
        } while clock.now < deadline

        throw HeyCyanMediaTransferError.mediaServerNotReady(
            lastError?.localizedDescription ?? "no response"
        )
    }

    private func waitForDeviceAddress(
        timeout: Duration,
        operationID: UUID
    ) async throws -> String {
        try ensureOperationIsActive(operationID)
        if let reportedDeviceIPv4Address {
            return reportedDeviceIPv4Address
        }
        if let reportedNetworkError {
            throw HeyCyanMediaTransferError.networkPreparationFailed(reportedNetworkError)
        }

        let waitID = UUID()
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                addressWaitID = waitID
                addressWaitContinuation = continuation
                addressTimeoutTask?.cancel()
                addressTimeoutTask = Task { [weak self] in
                    do {
                        try await Task.sleep(for: timeout)
                    } catch {
                        return
                    }
                    guard let self, addressWaitID == waitID else { return }
                    finishAddressWait(
                        with: .failure(HeyCyanMediaTransferError.networkAddressTimedOut)
                    )
                }
            }
        } onCancel: {
            Task { @MainActor [weak self] in
                guard let self, addressWaitID == waitID else { return }
                finishAddressWait(with: .failure(CancellationError()))
            }
        }
    }

    private func finishAddressWait(with result: Result<String, Error>) {
        addressTimeoutTask?.cancel()
        addressTimeoutTask = nil
        addressWaitID = nil
        guard let continuation = addressWaitContinuation else { return }
        addressWaitContinuation = nil
        continuation.resume(with: result)
    }

    private func beginOperation() throws -> UUID {
        guard activeOperationID == nil else {
            throw HeyCyanMediaTransferError.operationInProgress
        }
        let id = UUID()
        activeOperationID = id
        return id
    }

    private func endOperation(_ id: UUID) {
        if activeOperationID == id {
            activeOperationID = nil
        }
    }

    private func ensureOperationIsActive(_ id: UUID) throws {
        guard activeOperationID == id else { throw CancellationError() }
    }

    private func recoverTransferMode(sendBluetoothFinish: Bool) {
        finishAddressWait(with: .failure(CancellationError()))
        wifi.leave()
        activeAccessPoint = nil

        guard sendBluetoothFinish, session.state.isReady else { return }
        // Recovery must not wait for a response: the failed HTTP/Wi-Fi task may already be
        // cancelled, while the glasses still need the verified transfer-exit command.
        try? session.writeForCleanup(.finishMediaTransfer)
    }
}

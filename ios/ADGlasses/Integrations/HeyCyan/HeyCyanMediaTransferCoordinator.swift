import Foundation
import UIKit

enum HeyCyanMediaTransferState: Equatable, Sendable {
    case idle
    case checkingMediaCounts
    case preparingBluetooth
    case joiningNetwork(ssid: String)
    case awaitingManualNetworkJoin(credentials: HeyCyanNetworkCredentials)
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
    private var manualJoinWaitID: UUID?
    private var manualJoinContinuation: CheckedContinuation<Void, Error>?

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

            // Preserve the physically validated sync path: ask the glasses to enter their media
            // AP mode first, then move to Wi-Fi and inspect media.config over HTTP. A speculative
            // BLE inventory read here previously changed first-attempt behavior on the real device.
            state = .preparingBluetooth
            didIssueBluetoothPrepare = true
            let response = try await session.send(.prepareMediaTransfer(mode: .accessPoint))
            let preparation = try responseDecoder.decodeNetworkPreparation(
                response,
                expectedMode: .accessPoint
            )
            let credentials = try HeyCyanNetworkCredentials(
                ssid: preparation.ssid,
                passphrase: preparation.passphrase
            )

            state = .joiningNetwork(ssid: credentials.ssid)
            let deviceAddress: String
#if AD_PERSONAL_TEAM_BUILD
            // Personal Team builds cannot use Hotspot Configuration. Keep the BLE session alive,
            // copy the current glasses password, and use the same legacy Wi-Fi Settings handoff
            // that was physically validated on this sideloaded build. The HTTP/IP checks below are
            // still the authority for whether the user actually joined the glasses network.
            if let reportedDeviceIPv4Address {
                deviceAddress = reportedDeviceIPv4Address
            } else {
                state = .awaitingManualNetworkJoin(credentials: credentials)
                copyNetworkPassword(credentials.passphrase)
                openSettingsForManualWiFiJoin()
                try await waitForManualNetworkJoin(operationID: operationID)
                deviceAddress = try await waitForDeviceAddress(
                    timeout: readinessTimeout,
                    operationID: operationID
                )
            }
#else
            try await wifi.join(credentials)
            try ensureOperationIsActive(operationID)
            deviceAddress = try await waitForDeviceAddress(
                timeout: readinessTimeout,
                operationID: operationID
            )
#endif
            try ensureOperationIsActive(operationID)
            let accessPoint = try HeyCyanAccessPoint(
                credentials: credentials,
                deviceIPv4Address: deviceAddress
            )
            activeAccessPoint = accessPoint

            state = .verifyingMediaServer
            let items = try await waitForMediaServer(
                accessPoint: accessPoint,
                timeout: readinessTimeout,
                operationID: operationID
            )
            if items.isEmpty {
                // An empty manifest is a successful check. Immediately return the glasses to their
                // normal transport state rather than leaving an empty transfer session alive.
                recoverTransferMode(sendBluetoothFinish: true)
            }
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

        let continuedWorkID = ADContinuedProcessingCoordinator.shared.begin(
            title: "Syncing from AD Glasses",
            subtitle: item.fileName
        )
        var continuedWorkSucceeded = false
        defer {
            ADContinuedProcessingCoordinator.shared.finish(
                continuedWorkID,
                success: continuedWorkSucceeded
            )
        }

        state = .downloading(fileName: item.fileName)
        do {
            try await media.download(item, from: activeAccessPoint, to: destinationURL)
            try ensureOperationIsActive(operationID)
            ADContinuedProcessingCoordinator.shared.update(
                continuedWorkID,
                completed: 1,
                total: 1,
                subtitle: "Finishing \(item.fileName)"
            )
            let items = try await media.mediaList(on: activeAccessPoint)
            state = .ready(items: items)
            continuedWorkSucceeded = true
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
        finishManualNetworkJoin(with: .failure(CancellationError()))
        wifi.leave()
        activeAccessPoint = nil
        state = .idle
    }

    /// Cancels an in-flight or prepared transfer and returns both transports to their normal
    /// state. The exit command is a confirmed, non-destructive cleanup command.
    func cancel() {
        activeOperationID = nil
        finishAddressWait(with: .failure(CancellationError()))
        finishManualNetworkJoin(with: .failure(CancellationError()))
        recoverTransferMode(sendBluetoothFinish: true)
        state = .idle
    }

    func receiveDeviceEvent(_ event: HeyCyanDeviceEvent) {
        switch event {
        case .wifiAddress(let address):
            reportedDeviceIPv4Address = address
            if case .awaitingManualNetworkJoin = state {
                finishManualNetworkJoin(with: .success(()))
            }
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

    /// Continues the Personal Team flow after the user returns from Settings. This signal merely
    /// resumes the bounded verification; BLE-reported IP + the HTTP media server prove readiness.
    func continueAfterManualNetworkJoin() {
        guard case .awaitingManualNetworkJoin = state else { return }
        finishManualNetworkJoin(with: .success(()))
    }

    private func waitForMediaServer(
        accessPoint: HeyCyanAccessPoint,
        timeout: Duration,
        operationID: UUID
    ) async throws -> [HeyCyanMediaItem] {
        let clock = ContinuousClock()
        let deadline = clock.now.advanced(by: timeout)
        var lastError: Error?
        var consecutiveMissingManifestResponses = 0

        repeat {
            try Task.checkCancellation()
            try ensureOperationIsActive(operationID)
            guard session.state.isReady else { throw HeyCyanSessionError.notReady }
            if let reportedNetworkError {
                throw HeyCyanMediaTransferError.networkPreparationFailed(reportedNetworkError)
            }
            do {
                return try await media.mediaList(on: accessPoint)
            } catch let error as HeyCyanMediaError {
                if case .httpStatus(404) = error {
                    // Empty storage can legitimately omit media.config. Give the server time to
                    // finish booting, then treat a stable 404 as an empty Library rather than an
                    // endless/retry-style sync failure.
                    consecutiveMissingManifestResponses += 1
                    if consecutiveMissingManifestResponses >= 6 {
                        return []
                    }
                } else {
                    consecutiveMissingManifestResponses = 0
                    if case .httpStatus(let status) = error,
                       (400 ..< 500).contains(status) {
                        throw error
                    }
                }
                lastError = error
            } catch {
                consecutiveMissingManifestResponses = 0
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
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
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

    private func waitForManualNetworkJoin(operationID: UUID) async throws {
        try ensureOperationIsActive(operationID)
        if reportedDeviceIPv4Address != nil {
            return
        }

        let waitID = UUID()
        manualJoinWaitID = waitID

        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                guard activeOperationID == operationID else {
                    continuation.resume(throwing: CancellationError())
                    return
                }
                if reportedDeviceIPv4Address != nil {
                    manualJoinWaitID = nil
                    continuation.resume()
                    return
                }
                manualJoinContinuation = continuation
            }
        } onCancel: {
            Task { @MainActor [weak self] in
                guard let self, manualJoinWaitID == waitID else { return }
                finishManualNetworkJoin(with: .failure(CancellationError()))
            }
        }
    }

    private func finishManualNetworkJoin(with result: Result<Void, Error>) {
        manualJoinWaitID = nil
        guard let continuation = manualJoinContinuation else { return }
        manualJoinContinuation = nil
        continuation.resume(with: result)
    }

#if AD_PERSONAL_TEAM_BUILD
    /// Personal/sideloaded builds cannot provision Hotspot Configuration. `App-Prefs:` is the
    /// physically validated handoff on the current test iPhone: it opens the Settings root so Wi-Fi
    /// is one tap away. The public app-settings URL opens AD Glasses under Apps, and the accepted
    /// `prefs:root=WIFI` URL is redirected there as well on this iOS version.
    ///
    /// This is an undocumented convenience for personal builds; the in-app instructions remain
    /// the source of truth because iOS does not provide a supported Wi-Fi Settings deep link.
    private func openSettingsForManualWiFiJoin() {
        guard let url = URL(string: "App-Prefs:") else { return }
        UIApplication.shared.open(url, options: [:], completionHandler: nil)
    }

    private func copyNetworkPassword(_ passphrase: String) {
        UIPasteboard.general.setItems(
            [[UIPasteboard.typeAutomatic: passphrase]],
            options: [
                .localOnly: true,
                .expirationDate: Date().addingTimeInterval(120)
            ]
        )
    }
#endif

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
        finishManualNetworkJoin(with: .failure(CancellationError()))
        wifi.leave()
        activeAccessPoint = nil

        guard sendBluetoothFinish, session.state.isReady else { return }
        try? session.writeForCleanup(.finishMediaTransfer)
    }
}

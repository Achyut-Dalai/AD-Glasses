import Foundation
import OSLog

enum HeyCyanSessionState: Equatable, Sendable {
    case disconnected
    case scanning
    case connecting(name: String)
    case preparing(name: String)
    case ready(name: String)
    case reconnecting(name: String, attempt: Int, maximumAttempts: Int)
    case unavailable(reason: String)
    case failed(reason: String)

    var isReady: Bool {
        if case .ready = self { return true }
        return false
    }
}

enum HeyCyanSessionError: LocalizedError, Sendable {
    case notReady
    case commandFamilyBusy(UInt8)
    case responseTimedOut(UInt8)
    case disconnectedWhileAwaitingResponse(UInt8)
    case assistantSessionDidNotEnd

    var errorDescription: String? {
        switch self {
        case .notReady:
            return "The glasses are connected, but their control session is not ready."
        case .commandFamilyBusy(let family):
            return String(
                format: "An AD Glasses 0x%02X request is still waiting for its response.",
                family
            )
        case .responseTimedOut(let family):
            return String(
                format: "The glasses did not answer the 0x%02X request in time.",
                family
            )
        case .disconnectedWhileAwaitingResponse(let family):
            return String(
                format: "The glasses disconnected while request 0x%02X was waiting for a response.",
                family
            )
        case .assistantSessionDidNotEnd:
            return "The glasses did not finish their voice session, so the hardware command was not sent."
        }
    }
}

/// Coordinates verified framing with the byte transport without assigning unverified meanings to
/// responses. Incoming response correlation remains a deliberate hardware-validation boundary.
@MainActor
final class HeyCyanSession {
    var onStateChange: ((HeyCyanSessionState) -> Void)?
    var onFrame: ((HeyCyanFrame) -> Void)?
    var onUnparsedNotification: ((HeyCyanTransportChannel, Data) -> Void)?
    var onDiagnostic: ((String) -> Void)?

    private(set) var state: HeyCyanSessionState = .disconnected {
        didSet {
            guard state != oldValue else { return }
            onStateChange?(state)
        }
    }

    private struct PendingRequest {
        let id: UUID
        let command: HeyCyanCommand
        let continuation: CheckedContinuation<HeyCyanFrame, Error>
        let timeoutTask: Task<Void, Never>
    }

    let transport: any HeyCyanByteTransport

    private let codec: HeyCyanFrameCodec
    private let responseTimeout: Duration
    private let assistantEndTimeout: Duration
    private let logger = Logger(subsystem: "com.achyutdalai.ADGlasses", category: "HeyCyanSession")
    private var streamDecoder: HeyCyanFrameStreamDecoder
    private var pendingRequests = [UInt8: PendingRequest]()
    private var isAssistantListening = false

    init(
        transport: any HeyCyanByteTransport,
        codec: HeyCyanFrameCodec = .production,
        responseTimeout: Duration = .seconds(10),
        assistantEndTimeout: Duration = .seconds(30)
    ) {
        self.transport = transport
        self.codec = codec
        self.responseTimeout = responseTimeout
        self.assistantEndTimeout = assistantEndTimeout
        streamDecoder = HeyCyanFrameStreamDecoder(codec: codec)

        transport.onStateChange = { [weak self] transportState in
            self?.consume(transportState)
        }
        transport.onNotification = { [weak self] channel, data in
            self?.consumeNotification(data, channel: channel)
        }
        consume(transport.state)
    }

    func send(_ command: HeyCyanCommand) async throws -> HeyCyanFrame {
        guard state.isReady else {
            throw HeyCyanSessionError.notReady
        }
        try await waitForAssistantSessionToEndIfNeeded(for: command)
        guard state.isReady else {
            throw HeyCyanSessionError.notReady
        }
        guard pendingRequests[command.family] == nil else {
            throw HeyCyanSessionError.commandFamilyBusy(command.family)
        }
        let frame = try codec.encode(command: command.family, payload: command.payload)

        let requestID = UUID()
        let timeout = responseTimeout
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                let timeoutTask = Task { [weak self] in
                    do {
                        try await Task.sleep(for: timeout)
                    } catch {
                        return
                    }
                    self?.finishRequest(
                        family: command.family,
                        id: requestID,
                        result: .failure(HeyCyanSessionError.responseTimedOut(command.family))
                    )
                }
                pendingRequests[command.family] = PendingRequest(
                    id: requestID,
                    command: command,
                    continuation: continuation,
                    timeoutTask: timeoutTask
                )

                do {
                    try transport.write(frame, to: command.channel)
                } catch {
                    finishRequest(
                        family: command.family,
                        id: requestID,
                        result: .failure(error)
                    )
                }
            }
        } onCancel: {
            Task { @MainActor [weak self] in
                self?.finishRequest(
                    family: command.family,
                    id: requestID,
                    result: .failure(CancellationError())
                )
            }
        }
    }

    /// Writes a verified command without opening a response transaction. This exists for
    /// best-effort exit/cleanup when a longer operation fails or is cancelled; normal feature
    /// requests must use `send(_:)` so success is never inferred from a queued BLE write.
    func writeForCleanup(_ command: HeyCyanCommand) throws {
        guard state.isReady else { throw HeyCyanSessionError.notReady }
        guard pendingRequests[command.family] == nil else {
            throw HeyCyanSessionError.commandFamilyBusy(command.family)
        }
        let frame = try codec.encode(command: command.family, payload: command.payload)
        try transport.write(frame, to: command.channel)
    }

    private func waitForAssistantSessionToEndIfNeeded(for command: HeyCyanCommand) async throws {
        guard command.requiresAssistantIdle, isAssistantListening else { return }

        // The app is allowed to finish SpeechAnalyzer early after acoustic silence, but that does
        // not mean the glasses firmware has ended its own Assistant turn. Camera/recording commands
        // must wait for the verified 0x73/0x0A end notification instead of racing it and failing
        // after an arbitrary short delay. No speculative "stop listening" command is sent.
        let clock = ContinuousClock()
        let deadline = clock.now.advanced(by: assistantEndTimeout)
        while isAssistantListening {
            try Task.checkCancellation()
            guard clock.now < deadline else {
                throw HeyCyanSessionError.assistantSessionDidNotEnd
            }
            try await Task.sleep(for: .milliseconds(25))
        }
    }

    private func finishRequest(
        family: UInt8,
        id: UUID? = nil,
        result: Result<HeyCyanFrame, Error>
    ) {
        guard let pending = pendingRequests[family], id == nil || pending.id == id else { return }
        pendingRequests[family] = nil
        pending.timeoutTask.cancel()
        pending.continuation.resume(with: result)
    }

    private func failPendingRequests() {
        let pending = pendingRequests
        pendingRequests.removeAll()
        for (family, request) in pending {
            request.timeoutTask.cancel()
            request.continuation.resume(
                throwing: HeyCyanSessionError.disconnectedWhileAwaitingResponse(family)
            )
        }
    }

    private func consume(_ transportState: HeyCyanBLETransportState) {
        switch transportState {
        case .idle, .disconnecting:
            streamDecoder.reset()
            isAssistantListening = false
            failPendingRequests()
            state = .disconnected
        case .scanning:
            state = .scanning
        case .connecting(let name):
            state = .connecting(name: name)
        case .discoveringServices(let name), .enablingNotifications(let name):
            state = .preparing(name: name)
        case .ready(let name):
            state = .ready(name: name)
        case .reconnecting(let name, let attempt, let maximumAttempts):
            streamDecoder.reset()
            isAssistantListening = false
            failPendingRequests()
            state = .reconnecting(
                name: name,
                attempt: attempt,
                maximumAttempts: maximumAttempts
            )
        case .unavailable(let reason):
            streamDecoder.reset()
            isAssistantListening = false
            failPendingRequests()
            state = .unavailable(reason: reason)
        case .failed(let reason):
            streamDecoder.reset()
            isAssistantListening = false
            failPendingRequests()
            state = .failed(reason: reason)
        }
    }

    private func consumeNotification(_ data: Data, channel: HeyCyanTransportChannel) {
        guard channel == .largeData else {
            // The base-channel response format and routing map are not yet fully documented.
            onUnparsedNotification?(channel, data)
            return
        }

        for event in streamDecoder.append(data) {
            switch event {
            case .frame(let frame):
                updateAssistantListeningState(from: frame)
                if let pending = pendingRequests[frame.command],
                   pending.command.matchesResponse(frame) {
                    finishRequest(
                        family: frame.command,
                        result: .success(frame)
                    )
                }
                onFrame?(frame)
            case .discarded(let bytes):
                let message = "Discarded \(bytes.count) non-frame byte(s) while resynchronizing HeyCyan notifications."
                logger.notice("\(message, privacy: .public)")
                onDiagnostic?(message)
                onUnparsedNotification?(channel, bytes)
            case .malformed(let rawData, let error):
                let message = "Malformed HeyCyan notification: \(error.localizedDescription)"
                logger.error("\(message, privacy: .public)")
                onDiagnostic?(message)
                onUnparsedNotification?(channel, rawData)
            }
        }
    }

    private func updateAssistantListeningState(from frame: HeyCyanFrame) {
        guard frame.command == 0x73, frame.payload.count >= 2 else { return }
        let bytes = [UInt8](frame.payload)
        if bytes[0] == 0x03, bytes[1] == 0x01 {
            isAssistantListening = true
        } else if bytes[0] == 0x0A, bytes[1] == 0x01 {
            isAssistantListening = false
        }
    }
}

private extension HeyCyanCommand {
    var requiresAssistantIdle: Bool {
        switch self {
        case .takePhoto, .startVideoRecording, .startAudioRecording, .requestAIPhoto:
            return true
        default:
            return false
        }
    }
}

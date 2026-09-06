import Foundation

@MainActor
final class HeyCyanGlassesProvider: NSObject,
    GlassesProvider,
    GlassesReconnecting,
    GlassesForgettable,
    GlassesPhotoCapturing,
    GlassesVideoRecording,
    GlassesAudioRecording,
    GlassesVisualCapturing,
    GlassesBatteryProviding,
    GlassesDeviceInformationProviding,
    GlassesDeviceManagementPlanning,
    GlassesDeviceManaging,
    GlassesVolumeProviding,
    GlassesVoiceWakeProviding,
    GlassesAssistantAudioProviding,
    GlassesMediaInventoryProviding,
    GlassesMediaTransferring,
    GlassesDiagnosticsProviding
{
    let id = "heycyan"
    let displayName = "HeyCyan"
    let capabilities: Set<GlassesCapability> = {
        var capabilities: Set<GlassesCapability> = [
            .bluetoothConnection,
            .photoCapture,
            .videoRecording,
            .audioRecording,
            .camera,
            .microphoneAudio,
            .deviceInformation,
            .volumeControl
        ]
        capabilities.insert(.mediaTransfer)
        return capabilities
    }()
    let deviceManagementPlaceholders: [GlassesDeviceManagementPlaceholder] = [
        GlassesDeviceManagementPlaceholder(
            operation: .firmwareUpdate,
            reason: "Awaiting a captured update, recovery, and rollback session."
        ),
    ]

    var onConnectionStateChange: ((GlassesConnectionState) -> Void)?
    var onBatteryStatusChange: ((GlassesBatteryStatus?) -> Void)?
    var onDeviceInformationChange: ((GlassesDeviceInformation?) -> Void)?
    var onVolumeProfileChange: ((GlassesVolumeProfile?) -> Void)?
    var onGlassesVoiceWakeChange: ((Bool) -> Void)?
    var onAssistantAudioEvent: ((GlassesAssistantAudioEvent) -> Void)?
    var onVisualCapture: ((GlassesVisualCapture) -> Void)?
    var onVideoRecordingStateChange: ((Bool) -> Void)?
    var onAudioRecordingStateChange: ((Bool) -> Void)?
    var onMediaTransferStateChange: ((GlassesMediaTransferState) -> Void)?

    private(set) var mediaTransferState: GlassesMediaTransferState = .idle {
        didSet {
            guard mediaTransferState != oldValue else { return }
            onMediaTransferStateChange?(mediaTransferState)
        }
    }

    private(set) var connectionState: GlassesConnectionState = .disconnected {
        didSet {
            guard connectionState != oldValue else { return }
            onConnectionStateChange?(connectionState)
        }
    }

    private(set) var batteryStatus: GlassesBatteryStatus? {
        didSet {
            guard batteryStatus != oldValue else { return }
            onBatteryStatusChange?(batteryStatus)
        }
    }

    private(set) var deviceInformation: GlassesDeviceInformation? {
        didSet {
            guard deviceInformation != oldValue else { return }
            onDeviceInformationChange?(deviceInformation)
        }
    }

    private(set) var volumeProfile: GlassesVolumeProfile? {
        didSet {
            guard volumeProfile != oldValue else { return }
            onVolumeProfileChange?(volumeProfile)
        }
    }

    private(set) var glassesVoiceWakeEnabled = false {
        didSet {
            guard glassesVoiceWakeEnabled != oldValue else { return }
            onGlassesVoiceWakeChange?(glassesVoiceWakeEnabled)
        }
    }

    private(set) var isVideoRecording = false {
        didSet {
            guard isVideoRecording != oldValue else { return }
            onVideoRecordingStateChange?(isVideoRecording)
        }
    }

    private(set) var isAudioRecording = false {
        didSet {
            guard isAudioRecording != oldValue else { return }
            onAudioRecordingStateChange?(isAudioRecording)
        }
    }

    private let transport: HeyCyanBLETransport
    private let session: HeyCyanSession
    private let thumbnailTransfer: HeyCyanThumbnailTransfer
    private let mediaTransfer: HeyCyanMediaTransferCoordinator
    private let diagnostics: HeyCyanDiagnosticRecorder
    private let passiveScanner: HeyCyanPassiveBLEScanner
    private let opusDecoder: HeyCyanOpusDecoder?
    private let opusDecoderStartupError: String?
    private let responseDecoder = HeyCyanResponseDecoder()
    private let defaults: UserDefaults
    private let lastPeripheralIdentifierKey = "heycyan.lastPeripheralIdentifier.v1"
    private let desiredVoiceWakeKey = "heycyan.glassesVoiceWake.enabled.v1"
    private var discoveredDevices: [UUID: HeyCyanBLEDevice] = [:]
    private var statusRefreshTask: Task<Void, Never>?
    private var volumeChannelCode: UInt8 = 0x03
    private var isAssistantAudioStreaming = false
    private var visualCaptureTask: Task<Void, Never>?
    private var pendingVisualCapture: PendingVisualCapture?

    private struct PendingVisualCapture {
        let id: UUID
        let continuation: CheckedContinuation<GlassesVisualCapture, Error>
        let timeoutTask: Task<Void, Never>
    }

    var hasRememberedDevice: Bool {
        defaults.string(forKey: lastPeripheralIdentifierKey) != nil
    }

    override convenience init() {
        let diagnostics = HeyCyanDiagnosticRecorder.shared
        self.init(
            transport: HeyCyanBLETransport(diagnostics: diagnostics),
            defaults: .standard,
            diagnostics: diagnostics
        )
    }

    init(
        transport: HeyCyanBLETransport,
        defaults: UserDefaults,
        diagnostics: HeyCyanDiagnosticRecorder = .shared
    ) {
        self.transport = transport
        let session = HeyCyanSession(transport: transport)
        self.session = session
        thumbnailTransfer = HeyCyanThumbnailTransfer(session: session)
        mediaTransfer = HeyCyanMediaTransferCoordinator(session: session)
        self.defaults = defaults
        self.diagnostics = diagnostics
        passiveScanner = HeyCyanPassiveBLEScanner(diagnostics: diagnostics)
        do {
            opusDecoder = try HeyCyanOpusDecoder()
            opusDecoderStartupError = nil
        } catch {
            opusDecoder = nil
            opusDecoderStartupError = error.localizedDescription
        }
        super.init()

        session.onStateChange = { [weak self] state in
            self?.consume(state)
        }
        session.onFrame = { [weak self] frame in
            self?.consume(frame)
        }
        mediaTransfer.onStateChange = { [weak self] state in
            self?.mediaTransferState = state.glassesState
        }
    }

    func scan() async throws -> [GlassesDevice] {
        let result = try await transport.scan()
        discoveredDevices = Dictionary(uniqueKeysWithValues: result.map { ($0.id, $0) })
        return result.map {
            GlassesDevice(
                id: $0.id,
                name: $0.name,
                providerID: id,
                signalStrength: $0.signalStrength
            )
        }
    }

    func connect(to device: GlassesDevice) async throws {
        guard device.providerID == id,
              let transportDevice = discoveredDevices[device.id] else {
            throw GlassesProviderError.deviceNotFound
        }

        do {
            try await transport.connect(to: transportDevice)
            guard session.state.isReady else {
                throw HeyCyanSessionError.notReady
            }
            defaults.set(device.id.uuidString, forKey: lastPeripheralIdentifierKey)
        } catch let error as GlassesProviderError {
            throw error
        } catch {
            throw GlassesProviderError.connectionFailed(error.localizedDescription)
        }
    }

    func disconnect() async {
        await transport.disconnect()
    }

    func forgetLastDevice() async {
        await transport.disconnect()
        defaults.removeObject(forKey: lastPeripheralIdentifierKey)
        discoveredDevices.removeAll()
    }

    func reconnectLastDevice() async throws -> Bool {
        guard let rawIdentifier = defaults.string(forKey: lastPeripheralIdentifierKey),
              let identifier = UUID(uuidString: rawIdentifier) else {
            return false
        }

        do {
            let reconnected = try await transport.reconnect(identifier: identifier)
            guard reconnected, session.state.isReady else { return false }
            let name: String
            if case .ready(let readyName) = session.state {
                name = readyName
            } else {
                name = "HeyCyan glasses"
            }
            discoveredDevices[identifier] = HeyCyanBLEDevice(
                id: identifier,
                name: name,
                signalStrength: nil
            )
            return true
        } catch let error as GlassesProviderError {
            throw error
        } catch {
            throw GlassesProviderError.connectionFailed(error.localizedDescription)
        }
    }

    func requestPhotoCapture() async throws {
        do {
            let frame = try await session.send(.takePhoto)
            _ = try responseDecoder.decodeControlAcknowledgement(frame, expectedWorkType: 0x01)
        } catch {
            throw GlassesProviderError.connectionFailed(error.localizedDescription)
        }
    }

    func mediaInventory() async throws -> GlassesMediaInventory {
        let frame = try await session.send(.readMediaCounts)
        let counts = try responseDecoder.decodeMediaCounts(frame)
        return GlassesMediaInventory(
            photos: counts.photos,
            videos: counts.videos,
            recordings: counts.recordings
        )
    }

    func restartGlasses() async throws {
        let frame = try await session.send(.restartGlasses)
        _ = try responseDecoder.decodeControlAcknowledgement(frame, expectedWorkType: 0x0E)
    }

    func factoryResetGlasses() async throws {
        let frame = try await session.send(.factoryResetGlasses)
        _ = try responseDecoder.decodeControlAcknowledgement(frame, expectedWorkType: 0x0A)
    }

    func requestVisualCapture() async throws -> GlassesVisualCapture {
        guard pendingVisualCapture == nil, visualCaptureTask == nil else {
            throw HeyCyanThumbnailTransferError.operationInProgress
        }
        guard session.state.isReady else { throw HeyCyanSessionError.notReady }

        let requestID = UUID()
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                let timeoutTask = Task { [weak self] in
                    do {
                        try await Task.sleep(for: .seconds(30))
                    } catch {
                        return
                    }
                    self?.finishVisualCaptureRequest(
                        id: requestID,
                        result: .failure(
                            GlassesProviderError.connectionFailed(
                                "The glasses did not deliver their visual capture in time."
                            )
                        )
                    )
                }
                pendingVisualCapture = PendingVisualCapture(
                    id: requestID,
                    continuation: continuation,
                    timeoutTask: timeoutTask
                )

                Task { [weak self] in
                    guard let self else { return }
                    do {
                        let isDetailed = UserDefaults.standard.string(forKey: "adglasses.settings.camera.ai_vision_quality") == "detailed"
                        let frame = try await session.send(
                            .requestAIPhoto(quality: isDetailed ? .detailed : .quick)
                        )
                        _ = try responseDecoder.decodeControlAcknowledgement(
                            frame,
                            expectedWorkType: 0x06
                        )
                    } catch {
                        finishVisualCaptureRequest(
                            id: requestID,
                            result: .failure(error)
                        )
                    }
                }
            }
        } onCancel: {
            Task { @MainActor [weak self] in
                self?.finishVisualCaptureRequest(
                    id: requestID,
                    result: .failure(CancellationError())
                )
            }
        }
    }

    func prepareMediaTransfer() async throws -> [GlassesMediaItem] {
        try await mediaTransfer.prepare().map {
            GlassesMediaItem(
                remoteIdentifier: $0.fileName,
                fileName: $0.fileName,
                kind: $0.kind.glassesKind,
                providerID: id
            )
        }
    }

    func continueMediaTransferAfterManualNetworkJoin() {
        mediaTransfer.continueAfterManualNetworkJoin()
    }

    func downloadMediaItem(_ item: GlassesMediaItem, to destinationURL: URL) async throws {
        guard item.providerID == id,
              item.remoteIdentifier == item.fileName,
              let kind = HeyCyanMediaKind(glassesKind: item.kind) else {
            throw HeyCyanMediaError.unsafeFileName
        }
        try await mediaTransfer.download(
            HeyCyanMediaItem(fileName: item.remoteIdentifier, kind: kind),
            to: destinationURL
        )
    }

    func finishMediaTransfer() async throws {
        try await mediaTransfer.finish()
    }

    func cancelMediaTransfer() {
        mediaTransfer.cancel()
    }

    func refreshBatteryStatus() async throws {
        let frame = try await session.send(.synchronizeBattery)
        let response = try responseDecoder.decodeBattery(frame)
        batteryStatus = GlassesBatteryStatus(
            level: response.level,
            isCharging: response.isCharging
        )
    }

    func refreshDeviceInformation() async throws {
        let frame = try await session.send(.synchronizeDeviceInfo)
        let response = try responseDecoder.decodeDeviceInformation(frame)
        deviceInformation = GlassesDeviceInformation(
            firmwareVersion: response.bluetoothFirmwareVersion,
            hardwareVersion: response.bluetoothHardwareVersion,
            networkFirmwareVersion: response.wifiFirmwareVersion,
            networkHardwareVersion: response.wifiHardwareVersion
        )
    }

    func refreshVolumeProfile() async throws {
        let frame = try await session.send(.readVolumeControl)
        apply(try responseDecoder.decodeVolumeControl(frame))
    }

    private func synchronizeClock() async throws {
        let frame = try await session.send(.synchronizeTime(.current()))
        guard frame.payload == Data([0x00]) else {
            throw GlassesProviderError.connectionFailed(
                "The glasses rejected their clock synchronization."
            )
        }
    }

    private func requestClassicBluetoothConnection() async throws {
        _ = try await session.send(.openClassicBluetooth)
    }

    func setVolume(_ value: Int, for channel: GlassesVolumeChannel) async throws {
        if volumeProfile == nil {
            try await refreshVolumeProfile()
        }
        guard let profile = volumeProfile else {
            throw GlassesProviderError.connectionFailed(
                "The glasses did not return their volume settings."
            )
        }

        let requested = profile.replacing(channel, current: value)
        let commandProfile = HeyCyanVolumeProfile(
            music: requested.music.heyCyanLevel,
            calls: requested.calls.heyCyanLevel,
            system: requested.system.heyCyanLevel,
            activeChannelCode: volumeChannelCode
        )
        let frame = try await session.send(.setVolumeControl(commandProfile))
        apply(try responseDecoder.decodeVolumeControl(frame))
    }

    func isHardwareDiagnosticsEnabled() async -> Bool {
        await diagnostics.isPacketCaptureEnabled()
    }

    func setHardwareDiagnosticsEnabled(_ enabled: Bool) async {
        await diagnostics.setPacketCaptureEnabled(enabled)
    }

    func hardwareDiagnosticsURL() async throws -> URL {
        try await diagnostics.exportURL()
    }

    func clearHardwareDiagnostics() async throws {
        try await diagnostics.clear()
    }

    func runPassiveDiscoveryDiagnostics(duration: Duration) async throws {
        await diagnostics.setPacketCaptureEnabled(true)
        try await passiveScanner.scan(duration: duration)
    }

    func refreshGlassesVoiceWake() async throws {
        let frame = try await session.send(.readGlassesVoiceWake)
        glassesVoiceWakeEnabled = try responseDecoder.decodeGlassesVoiceWake(
            frame,
            expectedOperation: 0x01
        )
    }

    func setGlassesVoiceWakeEnabled(_ enabled: Bool) async throws {
        let frame = try await session.send(.setGlassesVoiceWake(enabled))
        glassesVoiceWakeEnabled = try responseDecoder.decodeGlassesVoiceWake(
            frame,
            expectedOperation: 0x02
        )
        defaults.set(enabled, forKey: desiredVoiceWakeKey)
    }

    func startVideoRecording() async throws {
        let frame = try await session.send(.startVideoRecording)
        _ = try responseDecoder.decodeControlAcknowledgement(frame, expectedWorkType: 0x02)
        isVideoRecording = true
    }

    func stopVideoRecording() async throws {
        let frame = try await session.send(.stopVideoRecording)
        _ = try responseDecoder.decodeControlAcknowledgement(frame, expectedWorkType: 0x03)
        isVideoRecording = false
    }

    func startAudioRecording() async throws {
        let frame = try await session.send(.startAudioRecording)
        _ = try responseDecoder.decodeControlAcknowledgement(frame, expectedWorkType: 0x08)
        isAudioRecording = true
    }

    func stopAudioRecording() async throws {
        let frame = try await session.send(.stopAudioRecording)
        _ = try responseDecoder.decodeControlAcknowledgement(frame, expectedWorkType: 0x0C)
        isAudioRecording = false
    }

    // Confirmed commands that still lack complete response/state semantics remain beneath the
    // provider boundary. They are deliberately not advertised as completed product capabilities.

    func requestAIPhotoForHardwareValidation(quality: HeyCyanAIPhotoQuality) async throws {
        let frame = try await session.send(.requestAIPhoto(quality: quality))
        _ = try responseDecoder.decodeControlAcknowledgement(frame, expectedWorkType: 0x06)
    }

    func prepareMediaTransferForHardwareValidation() async throws -> [HeyCyanMediaItem] {
        try await mediaTransfer.prepare()
    }

    func finishMediaTransferForHardwareValidation() async throws {
        try await mediaTransfer.finish()
    }

    private func consume(_ state: HeyCyanSessionState) {
        switch state {
        case .disconnected:
            clearDeviceStatus()
            mediaTransfer.abandonNetworkAssociation()
            connectionState = .disconnected
        case .scanning:
            connectionState = .scanning
        case .connecting(let name), .preparing(let name):
            connectionState = .connecting(name)
        case .ready(let name):
            connectionState = .connected(name)
            refreshKnownDeviceStatus()
        case .reconnecting(let name, _, _):
            clearDeviceStatus()
            connectionState = .connecting(name)
        case .unavailable(let reason):
            clearDeviceStatus()
            mediaTransfer.abandonNetworkAssociation()
            connectionState = .unavailable("Bluetooth: \(reason)")
        case .failed:
            clearDeviceStatus()
            mediaTransfer.abandonNetworkAssociation()
            connectionState = .disconnected
        }
    }

    private func consume(_ frame: HeyCyanFrame) {
        do {
            switch frame.command {
            case HeyCyanCommand.synchronizeBattery.family:
                let response = try responseDecoder.decodeBattery(frame)
                batteryStatus = GlassesBatteryStatus(
                    level: response.level,
                    isCharging: response.isCharging
                )
            case HeyCyanCommand.synchronizeDeviceInfo.family:
                let response = try responseDecoder.decodeDeviceInformation(frame)
                deviceInformation = GlassesDeviceInformation(
                    firmwareVersion: response.bluetoothFirmwareVersion,
                    hardwareVersion: response.bluetoothHardwareVersion,
                    networkFirmwareVersion: response.wifiFirmwareVersion,
                    networkHardwareVersion: response.wifiHardwareVersion
                )
            case HeyCyanCommand.readVolumeControl.family:
                apply(try responseDecoder.decodeVolumeControl(frame))
            case 0x59:
                consumeAssistantAudioPacket(frame.payload)
            case HeyCyanResponseDecoder.deviceNotificationFamily:
                let event = try responseDecoder.decodeDeviceEvent(frame)
                mediaTransfer.receiveDeviceEvent(event)
                switch event {
                case .battery(let response):
                    batteryStatus = GlassesBatteryStatus(
                        level: response.level,
                        isCharging: response.isCharging
                    )
                case .assistantListeningStarted:
                    beginAssistantAudioStream()
                case .assistantListeningEnded:
                    finishAssistantAudioStream()
                case .aiPhotoReady:
                    fetchReadyVisualCapture()
                default:
                    break
                }
            default:
                break
            }
        } catch {
            Task {
                await diagnostics.recordDiagnostic(
                    "Response decoding failed: \(error.localizedDescription)"
                )
            }
        }
    }

    private func refreshKnownDeviceStatus() {
        statusRefreshTask?.cancel()
        statusRefreshTask = Task { [weak self] in
            guard let self else { return }

            do {
                try await synchronizeClock()
            } catch is CancellationError {
                return
            } catch {
                await diagnostics.recordDiagnostic(
                    "Clock synchronization failed: \(error.localizedDescription)"
                )
            }

            guard !Task.isCancelled else { return }
            do {
                try await refreshBatteryStatus()
            } catch is CancellationError {
                return
            } catch {
                await diagnostics.recordDiagnostic(
                    "Battery synchronization failed: \(error.localizedDescription)"
                )
            }

            guard !Task.isCancelled else { return }
            do {
                try await refreshDeviceInformation()
            } catch is CancellationError {
                return
            } catch {
                await diagnostics.recordDiagnostic(
                    "Device-information synchronization failed: \(error.localizedDescription)"
                )
            }

            guard !Task.isCancelled else { return }
            do {
                try await refreshVolumeProfile()
            } catch is CancellationError {
                return
            } catch {
                await diagnostics.recordDiagnostic(
                    "Volume synchronization failed: \(error.localizedDescription)"
                )
            }

            guard !Task.isCancelled else { return }
            do {
                try await requestClassicBluetoothConnection()
            } catch is CancellationError {
                return
            } catch {
                await diagnostics.recordDiagnostic(
                    "Classic Bluetooth connection request failed: \(error.localizedDescription)"
                )
            }

            guard !Task.isCancelled else { return }
            do {
                let desired = defaults.object(forKey: desiredVoiceWakeKey) as? Bool ?? false
                try await refreshGlassesVoiceWake()
                if glassesVoiceWakeEnabled != desired {
                    try await setGlassesVoiceWakeEnabled(desired)
                }
            } catch is CancellationError {
                return
            } catch {
                await diagnostics.recordDiagnostic(
                    "Glasses voice-wake synchronization failed: \(error.localizedDescription)"
                )
            }
        }
    }

    private func apply(_ response: HeyCyanVolumeProfile) {
        volumeChannelCode = response.activeChannelCode
        volumeProfile = GlassesVolumeProfile(
            music: response.music.glassesLevel,
            calls: response.calls.glassesLevel,
            system: response.system.glassesLevel,
            activeChannel: GlassesVolumeChannel(rawValue: Int(response.activeChannelCode))
        )
    }

    private func beginAssistantAudioStream() {
        guard let opusDecoder else {
            let reason = opusDecoderStartupError ?? "native decoder unavailable"
            Task {
                await diagnostics.recordDiagnostic(
                    "Glasses Assistant audio could not start: \(reason)"
                )
            }
            return
        }

        // A verified hardware start is a session boundary. If a prior firmware end notification was
        // missed, close the stale local stream first so one bad turn cannot poison every later press.
        if isAssistantAudioStreaming {
            isAssistantAudioStreaming = false
            onAssistantAudioEvent?(.ended)
        }

        opusDecoder.reset()
        isAssistantAudioStreaming = true
        onAssistantAudioEvent?(.started(format: opusDecoder.outputFormat))
    }

    private func consumeAssistantAudioPacket(_ packet: Data) {
        guard isAssistantAudioStreaming, let opusDecoder else {
            Task {
                await diagnostics.recordDiagnostic(
                    "Ignored a HeyCyan 0x59 packet outside an active Assistant audio session."
                )
            }
            return
        }

        do {
            onAssistantAudioEvent?(.pcmBuffer(try opusDecoder.decode(packet)))
        } catch {
            Task {
                await diagnostics.recordDiagnostic(
                    "HeyCyan Opus decoding failed: \(error.localizedDescription)"
                )
            }
        }
    }

    private func finishAssistantAudioStream() {
        guard isAssistantAudioStreaming else { return }
        isAssistantAudioStreaming = false
        onAssistantAudioEvent?(.ended)
    }

    private func fetchReadyVisualCapture() {
        guard visualCaptureTask == nil else {
            Task {
                await diagnostics.recordDiagnostic(
                    "Ignored a duplicate visual-ready event while its JPEG was being fetched."
                )
            }
            return
        }

        visualCaptureTask = Task { [weak self] in
            guard let self else { return }
            do {
                let jpegData = try await thumbnailTransfer.fetchLatestThumbnail()
                try Task.checkCancellation()
                let capture = GlassesVisualCapture(
                    jpegData: jpegData,
                    providerID: id
                )
                visualCaptureTask = nil
                onVisualCapture?(capture)
                finishVisualCaptureRequest(result: .success(capture))
            } catch {
                visualCaptureTask = nil
                finishVisualCaptureRequest(result: .failure(error))
                await diagnostics.recordDiagnostic(
                    "Visual JPEG transfer failed: \(error.localizedDescription)"
                )
            }
        }
    }

    private func finishVisualCaptureRequest(
        id: UUID? = nil,
        result: Result<GlassesVisualCapture, Error>
    ) {
        guard let pending = pendingVisualCapture,
              id == nil || pending.id == id else { return }
        pendingVisualCapture = nil
        pending.timeoutTask.cancel()
        pending.continuation.resume(with: result)
    }

    private func clearDeviceStatus() {
        finishAssistantAudioStream()
        visualCaptureTask?.cancel()
        visualCaptureTask = nil
        finishVisualCaptureRequest(
            result: .failure(
                GlassesProviderError.connectionFailed(
                    "The glasses disconnected during visual capture."
                )
            )
        )
        statusRefreshTask?.cancel()
        statusRefreshTask = nil
        batteryStatus = nil
        deviceInformation = nil
        volumeProfile = nil
        glassesVoiceWakeEnabled = false
        isVideoRecording = false
        isAudioRecording = false
    }
}

private extension HeyCyanMediaTransferState {
    var glassesState: GlassesMediaTransferState {
        switch self {
        case .idle: return .idle
        case .checkingMediaCounts: return .checkingLibrary
        case .preparingBluetooth: return .preparing
        case .joiningNetwork: return .joiningNetwork
        case .awaitingManualNetworkJoin(let credentials):
            return .awaitingManualNetworkJoin(
                ssid: credentials.ssid,
                passphrase: credentials.passphrase
            )
        case .verifyingMediaServer: return .checkingLibrary
        case .ready(let items): return .ready(itemCount: items.count)
        case .downloading(let fileName): return .downloading(fileName: fileName)
        case .finishing: return .finishing
        case .failed(let reason): return .failed(reason: reason)
        }
    }
}

private extension HeyCyanMediaKind {
    var glassesKind: GlassesMediaKind {
        switch self {
        case .photo: return .photo
        case .video: return .video
        case .audio: return .audio
        }
    }

    init?(glassesKind: GlassesMediaKind) {
        switch glassesKind {
        case .photo: self = .photo
        case .video: self = .video
        case .audio: self = .audio
        }
    }
}

private extension GlassesVolumeLevel {
    var heyCyanLevel: HeyCyanVolumeLevel {
        HeyCyanVolumeLevel(
            minimum: UInt8(clamping: minimum),
            maximum: UInt8(clamping: maximum),
            current: UInt8(clamping: current)
        )
    }
}

private extension HeyCyanVolumeLevel {
    var glassesLevel: GlassesVolumeLevel {
        GlassesVolumeLevel(
            minimum: Int(minimum),
            maximum: Int(maximum),
            current: Int(current)
        )
    }
}

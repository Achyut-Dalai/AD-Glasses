import Foundation

@MainActor
final class HeyCyanGlassesProvider: NSObject,
    GlassesProvider,
    GlassesReconnecting,
    GlassesForgettable,
    GlassesPhotoCapturing,
    GlassesBatteryProviding,
    GlassesDeviceInformationProviding,
    GlassesDeviceManagementPlanning,
    GlassesVolumeProviding,
    GlassesAssistantAudioProviding,
    GlassesDiagnosticsProviding
{
    let id = "heycyan"
    let displayName = "HeyCyan"
    let capabilities: Set<GlassesCapability> = [
        .bluetoothConnection,
        .photoCapture,
        .deviceInformation,
        .volumeControl
    ]
    let deviceManagementPlaceholders: [GlassesDeviceManagementPlaceholder] = [
        GlassesDeviceManagementPlaceholder(
            operation: .firmwareUpdate,
            reason: "Awaiting a captured update, recovery, and rollback session."
        ),
        GlassesDeviceManagementPlaceholder(
            operation: .factoryReset,
            reason: "Awaiting a dedicated result and post-reset pairing/recovery trace."
        ),
        GlassesDeviceManagementPlaceholder(
            operation: .forcedRestart,
            reason: "A restart call site exists; its result and recovery trace are not verified."
        ),
        GlassesDeviceManagementPlaceholder(
            operation: .customWakePhrase,
            reason: "This firmware exposes wake listening on/off, not custom phrase data."
        )
    ]

    var onConnectionStateChange: ((GlassesConnectionState) -> Void)?
    var onBatteryStatusChange: ((GlassesBatteryStatus?) -> Void)?
    var onDeviceInformationChange: ((GlassesDeviceInformation?) -> Void)?
    var onVolumeProfileChange: ((GlassesVolumeProfile?) -> Void)?
    var onAssistantAudioEvent: ((GlassesAssistantAudioEvent) -> Void)?

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

    private let transport: HeyCyanBLETransport
    private let session: HeyCyanSession
    private let mediaTransfer: HeyCyanMediaTransferCoordinator
    private let diagnostics: HeyCyanDiagnosticRecorder
    private let opusDecoder: HeyCyanOpusDecoder?
    private let opusDecoderStartupError: String?
    private let responseDecoder = HeyCyanResponseDecoder()
    private let defaults: UserDefaults
    private let lastPeripheralIdentifierKey = "heycyan.lastPeripheralIdentifier.v1"
    private var discoveredDevices: [UUID: HeyCyanBLEDevice] = [:]
    private var statusRefreshTask: Task<Void, Never>?
    private var volumeChannelCode: UInt8 = 0x03
    private var isAssistantAudioStreaming = false

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
        session = HeyCyanSession(transport: transport)
        mediaTransfer = HeyCyanMediaTransferCoordinator(session: session)
        self.defaults = defaults
        self.diagnostics = diagnostics
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

    // Confirmed commands that still lack complete response/state semantics remain beneath the
    // provider boundary. They are deliberately not advertised as completed product capabilities.
    func requestVideoOperationForHardwareValidation() async throws {
        let frame = try await session.send(.startVideoRecording)
        _ = try responseDecoder.decodeControlAcknowledgement(frame, expectedWorkType: 0x02)
    }

    func requestAudioRecordingOperationForHardwareValidation() async throws {
        let frame = try await session.send(.startAudioRecording)
        _ = try responseDecoder.decodeControlAcknowledgement(frame, expectedWorkType: 0x08)
    }

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
        guard !isAssistantAudioStreaming else { return }
        guard let opusDecoder else {
            let reason = opusDecoderStartupError ?? "native decoder unavailable"
            Task {
                await diagnostics.recordDiagnostic(
                    "Glasses Assistant audio could not start: \(reason)"
                )
            }
            return
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

    private func clearDeviceStatus() {
        finishAssistantAudioStream()
        statusRefreshTask?.cancel()
        statusRefreshTask = nil
        batteryStatus = nil
        deviceInformation = nil
        volumeProfile = nil
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

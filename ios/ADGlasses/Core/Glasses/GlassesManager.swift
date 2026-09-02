import Combine
import Foundation

@MainActor
final class GlassesManager: ObservableObject {
    @Published private(set) var providers: [GlassesProviderSummary]
    @Published private(set) var devices: [GlassesDevice] = []
    @Published private(set) var selectedProviderID: String
    @Published private(set) var activeProviderID: String?
    @Published var errorMessage: String?
    @Published private(set) var assistantInputState: GlassesAssistantInputState = .idle
    @Published private(set) var isPassiveDiagnosticsScanRunning = false
    @Published private(set) var mediaTransferState: GlassesMediaTransferState = .idle
    @Published private(set) var latestVisualCapture: GlassesVisualCapture?
    @Published private(set) var isVideoRecording = false
    @Published private(set) var isAudioRecording = false

    var onAssistantAudioEvent: ((GlassesAssistantAudioEvent) -> Void)?
    var onVisualCapture: ((GlassesVisualCapture) -> Void)?

    @Published private var batteryStatuses: [String: GlassesBatteryStatus] = [:]
    @Published private var deviceInformationByProvider: [String: GlassesDeviceInformation] = [:]
    @Published private var volumeProfiles: [String: GlassesVolumeProfile] = [:]
    @Published private var voiceWakeStates: [String: Bool] = [:]

    private let providerInstances: [String: any GlassesProvider]
    private var scanRequestID = UUID()

    var selectedProvider: GlassesProviderSummary {
        providers.first { $0.id == selectedProviderID } ?? providers[0]
    }

    var connectionState: GlassesConnectionState {
        if let activeProviderID,
           let activeProvider = providers.first(where: { $0.id == activeProviderID }) {
            return activeProvider.connectionState
        }
        return selectedProvider.connectionState
    }

    var batteryLevel: Int? {
        batteryStatus?.level
    }

    var batteryStatus: GlassesBatteryStatus? {
        batteryStatuses[activeProviderID ?? selectedProviderID]
    }

    var hasRememberedDevice: Bool {
        let providerID = activeProviderID ?? selectedProviderID
        return (providerInstances[providerID] as? any GlassesForgettable)?
            .hasRememberedDevice == true
    }

    var deviceInformation: GlassesDeviceInformation? {
        deviceInformationByProvider[activeProviderID ?? selectedProviderID]
    }

    var volumeProfile: GlassesVolumeProfile? {
        volumeProfiles[activeProviderID ?? selectedProviderID]
    }

    var supportsGlassesVoiceWake: Bool {
        let providerID = activeProviderID ?? selectedProviderID
        return providerInstances[providerID] is any GlassesVoiceWakeProviding
    }

    var glassesVoiceWakeEnabled: Bool {
        voiceWakeStates[activeProviderID ?? selectedProviderID] ?? false
    }

    var deviceManagementPlaceholders: [GlassesDeviceManagementPlaceholder] {
        let providerID = activeProviderID ?? selectedProviderID
        return (providerInstances[providerID] as? any GlassesDeviceManagementPlanning)?
            .deviceManagementPlaceholders ?? []
    }

    init(providers: [any GlassesProvider]) {
        precondition(!providers.isEmpty, "GlassesManager requires at least one provider")
        precondition(Set(providers.map(\.id)).count == providers.count, "Provider IDs must be unique")

        providerInstances = Dictionary(uniqueKeysWithValues: providers.map { ($0.id, $0) })
        self.providers = providers.map {
            GlassesProviderSummary(
                id: $0.id,
                displayName: Self.consumerProviderName(id: $0.id, technicalName: $0.displayName),
                capabilities: $0.capabilities,
                connectionState: Self.consumerConnectionState($0.connectionState, providerID: $0.id)
            )
        }
        selectedProviderID = providers[0].id
        activeProviderID = providers.first(where: { $0.connectionState.isConnected })?.id

        for provider in providers {
            let providerID = provider.id
            provider.onConnectionStateChange = { [weak self] state in
                self?.updateProvider(providerID, connectionState: state)
            }

            if let batteryProvider = provider as? any GlassesBatteryProviding {
                updateProvider(providerID, batteryStatus: batteryProvider.batteryStatus)
                batteryProvider.onBatteryStatusChange = { [weak self] status in
                    self?.updateProvider(providerID, batteryStatus: status)
                }
            }

            if let informationProvider = provider as? any GlassesDeviceInformationProviding {
                updateProvider(providerID, deviceInformation: informationProvider.deviceInformation)
                informationProvider.onDeviceInformationChange = { [weak self] information in
                    self?.updateProvider(providerID, deviceInformation: information)
                }
            }


            if let volumeProvider = provider as? any GlassesVolumeProviding {
                updateProvider(providerID, volumeProfile: volumeProvider.volumeProfile)
                volumeProvider.onVolumeProfileChange = { [weak self] profile in
                    self?.updateProvider(providerID, volumeProfile: profile)
                }
            }

            if let audioProvider = provider as? any GlassesAssistantAudioProviding {
                audioProvider.onAssistantAudioEvent = { [weak self] event in
                    self?.consumeAssistantAudioEvent(event, from: providerID)
                }
            }

            if let mediaProvider = provider as? any GlassesMediaTransferring {
                mediaProvider.onMediaTransferStateChange = { [weak self] state in
                    guard self?.activeProviderID == providerID else { return }
                    self?.mediaTransferState = state
                }
            }

            if let visualProvider = provider as? any GlassesVisualCapturing {
                visualProvider.onVisualCapture = { [weak self] capture in
                    guard self?.activeProviderID == providerID else { return }
                    self?.latestVisualCapture = capture
                    self?.onVisualCapture?(capture)
                }
            }

            if let videoProvider = provider as? any GlassesVideoRecording {
                if provider.connectionState.isConnected {
                    isVideoRecording = videoProvider.isVideoRecording
                }
                videoProvider.onVideoRecordingStateChange = { [weak self] isRecording in
                    guard self?.activeProviderID == providerID else { return }
                    self?.isVideoRecording = isRecording
                }
            }

            if let recordingProvider = provider as? any GlassesAudioRecording {
                if provider.connectionState.isConnected {
                    isAudioRecording = recordingProvider.isAudioRecording
                }
                recordingProvider.onAudioRecordingStateChange = { [weak self] isRecording in
                    guard self?.activeProviderID == providerID else { return }
                    self?.isAudioRecording = isRecording
                }
            }


            if let wakeProvider = provider as? any GlassesVoiceWakeProviding {
                voiceWakeStates[providerID] = wakeProvider.glassesVoiceWakeEnabled
                wakeProvider.onGlassesVoiceWakeChange = { [weak self] enabled in
                    self?.voiceWakeStates[providerID] = enabled
                }
            }
        }
    }

    func selectProvider(_ providerID: String) {
        guard providerInstances[providerID] != nil else { return }
        if let activeProviderID, activeProviderID != providerID {
            errorMessage = "Disconnect the active glasses before choosing another integration."
            return
        }
        scanRequestID = UUID()
        selectedProviderID = providerID
        devices.removeAll()
        errorMessage = nil
    }

    func scan() async {
        errorMessage = nil
        devices.removeAll()

        let providerID = selectedProviderID
        let requestID = UUID()
        scanRequestID = requestID

        guard let provider = providerInstances[providerID] else { return }
        do {
            let result = try await provider.scan()
            guard scanRequestID == requestID, selectedProviderID == providerID else { return }
            devices = result.map(Self.consumerDevice)
        } catch {
            if error is CancellationError { return }
            guard scanRequestID == requestID, selectedProviderID == providerID else { return }
            errorMessage = error.localizedDescription
        }
    }

    func connect(to device: GlassesDevice) async {
        errorMessage = nil
        guard let provider = providerInstances[device.providerID] else {
            errorMessage = GlassesProviderError.deviceNotFound.localizedDescription
            return
        }

        selectedProviderID = device.providerID

        do {
            for (id, otherProvider) in providerInstances where id != device.providerID {
                if otherProvider.connectionState.isConnected {
                    await otherProvider.disconnect()
                    if activeProviderID == id {
                        activeProviderID = nil
                    }
                }
            }
            try await provider.connect(to: device)
        } catch {
            if error is CancellationError { return }
            errorMessage = error.localizedDescription
        }
    }

    func disconnect() async {
        errorMessage = nil
        let providerID = activeProviderID ?? selectedProviderID
        await providerInstances[providerID]?.disconnect()
        if activeProviderID == providerID {
            activeProviderID = nil
        }
    }

    func forgetLastDevice() async {
        errorMessage = nil
        let providerID = activeProviderID ?? selectedProviderID
        guard let provider = providerInstances[providerID] as? any GlassesForgettable else { return }
        await provider.forgetLastDevice()
        if activeProviderID == providerID {
            activeProviderID = nil
        }
        devices.removeAll { $0.providerID == providerID }
    }

    func reconnectLastDevice() async -> Bool {
        errorMessage = nil
        if connectionState.isConnected { return true }

        let orderedProviderIDs = [selectedProviderID] + providers
            .map(\.id)
            .filter { $0 != selectedProviderID }

        for providerID in orderedProviderIDs {
            guard let reconnectingProvider = providerInstances[providerID] as? any GlassesReconnecting else {
                continue
            }
            do {
                if let activeProviderID, activeProviderID != providerID {
                    await providerInstances[activeProviderID]?.disconnect()
                    self.activeProviderID = nil
                }
                selectedProviderID = providerID
                if try await reconnectingProvider.reconnectLastDevice() {
                    return true
                }
            } catch is CancellationError {
                return false
            } catch {
                errorMessage = error.localizedDescription
            }
        }
        return false
    }

    func supports(_ capability: GlassesCapability) -> Bool {
        let providerID = activeProviderID ?? selectedProviderID
        return providers
            .first(where: { $0.id == providerID })?
            .capabilities
            .contains(capability) == true
    }

    func requestPhotoCapture() async -> Bool {
        errorMessage = nil
        guard let activeProviderID,
              let provider = providerInstances[activeProviderID] as? any GlassesPhotoCapturing else {
            errorMessage = "Connect glasses with photo capture support first."
            return false
        }
        do {
            try await provider.requestPhotoCapture()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func toggleVideoRecording() async -> Bool {
        errorMessage = nil
        guard let activeProviderID,
              let provider = providerInstances[activeProviderID] as? any GlassesVideoRecording else {
            errorMessage = "Connect glasses with video recording support first."
            return false
        }
        do {
            if provider.isVideoRecording {
                try await provider.stopVideoRecording()
            } else {
                if let audioProvider = providerInstances[activeProviderID] as? any GlassesAudioRecording,
                   audioProvider.isAudioRecording {
                    try await audioProvider.stopAudioRecording()
                    isAudioRecording = audioProvider.isAudioRecording
                }
                try await provider.startVideoRecording()
            }
            isVideoRecording = provider.isVideoRecording
            return true
        } catch is CancellationError {
            return false
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func toggleAudioRecording() async -> Bool {
        errorMessage = nil
        guard let activeProviderID,
              let provider = providerInstances[activeProviderID] as? any GlassesAudioRecording else {
            errorMessage = "Connect glasses with audio recording support first."
            return false
        }
        do {
            if provider.isAudioRecording {
                try await provider.stopAudioRecording()
            } else {
                if let videoProvider = providerInstances[activeProviderID] as? any GlassesVideoRecording,
                   videoProvider.isVideoRecording {
                    try await videoProvider.stopVideoRecording()
                    isVideoRecording = videoProvider.isVideoRecording
                }
                try await provider.startAudioRecording()
            }
            isAudioRecording = provider.isAudioRecording
            return true
        } catch is CancellationError {
            return false
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func requestVisualCapture() async -> GlassesVisualCapture? {
        errorMessage = nil
        guard let activeProviderID,
              let provider = providerInstances[activeProviderID] as? any GlassesVisualCapturing else {
            errorMessage = "Connect glasses with visual capture support first."
            return nil
        }

        var lastError: Error?
        for attempt in 0 ..< 2 {
            do {
                let capture = try await provider.requestVisualCapture()
                latestVisualCapture = capture
                return capture
            } catch is CancellationError {
                return nil
            } catch {
                lastError = error
                if attempt == 0 {
                    try? await Task.sleep(for: .milliseconds(250))
                }
            }
        }

        errorMessage = lastError?.localizedDescription ?? "Visual capture failed."
        return nil
    }

    var supportsMediaTransfer: Bool {
        guard let activeProviderID,
              supports(.mediaTransfer) else { return false }
        return providerInstances[activeProviderID] is any GlassesMediaTransferring
    }

    func prepareMediaTransfer() async throws -> [GlassesMediaItem] {
        errorMessage = nil
        guard let activeProviderID,
              supports(.mediaTransfer),
              let provider = providerInstances[activeProviderID] as? any GlassesMediaTransferring else {
            throw GlassesProviderError.notConfigured(
                "Wi-Fi media sync is unavailable for the connected glasses."
            )
        }
        do {
            return try await provider.prepareMediaTransfer()
        } catch {
            errorMessage = error.localizedDescription
            throw error
        }
    }

    func continueMediaTransferAfterManualNetworkJoin() {
        guard let activeProviderID,
              let provider = providerInstances[activeProviderID] as? any GlassesMediaTransferring else {
            return
        }
        provider.continueMediaTransferAfterManualNetworkJoin()
    }

    func downloadMediaItem(_ item: GlassesMediaItem, to destinationURL: URL) async throws {
        guard let activeProviderID,
              item.providerID == activeProviderID,
              let provider = providerInstances[activeProviderID] as? any GlassesMediaTransferring else {
            throw GlassesProviderError.notConfigured("The media item does not belong to the connected glasses.")
        }
        do {
            try await provider.downloadMediaItem(item, to: destinationURL)
        } catch {
            errorMessage = error.localizedDescription
            throw error
        }
    }

    func finishMediaTransfer() async {
        guard let activeProviderID,
              let provider = providerInstances[activeProviderID] as? any GlassesMediaTransferring else {
            mediaTransferState = .idle
            return
        }
        do {
            try await provider.finishMediaTransfer()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func cancelMediaTransfer() {
        let providerID = activeProviderID ?? selectedProviderID
        (providerInstances[providerID] as? any GlassesMediaTransferring)?.cancelMediaTransfer()
        mediaTransferState = .idle
    }

    func refreshVolumeProfile() async {
        errorMessage = nil
        guard let activeProviderID,
              let provider = providerInstances[activeProviderID] as? any GlassesVolumeProviding else {
            errorMessage = "Connect glasses with audio controls first."
            return
        }
        do {
            try await provider.refreshVolumeProfile()
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func setVolume(_ value: Int, for channel: GlassesVolumeChannel) async {
        errorMessage = nil
        guard let activeProviderID,
              let provider = providerInstances[activeProviderID] as? any GlassesVolumeProviding else {
            errorMessage = "Connect glasses with audio controls first."
            return
        }
        do {
            try await provider.setVolume(value, for: channel)
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    var supportsHardwareDiagnostics: Bool {
        let providerID = activeProviderID ?? selectedProviderID
        return providerInstances[providerID] is any GlassesDiagnosticsProviding
    }

    func isHardwareDiagnosticsEnabled() async -> Bool? {
        let providerID = activeProviderID ?? selectedProviderID
        guard let provider = providerInstances[providerID] as? any GlassesDiagnosticsProviding else {
            return nil
        }
        return await provider.isHardwareDiagnosticsEnabled()
    }

    func setHardwareDiagnosticsEnabled(_ enabled: Bool) async {
        let providerID = activeProviderID ?? selectedProviderID
        guard let provider = providerInstances[providerID] as? any GlassesDiagnosticsProviding else {
            return
        }
        await provider.setHardwareDiagnosticsEnabled(enabled)
    }

    func hardwareDiagnosticsURL() async throws -> URL? {
        let providerID = activeProviderID ?? selectedProviderID
        guard let provider = providerInstances[providerID] as? any GlassesDiagnosticsProviding else {
            return nil
        }
        return try await provider.hardwareDiagnosticsURL()
    }

    func clearHardwareDiagnostics() async throws {
        let providerID = activeProviderID ?? selectedProviderID
        guard let provider = providerInstances[providerID] as? any GlassesDiagnosticsProviding else {
            return
        }
        try await provider.clearHardwareDiagnostics()
    }

    func runPassiveDiscoveryDiagnostics() async {
        let providerID = activeProviderID ?? selectedProviderID
        guard !isPassiveDiagnosticsScanRunning,
              let provider = providerInstances[providerID] as? any GlassesDiagnosticsProviding else {
            return
        }
        errorMessage = nil
        isPassiveDiagnosticsScanRunning = true
        defer { isPassiveDiagnosticsScanRunning = false }
        do {
            try await provider.runPassiveDiscoveryDiagnostics(duration: .seconds(60))
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func setGlassesVoiceWakeEnabled(_ enabled: Bool) async {
        errorMessage = nil
        guard let activeProviderID,
              let provider = providerInstances[activeProviderID] as? any GlassesVoiceWakeProviding else {
            errorMessage = "Connect AD Glasses before changing glasses voice wake."
            return
        }
        do {
            try await provider.setGlassesVoiceWakeEnabled(enabled)
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func technicalProviderName(for providerID: String) -> String {
        providerInstances[providerID]?.displayName ?? providerID
    }

    private func updateProvider(_ providerID: String, connectionState: GlassesConnectionState) {
        guard let index = providers.firstIndex(where: { $0.id == providerID }) else { return }
        let connectionState = Self.consumerConnectionState(connectionState, providerID: providerID)
        providers[index].connectionState = connectionState

        if connectionState.isConnected {
            activeProviderID = providerID
            selectedProviderID = providerID
            isVideoRecording = (providerInstances[providerID] as? any GlassesVideoRecording)?.isVideoRecording ?? false
            isAudioRecording = (providerInstances[providerID] as? any GlassesAudioRecording)?.isAudioRecording ?? false
        } else if activeProviderID == providerID,
                  case .disconnected = connectionState {
            activeProviderID = nil
        } else if activeProviderID == providerID,
                  case .unavailable = connectionState {
            activeProviderID = nil
        }

        if !connectionState.isConnected {
            batteryStatuses.removeValue(forKey: providerID)
            deviceInformationByProvider.removeValue(forKey: providerID)
            volumeProfiles.removeValue(forKey: providerID)
            mediaTransferState = .idle
            if activeProviderID == nil || activeProviderID == providerID {
                assistantInputState = .idle
                latestVisualCapture = nil
                isVideoRecording = false
                isAudioRecording = false
            }
        }

        if providerID == selectedProviderID,
           case .unavailable = connectionState {
            devices.removeAll()
        }
    }


    private static func consumerProviderName(id: String, technicalName: String) -> String {
        id == "heycyan" ? "AD Glasses" : technicalName
    }

    private static func consumerDevice(_ device: GlassesDevice) -> GlassesDevice {
        guard device.providerID == "heycyan" else { return device }
        return GlassesDevice(
            id: device.id,
            name: "AD Glasses",
            providerID: device.providerID,
            signalStrength: device.signalStrength
        )
    }

    private static func consumerConnectionState(
        _ state: GlassesConnectionState,
        providerID: String
    ) -> GlassesConnectionState {
        guard providerID == "heycyan" else { return state }
        switch state {
        case .connecting:
            return .connecting("AD Glasses")
        case .connected:
            return .connected("AD Glasses")
        default:
            return state
        }
    }

    private func updateProvider(_ providerID: String, batteryStatus: GlassesBatteryStatus?) {
        guard providerInstances[providerID] != nil else { return }

        if let batteryStatus {
            batteryStatuses[providerID] = GlassesBatteryStatus(
                level: min(max(batteryStatus.level, 0), 100),
                isCharging: batteryStatus.isCharging
            )
        } else {
            batteryStatuses.removeValue(forKey: providerID)
        }
    }

    private func updateProvider(
        _ providerID: String,
        deviceInformation: GlassesDeviceInformation?
    ) {
        guard providerInstances[providerID] != nil else { return }

        if let deviceInformation {
            deviceInformationByProvider[providerID] = deviceInformation
        } else {
            deviceInformationByProvider.removeValue(forKey: providerID)
        }
    }

    private func updateProvider(_ providerID: String, volumeProfile: GlassesVolumeProfile?) {
        guard providerInstances[providerID] != nil else { return }

        if let volumeProfile {
            volumeProfiles[providerID] = volumeProfile
        } else {
            volumeProfiles.removeValue(forKey: providerID)
        }
    }

    private func consumeAssistantAudioEvent(
        _ event: GlassesAssistantAudioEvent,
        from providerID: String
    ) {
        guard providerID == activeProviderID else { return }
        switch event {
        case .started:
            assistantInputState = .listening
        case .pcmBuffer:
            guard assistantInputState == .listening else { return }
        case .ended:
            assistantInputState = .idle
        }
        onAssistantAudioEvent?(event)
    }
}

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

    var onAssistantAudioEvent: ((GlassesAssistantAudioEvent) -> Void)?

    @Published private var batteryStatuses: [String: GlassesBatteryStatus] = [:]
    @Published private var deviceInformationByProvider: [String: GlassesDeviceInformation] = [:]
    @Published private var volumeProfiles: [String: GlassesVolumeProfile] = [:]

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
                displayName: $0.displayName,
                capabilities: $0.capabilities,
                connectionState: $0.connectionState
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
            devices = result
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

    private func updateProvider(_ providerID: String, connectionState: GlassesConnectionState) {
        guard let index = providers.firstIndex(where: { $0.id == providerID }) else { return }
        providers[index].connectionState = connectionState

        if connectionState.isConnected {
            activeProviderID = providerID
            selectedProviderID = providerID
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
            if activeProviderID == nil || activeProviderID == providerID {
                assistantInputState = .idle
            }
        }

        if providerID == selectedProviderID,
           case .unavailable = connectionState {
            devices.removeAll()
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

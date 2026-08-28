import Combine
import Foundation

@MainActor
final class GlassesManager: ObservableObject {
    @Published private(set) var providers: [GlassesProviderSummary]
    @Published private(set) var devices: [GlassesDevice] = []
    @Published private(set) var selectedProviderID: String
    @Published var errorMessage: String?

    @Published private var batteryLevels: [String: Int] = [:]

    private let providerInstances: [String: any GlassesProvider]
    private var scanRequestID = UUID()

    var selectedProvider: GlassesProviderSummary {
        providers.first { $0.id == selectedProviderID } ?? providers[0]
    }

    var connectionState: GlassesConnectionState {
        selectedProvider.connectionState
    }

    var batteryLevel: Int? {
        batteryLevels[selectedProviderID]
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

        for provider in providers {
            let providerID = provider.id
            provider.onConnectionStateChange = { [weak self] state in
                self?.updateProvider(providerID, connectionState: state)
            }

            if let batteryProvider = provider as? any GlassesBatteryProviding {
                updateProvider(providerID, batteryLevel: batteryProvider.batteryLevel)
                batteryProvider.onBatteryLevelChange = { [weak self] level in
                    self?.updateProvider(providerID, batteryLevel: level)
                }
            }
        }
    }

    func selectProvider(_ providerID: String) {
        guard providerInstances[providerID] != nil else { return }
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
        await providerInstances[selectedProviderID]?.disconnect()
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
        selectedProvider.capabilities.contains(capability)
    }

    private func updateProvider(_ providerID: String, connectionState: GlassesConnectionState) {
        guard let index = providers.firstIndex(where: { $0.id == providerID }) else { return }
        providers[index].connectionState = connectionState

        if !connectionState.isConnected {
            batteryLevels.removeValue(forKey: providerID)
        }

        if providerID == selectedProviderID,
           case .unavailable = connectionState {
            devices.removeAll()
        }
    }

    private func updateProvider(_ providerID: String, batteryLevel: Int?) {
        guard providerInstances[providerID] != nil else { return }

        if let batteryLevel {
            batteryLevels[providerID] = min(max(batteryLevel, 0), 100)
        } else {
            batteryLevels.removeValue(forKey: providerID)
        }
    }
}

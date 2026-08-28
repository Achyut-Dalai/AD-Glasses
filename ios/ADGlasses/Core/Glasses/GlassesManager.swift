import Combine
import Foundation

@MainActor
final class GlassesManager: ObservableObject {
    @Published private(set) var providers: [GlassesProviderSummary]
    @Published private(set) var devices: [GlassesDevice] = []
    @Published private(set) var selectedProviderID: String
    @Published var errorMessage: String?

    private let providerInstances: [String: any GlassesProvider]
    private var scanRequestID = UUID()

    var selectedProvider: GlassesProviderSummary {
        providers.first { $0.id == selectedProviderID } ?? providers[0]
    }

    var connectionState: GlassesConnectionState {
        selectedProvider.connectionState
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

    func supports(_ capability: GlassesCapability) -> Bool {
        selectedProvider.capabilities.contains(capability)
    }

    private func updateProvider(_ providerID: String, connectionState: GlassesConnectionState) {
        guard let index = providers.firstIndex(where: { $0.id == providerID }) else { return }
        providers[index].connectionState = connectionState

        if providerID == selectedProviderID,
           case .unavailable = connectionState {
            devices.removeAll()
        }
    }
}

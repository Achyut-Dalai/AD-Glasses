import Combine
import Foundation

@MainActor
final class GlassesManager: ObservableObject {
    @Published private(set) var devices: [GlassesDevice] = []
    @Published private(set) var connectionState: GlassesConnectionState = .disconnected
    @Published var errorMessage: String?

    let heyCyan: HeyCyanGlassesProvider
    let meta: MetaGlassesProvider

    init() {
        let heyCyan = HeyCyanGlassesProvider()
        let meta = MetaGlassesProvider()
        self.heyCyan = heyCyan
        self.meta = meta
        connectionState = heyCyan.connectionState
        observeHeyCyanState()
    }

    init(
        heyCyan: HeyCyanGlassesProvider,
        meta: MetaGlassesProvider
    ) {
        self.heyCyan = heyCyan
        self.meta = meta
        connectionState = heyCyan.connectionState
        observeHeyCyanState()
    }

    func scanHeyCyan() async {
        errorMessage = nil
        do {
            devices = try await heyCyan.scan()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func connect(to device: GlassesDevice) async {
        errorMessage = nil
        do {
            try await heyCyan.connect(to: device)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func disconnect() async {
        errorMessage = nil
        await heyCyan.disconnect()
    }

    private func observeHeyCyanState() {
        heyCyan.onConnectionStateChange = { [weak self] state in
            self?.connectionState = state
        }
    }
}

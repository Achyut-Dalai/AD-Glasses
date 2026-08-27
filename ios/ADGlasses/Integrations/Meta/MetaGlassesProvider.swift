import Foundation

@MainActor
final class MetaGlassesProvider: GlassesProvider {
    let id = "meta-experimental"
    let displayName = "Meta glasses"
    let vendor: GlassesVendor = .meta
    let supportLevel: GlassesSupportLevel = .experimental
    let capabilities: Set<GlassesCapability> = []

    var onConnectionStateChange: ((GlassesConnectionState) -> Void)?

    private(set) var connectionState: GlassesConnectionState = .unavailable("Experimental — SDK not linked") {
        didSet { onConnectionStateChange?(connectionState) }
    }

    func scan() async throws -> [GlassesDevice] {
        throw GlassesProviderError.experimentalIntegration
    }

    func connect(to device: GlassesDevice) async throws {
        throw GlassesProviderError.experimentalIntegration
    }

    func disconnect() async {
        connectionState = .unavailable("Experimental — SDK not linked")
    }
}

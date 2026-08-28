import Foundation

@MainActor
final class MetaGlassesProvider: GlassesProvider {
    let id = "meta"
    let displayName = "Meta"
    let vendor: GlassesVendor = .meta
    let capabilities: Set<GlassesCapability> = []

    var onConnectionStateChange: ((GlassesConnectionState) -> Void)?

    private(set) var connectionState: GlassesConnectionState = .unavailable("SDK not configured") {
        didSet { onConnectionStateChange?(connectionState) }
    }

    func scan() async throws -> [GlassesDevice] {
        throw GlassesProviderError.notConfigured(
            "Meta integration is not configured in this build."
        )
    }

    func connect(to device: GlassesDevice) async throws {
        throw GlassesProviderError.notConfigured(
            "Meta integration is not configured in this build."
        )
    }

    func disconnect() async {
        connectionState = .unavailable("SDK not configured")
    }
}

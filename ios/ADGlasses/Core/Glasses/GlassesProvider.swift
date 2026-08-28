import Foundation

@MainActor
protocol GlassesProvider: AnyObject {
    var id: String { get }
    var displayName: String { get }
    var vendor: GlassesVendor { get }
    var capabilities: Set<GlassesCapability> { get }
    var connectionState: GlassesConnectionState { get }
    var onConnectionStateChange: ((GlassesConnectionState) -> Void)? { get set }

    func scan() async throws -> [GlassesDevice]
    func connect(to device: GlassesDevice) async throws
    func disconnect() async
}

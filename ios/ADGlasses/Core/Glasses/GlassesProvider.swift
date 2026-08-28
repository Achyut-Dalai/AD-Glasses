import Foundation

@MainActor
protocol GlassesProvider: AnyObject {
    var id: String { get }
    var displayName: String { get }
    var capabilities: Set<GlassesCapability> { get }
    var connectionState: GlassesConnectionState { get }
    var onConnectionStateChange: ((GlassesConnectionState) -> Void)? { get set }

    func scan() async throws -> [GlassesDevice]
    func connect(to device: GlassesDevice) async throws
    func disconnect() async
}

@MainActor
protocol GlassesBatteryProviding: AnyObject {
    var batteryLevel: Int? { get }
    var onBatteryLevelChange: ((Int?) -> Void)? { get set }
}

@MainActor
protocol GlassesReconnecting: AnyObject {
    func reconnectLastDevice() async throws -> Bool
}

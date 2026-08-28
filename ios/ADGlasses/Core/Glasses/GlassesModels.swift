import Foundation

enum GlassesVendor: String, CaseIterable, Sendable {
    case heyCyan = "HeyCyan"
    case meta = "Meta"
}

enum GlassesCapability: String, Hashable, Sendable {
    case bluetoothConnection
    case microphoneAudio
    case camera
    case mediaTransfer
    case notifications
}

struct GlassesDevice: Identifiable, Hashable, Sendable {
    let id: UUID
    let name: String
    let vendor: GlassesVendor
    let signalStrength: Int?
}

enum GlassesConnectionState: Equatable, Sendable {
    case disconnected
    case scanning
    case connecting(String)
    case connected(String)
    case unavailable(String)

    var label: String {
        switch self {
        case .disconnected:
            return "Disconnected"
        case .scanning:
            return "Scanning"
        case .connecting(let name):
            return "Connecting to \(name)"
        case .connected(let name):
            return "Connected to \(name)"
        case .unavailable(let reason):
            return reason
        }
    }
}

enum GlassesProviderError: LocalizedError {
    case bluetoothUnavailable(String)
    case deviceNotFound
    case connectionFailed(String)
    case notConfigured(String)

    var errorDescription: String? {
        switch self {
        case .bluetoothUnavailable(let reason):
            return "Bluetooth is unavailable: \(reason)"
        case .deviceNotFound:
            return "The selected glasses are no longer available. Scan again."
        case .connectionFailed(let reason):
            return "Could not connect to the glasses: \(reason)"
        case .notConfigured(let reason):
            return reason
        }
    }
}

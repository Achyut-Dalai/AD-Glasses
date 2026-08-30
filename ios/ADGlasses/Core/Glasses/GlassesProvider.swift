@preconcurrency import AVFoundation
import Foundation

enum GlassesAssistantAudioEvent {
    case started(format: AVAudioFormat)
    case pcmBuffer(AVAudioPCMBuffer)
    case ended
}

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
    var batteryStatus: GlassesBatteryStatus? { get }
    var onBatteryStatusChange: ((GlassesBatteryStatus?) -> Void)? { get set }
    func refreshBatteryStatus() async throws
}

@MainActor
protocol GlassesDeviceInformationProviding: AnyObject {
    var deviceInformation: GlassesDeviceInformation? { get }
    var onDeviceInformationChange: ((GlassesDeviceInformation?) -> Void)? { get set }
    func refreshDeviceInformation() async throws
}

/// Read-only roadmap metadata. This deliberately has no execute method: destructive or firmware
/// operations become separate capability protocols only after their wire behavior is verified.
@MainActor
protocol GlassesDeviceManagementPlanning: AnyObject {
    var deviceManagementPlaceholders: [GlassesDeviceManagementPlaceholder] { get }
}

@MainActor
protocol GlassesVolumeProviding: AnyObject {
    var volumeProfile: GlassesVolumeProfile? { get }
    var onVolumeProfileChange: ((GlassesVolumeProfile?) -> Void)? { get set }
    func refreshVolumeProfile() async throws
    func setVolume(_ value: Int, for channel: GlassesVolumeChannel) async throws
}

@MainActor
protocol GlassesAssistantAudioProviding: AnyObject {
    var onAssistantAudioEvent: ((GlassesAssistantAudioEvent) -> Void)? { get set }
}

@MainActor
protocol GlassesReconnecting: AnyObject {
    func reconnectLastDevice() async throws -> Bool
}

@MainActor
protocol GlassesForgettable: AnyObject {
    var hasRememberedDevice: Bool { get }
    func forgetLastDevice() async
}

@MainActor
protocol GlassesPhotoCapturing: AnyObject {
    /// Returns after the verified request has been queued to the ready provider transport.
    /// A provider must not report a completed capture until it can parse the device response.
    func requestPhotoCapture() async throws
}

@MainActor
protocol GlassesDiagnosticsProviding: AnyObject {
    func isHardwareDiagnosticsEnabled() async -> Bool
    func setHardwareDiagnosticsEnabled(_ enabled: Bool) async
    func hardwareDiagnosticsURL() async throws -> URL
    func clearHardwareDiagnostics() async throws
}

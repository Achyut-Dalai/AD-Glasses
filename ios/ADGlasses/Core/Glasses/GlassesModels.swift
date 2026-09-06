import Foundation

enum GlassesCapability: String, Hashable, Sendable {
    case bluetoothConnection
    case photoCapture
    case videoRecording
    case audioRecording
    case microphoneAudio
    case camera
    case mediaTransfer
    case deviceInformation
    case volumeControl
    case notifications
}

enum GlassesMediaKind: String, Hashable, Sendable {
    case photo
    case video
    case audio
}

struct GlassesMediaItem: Identifiable, Equatable, Sendable {
    var id: String { "\(providerID):\(remoteIdentifier)" }

    let remoteIdentifier: String
    let fileName: String
    let kind: GlassesMediaKind
    let providerID: String
}

/// Lightweight inventory reported directly by the glasses control protocol. This is deliberately
/// item-count based: the reverse-engineered protocol proves photo/video/recording counts, but does
/// not yet prove a total/free byte-capacity field.
struct GlassesMediaInventory: Equatable, Sendable {
    let photos: Int
    let videos: Int
    let recordings: Int

    var total: Int { photos + videos + recordings }
}

struct GlassesVisualCapture: Identifiable, Equatable, Sendable {
    let id: UUID
    let jpegData: Data
    let capturedAt: Date
    let providerID: String

    init(
        id: UUID = UUID(),
        jpegData: Data,
        capturedAt: Date = Date(),
        providerID: String
    ) {
        self.id = id
        self.jpegData = jpegData
        self.capturedAt = capturedAt
        self.providerID = providerID
    }
}

enum GlassesMediaTransferState: Equatable, Sendable {
    case idle
    case preparing
    case joiningNetwork
    case awaitingManualNetworkJoin(ssid: String, passphrase: String)
    case checkingLibrary
    case ready(itemCount: Int)
    case downloading(fileName: String)
    case finishing
    case failed(reason: String)

    var label: String {
        switch self {
        case .idle:
            return "Ready to sync"
        case .preparing:
            return "Preparing glasses"
        case .joiningNetwork:
            return "Joining glasses Wi-Fi"
        case .awaitingManualNetworkJoin(let ssid, _):
            return "Join \(ssid) in iPhone Wi-Fi settings"
        case .checkingLibrary:
            return "Checking media"
        case .ready(let itemCount):
            return itemCount == 1 ? "1 item found" : "\(itemCount) items found"
        case .downloading(let fileName):
            return "Syncing \(fileName)"
        case .finishing:
            return "Finishing safely"
        case .failed(let reason):
            return reason
        }
    }
}

struct GlassesDevice: Identifiable, Hashable, Sendable {
    let id: UUID
    let name: String
    let providerID: String
    let signalStrength: Int?
}

struct GlassesProviderSummary: Identifiable, Sendable {
    let id: String
    let displayName: String
    let capabilities: Set<GlassesCapability>
    var connectionState: GlassesConnectionState
}

struct GlassesBatteryStatus: Equatable, Sendable {
    let level: Int
    let isCharging: Bool
}

struct GlassesDeviceInformation: Equatable, Sendable {
    let firmwareVersion: String
    let hardwareVersion: String
    let networkFirmwareVersion: String
    let networkHardwareVersion: String
}

/// Provider-declared device-management work that has a product destination but is not executable
/// until its transport and recovery behavior have been verified on physical hardware.
enum GlassesDeviceManagementOperation: String, Hashable, Sendable {
    case firmwareUpdate
    case factoryReset
    case forcedRestart
    case customWakePhrase
}

struct GlassesDeviceManagementPlaceholder: Identifiable, Equatable, Sendable {
    var id: GlassesDeviceManagementOperation { operation }

    let operation: GlassesDeviceManagementOperation
    let reason: String
}

enum GlassesVolumeChannel: Int, CaseIterable, Hashable, Sendable {
    case music = 1
    case calls = 2
    case system = 3

    var title: String {
        switch self {
        case .music: return "Music"
        case .calls: return "Calls"
        case .system: return "System"
        }
    }
}

struct GlassesVolumeLevel: Equatable, Sendable {
    let minimum: Int
    let maximum: Int
    let current: Int

    func replacingCurrent(with value: Int) -> GlassesVolumeLevel {
        GlassesVolumeLevel(
            minimum: minimum,
            maximum: maximum,
            current: min(max(value, minimum), maximum)
        )
    }
}

struct GlassesVolumeProfile: Equatable, Sendable {
    let music: GlassesVolumeLevel
    let calls: GlassesVolumeLevel
    let system: GlassesVolumeLevel
    let activeChannel: GlassesVolumeChannel?

    subscript(channel: GlassesVolumeChannel) -> GlassesVolumeLevel {
        switch channel {
        case .music: return music
        case .calls: return calls
        case .system: return system
        }
    }

    func replacing(_ channel: GlassesVolumeChannel, current value: Int) -> GlassesVolumeProfile {
        switch channel {
        case .music:
            return GlassesVolumeProfile(
                music: music.replacingCurrent(with: value),
                calls: calls,
                system: system,
                activeChannel: activeChannel
            )
        case .calls:
            return GlassesVolumeProfile(
                music: music,
                calls: calls.replacingCurrent(with: value),
                system: system,
                activeChannel: activeChannel
            )
        case .system:
            return GlassesVolumeProfile(
                music: music,
                calls: calls,
                system: system.replacingCurrent(with: value),
                activeChannel: activeChannel
            )
        }
    }
}

/// Camera capture and recording settings for smart glasses.
/// Physical captures on the hardware sensor save in full resolution to flash storage
/// and are retrieved losslessly over local Wi-Fi.
enum GlassesVideoDurationLimit: Int, CaseIterable, Identifiable, Sendable {
    case fifteenSeconds = 15
    case thirtySeconds = 30
    case sixtySeconds = 60
    case threeMinutes = 180
    case fiveMinutes = 300
    case nineMinutes = 540
    case twelveMinutes = 720

    var id: Int { rawValue }

    var label: String {
        switch self {
        case .fifteenSeconds: return "15s"
        case .thirtySeconds: return "30s"
        case .sixtySeconds: return "1m (Standard)"
        case .threeMinutes: return "3m"
        case .fiveMinutes: return "5m"
        case .nineMinutes: return "9m"
        case .twelveMinutes: return "12m (Maximum)"
        }
    }
}

enum GlassesAudioDurationLimit: Int, CaseIterable, Identifiable, Sendable {
    case thirtyMinutes = 1800
    case oneHour = 3600
    case twoHours = 7200

    var id: Int { rawValue }

    var label: String {
        switch self {
        case .thirtyMinutes: return "30m"
        case .oneHour: return "1h (Standard)"
        case .twoHours: return "2h (Maximum)"
        }
    }
}


enum GlassesAIVisionQuality: String, CaseIterable, Identifiable, Sendable {
    case fast = "fast"
    case detailed = "detailed"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .fast: return "Fast (Sub-second response)"
        case .detailed: return "High Detail (Fine text & distance)"
        }
    }

    var description: String {
        switch self {
        case .fast:
            return "Optimized lightweight frame sent to AI for instant voice answers."
        case .detailed:
            return "Higher resolution frame sent to AI when analyzing fine text, books, or distant objects."
        }
    }
}

enum GlassesPhotoQuality: String, CaseIterable, Identifiable, Sendable {
    case fullResolution = "full"
    case standard = "standard"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .fullResolution: return "Full Resolution (12MP Original)"
        case .standard: return "Standard Quality"
        }
    }

    var description: String {
        switch self {
        case .fullResolution:
            return "Pristine uncompressed captures stored directly to glasses flash memory and transferred in full quality over Wi-Fi."
        case .standard:
            return "Compressed captures to optimize on-device storage space."
        }
    }
}

enum GlassesCaptureIngestionMode: String, CaseIterable, Identifiable, Sendable {
    case wifiSync = "wifiSync"
    case bleThumbnailOnly = "bleThumbnail"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .wifiSync: return "Wi-Fi Transfer (Full Quality)"
        case .bleThumbnailOnly: return "Bluetooth Thumbnails Only"
        }
    }

    var description: String {
        switch self {
        case .wifiSync:
            return "Full resolution photos and videos are downloaded directly from the glasses Wi-Fi access point without quality degradation."
        case .bleThumbnailOnly:
            return "Only low-resolution previews are pulled over BLE. Full media remains safely on the glasses flash storage."
        }
    }
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

    var isConnected: Bool {
        if case .connected = self { return true }
        return false
    }

    var isBusy: Bool {
        switch self {
        case .scanning, .connecting:
            return true
        default:
            return false
        }
    }
}

enum GlassesAssistantInputState: Equatable, Sendable {
    case idle
    case listening
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
            return reason
        case .notConfigured(let reason):
            return reason
        }
    }
}

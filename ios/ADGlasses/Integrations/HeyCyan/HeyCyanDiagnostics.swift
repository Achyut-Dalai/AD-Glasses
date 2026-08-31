import CoreBluetooth
import Foundation

/// A bounded, user-controlled JSONL recorder for physical-hardware validation.
///
/// Packet capture is intentionally separate from OSLog so a user can export an exact trace from
/// the iPhone. Cloud credentials never enter this layer. Raw device packets can contain sensitive
/// device or network material, so capture is opt-in, cache-scoped and bounded; each packet is also
/// capped to keep accidental audio/media notifications from filling device storage.
actor HeyCyanDiagnosticRecorder {
    static let shared = HeyCyanDiagnosticRecorder()

    private struct Record: Codable {
        let timestamp: Date
        let category: String
        let message: String
        let byteCount: Int?
        let hex: String?
        let truncated: Bool?
    }

    private static let capturePreferenceKey = "heycyan.diagnostics.packetCapture.v1"
    private static let maximumLogBytes: UInt64 = 4 * 1_024 * 1_024
    private static let maximumPacketBytes = 4 * 1_024

    private let fileManager: FileManager
    private let defaults: UserDefaults
    private let directoryURL: URL
    private let logURL: URL
    private let encoder: JSONEncoder

    init(
        directoryURL: URL? = nil,
        defaults: UserDefaults = .standard,
        fileManager: FileManager = .default
    ) {
        self.fileManager = fileManager
        self.defaults = defaults
        let baseURL = directoryURL ?? fileManager.urls(
            for: .cachesDirectory,
            in: .userDomainMask
        ).first!.appendingPathComponent("ADGlasses/Diagnostics", isDirectory: true)
        self.directoryURL = baseURL
        logURL = baseURL.appendingPathComponent("heycyan-protocol.jsonl", isDirectory: false)
        encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
    }

    func isPacketCaptureEnabled() -> Bool {
        defaults.bool(forKey: Self.capturePreferenceKey)
    }

    func setPacketCaptureEnabled(_ enabled: Bool) {
        defaults.set(enabled, forKey: Self.capturePreferenceKey)
    }

    func recordState(_ value: String) {
        guard isPacketCaptureEnabled() else { return }
        append(
            Record(
                timestamp: Date(),
                category: "state",
                message: value,
                byteCount: nil,
                hex: nil,
                truncated: nil
            )
        )
    }

    func recordDiagnostic(_ value: String) {
        guard isPacketCaptureEnabled() else { return }
        append(
            Record(
                timestamp: Date(),
                category: "diagnostic",
                message: value,
                byteCount: nil,
                hex: nil,
                truncated: nil
            )
        )
    }

    func recordPacket(
        direction: String,
        channel: HeyCyanTransportChannel,
        data: Data
    ) {
        guard isPacketCaptureEnabled() else { return }
        let captured = data.prefix(Self.maximumPacketBytes)
        let hex = captured.map { String(format: "%02X", $0) }.joined(separator: " ")
        append(
            Record(
                timestamp: Date(),
                category: "packet",
                message: "\(direction) \(channel.rawValue)",
                byteCount: data.count,
                hex: hex,
                truncated: data.count > captured.count
            )
        )
    }

    func recordDiscovery(
        peripheralIdentifier: UUID,
        rssi: Int,
        verifiedServiceMatch: Bool
    ) {
        append(
            Record(
                timestamp: Date(),
                category: "ble-discovery",
                message: "peripheral=\(peripheralIdentifier.uuidString) rssi=\(rssi) verifiedServiceMatch=\(verifiedServiceMatch)",
                byteCount: nil,
                hex: nil,
                truncated: nil
            )
        )
    }

    func exportURL() throws -> URL {
        try ensureLogExists()
        return logURL
    }

    func clear() throws {
        try fileManager.createDirectory(
            at: directoryURL,
            withIntermediateDirectories: true,
            attributes: [.protectionKey: FileProtectionType.complete]
        )
        try Data().write(to: logURL, options: [.atomic, .completeFileProtection])
    }

    private func append(_ record: Record) {
        do {
            try ensureLogExists()
            if currentLogSize() >= Self.maximumLogBytes {
                try Data().write(to: logURL, options: [.atomic, .completeFileProtection])
            }
            var line = try encoder.encode(record)
            line.append(0x0A)
            let handle = try FileHandle(forWritingTo: logURL)
            defer { try? handle.close() }
            try handle.seekToEnd()
            try handle.write(contentsOf: line)
        } catch {
            // Diagnostics must never interfere with connection or command delivery.
        }
    }

    private func ensureLogExists() throws {
        try fileManager.createDirectory(
            at: directoryURL,
            withIntermediateDirectories: true,
            attributes: [.protectionKey: FileProtectionType.complete]
        )
        if !fileManager.fileExists(atPath: logURL.path) {
            try Data().write(to: logURL, options: [.atomic, .completeFileProtection])
        }
    }

    private func currentLogSize() -> UInt64 {
        let attributes = try? fileManager.attributesOfItem(atPath: logURL.path)
        return (attributes?[.size] as? NSNumber)?.uint64Value ?? 0
    }
}

/// A separate diagnostics-only CoreBluetooth central. It only observes advertisements: this type
/// has no peripheral connection or characteristic-writing API by design.
@MainActor
final class HeyCyanPassiveBLEScanner: NSObject, @preconcurrency CBCentralManagerDelegate {
    private enum ScanError: LocalizedError {
        case bluetoothUnavailable(String)
        case alreadyScanning

        var errorDescription: String? {
            switch self {
            case .bluetoothUnavailable(let reason):
                return "Bluetooth is unavailable: \(reason)"
            case .alreadyScanning:
                return "A passive Bluetooth diagnostic scan is already running."
            }
        }
    }

    private static let verifiedService = HeyCyanBLETransport.GATT.baseService

    private let diagnostics: HeyCyanDiagnosticRecorder
    private var central: CBCentralManager!
    private var isRunning = false

    init(diagnostics: HeyCyanDiagnosticRecorder) {
        self.diagnostics = diagnostics
        super.init()
        central = CBCentralManager(delegate: self, queue: .main)
    }

    func scan(duration: Duration) async throws {
        guard !isRunning else { throw ScanError.alreadyScanning }
        isRunning = true
        defer {
            central.stopScan()
            isRunning = false
        }

        for _ in 0 ..< 30 where central.state == .unknown || central.state == .resetting {
            try await Task.sleep(for: .milliseconds(100))
        }
        guard central.state == .poweredOn else {
            throw ScanError.bluetoothUnavailable(central.state.diagnosticLabel)
        }

        central.scanForPeripherals(
            withServices: nil,
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: true]
        )
        try await Task.sleep(for: duration)
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {}

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        guard isRunning else { return }
        let services = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] ?? []
        Task {
            await diagnostics.recordDiscovery(
                peripheralIdentifier: peripheral.identifier,
                rssi: RSSI.intValue,
                verifiedServiceMatch: services.contains(Self.verifiedService)
            )
        }
    }
}

private extension CBManagerState {
    var diagnosticLabel: String {
        switch self {
        case .unknown: return "initializing"
        case .resetting: return "resetting"
        case .unsupported: return "unsupported"
        case .unauthorized: return "not authorized"
        case .poweredOff: return "turned off"
        case .poweredOn: return "ready"
        @unknown default: return "unknown"
        }
    }
}

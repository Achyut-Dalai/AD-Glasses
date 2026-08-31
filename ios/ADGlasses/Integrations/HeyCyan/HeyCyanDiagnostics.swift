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
    private static let sanitizedLogVersionKey = "heycyan.diagnostics.sanitizedLogVersion"
    private static let sanitizedLogVersion = 2
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

        // Version 1 traces could contain the 0x41/0x04 Wi-Fi credential response. Diagnostics
        // are disposable validation data, so discard that legacy trace once rather than allowing
        // a later Share action to export credentials captured by an older build.
        if defaults.integer(forKey: Self.sanitizedLogVersionKey) < Self.sanitizedLogVersion {
            try? fileManager.removeItem(at: logURL)
            defaults.set(Self.sanitizedLogVersion, forKey: Self.sanitizedLogVersionKey)
        }
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
        let rendered = Self.sanitizedPacketHex(data, maximumBytes: Self.maximumPacketBytes)
        append(
            Record(
                timestamp: Date(),
                category: "packet",
                message: "\(direction) \(channel.rawValue)",
                byteCount: data.count,
                hex: rendered.hex,
                truncated: rendered.wasTruncated
            )
        )
    }

    /// The 0x41/0x04 response carries the temporary SSID and passphrase in plaintext inside the
    /// encrypted BLE link. Packet diagnostics must never turn those credentials back into an
    /// exportable log or Xcode console string.
    nonisolated static func sanitizedPacketHex(
        _ data: Data,
        maximumBytes: Int
    ) -> (hex: String, wasTruncated: Bool) {
        let bytes = [UInt8](data)
        if bytes.count >= 14,
           bytes[0] == 0xBC,
           bytes[1] == 0x41,
           bytes[8] == 0x04 {
            let ssidLength = Int(UInt16(bytes[10]) | (UInt16(bytes[11]) << 8))
            let passphraseLength = Int(UInt16(bytes[12]) | (UInt16(bytes[13]) << 8))
            let credentialEnd = 14 + ssidLength + passphraseLength
            if ssidLength > 0,
               passphraseLength > 0,
               credentialEnd <= bytes.count {
                let prefix = bytes.prefix(14)
                    .map { String(format: "%02X", $0) }
                    .joined(separator: " ")
                return (
                    "\(prefix) <Wi-Fi credentials redacted>",
                    data.count > maximumBytes
                )
            }
        }

        let captured = data.prefix(maximumBytes)
        return (
            captured.map { String(format: "%02X", $0) }.joined(separator: " "),
            data.count > captured.count
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
    private var recordedPeripherals = Set<UUID>()

    init(diagnostics: HeyCyanDiagnosticRecorder) {
        self.diagnostics = diagnostics
        super.init()
        central = CBCentralManager(delegate: self, queue: .main)
    }

    func scan(duration: Duration) async throws {
        guard !isRunning else { throw ScanError.alreadyScanning }
        isRunning = true
        recordedPeripherals.removeAll()
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
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: false]
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
        guard recordedPeripherals.insert(peripheral.identifier).inserted else { return }
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

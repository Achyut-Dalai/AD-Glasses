@preconcurrency import CoreBluetooth
import Foundation

@MainActor
final class HeyCyanGlassesProvider: NSObject, GlassesProvider {
    let id = "heycyan"
    let displayName = "HeyCyan"
    let vendor: GlassesVendor = .heyCyan
    let capabilities: Set<GlassesCapability> = [.bluetoothConnection]

    var onConnectionStateChange: ((GlassesConnectionState) -> Void)?

    private(set) var connectionState: GlassesConnectionState = .disconnected {
        didSet { onConnectionStateChange?(connectionState) }
    }

    private var central: CBCentralManager!
    private var discoveredPeripherals: [UUID: CBPeripheral] = [:]
    private var discoveredDevices: [UUID: GlassesDevice] = [:]
    private var connectContinuation: CheckedContinuation<Void, Error>?
    private var pendingConnectionID: UUID?
    private var connectedPeripheralID: UUID?

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: .main)
    }

    func scan() async throws -> [GlassesDevice] {
        guard central.state == .poweredOn else {
            throw GlassesProviderError.bluetoothUnavailable(central.state.readableName)
        }

        discoveredPeripherals.removeAll()
        discoveredDevices.removeAll()
        connectionState = .scanning

        central.scanForPeripherals(
            withServices: nil,
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: false]
        )

        do {
            try await Task.sleep(nanoseconds: 4_000_000_000)
        } catch {
            central.stopScan()
            if connectedPeripheralID == nil { connectionState = .disconnected }
            throw error
        }

        central.stopScan()
        if connectedPeripheralID == nil { connectionState = .disconnected }

        return discoveredDevices.values.sorted {
            ($0.signalStrength ?? Int.min) > ($1.signalStrength ?? Int.min)
        }
    }

    func connect(to device: GlassesDevice) async throws {
        guard device.vendor == .heyCyan,
              let peripheral = discoveredPeripherals[device.id] else {
            throw GlassesProviderError.deviceNotFound
        }

        if peripheral.state == .connected {
            connectedPeripheralID = peripheral.identifier
            connectionState = .connected(device.name)
            return
        }

        guard connectContinuation == nil else {
            throw GlassesProviderError.connectionFailed("another connection attempt is already running")
        }

        connectionState = .connecting(device.name)
        pendingConnectionID = peripheral.identifier

        try await withCheckedThrowingContinuation { continuation in
            connectContinuation = continuation
            central.connect(peripheral, options: nil)
        }
    }

    func disconnect() async {
        guard let connectedPeripheralID,
              let peripheral = discoveredPeripherals[connectedPeripheralID] else {
            connectionState = .disconnected
            self.connectedPeripheralID = nil
            return
        }
        central.cancelPeripheralConnection(peripheral)
    }
}

@MainActor
extension HeyCyanGlassesProvider: @preconcurrency CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            if connectedPeripheralID == nil && connectionState != .scanning {
                connectionState = .disconnected
            }
        } else {
            connectionState = .unavailable("Bluetooth: \(central.state.readableName)")
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        let advertisedName = advertisementData[CBAdvertisementDataLocalNameKey] as? String
        let name = advertisedName ?? peripheral.name ?? "BLE device"
        let id = peripheral.identifier

        discoveredPeripherals[id] = peripheral
        discoveredDevices[id] = GlassesDevice(
            id: id,
            name: name,
            vendor: .heyCyan,
            signalStrength: RSSI.intValue
        )
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        connectedPeripheralID = peripheral.identifier
        pendingConnectionID = nil
        connectionState = .connected(peripheral.name ?? "HeyCyan glasses")
        let continuation = connectContinuation
        connectContinuation = nil
        continuation?.resume()

        // Verified HeyCyan GATT discovery/commands belong here once the protocol
        // identifiers are documented. Do not guess vendor service UUIDs.
    }

    func centralManager(
        _ central: CBCentralManager,
        didFailToConnect peripheral: CBPeripheral,
        error: Error?
    ) {
        pendingConnectionID = nil
        connectionState = .disconnected
        let continuation = connectContinuation
        connectContinuation = nil
        continuation?.resume(
            throwing: GlassesProviderError.connectionFailed(
                error?.localizedDescription ?? "unknown error"
            )
        )
    }

    func centralManager(
        _ central: CBCentralManager,
        didDisconnectPeripheral peripheral: CBPeripheral,
        error: Error?
    ) {
        if connectedPeripheralID == peripheral.identifier {
            connectedPeripheralID = nil
        }
        connectionState = .disconnected
    }
}

private extension CBManagerState {
    var readableName: String {
        switch self {
        case .unknown: return "initializing"
        case .resetting: return "resetting"
        case .unsupported: return "unsupported on this device"
        case .unauthorized: return "permission not granted"
        case .poweredOff: return "powered off"
        case .poweredOn: return "ready"
        @unknown default: return "unknown state"
        }
    }
}

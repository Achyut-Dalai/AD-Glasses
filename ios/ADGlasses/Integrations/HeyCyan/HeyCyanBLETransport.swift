@preconcurrency import CoreBluetooth
import Foundation
import OSLog

struct HeyCyanBLEDevice: Equatable, Sendable {
    let id: UUID
    let name: String
    let signalStrength: Int?
}

enum HeyCyanBLETransportState: Equatable, Sendable {
    case idle
    case scanning
    case connecting(name: String)
    case discoveringServices(name: String)
    case enablingNotifications(name: String)
    case ready(name: String)
    case reconnecting(name: String, attempt: Int, maximumAttempts: Int)
    case disconnecting(name: String)
    case unavailable(reason: String)
    case failed(reason: String)

    var isReady: Bool {
        if case .ready = self { return true }
        return false
    }
}

enum HeyCyanBLETransportError: LocalizedError, Sendable {
    case bluetoothUnavailable(String)
    case operationInProgress
    case deviceNotFound
    case connectionTimedOut
    case readinessTimedOut
    case disconnectedDuringSetup
    case missingService(String)
    case missingCharacteristic(String)
    case notificationSetupFailed(String)
    case transportNotReady
    case unsupportedWriteMode(String)

    var errorDescription: String? {
        switch self {
        case .bluetoothUnavailable(let reason):
            return "Bluetooth is unavailable: \(reason)"
        case .operationInProgress:
            return "Another HeyCyan Bluetooth operation is already running."
        case .deviceNotFound:
            return "The selected HeyCyan glasses are no longer available. Scan again."
        case .connectionTimedOut:
            return "The Bluetooth connection timed out."
        case .readinessTimedOut:
            return "The glasses connected, but their verified services did not become ready."
        case .disconnectedDuringSetup:
            return "The glasses disconnected before protocol setup completed."
        case .missingService(let uuid):
            return "The connected device does not expose the required HeyCyan service \(uuid)."
        case .missingCharacteristic(let uuid):
            return "The connected device does not expose the required HeyCyan characteristic \(uuid)."
        case .notificationSetupFailed(let reason):
            return "HeyCyan notification setup failed: \(reason)"
        case .transportNotReady:
            return "The HeyCyan protocol transport is not ready."
        case .unsupportedWriteMode(let uuid):
            return "The HeyCyan write characteristic \(uuid) does not support write without response."
        }
    }
}

@MainActor
protocol HeyCyanByteTransport: AnyObject {
    var state: HeyCyanBLETransportState { get }
    var onStateChange: ((HeyCyanBLETransportState) -> Void)? { get set }
    var onNotification: ((HeyCyanTransportChannel, Data) -> Void)? { get set }

    func write(_ data: Data, to channel: HeyCyanTransportChannel) throws
}

/// Byte transport only. Product operations and command payload meanings live in HeyCyanSession.
@MainActor
final class HeyCyanBLETransport: NSObject, HeyCyanByteTransport {
    enum GATT {
        static let baseService = CBUUID(string: "6e40fff0-b5a3-f393-e0a9-e50e24dcca9e")
        static let baseWrite = CBUUID(string: "6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        static let baseNotify = CBUUID(string: "6e400003-b5a3-f393-e0a9-e50e24dcca9e")

        static let largeDataService = CBUUID(string: "de5bf728-d711-4e47-af26-65e3012a5dc7")
        static let largeDataNotify = CBUUID(string: "de5bf729-d711-4e47-af26-65e3012a5dc7")
        static let largeDataWrite = CBUUID(string: "de5bf72a-d711-4e47-af26-65e3012a5dc7")
    }

    var onStateChange: ((HeyCyanBLETransportState) -> Void)?
    var onNotification: ((HeyCyanTransportChannel, Data) -> Void)?
    var onDiscoveredDevicesChange: (([HeyCyanBLEDevice]) -> Void)?

    private(set) var state: HeyCyanBLETransportState = .idle {
        didSet {
            guard state != oldValue else { return }
            let diagnosticValue = state.diagnosticValue
            Task { await diagnostics.recordState(diagnosticValue) }
            onStateChange?(state)
        }
    }

    private let logger = Logger(subsystem: "com.achyutdalai.ADGlasses", category: "HeyCyanBLE")
    private let diagnostics: HeyCyanDiagnosticRecorder
    private let reconnectDelays: [Duration] = [.seconds(2), .seconds(5), .seconds(10), .seconds(20), .seconds(30)]

    private var central: CBCentralManager!
    private var peripherals: [UUID: CBPeripheral] = [:]
    private var devices: [UUID: HeyCyanBLEDevice] = [:]
    private var currentPeripheral: CBPeripheral?
    private var currentName = "HeyCyan glasses"

    private var baseWriteCharacteristic: CBCharacteristic?
    private var baseNotifyCharacteristic: CBCharacteristic?
    private var largeDataWriteCharacteristic: CBCharacteristic?
    private var largeDataNotifyCharacteristic: CBCharacteristic?
    private var characteristicServicesAwaitingResponse = Set<String>()
    private var notificationCharacteristicsAwaitingResponse = Set<String>()

    private var pendingWriteChunks: [(characteristic: CBCharacteristic, data: Data)] = []
    private var connectionContinuation: CheckedContinuation<Void, Error>?
    private var disconnectContinuations = [CheckedContinuation<Void, Never>]()
    private var powerContinuation: CheckedContinuation<Void, Error>?
    private var pendingConnectionID: UUID?
    private var connectionTimeoutTask: Task<Void, Never>?
    private var disconnectTimeoutTask: Task<Void, Never>?
    private var powerTimeoutTask: Task<Void, Never>?
    private var reconnectTask: Task<Void, Never>?
    private var reconnectAttempt = 0
    private var wantsConnection = false

    override convenience init() {
        self.init(diagnostics: .shared)
    }

    init(diagnostics: HeyCyanDiagnosticRecorder) {
        self.diagnostics = diagnostics
        super.init()
        central = CBCentralManager(
            delegate: self,
            queue: .main,
            options: [
                CBCentralManagerOptionRestoreIdentifierKey: "com.achyutdalai.ADGlasses.heycyan.central"
            ]
        )
    }

    deinit {
        connectionTimeoutTask?.cancel()
        disconnectTimeoutTask?.cancel()
        powerTimeoutTask?.cancel()
        reconnectTask?.cancel()
    }

    func scan(duration: Duration = .seconds(4)) async throws -> [HeyCyanBLEDevice] {
        try await waitUntilBluetoothIsPoweredOn()
        guard connectionContinuation == nil, disconnectContinuations.isEmpty else {
            throw HeyCyanBLETransportError.operationInProgress
        }

        // Never replace a live protocol state with `.scanning`. CoreBluetooth can scan while
        // connected, but product discovery has no reason to do so and the session must remain
        // authoritative until the user disconnects.
        if state.isReady, let peripheral = currentPeripheral {
            return [
                devices[peripheral.identifier] ?? HeyCyanBLEDevice(
                    id: peripheral.identifier,
                    name: peripheral.name ?? currentName,
                    signalStrength: nil
                )
            ]
        }

        devices.removeAll()
        if let currentIdentifier = currentPeripheral?.identifier,
           let current = peripherals[currentIdentifier] {
            peripherals = [currentIdentifier: current]
        } else {
            peripherals.removeAll()
        }
        state = .scanning

        // The service UUID is verified from the official production application. Filtering here
        // prevents unrelated BLE accessories from being presented or saved as HeyCyan glasses.
        central.scanForPeripherals(
            withServices: [GATT.baseService],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: false]
        )

        do {
            try await Task.sleep(for: duration)
        } catch {
            central.stopScan()
            if !state.isReady { state = .idle }
            throw error
        }

        central.stopScan()
        if !state.isReady { state = .idle }
        return sortedDevices
    }

    func connect(to device: HeyCyanBLEDevice) async throws {
        try await waitUntilBluetoothIsPoweredOn()
        guard connectionContinuation == nil, disconnectContinuations.isEmpty else {
            throw HeyCyanBLETransportError.operationInProgress
        }
        guard let peripheral = peripherals[device.id] else {
            throw HeyCyanBLETransportError.deviceNotFound
        }

        if state.isReady, currentPeripheral?.identifier == peripheral.identifier {
            return
        }
        guard !state.isReady else {
            throw HeyCyanBLETransportError.operationInProgress
        }

        reconnectTask?.cancel()
        reconnectTask = nil
        reconnectAttempt = 0
        wantsConnection = true
        currentPeripheral = peripheral
        currentName = device.name
        pendingConnectionID = peripheral.identifier
        resetGATTState()
        state = .connecting(name: device.name)

        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                connectionContinuation = continuation
                peripheral.delegate = self
                if peripheral.state == .connected {
                    pendingConnectionID = nil
                    beginServiceDiscovery(for: peripheral)
                } else {
                    central.connect(peripheral, options: nil)
                    startConnectionTimeout(for: peripheral, readinessPhase: false)
                }
            }
        } onCancel: {
            Task { @MainActor [weak self] in
                self?.cancelConnectionAttempt()
            }
        }
    }

    func reconnect(identifier: UUID, fallbackName: String = "HeyCyan glasses") async throws -> Bool {
        try await waitUntilBluetoothIsPoweredOn()
        guard let peripheral = central.retrievePeripherals(withIdentifiers: [identifier]).first else {
            return false
        }

        let device = HeyCyanBLEDevice(
            id: identifier,
            name: peripheral.name ?? fallbackName,
            signalStrength: nil
        )
        peripherals[identifier] = peripheral
        devices[identifier] = device
        try await connect(to: device)
        return state.isReady
    }

    func disconnect() async {
        wantsConnection = false
        reconnectTask?.cancel()
        reconnectTask = nil
        reconnectAttempt = 0

        if connectionContinuation != nil {
            finishConnection(with: .failure(CancellationError()))
        }

        guard let peripheral = currentPeripheral else {
            pendingConnectionID = nil
            resetGATTState()
            state = .idle
            return
        }

        await withCheckedContinuation { continuation in
            disconnectContinuations.append(continuation)
            guard disconnectContinuations.count == 1 else { return }

            currentName = peripheral.name ?? currentName
            state = .disconnecting(name: currentName)
            pendingConnectionID = nil

            if peripheral.state == .disconnected {
                finishDisconnect(for: peripheral)
                return
            }
            central.cancelPeripheralConnection(peripheral)
            startDisconnectTimeout(for: peripheral)
        }
    }

    func write(_ data: Data, to channel: HeyCyanTransportChannel) throws {
        guard state.isReady, let peripheral = currentPeripheral else {
            throw HeyCyanBLETransportError.transportNotReady
        }

        let characteristic: CBCharacteristic?
        switch channel {
        case .base:
            characteristic = baseWriteCharacteristic
        case .largeData:
            characteristic = largeDataWriteCharacteristic
        }
        guard let characteristic else {
            throw HeyCyanBLETransportError.missingCharacteristic(
                channel == .base ? GATT.baseWrite.uuidString : GATT.largeDataWrite.uuidString
            )
        }
        guard characteristic.properties.contains(.writeWithoutResponse) else {
            throw HeyCyanBLETransportError.unsupportedWriteMode(characteristic.uuid.uuidString)
        }

        logPacket(direction: "TX", channel: channel, data: data)
        let maximumLength = max(peripheral.maximumWriteValueLength(for: .withoutResponse), 1)
        var offset = 0
        while offset < data.count {
            let end = min(offset + maximumLength, data.count)
            pendingWriteChunks.append((characteristic, Data(data[offset ..< end])))
            offset = end
        }
        drainWriteQueue(for: peripheral)
    }

    private var sortedDevices: [HeyCyanBLEDevice] {
        devices.values.sorted {
            ($0.signalStrength ?? Int.min) > ($1.signalStrength ?? Int.min)
        }
    }

    private func waitUntilBluetoothIsPoweredOn() async throws {
        switch central.state {
        case .poweredOn:
            return
        case .unknown, .resetting:
            guard powerContinuation == nil else {
                throw HeyCyanBLETransportError.operationInProgress
            }
            try await withTaskCancellationHandler {
                try await withCheckedThrowingContinuation { continuation in
                    powerContinuation = continuation
                    powerTimeoutTask?.cancel()
                    powerTimeoutTask = Task { [weak self] in
                        do {
                            try await Task.sleep(for: .seconds(5))
                        } catch {
                            return
                        }
                        guard let self else { return }
                        finishPowerWait(
                            with: .failure(
                                HeyCyanBLETransportError.bluetoothUnavailable("still initializing")
                            )
                        )
                    }
                }
            } onCancel: {
                Task { @MainActor [weak self] in
                    self?.finishPowerWait(with: .failure(CancellationError()))
                }
            }
        default:
            throw HeyCyanBLETransportError.bluetoothUnavailable(central.state.readableName)
        }
    }

    private func finishPowerWait(with result: Result<Void, Error>) {
        powerTimeoutTask?.cancel()
        powerTimeoutTask = nil
        guard let continuation = powerContinuation else { return }
        powerContinuation = nil
        continuation.resume(with: result)
    }

    private func beginServiceDiscovery(for peripheral: CBPeripheral) {
        connectionTimeoutTask?.cancel()
        peripheral.delegate = self
        currentPeripheral = peripheral
        currentName = peripheral.name ?? currentName
        resetGATTState()
        state = .discoveringServices(name: currentName)
        peripheral.discoverServices([GATT.baseService, GATT.largeDataService])
        startConnectionTimeout(for: peripheral, readinessPhase: true)
    }

    private func startConnectionTimeout(for peripheral: CBPeripheral, readinessPhase: Bool) {
        connectionTimeoutTask?.cancel()
        connectionTimeoutTask = Task { [weak self, weak peripheral] in
            do {
                try await Task.sleep(for: readinessPhase ? .seconds(12) : .seconds(15))
            } catch {
                return
            }
            guard let self, let peripheral else { return }
            let isRelevant = pendingConnectionID == peripheral.identifier ||
                currentPeripheral?.identifier == peripheral.identifier
            guard isRelevant, !state.isReady else { return }

            central.cancelPeripheralConnection(peripheral)
            pendingConnectionID = nil
            let error: HeyCyanBLETransportError = readinessPhase
                ? .readinessTimedOut
                : .connectionTimedOut
            if connectionContinuation != nil {
                wantsConnection = false
                state = .failed(reason: error.localizedDescription)
                finishConnection(with: .failure(error))
            } else if wantsConnection {
                state = .idle
                scheduleReconnect(to: peripheral)
            }
        }
    }

    private func startDisconnectTimeout(for peripheral: CBPeripheral) {
        disconnectTimeoutTask?.cancel()
        disconnectTimeoutTask = Task { [weak self, weak peripheral] in
            do {
                try await Task.sleep(for: .seconds(5))
            } catch {
                return
            }
            guard let self, let peripheral, !disconnectContinuations.isEmpty else { return }
            logger.warning("Timed out waiting for CoreBluetooth disconnect callback")
            finishDisconnect(for: peripheral)
        }
    }

    private func cancelConnectionAttempt() {
        wantsConnection = false
        reconnectTask?.cancel()
        reconnectTask = nil
        if let pendingConnectionID, let peripheral = peripherals[pendingConnectionID] {
            central.cancelPeripheralConnection(peripheral)
        } else if let currentPeripheral, !state.isReady {
            central.cancelPeripheralConnection(currentPeripheral)
        }
        self.pendingConnectionID = nil
        state = .idle
        finishConnection(with: .failure(CancellationError()))
    }

    private func finishConnection(with result: Result<Void, Error>) {
        connectionTimeoutTask?.cancel()
        connectionTimeoutTask = nil
        pendingConnectionID = nil
        guard let continuation = connectionContinuation else { return }
        connectionContinuation = nil
        continuation.resume(with: result)
    }

    private func finishDisconnect(for peripheral: CBPeripheral) {
        disconnectTimeoutTask?.cancel()
        disconnectTimeoutTask = nil
        if currentPeripheral?.identifier == peripheral.identifier {
            currentPeripheral = nil
        }
        pendingConnectionID = nil
        resetGATTState()
        state = .idle
        let continuations = disconnectContinuations
        disconnectContinuations.removeAll()
        continuations.forEach { $0.resume() }
    }

    private func failPreparation(
        _ error: HeyCyanBLETransportError,
        peripheral: CBPeripheral,
        mayRetry: Bool = false
    ) {
        logger.error("HeyCyan setup failed: \(error.localizedDescription, privacy: .public)")
        connectionTimeoutTask?.cancel()
        pendingConnectionID = nil
        resetGATTState()

        if connectionContinuation != nil {
            wantsConnection = false
            state = .failed(reason: error.localizedDescription)
            finishConnection(with: .failure(error))
        } else if mayRetry, wantsConnection {
            state = .idle
            scheduleReconnect(to: peripheral)
        } else {
            wantsConnection = false
            state = .failed(reason: error.localizedDescription)
        }
        central.cancelPeripheralConnection(peripheral)
    }

    private func scheduleReconnect(to peripheral: CBPeripheral) {
        guard wantsConnection else { return }
        reconnectTask?.cancel()

        guard reconnectAttempt < reconnectDelays.count else {
            wantsConnection = false
            state = .failed(reason: "Automatic reconnect stopped after \(reconnectDelays.count) attempts.")
            return
        }

        let attempt = reconnectAttempt + 1
        let delay = reconnectDelays[reconnectAttempt]
        reconnectAttempt = attempt
        currentPeripheral = peripheral
        currentName = peripheral.name ?? currentName
        state = .reconnecting(
            name: currentName,
            attempt: attempt,
            maximumAttempts: reconnectDelays.count
        )

        reconnectTask = Task { [weak self, weak peripheral] in
            do {
                try await Task.sleep(for: delay)
            } catch {
                return
            }
            guard let self, let peripheral, wantsConnection else { return }
            guard central.state == .poweredOn else { return }
            pendingConnectionID = peripheral.identifier
            state = .connecting(name: currentName)
            peripheral.delegate = self
            central.connect(peripheral, options: nil)
            startConnectionTimeout(for: peripheral, readinessPhase: false)
        }
    }

    private func resetGATTState() {
        baseWriteCharacteristic = nil
        baseNotifyCharacteristic = nil
        largeDataWriteCharacteristic = nil
        largeDataNotifyCharacteristic = nil
        characteristicServicesAwaitingResponse.removeAll()
        notificationCharacteristicsAwaitingResponse.removeAll()
        pendingWriteChunks.removeAll()
    }

    private func validateCharacteristicsAndSubscribe(on peripheral: CBPeripheral) {
        let required: [(CBCharacteristic?, CBUUID)] = [
            (baseWriteCharacteristic, GATT.baseWrite),
            (baseNotifyCharacteristic, GATT.baseNotify),
            (largeDataWriteCharacteristic, GATT.largeDataWrite),
            (largeDataNotifyCharacteristic, GATT.largeDataNotify)
        ]
        if let missing = required.first(where: { $0.0 == nil }) {
            failPreparation(
                .missingCharacteristic(missing.1.uuidString),
                peripheral: peripheral
            )
            return
        }

        guard baseWriteCharacteristic?.properties.contains(.writeWithoutResponse) == true else {
            failPreparation(
                .unsupportedWriteMode(GATT.baseWrite.uuidString),
                peripheral: peripheral
            )
            return
        }
        guard largeDataWriteCharacteristic?.properties.contains(.writeWithoutResponse) == true else {
            failPreparation(
                .unsupportedWriteMode(GATT.largeDataWrite.uuidString),
                peripheral: peripheral
            )
            return
        }

        state = .enablingNotifications(name: currentName)
        for characteristic in [baseNotifyCharacteristic, largeDataNotifyCharacteristic].compactMap({ $0 }) {
            if characteristic.isNotifying {
                continue
            }
            notificationCharacteristicsAwaitingResponse.insert(characteristic.uuid.uuidString)
            peripheral.setNotifyValue(true, for: characteristic)
        }

        if notificationCharacteristicsAwaitingResponse.isEmpty {
            markReady()
        }
    }

    private func markReady() {
        guard let peripheral = currentPeripheral else { return }
        connectionTimeoutTask?.cancel()
        connectionTimeoutTask = nil
        reconnectTask?.cancel()
        reconnectTask = nil
        reconnectAttempt = 0
        pendingConnectionID = nil
        currentName = peripheral.name ?? currentName
        state = .ready(name: currentName)
        finishConnection(with: .success(()))
    }

    private func drainWriteQueue(for peripheral: CBPeripheral) {
        guard state.isReady else {
            pendingWriteChunks.removeAll()
            return
        }
        while peripheral.canSendWriteWithoutResponse, !pendingWriteChunks.isEmpty {
            let next = pendingWriteChunks.removeFirst()
            peripheral.writeValue(next.data, for: next.characteristic, type: .withoutResponse)
        }
    }

    private func logPacket(direction: String, channel: HeyCyanTransportChannel, data: Data) {
        Task {
            await diagnostics.recordPacket(
                direction: direction,
                channel: channel,
                data: data
            )
        }
#if DEBUG
        let hex = data.prefix(512).map { String(format: "%02X", $0) }.joined(separator: " ")
        let suffix = data.count > 512 ? " … (\(data.count) bytes)" : ""
        logger.debug(
            "\(direction, privacy: .public) [\(channel.rawValue, privacy: .public)] \(hex + suffix, privacy: .public)"
        )
#else
        logger.debug(
            "\(direction, privacy: .public) [\(channel.rawValue, privacy: .public)] \(data.count, privacy: .public) bytes"
        )
#endif
    }
}

private extension HeyCyanBLETransportState {
    var diagnosticValue: String {
        switch self {
        case .idle: return "idle"
        case .scanning: return "scanning"
        case .connecting(let name): return "connecting: \(name)"
        case .discoveringServices(let name): return "discovering services: \(name)"
        case .enablingNotifications(let name): return "enabling notifications: \(name)"
        case .ready(let name): return "ready: \(name)"
        case .reconnecting(let name, let attempt, let maximumAttempts):
            return "reconnecting: \(name), attempt \(attempt)/\(maximumAttempts)"
        case .disconnecting(let name): return "disconnecting: \(name)"
        case .unavailable(let reason): return "unavailable: \(reason)"
        case .failed(let reason): return "failed: \(reason)"
        }
    }
}

@MainActor
extension HeyCyanBLETransport: @preconcurrency CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            finishPowerWait(with: .success(()))
            if wantsConnection,
               connectionContinuation == nil,
               reconnectTask == nil,
               !state.isReady,
               let currentPeripheral {
                scheduleReconnect(to: currentPeripheral)
            } else if !wantsConnection, !state.isReady, disconnectContinuations.isEmpty {
                state = .idle
            }
            return
        }

        if central.state != .unknown, central.state != .resetting {
            finishPowerWait(
                with: .failure(
                    HeyCyanBLETransportError.bluetoothUnavailable(central.state.readableName)
                )
            )
        }
        central.stopScan()
        reconnectTask?.cancel()
        reconnectTask = nil
        resetGATTState()
        state = .unavailable(reason: central.state.readableName)

        if connectionContinuation != nil {
            finishConnection(
                with: .failure(
                    HeyCyanBLETransportError.bluetoothUnavailable(central.state.readableName)
                )
            )
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        willRestoreState dict: [String: Any]
    ) {
        guard let restored = dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral],
              let peripheral = restored.first else { return }
        restored.forEach {
            $0.delegate = self
            peripherals[$0.identifier] = $0
            devices[$0.identifier] = HeyCyanBLEDevice(
                id: $0.identifier,
                name: $0.name ?? "HeyCyan glasses",
                signalStrength: nil
            )
        }
        currentPeripheral = peripheral
        currentName = peripheral.name ?? "HeyCyan glasses"
        wantsConnection = true
        if peripheral.state == .connected {
            beginServiceDiscovery(for: peripheral)
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        let advertisedServices = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID]
        guard advertisedServices?.contains(GATT.baseService) == true else {
            // A service-filtered scan should already enforce this. Retain the guard so a future
            // broad diagnostic scan cannot accidentally turn an arbitrary accessory into HeyCyan.
            return
        }

        let advertisedName = advertisementData[CBAdvertisementDataLocalNameKey] as? String
        let device = HeyCyanBLEDevice(
            id: peripheral.identifier,
            name: advertisedName ?? peripheral.name ?? "HeyCyan glasses",
            signalStrength: RSSI.intValue
        )
        peripheral.delegate = self
        peripherals[peripheral.identifier] = peripheral
        devices[peripheral.identifier] = device
        onDiscoveredDevicesChange?(sortedDevices)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        guard pendingConnectionID == peripheral.identifier ||
                (wantsConnection && currentPeripheral?.identifier == peripheral.identifier) else {
            logger.warning("Ignoring stale didConnect callback for \(peripheral.identifier.uuidString, privacy: .public)")
            central.cancelPeripheralConnection(peripheral)
            return
        }
        pendingConnectionID = nil
        beginServiceDiscovery(for: peripheral)
    }

    func centralManager(
        _ central: CBCentralManager,
        didFailToConnect peripheral: CBPeripheral,
        error: Error?
    ) {
        guard pendingConnectionID == peripheral.identifier ||
                currentPeripheral?.identifier == peripheral.identifier else {
            logger.warning("Ignoring stale didFailToConnect callback for \(peripheral.identifier.uuidString, privacy: .public)")
            return
        }
        connectionTimeoutTask?.cancel()
        connectionTimeoutTask = nil
        pendingConnectionID = nil
        let failure = HeyCyanBLETransportError.bluetoothUnavailable(
            error?.localizedDescription ?? "connection failed"
        )
        if connectionContinuation != nil {
            wantsConnection = false
            state = .failed(reason: failure.localizedDescription)
            finishConnection(with: .failure(failure))
        } else if wantsConnection {
            state = .idle
            scheduleReconnect(to: peripheral)
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDisconnectPeripheral peripheral: CBPeripheral,
        error: Error?
    ) {
        guard currentPeripheral?.identifier == peripheral.identifier ||
                pendingConnectionID == peripheral.identifier else {
            logger.warning("Ignoring stale didDisconnect callback for \(peripheral.identifier.uuidString, privacy: .public)")
            return
        }

        connectionTimeoutTask?.cancel()
        connectionTimeoutTask = nil
        pendingConnectionID = nil
        resetGATTState()

        if !disconnectContinuations.isEmpty || !wantsConnection {
            finishDisconnect(for: peripheral)
            return
        }

        if connectionContinuation != nil {
            wantsConnection = false
            currentPeripheral = nil
            state = .idle
            finishConnection(with: .failure(HeyCyanBLETransportError.disconnectedDuringSetup))
            return
        }

        if let error {
            logger.warning("Unexpected HeyCyan disconnect: \(error.localizedDescription, privacy: .public)")
        }
        state = .idle
        // A connection/readiness timeout schedules its retry before asking CoreBluetooth to
        // cancel the stale attempt. The ensuing disconnect callback must not consume a second
        // backoff slot or replace the already scheduled retry.
        if reconnectTask == nil {
            scheduleReconnect(to: peripheral)
        }
    }
}

@MainActor
extension HeyCyanBLETransport: @preconcurrency CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard peripheral.identifier == currentPeripheral?.identifier else { return }
        if let error {
            failPreparation(
                .notificationSetupFailed(error.localizedDescription),
                peripheral: peripheral
            )
            return
        }

        let services = peripheral.services ?? []
        guard let baseService = services.first(where: { $0.uuid == GATT.baseService }) else {
            failPreparation(.missingService(GATT.baseService.uuidString), peripheral: peripheral)
            return
        }
        guard let largeDataService = services.first(where: { $0.uuid == GATT.largeDataService }) else {
            failPreparation(.missingService(GATT.largeDataService.uuidString), peripheral: peripheral)
            return
        }

        characteristicServicesAwaitingResponse = [
            baseService.uuid.uuidString,
            largeDataService.uuid.uuidString
        ]
        peripheral.discoverCharacteristics(
            [GATT.baseWrite, GATT.baseNotify],
            for: baseService
        )
        peripheral.discoverCharacteristics(
            [GATT.largeDataWrite, GATT.largeDataNotify],
            for: largeDataService
        )
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didDiscoverCharacteristicsFor service: CBService,
        error: Error?
    ) {
        guard peripheral.identifier == currentPeripheral?.identifier else { return }
        if let error {
            failPreparation(
                .notificationSetupFailed(error.localizedDescription),
                peripheral: peripheral
            )
            return
        }

        for characteristic in service.characteristics ?? [] {
            switch characteristic.uuid {
            case GATT.baseWrite:
                baseWriteCharacteristic = characteristic
            case GATT.baseNotify:
                baseNotifyCharacteristic = characteristic
            case GATT.largeDataWrite:
                largeDataWriteCharacteristic = characteristic
            case GATT.largeDataNotify:
                largeDataNotifyCharacteristic = characteristic
            default:
                break
            }
        }

        characteristicServicesAwaitingResponse.remove(service.uuid.uuidString)
        if characteristicServicesAwaitingResponse.isEmpty {
            validateCharacteristicsAndSubscribe(on: peripheral)
        }
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateNotificationStateFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        guard peripheral.identifier == currentPeripheral?.identifier else { return }
        guard notificationCharacteristicsAwaitingResponse.contains(characteristic.uuid.uuidString) else {
            return
        }
        if let error {
            failPreparation(
                .notificationSetupFailed(error.localizedDescription),
                peripheral: peripheral
            )
            return
        }
        guard characteristic.isNotifying else {
            failPreparation(
                .notificationSetupFailed("\(characteristic.uuid.uuidString) did not enter notify mode"),
                peripheral: peripheral
            )
            return
        }

        notificationCharacteristicsAwaitingResponse.remove(characteristic.uuid.uuidString)
        if notificationCharacteristicsAwaitingResponse.isEmpty {
            markReady()
        }
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        guard peripheral.identifier == currentPeripheral?.identifier else { return }
        if let error {
            logger.error(
                "HeyCyan notification failed on \(characteristic.uuid.uuidString, privacy: .public): \(error.localizedDescription, privacy: .public)"
            )
            return
        }
        guard let data = characteristic.value else { return }

        let channel: HeyCyanTransportChannel
        switch characteristic.uuid {
        case GATT.baseNotify:
            channel = .base
        case GATT.largeDataNotify:
            channel = .largeData
        default:
            return
        }
        logPacket(direction: "RX", channel: channel, data: data)
        onNotification?(channel, data)
    }

    func peripheralIsReady(toSendWriteWithoutResponse peripheral: CBPeripheral) {
        guard peripheral.identifier == currentPeripheral?.identifier else { return }
        drainWriteQueue(for: peripheral)
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

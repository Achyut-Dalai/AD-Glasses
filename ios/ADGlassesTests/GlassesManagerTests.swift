import XCTest
@testable import ADGlasses

@MainActor
final class GlassesManagerTests: XCTestCase {
    func testActiveProviderCannotBeHiddenBySelection() async {
        let heyCyan = FakeGlassesProvider(id: "heycyan", displayName: "HeyCyan")
        let meta = FakeGlassesProvider(id: "meta", displayName: "Meta")
        let manager = GlassesManager(providers: [heyCyan, meta])
        let device = GlassesDevice(
            id: UUID(),
            name: "Cyan",
            providerID: heyCyan.id,
            signalStrength: nil
        )

        heyCyan.scanResult = [device]
        await manager.scan()
        await manager.connect(to: device)
        manager.selectProvider(meta.id)

        XCTAssertEqual(manager.activeProviderID, heyCyan.id)
        XCTAssertEqual(manager.selectedProviderID, heyCyan.id)
        XCTAssertTrue(manager.connectionState.isConnected)
        XCTAssertNotNil(manager.errorMessage)
    }

    func testDisconnectTargetsTheActiveProviderAndWaitsForIt() async {
        let heyCyan = FakeGlassesProvider(id: "heycyan", displayName: "HeyCyan")
        let meta = FakeGlassesProvider(id: "meta", displayName: "Meta")
        let manager = GlassesManager(providers: [heyCyan, meta])
        let device = GlassesDevice(
            id: UUID(),
            name: "Cyan",
            providerID: heyCyan.id,
            signalStrength: nil
        )

        heyCyan.scanResult = [device]
        await manager.scan()
        await manager.connect(to: device)
        await manager.disconnect()

        XCTAssertEqual(heyCyan.disconnectCount, 1)
        XCTAssertEqual(meta.disconnectCount, 0)
        XCTAssertNil(manager.activeProviderID)
        XCTAssertEqual(manager.connectionState, .disconnected)
    }

    func testPhotoRequestResolvesCapabilityWithoutVendorBranching() async {
        let provider = FakeGlassesProvider(id: "provider", displayName: "Provider")
        let manager = GlassesManager(providers: [provider])
        let device = GlassesDevice(
            id: UUID(),
            name: "Glasses",
            providerID: provider.id,
            signalStrength: nil
        )

        provider.scanResult = [device]
        await manager.scan()
        await manager.connect(to: device)

        let didRequestPhoto = await manager.requestPhotoCapture()
        XCTAssertTrue(didRequestPhoto)
        XCTAssertEqual(provider.photoRequestCount, 1)
    }

    func testProviderDeviceStatusFlowsWithoutVendorBranchingAndClearsOnDisconnect() async {
        let provider = FakeGlassesProvider(id: "provider", displayName: "Provider")
        let manager = GlassesManager(providers: [provider])
        let device = GlassesDevice(
            id: UUID(),
            name: "Glasses",
            providerID: provider.id,
            signalStrength: nil
        )

        provider.scanResult = [device]
        await manager.scan()
        await manager.connect(to: device)
        provider.publish(
            batteryStatus: GlassesBatteryStatus(level: 72, isCharging: true),
            deviceInformation: GlassesDeviceInformation(
                firmwareVersion: "1.2.3",
                hardwareVersion: "A1",
                networkFirmwareVersion: "4.5.6",
                networkHardwareVersion: "W1"
            )
        )

        XCTAssertEqual(manager.batteryStatus, GlassesBatteryStatus(level: 72, isCharging: true))
        XCTAssertEqual(manager.deviceInformation?.firmwareVersion, "1.2.3")

        await manager.disconnect()
        XCTAssertNil(manager.batteryStatus)
        XCTAssertNil(manager.deviceInformation)
    }

    func testDeviceManagementRoadmapComesFromProviderWithoutExecutableOperations() {
        let provider = FakeGlassesProvider(id: "provider", displayName: "Provider")
        let manager = GlassesManager(providers: [provider])

        XCTAssertEqual(
            manager.deviceManagementPlaceholders,
            provider.deviceManagementPlaceholders
        )
    }
}

@MainActor
private final class FakeGlassesProvider:
    GlassesProvider,
    GlassesPhotoCapturing,
    GlassesBatteryProviding,
    GlassesDeviceInformationProviding,
    GlassesDeviceManagementPlanning
{
    let id: String
    let displayName: String
    let capabilities: Set<GlassesCapability> = [.bluetoothConnection, .photoCapture]
    let deviceManagementPlaceholders = [
        GlassesDeviceManagementPlaceholder(
            operation: .firmwareUpdate,
            reason: "Hardware validation required."
        )
    ]

    var connectionState: GlassesConnectionState = .disconnected {
        didSet { onConnectionStateChange?(connectionState) }
    }
    var onConnectionStateChange: ((GlassesConnectionState) -> Void)?
    var onBatteryStatusChange: ((GlassesBatteryStatus?) -> Void)?
    var onDeviceInformationChange: ((GlassesDeviceInformation?) -> Void)?
    private(set) var batteryStatus: GlassesBatteryStatus?
    private(set) var deviceInformation: GlassesDeviceInformation?
    var scanResult = [GlassesDevice]()
    private(set) var disconnectCount = 0
    private(set) var photoRequestCount = 0

    init(id: String, displayName: String) {
        self.id = id
        self.displayName = displayName
    }

    func scan() async throws -> [GlassesDevice] {
        connectionState = .scanning
        connectionState = .disconnected
        return scanResult
    }

    func connect(to device: GlassesDevice) async throws {
        connectionState = .connecting(device.name)
        connectionState = .connected(device.name)
    }

    func disconnect() async {
        disconnectCount += 1
        connectionState = .disconnected
    }

    func requestPhotoCapture() async throws {
        photoRequestCount += 1
    }

    func refreshBatteryStatus() async throws {}

    func refreshDeviceInformation() async throws {}

    func publish(
        batteryStatus: GlassesBatteryStatus?,
        deviceInformation: GlassesDeviceInformation?
    ) {
        self.batteryStatus = batteryStatus
        self.deviceInformation = deviceInformation
        onBatteryStatusChange?(batteryStatus)
        onDeviceInformationChange?(deviceInformation)
    }
}

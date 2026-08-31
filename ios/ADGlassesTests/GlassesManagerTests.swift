import XCTest
@testable import ADGlasses

@MainActor
final class GlassesManagerTests: XCTestCase {
    func testHeyCyanTechnicalNamesStayBehindConsumerBoundary() async throws {
        let provider = FakeGlassesProvider(id: "heycyan", displayName: "HeyCyan")
        let manager = GlassesManager(providers: [provider])
        provider.scanResult = [
            GlassesDevice(
                id: UUID(),
                name: "Raw-HeyCyan-Peripheral",
                providerID: provider.id,
                signalStrength: -48
            )
        ]

        await manager.scan()
        let device = try XCTUnwrap(manager.devices.first)
        await manager.connect(to: device)

        XCTAssertEqual(manager.providers.first?.displayName, "AD Glasses")
        XCTAssertEqual(device.name, "AD Glasses")
        XCTAssertEqual(manager.connectionState, .connected("AD Glasses"))
        XCTAssertEqual(manager.technicalProviderName(for: provider.id), "HeyCyan")
    }

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

    func testDisconnectKeepsRememberedGlassesWhileForgetClearsThem() async {
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
        XCTAssertTrue(manager.hasRememberedDevice)

        await manager.disconnect()
        XCTAssertTrue(manager.hasRememberedDevice)
        XCTAssertEqual(provider.forgetCount, 0)

        await manager.forgetLastDevice()
        XCTAssertFalse(manager.hasRememberedDevice)
        XCTAssertEqual(provider.forgetCount, 1)
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

    func testVideoRecordingToggleRoutesThroughCapabilityProvider() async {
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

        let didStart = await manager.toggleVideoRecording()
        XCTAssertTrue(didStart)
        XCTAssertTrue(manager.isVideoRecording)
        XCTAssertEqual(provider.startVideoCount, 1)

        let didStop = await manager.toggleVideoRecording()
        XCTAssertTrue(didStop)
        XCTAssertFalse(manager.isVideoRecording)
        XCTAssertEqual(provider.stopVideoCount, 1)
    }

    func testAudioRecordingToggleRoutesThroughCapabilityProvider() async {
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

        let didStart = await manager.toggleAudioRecording()
        XCTAssertTrue(didStart)
        XCTAssertTrue(manager.isAudioRecording)
        XCTAssertEqual(provider.startAudioCount, 1)

        let didStop = await manager.toggleAudioRecording()
        XCTAssertTrue(didStop)
        XCTAssertFalse(manager.isAudioRecording)
        XCTAssertEqual(provider.stopAudioCount, 1)
    }

    func testVisualCaptureFlowsThroughProviderBoundaryAndPublishesLatestCapture() async throws {
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

        var publishedCapture: GlassesVisualCapture?
        manager.onVisualCapture = { publishedCapture = $0 }
        let requestedCapture = await manager.requestVisualCapture()
        let result = try XCTUnwrap(requestedCapture)

        XCTAssertEqual(provider.visualCaptureRequestCount, 1)
        XCTAssertEqual(result.jpegData, Data([0xFF, 0xD8, 0xFF, 0xD9]))
        XCTAssertEqual(manager.latestVisualCapture, result)
        XCTAssertEqual(publishedCapture, result)
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

    func testMediaTransferRoutesThroughCapabilityProvider() async throws {
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

        let items = try await manager.prepareMediaTransfer()
        let item = try XCTUnwrap(items.first)
        let destination = FileManager.default.temporaryDirectory
            .appendingPathComponent("ADGlasses-MediaTest-\(UUID().uuidString).jpg")
        defer { try? FileManager.default.removeItem(at: destination) }

        try await manager.downloadMediaItem(item, to: destination)
        await manager.finishMediaTransfer()

        XCTAssertTrue(manager.supportsMediaTransfer)
        XCTAssertEqual(provider.prepareMediaCount, 1)
        XCTAssertEqual(provider.downloadMediaCount, 1)
        XCTAssertEqual(provider.finishMediaCount, 1)
        XCTAssertTrue(FileManager.default.fileExists(atPath: destination.path))
    }
}

@MainActor
private final class FakeGlassesProvider:
    GlassesProvider,
    GlassesPhotoCapturing,
    GlassesVideoRecording,
    GlassesAudioRecording,
    GlassesVisualCapturing,
    GlassesMediaTransferring,
    GlassesBatteryProviding,
    GlassesDeviceInformationProviding,
    GlassesDeviceManagementPlanning,
    GlassesForgettable
{
    let id: String
    let displayName: String
    let capabilities: Set<GlassesCapability> = [
        .bluetoothConnection,
        .photoCapture,
        .videoRecording,
        .audioRecording,
        .camera,
        .mediaTransfer
    ]
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
    var onVisualCapture: ((GlassesVisualCapture) -> Void)?
    var onVideoRecordingStateChange: ((Bool) -> Void)?
    var onAudioRecordingStateChange: ((Bool) -> Void)?
    var onBatteryStatusChange: ((GlassesBatteryStatus?) -> Void)?
    var onDeviceInformationChange: ((GlassesDeviceInformation?) -> Void)?
    var onMediaTransferStateChange: ((GlassesMediaTransferState) -> Void)?
    private(set) var mediaTransferState: GlassesMediaTransferState = .idle {
        didSet { onMediaTransferStateChange?(mediaTransferState) }
    }
    private(set) var batteryStatus: GlassesBatteryStatus?
    private(set) var deviceInformation: GlassesDeviceInformation?
    var scanResult = [GlassesDevice]()
    private(set) var disconnectCount = 0
    private(set) var photoRequestCount = 0
    private(set) var startVideoCount = 0
    private(set) var stopVideoCount = 0
    private(set) var isVideoRecording = false
    private(set) var startAudioCount = 0
    private(set) var stopAudioCount = 0
    private(set) var isAudioRecording = false
    private(set) var visualCaptureRequestCount = 0
    private(set) var prepareMediaCount = 0
    private(set) var downloadMediaCount = 0
    private(set) var finishMediaCount = 0
    private(set) var forgetCount = 0
    private(set) var hasRememberedDevice = true

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

    func forgetLastDevice() async {
        forgetCount += 1
        hasRememberedDevice = false
        connectionState = .disconnected
    }

    func requestPhotoCapture() async throws {
        photoRequestCount += 1
    }

    func startVideoRecording() async throws {
        startVideoCount += 1
        isVideoRecording = true
        onVideoRecordingStateChange?(true)
    }

    func stopVideoRecording() async throws {
        stopVideoCount += 1
        isVideoRecording = false
        onVideoRecordingStateChange?(false)
    }

    func startAudioRecording() async throws {
        startAudioCount += 1
        isAudioRecording = true
        onAudioRecordingStateChange?(true)
    }

    func stopAudioRecording() async throws {
        stopAudioCount += 1
        isAudioRecording = false
        onAudioRecordingStateChange?(false)
    }

    func requestVisualCapture() async throws -> GlassesVisualCapture {
        visualCaptureRequestCount += 1
        let capture = GlassesVisualCapture(
            jpegData: Data([0xFF, 0xD8, 0xFF, 0xD9]),
            providerID: id
        )
        onVisualCapture?(capture)
        return capture
    }

    func prepareMediaTransfer() async throws -> [GlassesMediaItem] {
        prepareMediaCount += 1
        let item = GlassesMediaItem(
            remoteIdentifier: "capture.jpg",
            fileName: "capture.jpg",
            kind: .photo,
            providerID: id
        )
        mediaTransferState = .ready(itemCount: 1)
        return [item]
    }

    func continueMediaTransferAfterManualNetworkJoin() {}

    func downloadMediaItem(_ item: GlassesMediaItem, to destinationURL: URL) async throws {
        downloadMediaCount += 1
        try Data([0xFF, 0xD8, 0xFF, 0xD9]).write(to: destinationURL)
    }

    func finishMediaTransfer() async throws {
        finishMediaCount += 1
        mediaTransferState = .idle
    }

    func cancelMediaTransfer() {
        mediaTransferState = .idle
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

import Foundation
import NetworkExtension

struct HeyCyanAccessPoint: Equatable, Sendable {
    let ssid: String
    let passphrase: String
    let deviceIPv4Address: String

    init(ssid: String, passphrase: String, deviceIPv4Address: String) throws {
        let trimmedSSID = ssid.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedSSID.isEmpty, trimmedSSID.utf8.count <= 32 else {
            throw HeyCyanWiFiError.invalidSSID
        }
        guard (8 ... 63).contains(passphrase.utf8.count) else {
            throw HeyCyanWiFiError.invalidPassphrase
        }
        guard let normalizedAddress = Self.privateIPv4Address(deviceIPv4Address) else {
            throw HeyCyanWiFiError.invalidDeviceAddress
        }

        self.ssid = trimmedSSID
        self.passphrase = passphrase
        self.deviceIPv4Address = normalizedAddress
    }

    private static func privateIPv4Address(_ value: String) -> String? {
        let components = value.trimmingCharacters(in: .whitespacesAndNewlines)
            .split(separator: ".", omittingEmptySubsequences: false)
        guard components.count == 4 else { return nil }

        var octets = [UInt8]()
        for component in components {
            guard !component.isEmpty,
                  component.count <= 3,
                  component.allSatisfy(\.isNumber),
                  let octet = UInt8(component) else { return nil }
            octets.append(octet)
        }

        let isPrivate = octets[0] == 10 ||
            (octets[0] == 172 && (16 ... 31).contains(octets[1])) ||
            (octets[0] == 192 && octets[1] == 168)
        guard isPrivate, octets[3] != 0, octets[3] != 255 else { return nil }
        return octets.map(String.init).joined(separator: ".")
    }
}

enum HeyCyanWiFiState: Equatable, Sendable {
    case idle
    case joining(ssid: String)
    case joined(ssid: String, deviceIPv4Address: String)
    case failed(reason: String)
}

enum HeyCyanWiFiError: LocalizedError, Sendable {
    case invalidSSID
    case invalidPassphrase
    case invalidDeviceAddress
    case associationFailed(String)

    var errorDescription: String? {
        switch self {
        case .invalidSSID:
            return "The glasses returned an invalid Wi-Fi network name."
        case .invalidPassphrase:
            return "The glasses returned an invalid Wi-Fi passphrase."
        case .invalidDeviceAddress:
            return "The glasses returned an invalid private IPv4 address."
        case .associationFailed(let reason):
            return "Could not join the glasses Wi-Fi network: \(reason)"
        }
    }
}

/// Joins a *known* glasses-hosted access point. It never derives or hard-codes credentials.
/// A caller may enter this layer only after the BLE protocol supplies or otherwise verifies the
/// SSID, passphrase, device IP and readiness state for the current session.
@MainActor
final class HeyCyanWiFiCoordinator {
    var onStateChange: ((HeyCyanWiFiState) -> Void)?

    private(set) var state: HeyCyanWiFiState = .idle {
        didSet {
            guard state != oldValue else { return }
            onStateChange?(state)
        }
    }

    private let manager: NEHotspotConfigurationManager
    private var activeSSID: String?

    init(manager: NEHotspotConfigurationManager = .shared) {
        self.manager = manager
    }

    func join(_ accessPoint: HeyCyanAccessPoint) async throws {
        state = .joining(ssid: accessPoint.ssid)
        let configuration = NEHotspotConfiguration(
            ssid: accessPoint.ssid,
            passphrase: accessPoint.passphrase,
            isWEP: false
        )
        configuration.joinOnce = true

        do {
            try await apply(configuration)
            activeSSID = accessPoint.ssid
            state = .joined(
                ssid: accessPoint.ssid,
                deviceIPv4Address: accessPoint.deviceIPv4Address
            )
        } catch {
            state = .failed(reason: error.localizedDescription)
            throw error
        }
    }

    func leave() {
        if let activeSSID {
            manager.removeConfiguration(forSSID: activeSSID)
        }
        self.activeSSID = nil
        state = .idle
    }

    private func apply(_ configuration: NEHotspotConfiguration) async throws {
        try await withCheckedThrowingContinuation { continuation in
            manager.apply(configuration) { error in
                guard let error else {
                    continuation.resume()
                    return
                }

                let nsError = error as NSError
                if nsError.domain == NEHotspotConfigurationErrorDomain,
                   nsError.code == NEHotspotConfigurationError.alreadyAssociated.rawValue {
                    continuation.resume()
                    return
                }
                continuation.resume(
                    throwing: HeyCyanWiFiError.associationFailed(error.localizedDescription)
                )
            }
        }
    }
}

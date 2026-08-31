import Foundation
import Porcupine
import Security

enum PorcupinePhoneWakeWordError: LocalizedError {
    case accessKeyMissing
    case modelMissing
    case invalidModel
    case storageUnavailable

    var errorDescription: String? {
        switch self {
        case .accessKeyMissing: return "Add a Picovoice AccessKey before configuring Hey AD."
        case .modelMissing: return "Import or train an iOS .ppn model for Hey AD first."
        case .invalidModel: return "Choose a Porcupine iOS model with the .ppn extension."
        case .storageUnavailable: return "The wake-word model could not be stored on this iPhone."
        }
    }
}

private enum WakeWordKeychainError: LocalizedError {
    case unavailable

    var errorDescription: String? {
        "Secure wake-word configuration is unavailable."
    }
}

@MainActor
final class PorcupinePhoneWakeWordService: PhoneWakeWordDetecting {
    private let keychain = WakeWordKeychain(service: "com.achyutdalai.ADGlasses.porcupine")
    private let defaults: UserDefaults
    private let fileManager: FileManager
    private let phraseKey = "porcupine.wakePhrase.v1"
    private let accessKeyAccount = "access-key"
    private var manager: PorcupineManager?

    init(defaults: UserDefaults = .standard, fileManager: FileManager = .default) {
        self.defaults = defaults
        self.fileManager = fileManager
    }

    var phrase: String {
        defaults.string(forKey: phraseKey) ?? "Hey AD"
    }

    var configurationState: PhoneWakeWordConfigurationState {
        do {
            guard let key = try keychain.read(account: accessKeyAccount), !key.isEmpty else {
                return .missingAccessKey
            }
            guard fileManager.fileExists(atPath: modelURL.path) else { return .missingModel }
            return .ready
        } catch {
            return .unavailable("Secure configuration unavailable")
        }
    }

    func start(onDetection: @escaping @MainActor () -> Void) throws {
        stop()
        guard let accessKey = try keychain.read(account: accessKeyAccount), !accessKey.isEmpty else {
            throw PorcupinePhoneWakeWordError.accessKeyMissing
        }
        guard fileManager.fileExists(atPath: modelURL.path) else {
            throw PorcupinePhoneWakeWordError.modelMissing
        }

        let manager = try PorcupineManager(
            accessKey: accessKey,
            keywordPath: modelURL.path,
            onDetection: { _ in Task { @MainActor in onDetection() } }
        )
        try manager.start()
        self.manager = manager
    }

    func stop() {
        try? manager?.stop()
        try? manager?.delete()
        manager = nil
    }

    func saveAccessKey(_ value: String) throws {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            try keychain.delete(account: accessKeyAccount)
        } else {
            try keychain.write(trimmed, account: accessKeyAccount)
        }
    }

    func importModel(from sourceURL: URL, phrase: String) throws {
        guard sourceURL.pathExtension.lowercased() == "ppn" else {
            throw PorcupinePhoneWakeWordError.invalidModel
        }
        try createModelDirectory()
        let accessed = sourceURL.startAccessingSecurityScopedResource()
        defer { if accessed { sourceURL.stopAccessingSecurityScopedResource() } }
        if fileManager.fileExists(atPath: modelURL.path) {
            try fileManager.removeItem(at: modelURL)
        }
        try fileManager.copyItem(at: sourceURL, to: modelURL)
        try protectModelForLockedScreenUse()
        defaults.set(normalized(phrase), forKey: phraseKey)
    }

    func trainModel(phrase: String, language: String) async throws {
        guard let accessKey = try keychain.read(account: accessKeyAccount), !accessKey.isEmpty else {
            throw PorcupinePhoneWakeWordError.accessKeyMissing
        }
        try createModelDirectory()
        let outputPath = modelURL.path
        let requestedPhrase = normalized(phrase)
        try await Task.detached {
            try Porcupine.trainWakeWordFromPhrase(
                accessKey: accessKey,
                outputPath: outputPath,
                language: language,
                phrase: requestedPhrase
            )
        }.value
        try protectModelForLockedScreenUse()
        defaults.set(requestedPhrase, forKey: phraseKey)
    }

    private var modelURL: URL {
        modelDirectoryURL.appendingPathComponent("phone-wake-word_ios.ppn", isDirectory: false)
    }

    private var modelDirectoryURL: URL {
        let root = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.temporaryDirectory
        return root.appendingPathComponent("ADGlasses/WakeWords", isDirectory: true)
    }

    private func createModelDirectory() throws {
        do {
            try fileManager.createDirectory(
                at: modelDirectoryURL,
                withIntermediateDirectories: true,
                attributes: [
                    .protectionKey: FileProtectionType.completeUntilFirstUserAuthentication
                ]
            )
        } catch {
            throw PorcupinePhoneWakeWordError.storageUnavailable
        }
    }

    private func protectModelForLockedScreenUse() throws {
        do {
            try fileManager.setAttributes(
                [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
                ofItemAtPath: modelURL.path
            )
        } catch {
            throw PorcupinePhoneWakeWordError.storageUnavailable
        }
    }

    private func normalized(_ phrase: String) -> String {
        let trimmed = phrase.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "Hey AD" : trimmed
    }
}

private struct WakeWordKeychain {
    let service: String

    func read(account: String) throws -> String? {
        var query = identity(account: account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess,
              let data = result as? Data,
              let value = String(data: data, encoding: .utf8) else {
            throw WakeWordKeychainError.unavailable
        }
        return value
    }

    func write(_ value: String, account: String) throws {
        let data = Data(value.utf8)
        let identity = identity(account: account)
        let updateStatus = SecItemUpdate(
            identity as CFDictionary,
            [kSecValueData as String: data] as CFDictionary
        )
        if updateStatus == errSecSuccess { return }
        guard updateStatus == errSecItemNotFound else {
            throw WakeWordKeychainError.unavailable
        }
        var insertion = identity
        insertion[kSecValueData as String] = data
        insertion[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        guard SecItemAdd(insertion as CFDictionary, nil) == errSecSuccess else {
            throw WakeWordKeychainError.unavailable
        }
    }

    func delete(account: String) throws {
        let status = SecItemDelete(identity(account: account) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw WakeWordKeychainError.unavailable
        }
    }

    private func identity(account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
    }
}

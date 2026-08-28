import Combine
import Foundation
import Security

enum AIProviderKind: String, Codable, CaseIterable, Identifiable, Sendable {
    case openAI
    case google
    case deepSeek
    case openRouter
    case groq
    case custom

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .openAI: return "OpenAI"
        case .google: return "Google Gemini"
        case .deepSeek: return "DeepSeek"
        case .openRouter: return "OpenRouter"
        case .groq: return "Groq"
        case .custom: return "OpenAI-compatible"
        }
    }

    var defaultBaseURL: String {
        switch self {
        case .openAI: return "https://api.openai.com/v1"
        case .google: return "https://generativelanguage.googleapis.com/v1beta"
        case .deepSeek: return "https://api.deepseek.com"
        case .openRouter: return "https://openrouter.ai/api/v1"
        case .groq: return "https://api.groq.com/openai/v1"
        case .custom: return ""
        }
    }

    var defaultModel: String {
        switch self {
        case .openAI: return "gpt-5.6-luna"
        case .google: return "gemini-3.6-flash"
        case .deepSeek: return "deepseek-v4-flash"
        case .openRouter: return "openrouter/auto"
        case .groq: return "openai/gpt-oss-120b"
        case .custom: return ""
        }
    }

    var managesEndpoint: Bool { self != .custom }
}

struct AIProfile: Codable, Identifiable, Equatable, Sendable {
    var id: UUID
    var name: String
    var provider: AIProviderKind
    var baseURL: String
    var model: String

    static func new(provider: AIProviderKind = .openAI, existingCount: Int = 0) -> AIProfile {
        AIProfile(
            id: UUID(),
            name: existingCount == 0 ? provider.displayName : "\(provider.displayName) \(existingCount + 1)",
            provider: provider,
            baseURL: provider.defaultBaseURL,
            model: provider.defaultModel
        )
    }
}

enum AIConfigurationError: LocalizedError {
    case missingProfile
    case missingCredential
    case invalidName
    case invalidModel
    case invalidEndpoint
    case credentialScopeChanged
    case secureStorage(OSStatus)
    case invalidResponse
    case requestFailed(String)

    var errorDescription: String? {
        switch self {
        case .missingProfile:
            return "Configure a Cloud AI profile in Settings first."
        case .missingCredential:
            return "Enter an API key for this Cloud AI profile."
        case .invalidName:
            return "Enter a name for this profile."
        case .invalidModel:
            return "Enter a model name."
        case .invalidEndpoint:
            return "Custom API endpoints must be valid HTTPS URLs."
        case .credentialScopeChanged:
            return "Enter a new API key after changing the provider or custom endpoint."
        case .secureStorage(let status):
            return "The API key could not be saved securely (\(status))."
        case .invalidResponse:
            return "The AI service returned a response AD Glasses could not read."
        case .requestFailed(let message):
            return message
        }
    }
}

@MainActor
final class AIProfileStore: ObservableObject {
    @Published private(set) var profiles: [AIProfile] = []
    @Published private(set) var activeProfileID: UUID?

    private let defaults: UserDefaults
    private let keychain: AIKeychain
    private let profilesKey = "cloudAI.profiles.v1"
    private let activeProfileKey = "cloudAI.activeProfile.v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        keychain = AIKeychain(service: "com.achyutdalai.ADGlasses.cloud-ai")
        load()
    }

    var activeProfile: AIProfile? {
        if let activeProfileID,
           let profile = profiles.first(where: { $0.id == activeProfileID }) {
            return profile
        }
        return profiles.first
    }

    var isConfigured: Bool {
        guard let activeProfile else { return false }
        return hasCredential(for: activeProfile.id)
    }

    func hasCredential(for profileID: UUID) -> Bool {
        (try? keychain.read(account: profileID.uuidString))?.isEmpty == false
    }

    func credential(for profileID: UUID) throws -> String {
        guard let credential = try keychain.read(account: profileID.uuidString),
              !credential.isEmpty else {
            throw AIConfigurationError.missingCredential
        }
        return credential
    }

    @discardableResult
    func save(
        _ draft: AIProfile,
        apiKeyReplacement: String,
        makeActive: Bool
    ) throws -> AIProfile {
        var profile = draft
        profile.name = profile.name.trimmingCharacters(in: .whitespacesAndNewlines)
        profile.model = profile.model.trimmingCharacters(in: .whitespacesAndNewlines)
        profile.baseURL = normalizedBaseURL(
            profile.provider.managesEndpoint ? profile.provider.defaultBaseURL : profile.baseURL
        )

        guard !profile.name.isEmpty else { throw AIConfigurationError.invalidName }
        guard !profile.model.isEmpty else { throw AIConfigurationError.invalidModel }
        guard validHTTPSBaseURL(profile.baseURL) else { throw AIConfigurationError.invalidEndpoint }

        let replacement = normalizedCredential(apiKeyReplacement)
        let existingProfile = profiles.first(where: { $0.id == profile.id })
        let isExisting = existingProfile != nil
        if let existingProfile, replacement.isEmpty {
            let changedCredentialScope = existingProfile.provider != profile.provider ||
                (!profile.provider.managesEndpoint && existingProfile.baseURL != profile.baseURL)
            if changedCredentialScope {
                throw AIConfigurationError.credentialScopeChanged
            }
        }
        if replacement.isEmpty && !hasCredential(for: profile.id) {
            throw AIConfigurationError.missingCredential
        }
        if !isExisting && replacement.isEmpty {
            throw AIConfigurationError.missingCredential
        }

        if !replacement.isEmpty {
            try keychain.write(replacement, account: profile.id.uuidString)
        }

        if let index = profiles.firstIndex(where: { $0.id == profile.id }) {
            profiles[index] = profile
        } else {
            profiles.append(profile)
        }

        if makeActive || activeProfileID == nil {
            activeProfileID = profile.id
        }
        persist()
        return profile
    }

    func setActive(_ profileID: UUID) {
        guard profiles.contains(where: { $0.id == profileID }) else { return }
        activeProfileID = profileID
        persist()
    }

    func delete(_ profileID: UUID) throws {
        profiles.removeAll { $0.id == profileID }
        try keychain.delete(account: profileID.uuidString)
        if activeProfileID == profileID {
            activeProfileID = profiles.first?.id
        }
        persist()
    }

    private func load() {
        if let data = defaults.data(forKey: profilesKey),
           let saved = try? JSONDecoder().decode([AIProfile].self, from: data) {
            profiles = saved
        }
        if let value = defaults.string(forKey: activeProfileKey),
           let id = UUID(uuidString: value),
           profiles.contains(where: { $0.id == id }) {
            activeProfileID = id
        } else {
            activeProfileID = profiles.first?.id
        }
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(profiles) {
            defaults.set(data, forKey: profilesKey)
        }
        defaults.set(activeProfileID?.uuidString, forKey: activeProfileKey)
    }

    private func normalizedBaseURL(_ raw: String) -> String {
        var value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while value.hasSuffix("/") { value.removeLast() }
        for suffix in ["/chat/completions", "/models", "/responses"] where value.lowercased().hasSuffix(suffix) {
            value.removeLast(suffix.count)
            while value.hasSuffix("/") { value.removeLast() }
        }
        return value
    }

    private func validHTTPSBaseURL(_ raw: String) -> Bool {
        guard let components = URLComponents(string: raw),
              components.scheme?.lowercased() == "https",
              components.host?.isEmpty == false else { return false }
        return true
    }

    private func normalizedCredential(_ raw: String) -> String {
        var value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.lowercased().hasPrefix("authorization:") {
            value = String(value.dropFirst("authorization:".count)).trimmingCharacters(in: .whitespaces)
        }
        if value.lowercased().hasPrefix("bearer ") {
            value = String(value.dropFirst("bearer ".count)).trimmingCharacters(in: .whitespaces)
        }
        if value.count >= 2,
           (value.hasPrefix("\"") && value.hasSuffix("\"")) ||
           (value.hasPrefix("'") && value.hasSuffix("'")) {
            value.removeFirst()
            value.removeLast()
        }
        return value.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

private struct AIKeychain {
    let service: String

    func read(account: String) throws -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess,
              let data = item as? Data,
              let value = String(data: data, encoding: .utf8) else {
            throw AIConfigurationError.secureStorage(status)
        }
        return value
    }

    func write(_ value: String, account: String) throws {
        let data = Data(value.utf8)
        let identity: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let attributes: [String: Any] = [kSecValueData as String: data]
        let updateStatus = SecItemUpdate(identity as CFDictionary, attributes as CFDictionary)
        if updateStatus == errSecSuccess { return }
        guard updateStatus == errSecItemNotFound else {
            throw AIConfigurationError.secureStorage(updateStatus)
        }

        var insertion = identity
        insertion[kSecValueData as String] = data
        insertion[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let addStatus = SecItemAdd(insertion as CFDictionary, nil)
        guard addStatus == errSecSuccess else {
            throw AIConfigurationError.secureStorage(addStatus)
        }
    }

    func delete(account: String) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw AIConfigurationError.secureStorage(status)
        }
    }
}

protocol AIResponding: Sendable {
    func response(
        to messages: [ConversationMessage],
        profile: AIProfile,
        credential: String
    ) async throws -> String
}

struct CloudAIClient: AIResponding {
    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func response(
        to messages: [ConversationMessage],
        profile: AIProfile,
        credential: String
    ) async throws -> String {
        switch profile.provider {
        case .openAI:
            return try await openAIResponse(messages: messages, profile: profile, credential: credential)
        case .google:
            return try await geminiResponse(messages: messages, profile: profile, credential: credential)
        case .deepSeek, .openRouter, .groq, .custom:
            return try await compatibleResponse(messages: messages, profile: profile, credential: credential)
        }
    }

    private func openAIResponse(
        messages: [ConversationMessage],
        profile: AIProfile,
        credential: String
    ) async throws -> String {
        let url = try endpoint(base: profile.baseURL, suffix: "/responses")
        let input = messages.map { message in
            ["role": message.role.wireRole, "content": message.text]
        }
        let payload: [String: Any] = [
            "model": profile.model,
            "instructions": Self.systemInstruction,
            "input": input
        ]
        let json = try await post(url: url, credential: credential, apiKeyHeader: nil, payload: payload)

        if let text = json["output_text"] as? String, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return text.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if let output = json["output"] as? [[String: Any]] {
            let text = output
                .compactMap { $0["content"] as? [[String: Any]] }
                .flatMap { $0 }
                .filter { ($0["type"] as? String) == "output_text" }
                .compactMap { $0["text"] as? String }
                .joined()
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if !text.isEmpty { return text }
        }
        throw AIConfigurationError.invalidResponse
    }

    private func geminiResponse(
        messages: [ConversationMessage],
        profile: AIProfile,
        credential: String
    ) async throws -> String {
        let model = profile.model
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "models/", with: "")
            .components(separatedBy: ":generateContent").first ?? profile.model
        let url = try endpoint(base: profile.baseURL, suffix: "/models/\(model):generateContent")
        let contents: [[String: Any]] = messages.map { message in
            [
                "role": message.role == .assistant ? "model" : "user",
                "parts": [["text": message.text]]
            ]
        }
        let payload: [String: Any] = [
            "systemInstruction": ["parts": [["text": Self.systemInstruction]]],
            "contents": contents
        ]
        let json = try await post(
            url: url,
            credential: nil,
            apiKeyHeader: credential,
            payload: payload
        )
        guard let candidates = json["candidates"] as? [[String: Any]],
              let content = candidates.first?["content"] as? [String: Any],
              let parts = content["parts"] as? [[String: Any]] else {
            throw AIConfigurationError.invalidResponse
        }
        let text = parts.compactMap { $0["text"] as? String }
            .joined()
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { throw AIConfigurationError.invalidResponse }
        return text
    }

    private func compatibleResponse(
        messages: [ConversationMessage],
        profile: AIProfile,
        credential: String
    ) async throws -> String {
        let url = try endpoint(base: profile.baseURL, suffix: "/chat/completions")
        let payloadMessages: [[String: String]] = [
            ["role": "system", "content": Self.systemInstruction]
        ] + messages.map { ["role": $0.role.wireRole, "content": $0.text] }
        let payload: [String: Any] = [
            "model": profile.model,
            "messages": payloadMessages
        ]
        let json = try await post(url: url, credential: credential, apiKeyHeader: nil, payload: payload)
        guard let choices = json["choices"] as? [[String: Any]],
              let message = choices.first?["message"] as? [String: Any] else {
            throw AIConfigurationError.invalidResponse
        }
        if let content = message["content"] as? String {
            let text = content.trimmingCharacters(in: .whitespacesAndNewlines)
            if !text.isEmpty { return text }
        }
        if let parts = message["content"] as? [[String: Any]] {
            let text = parts.compactMap { $0["text"] as? String }
                .joined()
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if !text.isEmpty { return text }
        }
        throw AIConfigurationError.invalidResponse
    }

    private func endpoint(base: String, suffix: String) throws -> URL {
        var normalized = base.trimmingCharacters(in: .whitespacesAndNewlines)
        while normalized.hasSuffix("/") { normalized.removeLast() }
        guard let components = URLComponents(string: normalized),
              components.scheme?.lowercased() == "https",
              components.host?.isEmpty == false,
              let url = URL(string: normalized + suffix) else {
            throw AIConfigurationError.invalidEndpoint
        }
        return url
    }

    private func post(
        url: URL,
        credential: String?,
        apiKeyHeader: String?,
        payload: [String: Any]
    ) async throws -> [String: Any] {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 60
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let credential {
            request.setValue("Bearer \(credential)", forHTTPHeaderField: "Authorization")
        }
        if let apiKeyHeader {
            request.setValue(apiKeyHeader, forHTTPHeaderField: "x-goog-api-key")
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                throw AIConfigurationError.invalidResponse
            }
            let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
            guard 200..<300 ~= http.statusCode else {
                let providerMessage = ((json["error"] as? [String: Any])?["message"] as? String)
                    ?? (json["message"] as? String)
                throw AIConfigurationError.requestFailed(
                    providerMessage?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                        ?? "The AI service returned HTTP \(http.statusCode)."
                )
            }
            return json
        } catch let error as AIConfigurationError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw AIConfigurationError.requestFailed("Could not reach the AI service: \(error.localizedDescription)")
        }
    }

    private static let systemInstruction = "You are AD Assistant, the quiet companion for AD Glasses. Be concise, useful, and honest. Help the user understand or continue from what their glasses captured; do not pretend to control hardware or access data that was not provided."
}

private extension ConversationRole {
    var wireRole: String {
        switch self {
        case .user: return "user"
        case .assistant: return "assistant"
        }
    }
}

private extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}

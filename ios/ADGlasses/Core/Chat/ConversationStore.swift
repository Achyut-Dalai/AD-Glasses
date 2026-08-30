import Foundation

actor ConversationStore {
    private let fileURL: URL
    private let fileManager: FileManager

    init(fileManager: FileManager = .default, fileURL: URL? = nil) {
        self.fileManager = fileManager
        if let fileURL {
            self.fileURL = fileURL
        } else {
            let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? fileManager.temporaryDirectory
            self.fileURL = base
                .appendingPathComponent("ADGlasses", isDirectory: true)
                .appendingPathComponent("conversations.json", isDirectory: false)
        }
    }

    func load() throws -> [ConversationThread] {
        guard fileManager.fileExists(atPath: fileURL.path) else { return [] }
        let data = try Data(contentsOf: fileURL)
        let decoded: [ConversationThread]
        do {
            decoded = try Self.decoder.decode([ConversationThread].self, from: data)
        } catch {
            decoded = try Self.legacyDecoder.decode([ConversationThread].self, from: data)
        }
        return decoded
            .sorted { $0.updatedAt > $1.updatedAt }
    }

    func save(_ conversations: [ConversationThread]) throws {
        let folder = fileURL.deletingLastPathComponent()
        try fileManager.createDirectory(at: folder, withIntermediateDirectories: true)
        let data = try Self.encoder.encode(conversations.sorted { $0.updatedAt > $1.updatedAt })
        try data.write(to: fileURL, options: [.atomic, .completeFileProtection])
    }

    func deleteAll() throws {
        guard fileManager.fileExists(atPath: fileURL.path) else { return }
        try fileManager.removeItem(at: fileURL)
    }

    private static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .deferredToDate
        encoder.outputFormatting = [.sortedKeys]
        return encoder
    }()

    private static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .deferredToDate
        return decoder
    }()

    private static let legacyDecoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()
}

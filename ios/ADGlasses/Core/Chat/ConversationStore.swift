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
        // Glasses voice turns may complete while the screen is locked. Keep this encrypted until
        // the first unlock after boot, then available to the running companion app across locks.
        try data.write(
            to: fileURL,
            options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication]
        )
    }

    func saveImageAttachment(
        _ data: Data,
        pixelWidth: Int,
        pixelHeight: Int,
        id: UUID = UUID()
    ) throws -> ConversationImageAttachment {
        let folder = imageFolderURL
        try fileManager.createDirectory(at: folder, withIntermediateDirectories: true)
        let fileName = "\(id.uuidString).jpg"
        let destination = folder.appendingPathComponent(fileName, isDirectory: false)
        try data.write(
            to: destination,
            options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication]
        )
        return ConversationImageAttachment(
            id: id,
            fileName: fileName,
            pixelWidth: pixelWidth,
            pixelHeight: pixelHeight
        )
    }

    func loadImageAttachment(_ attachment: ConversationImageAttachment) throws -> Data {
        try Data(contentsOf: imageURL(for: attachment))
    }

    func deleteImageAttachments(_ attachments: [ConversationImageAttachment]) throws {
        for attachment in attachments {
            let url = imageURL(for: attachment)
            guard fileManager.fileExists(atPath: url.path) else { continue }
            try fileManager.removeItem(at: url)
        }
    }

    func deleteAll() throws {
        if fileManager.fileExists(atPath: fileURL.path) {
            try fileManager.removeItem(at: fileURL)
        }
        if fileManager.fileExists(atPath: imageFolderURL.path) {
            try fileManager.removeItem(at: imageFolderURL)
        }
    }

    private var imageFolderURL: URL {
        fileURL.deletingLastPathComponent()
            .appendingPathComponent("ConversationImages", isDirectory: true)
    }

    private func imageURL(for attachment: ConversationImageAttachment) -> URL {
        imageFolderURL.appendingPathComponent(attachment.fileName, isDirectory: false)
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

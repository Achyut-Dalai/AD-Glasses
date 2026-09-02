import CoreSpotlight
import Foundation
import UniformTypeIdentifiers

actor LibraryStore {
    private static let spotlightDomain = "com.achyutdalai.ADGlasses.library"
    private static let spotlightPrefix = "ad-library:"

    private let fileManager: FileManager
    private let rootURL: URL
    private nonisolated let filesURL: URL
    private nonisolated let enhancedPhotosURL: URL
    private let indexURL: URL

    init(fileManager: FileManager = .default, rootURL: URL? = nil) {
        self.fileManager = fileManager
        let base = rootURL ?? (fileManager.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask
        ).first ?? fileManager.temporaryDirectory)
            .appendingPathComponent("ADGlasses/Library", isDirectory: true)
        self.rootURL = base
        filesURL = base.appendingPathComponent("Files", isDirectory: true)
        enhancedPhotosURL = base.appendingPathComponent("EnhancedPhotos", isDirectory: true)
        indexURL = base.appendingPathComponent("library.json", isDirectory: false)
    }

    func load() throws -> [LibraryItem] {
        try ensureDirectories()
        guard fileManager.fileExists(atPath: indexURL.path) else {
            reindexSpotlight([])
            return []
        }
        let data = try Data(contentsOf: indexURL)
        let decoded: [LibraryItem]
        do {
            decoded = try Self.decoder.decode([LibraryItem].self, from: data)
        } catch {
            // Early builds used whole-second ISO-8601 dates. Keep those local libraries readable
            // while writing the exact Foundation Date representation going forward.
            decoded = try Self.legacyDecoder.decode([LibraryItem].self, from: data)
        }
        let items = decoded
            .filter { safeRelativeName($0.relativeFileName) != nil }
            .filter { fileManager.fileExists(atPath: fileURL(for: $0).path) }
            .sorted { $0.createdAt > $1.createdAt }
        reindexSpotlight(items)
        return items
    }

    func saveTranscript(title: String, text: String) throws -> LibraryItem {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { throw LibraryStoreError.emptyTranscript }
        var items = try load()
        let id = UUID()
        let relativeName = "\(id.uuidString).txt"
        try Data(value.utf8).write(
            to: filesURL.appendingPathComponent(relativeName),
            options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication]
        )
        let normalizedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let item = LibraryItem(
            id: id,
            title: normalizedTitle.isEmpty ? defaultTranscriptTitle() : normalizedTitle,
            kind: .transcript,
            relativeFileName: relativeName
        )
        items.insert(item, at: 0)
        try saveIndex(items)
        return item
    }

    func importFile(
        from sourceURL: URL,
        title: String,
        kind: LibraryItemKind,
        sourceProviderID: String?,
        sourceReference: String? = nil
    ) throws -> LibraryItem {
        guard sourceURL.isFileURL,
              fileManager.fileExists(atPath: sourceURL.path) else {
            throw LibraryStoreError.invalidSourceFile
        }
        let sourceExtension = sourceURL.pathExtension.lowercased()
        guard Self.allowedExtensions[kind]?.contains(sourceExtension) == true else {
            throw LibraryStoreError.invalidFileName
        }

        try ensureDirectories()
        var items = try load()
        let id = UUID()
        let relativeName = "\(id.uuidString).\(sourceExtension)"
        let destination = filesURL.appendingPathComponent(relativeName)
        try fileManager.copyItem(at: sourceURL, to: destination)
        try fileManager.setAttributes(
            [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
            ofItemAtPath: destination.path
        )

        let normalizedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let item = LibraryItem(
            id: id,
            title: normalizedTitle.isEmpty ? sourceURL.deletingPathExtension().lastPathComponent : normalizedTitle,
            kind: kind,
            relativeFileName: relativeName,
            sourceProviderID: sourceProviderID,
            sourceReference: sourceReference
        )
        items.insert(item, at: 0)
        do {
            try saveIndex(items)
        } catch {
            try? fileManager.removeItem(at: destination)
            throw error
        }
        return item
    }

    func setFavorite(_ favorite: Bool, itemID: UUID) throws -> [LibraryItem] {
        var items = try load()
        guard let index = items.firstIndex(where: { $0.id == itemID }) else { return items }
        items[index].isFavorite = favorite
        try saveIndex(items)
        return items
    }

    func delete(itemID: UUID) throws -> [LibraryItem] {
        var items = try load()
        guard let item = items.first(where: { $0.id == itemID }) else { return items }

        let sourceURL = fileURL(for: item)
        if fileManager.fileExists(atPath: sourceURL.path) {
            try fileManager.removeItem(at: sourceURL)
        }

        let enhancedURL = enhancedPhotoURL(for: item)
        if fileManager.fileExists(atPath: enhancedURL.path) {
            try fileManager.removeItem(at: enhancedURL)
        }

        items.removeAll { $0.id == itemID }
        try saveIndex(items)
        return items
    }

    func existingEnhancedPhotoURL(for item: LibraryItem) -> URL? {
        guard item.kind == .photo else { return nil }
        let url = enhancedPhotoURL(for: item)
        return fileManager.fileExists(atPath: url.path) ? url : nil
    }

    func saveEnhancedPhoto(_ data: Data, for item: LibraryItem) throws -> URL {
        guard item.kind == .photo, !data.isEmpty else {
            throw LibraryStoreError.invalidSourceFile
        }
        try ensureDirectories()
        let destination = enhancedPhotoURL(for: item)
        try data.write(
            to: destination,
            options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication]
        )
        return destination
    }

    nonisolated func fileURL(for item: LibraryItem) -> URL {
        filesURL.appendingPathComponent(item.relativeFileName, isDirectory: false)
    }

    nonisolated func enhancedPhotoURL(for item: LibraryItem) -> URL {
        enhancedPhotosURL.appendingPathComponent("\(item.id.uuidString)-ad-v1.jpg", isDirectory: false)
    }

    private func ensureDirectories() throws {
        let attributes: [FileAttributeKey: Any] = [
            .protectionKey: FileProtectionType.completeUntilFirstUserAuthentication
        ]
        try fileManager.createDirectory(
            at: filesURL,
            withIntermediateDirectories: true,
            attributes: attributes
        )
        try fileManager.createDirectory(
            at: enhancedPhotosURL,
            withIntermediateDirectories: true,
            attributes: attributes
        )
    }

    private func saveIndex(_ items: [LibraryItem]) throws {
        try ensureDirectories()
        let sorted = items.sorted { $0.createdAt > $1.createdAt }
        let data = try Self.encoder.encode(sorted)
        try data.write(
            to: indexURL,
            options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication]
        )
        reindexSpotlight(sorted)
    }

    /// Keeps the system's semantic/lexical index aligned with the protected local Library. This is
    /// best-effort metadata indexing: a Spotlight failure never makes a local save or delete fail.
    private func reindexSpotlight(_ items: [LibraryItem]) {
        let searchableItems = items.map(makeSearchableItem)
        let index = CSSearchableIndex.default()
        index.deleteSearchableItems(withDomainIdentifiers: [Self.spotlightDomain]) { _ in
            guard !searchableItems.isEmpty else { return }
            index.indexSearchableItems(searchableItems) { _ in }
        }
    }

    private func makeSearchableItem(_ item: LibraryItem) -> CSSearchableItem {
        let contentType: UTType
        switch item.kind {
        case .photo:
            contentType = .image
        case .video:
            contentType = .movie
        case .audio:
            contentType = .audio
        case .transcript:
            contentType = .plainText
        }

        let attributes = CSSearchableItemAttributeSet(contentType: contentType)
        attributes.title = item.title
        attributes.displayName = item.title
        attributes.contentURL = fileURL(for: item)
        attributes.creationDate = item.createdAt
        attributes.metadataModificationDate = item.createdAt
        attributes.containerTitle = "AD Glasses Library"
        attributes.containerDisplayName = "AD Glasses Library"
        attributes.keywords = spotlightKeywords(for: item)
        attributes.rankingHint = NSNumber(value: item.isFavorite ? 1.0 : 0.35)
        attributes.contentDescription = spotlightDescription(for: item)

        if item.kind == .transcript,
           let text = try? String(contentsOf: fileURL(for: item), encoding: .utf8) {
            let searchableText = String(text.prefix(20_000))
            attributes.textContent = searchableText
            attributes.transcribedTextContent = searchableText
        }

        let searchable = CSSearchableItem(
            uniqueIdentifier: Self.spotlightPrefix + item.id.uuidString,
            domainIdentifier: Self.spotlightDomain,
            attributeSet: attributes
        )
        searchable.expirationDate = .distantFuture
        return searchable
    }

    private func spotlightKeywords(for item: LibraryItem) -> [String] {
        var values = ["AD Glasses", "Library", item.kind.rawValue]
        if item.isFavorite { values.append("favorite") }
        if item.sourceProviderID != nil { values.append("glasses") }
        switch item.kind {
        case .photo:
            values.append(contentsOf: ["photo", "picture", "capture"])
        case .video:
            values.append(contentsOf: ["video", "recording", "capture"])
        case .audio:
            values.append(contentsOf: ["audio", "recording", "soundbite"])
        case .transcript:
            values.append(contentsOf: ["transcript", "note", "soundbite", "text"])
        }
        return values
    }

    private func spotlightDescription(for item: LibraryItem) -> String {
        switch item.kind {
        case .photo:
            return "Photo saved in AD Glasses Library"
        case .video:
            return "Video saved in AD Glasses Library"
        case .audio:
            return "Audio recording saved in AD Glasses Library"
        case .transcript:
            return "Transcript or note saved in AD Glasses Library"
        }
    }

    private func safeRelativeName(_ value: String) -> String? {
        guard !value.isEmpty,
              value == URL(fileURLWithPath: value).lastPathComponent,
              !value.contains(".."),
              !value.contains("/"),
              !value.contains("\\") else { return nil }
        return value
    }

    private func defaultTranscriptTitle() -> String {
        "Soundbite \(Date().formatted(date: .abbreviated, time: .shortened))"
    }

    private static let allowedExtensions: [LibraryItemKind: Set<String>] = [
        .photo: ["jpg", "jpeg", "heic", "png"],
        .video: ["mp4", "mov"],
        .audio: ["opus", "ogg", "m4a", "wav", "caf"],
        .transcript: ["txt", "md"]
    ]

    private static let encoder: JSONEncoder = {
        let value = JSONEncoder()
        value.dateEncodingStrategy = .deferredToDate
        value.outputFormatting = [.sortedKeys]
        return value
    }()

    private static let decoder: JSONDecoder = {
        let value = JSONDecoder()
        value.dateDecodingStrategy = .deferredToDate
        return value
    }()

    private static let legacyDecoder: JSONDecoder = {
        let value = JSONDecoder()
        value.dateDecodingStrategy = .iso8601
        return value
    }()
}

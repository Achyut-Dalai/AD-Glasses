import Foundation

enum LibraryItemKind: String, Codable, CaseIterable, Sendable {
    case photo
    case video
    case audio
    case transcript
}

struct LibraryItem: Identifiable, Codable, Equatable, Sendable {
    let id: UUID
    var title: String
    let kind: LibraryItemKind
    let relativeFileName: String
    let sourceProviderID: String?
    let sourceReference: String?
    let createdAt: Date
    var isFavorite: Bool

    init(
        id: UUID = UUID(),
        title: String,
        kind: LibraryItemKind,
        relativeFileName: String,
        sourceProviderID: String? = nil,
        sourceReference: String? = nil,
        createdAt: Date = Date(),
        isFavorite: Bool = false
    ) {
        self.id = id
        self.title = title
        self.kind = kind
        self.relativeFileName = relativeFileName
        self.sourceProviderID = sourceProviderID
        self.sourceReference = sourceReference
        self.createdAt = createdAt
        self.isFavorite = isFavorite
    }
}

enum LibraryStoreError: LocalizedError, Sendable {
    case invalidSourceFile
    case invalidFileName
    case emptyTranscript

    var errorDescription: String? {
        switch self {
        case .invalidSourceFile:
            return "The item could not be found on this iPhone."
        case .invalidFileName:
            return "The item has an unsafe filename."
        case .emptyTranscript:
            return "There is no transcript to save."
        }
    }
}

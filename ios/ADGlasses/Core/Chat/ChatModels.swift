import Foundation

enum ConversationRole: String, Codable, Sendable {
    case user
    case assistant
}

struct ConversationImageAttachment: Identifiable, Codable, Equatable, Sendable {
    let id: UUID
    let fileName: String
    let pixelWidth: Int
    let pixelHeight: Int

    init(
        id: UUID = UUID(),
        fileName: String,
        pixelWidth: Int,
        pixelHeight: Int
    ) {
        self.id = id
        self.fileName = fileName
        self.pixelWidth = pixelWidth
        self.pixelHeight = pixelHeight
    }
}

struct ConversationMessage: Identifiable, Codable, Equatable, Sendable {
    let id: UUID
    let role: ConversationRole
    let text: String
    let createdAt: Date
    let imageAttachment: ConversationImageAttachment?

    init(
        id: UUID = UUID(),
        role: ConversationRole,
        text: String,
        createdAt: Date = Date(),
        imageAttachment: ConversationImageAttachment? = nil
    ) {
        self.id = id
        self.role = role
        self.text = text
        self.createdAt = createdAt
        self.imageAttachment = imageAttachment
    }
}

struct ConversationThread: Identifiable, Codable, Equatable, Sendable {
    let id: UUID
    var title: String
    var messages: [ConversationMessage]
    let createdAt: Date
    var updatedAt: Date

    init(
        id: UUID = UUID(),
        title: String = "New conversation",
        messages: [ConversationMessage] = [],
        createdAt: Date = Date(),
        updatedAt: Date = Date()
    ) {
        self.id = id
        self.title = title
        self.messages = messages
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    var preview: String {
        messages.last?.text ?? "No messages"
    }
}

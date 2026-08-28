import Foundation

enum ConversationRole: Sendable {
    case user
    case assistant
}

struct ConversationMessage: Identifiable, Equatable, Sendable {
    let id: UUID
    let role: ConversationRole
    let text: String
    let createdAt: Date

    init(
        id: UUID = UUID(),
        role: ConversationRole,
        text: String,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.role = role
        self.text = text
        self.createdAt = createdAt
    }
}

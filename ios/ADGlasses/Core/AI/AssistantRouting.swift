import Foundation

enum AssistantRequestSource: Sendable {
    case chat
    case phoneVoice
    case glassesVoice
    case lensImage
}

struct AssistantRequest: Sendable {
    let text: String
    let source: AssistantRequestSource
    let hasImage: Bool
}

enum AssistantRoute: Equatable, Sendable {
    case clarify
    case conversation
    case visualQuestion
}

/// Structural routing for the Assistant entry point.
///
/// Natural-language similarity is intentionally not used to guess tools. A route is selected from
/// input that is actually present, and future tools must register a real executor before they can
/// become a route. This avoids sending a weather-looking sentence, for example, to a service the
/// iOS app does not implement.
struct AssistantRequestRouter: Sendable {
    func route(_ request: AssistantRequest) -> AssistantRoute {
        guard !request.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return .clarify
        }
        return request.hasImage ? .visualQuestion : .conversation
    }
}

enum ConversationContextPolicy {
    static let maximumMessages = 40
    static let maximumCharacters = 48_000

    /// Keeps newest complete messages under a predictable request budget without modifying the
    /// locally saved conversation. The newest message is always retained.
    static func requestMessages(from messages: [ConversationMessage]) -> [ConversationMessage] {
        var result = [ConversationMessage]()
        var characters = 0

        for message in messages.suffix(maximumMessages).reversed() {
            let nextCount = message.text.count
            if !result.isEmpty, characters + nextCount > maximumCharacters { break }
            result.append(message)
            characters += nextCount
        }
        return result.reversed()
    }
}

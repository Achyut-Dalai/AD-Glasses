import Foundation

struct TextTranslationResult: Equatable, Sendable {
    let sourceText: String
    let translatedText: String
    let sourceLanguage: String
    let targetLanguage: String
}

enum TextTranslationError: LocalizedError, Sendable {
    case requiresIOS18
    case operationInProgress

    var errorDescription: String? {
        switch self {
        case .requiresIOS18:
            return "Native translation requires iOS 18 or later."
        case .operationInProgress:
            return "Another translation is already in progress."
        }
    }
}

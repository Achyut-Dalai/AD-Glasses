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
    case sourceLanguageUndetermined
    case unsupportedLanguagePair
    case translationFailed(String)

    var errorDescription: String? {
        switch self {
        case .requiresIOS18:
            return "Native translation requires iOS 18 or later."
        case .operationInProgress:
            return "Another translation is already in progress."
        case .sourceLanguageUndetermined:
            return "Apple Translation could not identify the source language. Try a slightly longer phrase."
        case .unsupportedLanguagePair:
            return "Apple Translation does not support this source and target language combination."
        case .translationFailed(let reason):
            return "Apple Translation could not complete this request: \(reason)"
        }
    }
}

/// App-wide bridge used by deterministic Jarvis shortcuts without making the iOS 17-compatible
/// voice layer depend directly on the iOS 18 Translation framework types. NativeTranslationHost
/// installs the closures while Apple's translationTask is alive.
@MainActor
final class AssistantTranslationBridge {
    static let shared = AssistantTranslationBridge()

    typealias TranslateHandler = @MainActor (
        _ text: String,
        _ sourceLanguageCode: String?,
        _ targetLanguageCode: String
    ) async throws -> TextTranslationResult

    typealias StartLiveHandler = @MainActor (
        _ sourceLanguageCode: String,
        _ targetLanguageCode: String,
        _ speechOutput: SpeechOutputController
    ) async -> Bool

    private(set) var translateHandler: TranslateHandler?
    private(set) var startLiveHandler: StartLiveHandler?
    private(set) var stopLiveHandler: (@MainActor () async -> Void)?
    private(set) var liveStateHandler: (@MainActor () -> Bool)?

    private init() {}

    func install(
        translate: @escaping TranslateHandler,
        startLive: @escaping StartLiveHandler,
        stopLive: @escaping @MainActor () async -> Void,
        isLiveRunning: @escaping @MainActor () -> Bool
    ) {
        translateHandler = translate
        startLiveHandler = startLive
        stopLiveHandler = stopLive
        liveStateHandler = isLiveRunning
    }

    func clear() {
        translateHandler = nil
        startLiveHandler = nil
        stopLiveHandler = nil
        liveStateHandler = nil
    }

    var isAvailable: Bool { translateHandler != nil }
    var isLiveRunning: Bool { liveStateHandler?() ?? false }

    func translate(
        _ text: String,
        sourceLanguageCode: String? = nil,
        targetLanguageCode: String
    ) async throws -> TextTranslationResult {
        guard let translateHandler else { throw TextTranslationError.requiresIOS18 }
        return try await translateHandler(text, sourceLanguageCode, targetLanguageCode)
    }

    func startLive(
        sourceLanguageCode: String,
        targetLanguageCode: String,
        speechOutput: SpeechOutputController
    ) async -> Bool {
        guard let startLiveHandler else { return false }
        return await startLiveHandler(sourceLanguageCode, targetLanguageCode, speechOutput)
    }

    func stopLive() async {
        await stopLiveHandler?()
    }
}
